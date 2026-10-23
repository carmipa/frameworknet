package org.framework.net.analiseDidatica.infrastructure.historico;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.analiseDidatica.config.AnaliseDidaticaConfig;
import org.framework.net.analiseDidatica.exception.HistoricoPersistenciaException;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PROPÓSITO DE NEGÓCIO: histórico das consultas da Análise Didática (lista, paginação e replay),
 * persistido em arquivo JSON no volume para sobreviver a reinício.
 *
 * INVARIANTES DO DOMÍNIO: (1) arquivo ilegível NUNCA aborta o boot — é dado didático, então a falha é
 * aberta: o arquivo vai para quarentena ({@code .corrompido-<epoch>}) com log ERROR e o histórico sobe
 * vazio; (2) toda leitura e escrita do deque passa pela mesma trava, e a gravação é atômica
 * (temporário + move), então crash no meio preserva o arquivo anterior; (3) quem lê recebe CÓPIA dos
 * registros — nada fora desta classe muta o que está guardado.
 *
 * (4) PRIVACIDADE (decisão de Paulo, 2026-08-04): cada registro pertence a uma sessão de navegador
 * ({@link SessaoHistorico}); leitura, paginação e replay só enxergam a própria sessão. Teto por sessão =
 * {@code framework.app.max-history}; teto do arquivo = {@link #MAX_TOTAL}. Registros legados sem sessão
 * (o antigo balde global com IP de terceiros) são descartados na carga. {@link #listarTodos()} vê todas
 * as sessões e NÃO é exposto por HTTP (nem o export: ele usa a sessão, como a tela).
 *
 * COMPORTAMENTO EM CASO DE FALHA: erro de E/S ao gravar lança {@link HistoricoPersistenciaException}
 * (o chamador registra e segue); carga ilegível não lança. Sem requisição ativa (sem sessão), a
 * leitura devolve vazio e a escrita é descartada — falha FECHADA para dado pessoal.
 */
@ApplicationScoped
public class HistoricoStore {

    private static final Logger LOG = Logger.getLogger(HistoricoStore.class);
    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    @Inject
    AnaliseDidaticaConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Pasta-base do histórico ({@code <base>/.framework-net}). Propriedade própria porque {@code user.home}
     * é propriedade de sistema e vence o application.properties: os testes gravavam no histórico real da
     * máquina (auditoria OPS-21). Padrão: a pasta do usuário, como antes.
     * Sem {@code defaultValue = "${user.home}"}: o valor padrão NÃO é expandido, e em 02/10/2026 o
     * quarkusDev gravou o histórico numa pasta chamada literalmente "${user.home}" (no contêiner ela
     * cairia fora do volume /deployments/data e o histórico sumiria a cada recriação).
     */
    @ConfigProperty(name = "framework.historico.home")
    Optional<String> homeConfigurado = Optional.empty();

    /** Base efetiva; os testes de unidade a fixam direto. Nula = resolver pela configuração. */
    String userHome;

    /** Teto do arquivo inteiro (todas as sessões): mantém a regravação por consulta pequena. */
    static final int MAX_TOTAL = 500;

    private static final String CHAVE_SESSAO = "sessao";

    @Inject
    SessaoHistorico sessaoHistorico;

    private final Deque<Map<String, Object>> historyStore = new ArrayDeque<>();

    /** Guarda o deque e o arquivo: sem ela, duas gravações truncavam o mesmo arquivo ao mesmo tempo. */
    private final Object trava = new Object();

    /**
     * Espera entre a consulta e a gravação do arquivo. Cada consulta regravava o arquivo inteiro (até
     * ~287 KB), inclusive em GET anônimo: um visitante nas rotas pesadas chegava a ~26 MB/min de escrita
     * (auditoria OPS-03). Agora as consultas da janela saem numa gravação só. Risco residual declarado:
     * processo morto sem encerramento perde no máximo esta janela de histórico didático.
     */
    long atrasoGravacaoMs = 5_000;

    /** Quantas vezes o arquivo foi gravado (diagnóstico e teste). */
    final java.util.concurrent.atomic.AtomicInteger gravacoes = new java.util.concurrent.atomic.AtomicInteger();

    private final java.util.concurrent.ScheduledExecutorService gravador =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "historico-gravador");
                t.setDaemon(true);
                return t;
            });

    /** Há gravação agendada? (guardado por {@link #trava}) */
    private boolean gravacaoAgendada;

    void onStart(@Observes @Priority(1) StartupEvent event) {
        carregar();
    }

    public void carregar() {
        Path file = historyFile();
        if (!Files.exists(file)) {
            return;
        }
        synchronized (trava) {
            try {
                List<Map<String, Object>> raw = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
                int legados = 0;
                if (raw != null) {
                    for (Map<String, Object> item : raw) {
                        if (item == null || !(item.get(CHAVE_SESSAO) instanceof String)) {
                            legados++;   // balde global antigo: IP de terceiros sem dono — não volta
                            continue;
                        }
                        if (historyStore.size() < MAX_TOTAL) {
                            historyStore.addLast(item);
                        }
                    }
                }
                if (legados > 0) {
                    LOG.warnf("Histórico: %d registro(s) legado(s) sem sessão descartado(s) na carga.", legados);
                }
                LOG.infof("Histórico carregado: %d registros", historyStore.size());
                telemetriaLogger.logEvent("info", "analiseDidatica", "history_load",
                        Map.of("status", "ok", "total", historyStore.size()));
            } catch (IOException ex) {
                quarentena(file, ex);
            }
        }
    }

    /**
     * Tira do caminho um arquivo ilegível (preservando o conteúdo para investigação) e sobe vazio:
     * antes, a exceção saía do observer de StartupEvent, abortava o Quarkus e o container entrava
     * em reinício infinito — um histórico didático derrubando o site inteiro.
     */
    private void quarentena(Path file, IOException causa) {
        historyStore.clear();
        Path destino = file.resolveSibling(file.getFileName() + ".corrompido-" + Instant.now().getEpochSecond());
        try {
            Files.move(file, destino, StandardCopyOption.REPLACE_EXISTING);
            LOG.errorf(causa, "Histórico ilegível movido para %s; iniciando vazio.", destino);
        } catch (IOException moveEx) {
            LOG.errorf(moveEx, "Histórico ilegível em %s e não foi possível movê-lo; iniciando vazio.", file);
        }
        telemetriaLogger.logEvent("error", "analiseDidatica", "history_load",
                Map.of("status", "quarentena", "motivo", causa.getClass().getSimpleName()));
    }

    /**
     * Grava agora o que estiver pendente.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> fecha a janela de agrupamento — no encerramento do processo, no
     * agendador e nos testes que leem o arquivo.</p>
     * <p><b>INVARIANTES DO DOMÍNIO:</b> grava sob a mesma trava das consultas, de forma atômica (arquivo
     * temporário + move); desmarca a gravação agendada.</p>
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link HistoricoPersistenciaException} se o disco
     * recusar; o conteúdo continua em memória para a próxima gravação.</p>
     */
    public void gravarPendente() {
        synchronized (trava) {
            gravacaoAgendada = false;
            persistir();
        }
    }

    @jakarta.annotation.PreDestroy
    void aoEncerrar() {
        gravador.shutdownNow();
        try {
            gravarPendente();
        } catch (HistoricoPersistenciaException ex) {
            LOG.errorf(ex, "Histórico não gravado ao encerrar.");
        }
    }

    private void agendarGravacao() {
        if (gravacaoAgendada) {
            return;
        }
        gravacaoAgendada = true;
        try {
            gravador.schedule(() -> {
                try {
                    gravarPendente();
                } catch (HistoricoPersistenciaException ex) {
                    LOG.errorf(ex, "Histórico não gravado; nova tentativa na próxima consulta.");
                }
            }, atrasoGravacaoMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException encerrando) {
            gravacaoAgendada = false;
            persistir();
        }
    }

    public void persistir() {
        synchronized (trava) {
            gravacoes.incrementAndGet();
            Path destino = historyFile();
            try {
                Files.createDirectories(destino.getParent());
                Path temp = Files.createTempFile(destino.getParent(), "consulta_history", ".tmp");
                try {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), new ArrayList<>(historyStore));
                    try {
                        Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException semAtomico) {
                        Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
                LOG.debugf("Histórico persistido: %d registros", historyStore.size());
                telemetriaLogger.logEvent("info", "analiseDidatica", "history_persist",
                        Map.of("status", "ok", "total", historyStore.size()));
            } catch (IOException ex) {
                throw new HistoricoPersistenciaException("Falha ao persistir histórico local.", ex);
            }
        }
    }

    public void registrarConsulta(Map<String, String> entrada, Map<String, Object> res) {
        Optional<String> sessao = sessaoAtual();
        if (sessao.isEmpty()) {
            LOG.debug("Histórico: consulta fora de requisição HTTP não é registrada (sem sessão).");
            return;
        }
        registrarConsulta(sessao.get(), entrada, res);
    }

    public void registrarConsulta(String sessao, Map<String, String> entrada, Map<String, Object> res) {
        if (res == null || res.isEmpty() || sessao == null || sessao.isBlank()) {
            return;
        }
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("id", UUID.randomUUID().toString().substring(0, 8));
        registro.put(CHAVE_SESSAO, sessao);
        registro.put("timestamp", Instant.now().toString());
        registro.put("modo", entrada.getOrDefault("modo", ""));
        registro.put("ip_entrada", entrada.getOrDefault("ip", ""));
        registro.put("ipv6_entrada", entrada.getOrDefault("ipv6", ""));
        registro.put("cidr_entrada", entrada.getOrDefault("cidr", ""));
        registro.put("mask_entrada", entrada.getOrDefault("mask_decimal", ""));
        registro.put("wildcard_entrada", entrada.getOrDefault("wildcard_mask", ""));
        registro.put("rede", res.getOrDefault("rede", ""));
        registro.put("broadcast", res.getOrDefault("broad", ""));
        registro.put("mask", res.getOrDefault("mask", ""));
        registro.put("cidr", res.getOrDefault("cidr", ""));
        registro.put("tema", res.getOrDefault("nivel_tema", ""));
        Object gc = res.get("geo_consulta");
        if (gc instanceof Map<?, ?> geoMap) {
            registro.put("geo_consulta", geoMap);
        }
        synchronized (trava) {
            historyStore.addFirst(registro);
            // Teto por sessão: remove os mais antigos DESTA sessão (o deque vai do mais novo ao mais velho).
            int daSessao = 0;
            for (java.util.Iterator<Map<String, Object>> it = historyStore.iterator(); it.hasNext(); ) {
                Map<String, Object> item = it.next();
                if (sessao.equals(item.get(CHAVE_SESSAO)) && ++daSessao > config.maxHistory()) {
                    it.remove();
                }
            }
            despejarAcimaDoTeto(sessao);
            LOG.infof("Histórico append modo=%s id=%s", registro.get("modo"), registro.get("id"));
            agendarGravacao();
        }
    }

    /**
     * Teto global do arquivo ({@link #MAX_TOTAL}) sem despejo cego.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico existe para a pessoa reaproveitar o que já consultou. Com FIFO
     * global, 500 consultas de sessões descartáveis (um POST sem cookie = uma sessão nova) apagavam o histórico de
     * todos os outros visitantes em ~8 minutos (BACK-14, auditoria de 22/10/2026).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a sessão que acabou de consultar nunca perde o próprio registro novo; acima do
     * teto sai o registro mais antigo da sessão de OUTRA pessoa que está há mais tempo parada — primeiro entre as
     * consultas avulsas (sessão com um registro só), depois entre as demais. Assim, arquivo cheio não congela quem
     * chega (a primeira versão desta correção apagava a consulta nova na hora — achado da revisão adversarial) e
     * consulta avulsa em massa não expulsa quem tem histórico de verdade; o arquivo nunca passa de
     * {@link #MAX_TOTAL}. Chamado com a trava tomada.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; sem outra sessão no arquivo, cai no FIFO.</p>
     */
    private void despejarAcimaDoTeto(String sessaoAtual) {
        while (historyStore.size() > MAX_TOTAL) {
            Map<Object, Integer> quantidade = new java.util.HashMap<>();
            Map<Object, Integer> atividade = new java.util.HashMap<>();   // posição do registro mais novo (0 = agora)
            int posicao = 0;
            for (Map<String, Object> item : historyStore) {
                Object dono = item.get(CHAVE_SESSAO);
                quantidade.merge(dono, 1, Integer::sum);
                atividade.putIfAbsent(dono, posicao++);
            }
            Object escolhida = null;
            boolean achou = false;
            for (Map.Entry<Object, Integer> e : atividade.entrySet()) {
                Object dono = e.getKey();
                if (java.util.Objects.equals(dono, sessaoAtual)) {
                    continue;   // registro legado sem sessão (dono null) é candidato como qualquer outro
                }
                if (!achou) {
                    escolhida = dono;
                    achou = true;
                    continue;
                }
                boolean avulsa = quantidade.get(dono) == 1;
                boolean escolhidaAvulsa = quantidade.get(escolhida) == 1;
                if (avulsa != escolhidaAvulsa ? avulsa : e.getValue() > atividade.get(escolhida)) {
                    escolhida = dono;
                }
            }
            if (!achou) {
                historyStore.removeLast();
                continue;
            }
            for (java.util.Iterator<Map<String, Object>> it = historyStore.descendingIterator(); it.hasNext(); ) {
                if (java.util.Objects.equals(escolhida, it.next().get(CHAVE_SESSAO))) {
                    it.remove();
                    break;
                }
            }
        }
    }

    /** Registros da sessão da requisição atual (vazio fora de requisição). */
    public List<Map<String, Object>> listar() {
        return sessaoAtual().map(this::listar).orElseGet(List::of);
    }

    /** Registros de uma sessão, sem a chave de sessão (que não precisa sair para o navegador). */
    public List<Map<String, Object>> listar(String sessao) {
        synchronized (trava) {
            List<Map<String, Object>> copia = new ArrayList<>();
            for (Map<String, Object> item : historyStore) {
                if (sessao != null && sessao.equals(item.get(CHAVE_SESSAO))) {
                    Map<String, Object> c = new LinkedHashMap<>(item);
                    c.remove(CHAVE_SESSAO);
                    copia.add(c);
                }
            }
            return copia;
        }
    }

    /** Todas as sessões, com a chave de sessão — uso interno (testes de persistência); nunca sai por HTTP. */
    public List<Map<String, Object>> listarTodos() {
        synchronized (trava) {
            List<Map<String, Object>> copia = new ArrayList<>(historyStore.size());
            for (Map<String, Object> item : historyStore) {
                copia.add(new LinkedHashMap<>(item));
            }
            return copia;
        }
    }

    private Optional<String> sessaoAtual() {
        if (sessaoHistorico == null) {
            return Optional.empty();
        }
        io.quarkus.arc.ManagedContext ctx = io.quarkus.arc.Arc.container().requestContext();
        if (!ctx.isActive()) {
            return Optional.empty();
        }
        return Optional.of(sessaoHistorico.chave());
    }

    public Map<String, Object> paginar(String historyLimitPre, String historyPagePre) {
        return paginar(listar(), historyLimitPre, historyPagePre);
    }

    public Map<String, Object> paginar(String sessao, String historyLimitPre, String historyPagePre) {
        return paginar(listar(sessao), historyLimitPre, historyPagePre);
    }

    private Map<String, Object> paginar(List<Map<String, Object>> historyList, String historyLimitPre,
            String historyPagePre) {
        int historyLimitInt = parsePositive(historyLimitPre, 1);
        if (historyLimitInt > config.maxHistory()) {
            historyLimitInt = config.maxHistory();
        }
        int historyPageInt = Math.max(1, parsePositive(historyPagePre, 1));

        int totalHistory = historyList.size();
        int totalHistoryPages;
        List<Map<String, Object>> historyPageItems;

        if (historyLimitInt > 0) {
            totalHistoryPages = Math.max(1, (totalHistory + historyLimitInt - 1) / historyLimitInt);
            if (historyPageInt > totalHistoryPages) {
                historyPageInt = totalHistoryPages;
            }
            int start = (historyPageInt - 1) * historyLimitInt;
            int end = Math.min(start + historyLimitInt, totalHistory);
            historyPageItems = new ArrayList<>(historyList.subList(start, end));
            for (Map<String, Object> item : historyPageItems) {
                item.put("timestamp_utc", formatarTimestampUtc(String.valueOf(item.getOrDefault("timestamp", ""))));
            }
        } else {
            totalHistoryPages = 1;
            historyPageItems = List.of();
            historyPageInt = 1;
        }

        Map<String, Object> pag = new LinkedHashMap<>();
        pag.put("history", historyList);
        pag.put("history_limit", historyLimitInt);
        pag.put("history_limit_pre", String.valueOf(historyLimitInt));
        pag.put("history_limit_max", config.maxHistory());
        pag.put("history_page", historyPageInt);
        pag.put("total_history_pages", totalHistoryPages);
        pag.put("has_prev_history", historyPageInt > 1);
        pag.put("has_next_history", historyPageInt < totalHistoryPages);
        pag.put("history_page_items", historyPageItems);
        return pag;
    }

    /** Replay só dentro da própria sessão: id de outra sessão devolve null, como id inexistente. */
    public Map<String, Object> buscarReplay(String replayId) {
        if (replayId == null || replayId.isBlank()) {
            return null;
        }
        return listar().stream()
                .filter(item -> replayId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
    }

    public void registrarCatalogo(String modo, String entrada) {
        Map<String, String> fields = Map.of(
                "modo", modo,
                "ip", entrada == null ? "" : entrada,
                "ipv6", "",
                "cidr", "",
                "mask_decimal", "",
                "wildcard_mask", ""
        );
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("rede", "N/A");
        resumo.put("broad", "N/A");
        resumo.put("mask", "N/A");
        resumo.put("cidr", "");
        resumo.put("nivel_tema", "Consulta de catálogo: " + modo);
        registrarConsulta(fields, resumo);
    }

    public List<Map<String, Object>> listarPorModo(String modo) {
        return listar().stream()
                .filter(item -> modo.equalsIgnoreCase(String.valueOf(item.getOrDefault("modo", ""))))
                .toList();
    }

    /**
     * Arquivo do histórico POR SESSÃO. O nome mudou de propósito ("v2"): a versão anterior ao F07 lê
     * {@code consulta_history.json} e devolve tudo no {@code GET /history} público — num rollback, ela
     * publicaria o histórico de todas as sessões. Com outro nome, a versão antiga nem o enxerga, e o
     * arquivo antigo fica intocado com o que já era público antes da atualização.
     */
    private Path historyFile() {
        return Paths.get(baseDoHistorico(), ".framework-net", "consulta_history.v2.json");
    }

    /**
     * Pasta-base do histórico.
     * PROPÓSITO DE NEGÓCIO: o histórico didático sobrevive ao reinício; no contêiner, HOME aponta para o volume.
     * INVARIANTES: nunca devolve texto com expressão de configuração por expandir ("${...}"); sem
     *   propriedade, vale a pasta do usuário do processo (user.home).
     * FALHA: propriedade com "${" não expandido é recusada com IllegalStateException, em vez de gravar numa
     *   pasta com esse nome e perder o histórico em silêncio.
     */
    String baseDoHistorico() {
        String base = userHome != null ? userHome
                : homeConfigurado.filter(h -> !h.isBlank()).orElse(System.getProperty("user.home"));
        if (base == null || base.contains("${")) {
            throw new IllegalStateException("framework.historico.home sem valor válido: " + base);
        }
        return base;
    }

    private static int parsePositive(String value, int defaultValue) {
        return org.framework.net.shared.NumeroAscii.inteiro(value, org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT).orElse(defaultValue);
    }

    private static String formatarTimestampUtc(String ts) {
        if (ts == null || ts.isBlank()) {
            return "—";
        }
        try {
            return UTC_FMT.format(Instant.parse(ts.replace("Z", "+00:00"))) + " UTC";
        } catch (Exception ex) {
            return ts + " UTC";
        }
    }
}
