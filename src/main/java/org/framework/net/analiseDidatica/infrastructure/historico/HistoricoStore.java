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

    /**
     * Maior texto guardado por campo do registro. Cabe um nome de domínio inteiro (253 caracteres), então nada
     * legítimo é cortado; acima disso o valor sai truncado com "…". Sem teto, cada consulta guardava o campo
     * como veio (até 1 MB) e ~480 consultas faziam um arquivo de ~450 MB.
     */
    static final int MAX_CARACTERES_CAMPO = 255;

    /**
     * Chaves da consulta de geolocalização que identificam ou localizam QUEM ACESSA: o IP da conexão e as
     * coordenadas (inclusive a URL do mapa, que as carrega). Nunca vão ao histórico — a tela promete que o site
     * guarda a consulta, não a pessoa.
     */
    static final java.util.Set<String> CHAVES_PESSOAIS_GEO =
            java.util.Set.of("cliente_ip", "latitude", "longitude", "lat", "lon", "maps_url");

    /**
     * Cópia da consulta de geolocalização sem as chaves pessoais.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico mostra de novo o país, a região e o provedor do IP que a pessoa
     * digitou, sem guardar o IP de quem acessou nem coordenadas.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhuma chave de {@link #CHAVES_PESSOAIS_GEO} sobrevive; as demais passam
     * iguais, na mesma ordem; a entrada nunca é alterada.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; chave que não é texto é descartada.</p>
     */
    static Map<String, Object> geoSemDadoPessoal(Map<?, ?> geo) {
        Map<String, Object> limpo = new LinkedHashMap<>();
        geo.forEach((chave, valor) -> {
            if (chave instanceof String nome && !CHAVES_PESSOAIS_GEO.contains(nome)) {
                limpo.put(nome, valor);
            }
        });
        return limpo;
    }

    /**
     * A consulta de geolocalização foi sobre o próprio IP de quem acessou?
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico guarda o que a pessoa DIGITOU para consultar; a detecção da
     * própria ligação (modo {@code ligacao}) ou a consulta do próprio IP não são entrada dela e não entram.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> modo {@code ligacao} é sempre do próprio visitante; no modo manual, conta
     * quando o IP consultado é o mesmo endereço do IP da conexão.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; sem os dois endereços, responde {@code false}.</p>
     */
    static boolean consultaDoProprioVisitante(Map<?, ?> geo) {
        if ("ligacao".equals(geo.get("modo"))) {
            return true;
        }
        Object cliente = geo.get("cliente_ip");
        Object consultado = geo.get("consultado");
        return cliente != null && consultado != null
                && mesmoEndereco(String.valueOf(cliente), String.valueOf(consultado));
    }

    /**
     * Dois textos de IP são o mesmo endereço? Compara pelo endereço ({@code ::ffff:1.2.3.4} é {@code 1.2.3.4}),
     * sem consulta de DNS.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> reconhecer a consulta do próprio IP mesmo escrita de outro jeito.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só literal de IP é interpretado ({@code InetAddress.ofLiteral}, nunca DNS);
     * texto vazio nunca é "o mesmo".</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; texto que não é IP cai na comparação literal sem
     * maiúsculas.</p>
     */
    public static boolean mesmoEndereco(String a, String b) {
        String x = a == null ? "" : a.strip();
        String y = b == null ? "" : b.strip();
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        try {
            return java.net.InetAddress.ofLiteral(x).equals(java.net.InetAddress.ofLiteral(y));
        } catch (IllegalArgumentException naoEIp) {
            return x.equalsIgnoreCase(y);
        }
    }

    /**
     * Maior arquivo que a carga aceita ler. O maior que esta versão consegue escrever é ~9 MB (500 registros, 11
     * campos no teto, todo caractere escapado como {@code \\uXXXX}); acima disto o arquivo não veio dela e vai
     * para a quarentena SEM ser lido — antes, um histórico de ~450 MB derrubava cada arranque por falta de
     * memória, em laço.
     */
    static final long MAX_BYTES_ARQUIVO = 16L * 1024 * 1024;

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

    /**
     * Ligada quando a quarentena não conseguiu mover o arquivo ilegível ou grande demais: gravar por cima dele o
     * apagaria. Só um reinício (com o arquivo resolvido) a desliga.
     */
    volatile boolean gravacaoSuspensa;

    /** Último aviso de gravação suspensa (guardado por {@link #trava}). */
    private long ultimoAvisoSuspensa;

    void onStart(@Observes @Priority(1) StartupEvent event) {
        carregar();
    }

    /**
     * Carrega o histórico do volume no arranque.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico didático sobrevive ao reinício do contêiner.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> arquivo acima de {@link #MAX_BYTES_ARQUIVO} não é lido (quarentena pelo
     * tamanho, antes do parse); registro sem sessão não volta; no máximo {@link #MAX_TOTAL} registros; todo
     * texto carregado passa pelo mesmo teto por campo da gravação ({@link #MAX_CARACTERES_CAMPO}), inclusive o
     * que foi gravado por versão anterior. Consulta de geolocalização do próprio visitante, gravada por versão
     * anterior, não volta; nas demais saem o IP da conexão e as coordenadas ({@link #CHAVES_PESSOAIS_GEO}) — e o
     * arquivo é regravado logo, para a limpeza valer no disco.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança — arquivo grande demais ou ilegível vai para a
     * quarentena ({@code .grande-demais-<epoch>} ou {@code .corrompido-<epoch>}), com log ERROR e evento de
     * telemetria, e o histórico sobe vazio.</p>
     */
    public void carregar() {
        Path file = historyFile();
        if (!Files.exists(file)) {
            return;
        }
        synchronized (trava) {
            try {
                long tamanho = Files.size(file);
                if (tamanho > MAX_BYTES_ARQUIVO) {
                    quarentena(file, "tamanho", tamanho, null);
                    return;
                }
                List<Map<String, Object>> raw = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
                int legados = 0;
                int[] truncados = {0};
                int doProprioVisitante = 0;
                int geoLimpos = 0;
                if (raw != null) {
                    for (Map<String, Object> item : raw) {
                        if (item == null || !(item.get(CHAVE_SESSAO) instanceof String)) {
                            legados++;   // balde global antigo: IP de terceiros sem dono — não volta
                            continue;
                        }
                        // Versões anteriores gravavam a consulta do próprio IP e o IP da conexão com coordenadas.
                        if (item.get("geo_consulta") instanceof Map<?, ?> geo) {
                            if (consultaDoProprioVisitante(geo)) {
                                doProprioVisitante++;
                                continue;
                            }
                            Map<String, Object> limpo = geoSemDadoPessoal(geo);
                            if (limpo.size() != geo.size()) {
                                item.put("geo_consulta", limpo);
                                geoLimpos++;
                            }
                        }
                        if (historyStore.size() < MAX_TOTAL) {
                            historyStore.addLast(comTextoNoTeto(item, truncados));
                        }
                    }
                }
                if (legados > 0) {
                    LOG.warnf("Histórico: %d registro(s) legado(s) sem sessão descartado(s) na carga.", legados);
                }
                if (truncados[0] > 0) {
                    // O corte vira definitivo na próxima gravação: fica registrado que houve reparo (a causa não se perde).
                    LOG.warnf("Histórico: %d campo(s) acima de %d caracteres cortado(s) na carga.", truncados[0],
                            MAX_CARACTERES_CAMPO);
                }
                if (doProprioVisitante > 0 || geoLimpos > 0) {
                    // A limpeza só vale quando o arquivo é regravado: agenda já, sem esperar a próxima consulta.
                    LOG.warnf("Histórico: %d consulta(s) do próprio IP de quem acessou removida(s) e %d consulta(s) "
                            + "de geolocalização sem o IP da conexão e as coordenadas, na carga.", doProprioVisitante,
                            geoLimpos);
                    agendarGravacao();
                }
                LOG.infof("Histórico carregado: %d registros", historyStore.size());
                telemetriaLogger.logEvent("info", "analiseDidatica", "history_load",
                        Map.of("status", "ok", "total", historyStore.size(), "truncados", truncados[0],
                                "pessoaisRemovidos", doProprioVisitante, "pessoaisLimpos", geoLimpos));
            } catch (IOException ex) {
                quarentena(file, ex.getClass().getSimpleName(), -1, ex);
            }
        }
    }

    /**
     * Tira do caminho um arquivo que não pode ser carregado, preservando o conteúdo para investigação, e sobe
     * vazio.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> um histórico didático nunca derruba o site — antes, a exceção saía do observer
     * de StartupEvent e o contêiner entrava em reinício infinito; e um arquivo gigante estourava a memória em
     * todo arranque.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o arquivo nunca é apagado (vai para {@code .grande-demais-<epoch>} quando nem
     * foi lido, {@code .corrompido-<epoch>} quando a leitura falhou); o motivo
     * (o tamanho ou o tipo do erro) sai no log e no evento — a causa não se perde.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; se nem mover der, registra ERROR e sobe vazio do mesmo
     * jeito.</p>
     *
     * @param motivo  "tamanho" ou o nome simples da exceção de leitura
     * @param tamanho bytes do arquivo quando o motivo é o tamanho; -1 nos demais
     * @param causa   exceção da leitura, ou {@code null} quando o arquivo nem foi lido
     */
    private void quarentena(Path file, String motivo, long tamanho, IOException causa) {
        historyStore.clear();
        // O arquivo grande demais pode ser JSON válido: chamá-lo de "corrompido" mandava o operador procurar o defeito
        // errado.
        String sufixo = causa == null ? ".grande-demais-" : ".corrompido-";
        Path destino = file.resolveSibling(file.getFileName() + sufixo + Instant.now().getEpochSecond());
        try {
            Files.move(file, destino, StandardCopyOption.REPLACE_EXISTING);
            if (causa != null) {
                LOG.errorf(causa, "Histórico ilegível movido para %s; iniciando vazio.", destino);
            } else {
                LOG.errorf("Histórico com %d bytes (teto %d) movido sem ler para %s; iniciando vazio.",
                        tamanho, MAX_BYTES_ARQUIVO, destino);
            }
        } catch (IOException moveEx) {
            // Sem mover, a primeira gravação escreveria por cima do arquivo que não pôde ser lido — e ele "nunca é
            // apagado". A gravação fica suspensa até alguém resolver e reiniciar.
            gravacaoSuspensa = true;
            LOG.errorf(moveEx, "Histórico impossível de carregar (%s) em %s e não foi possível movê-lo; iniciando vazio "
                    + "e SEM gravar até o arquivo ser resolvido.", motivo, file);
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("status", "quarentena");
        campos.put("motivo", motivo);
        if (tamanho >= 0) {
            campos.put("bytes", tamanho);
        }
        telemetriaLogger.logEvent("error", "analiseDidatica", "history_load", campos);
    }

    /**
     * Texto de um campo do registro, no teto.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o registro guarda o que a pessoa digitou; um campo de 1 MB não pode virar
     * 1 MB no volume a cada consulta.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> texto até {@link #MAX_CARACTERES_CAMPO} passa igual; acima, sai com os
     * primeiros {@code MAX_CARACTERES_CAMPO - 1} caracteres e "…" — o total continua no teto. O corte nunca parte
     * um par substituto (emoji) ao meio: a metade órfã é um caractere que a pessoa não digitou (no arquivo vira o
     * escape solto {@code \uD83D}, e na tela, "?"). Outros tipos passam como estão.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo continua nulo.</p>
     */
    static Object noTeto(Object valor) {
        if (valor instanceof CharSequence texto && texto.length() > MAX_CARACTERES_CAMPO) {
            int corte = MAX_CARACTERES_CAMPO - 1;
            if (Character.isHighSurrogate(texto.charAt(corte - 1))) {
                corte--;
            }
            return texto.subSequence(0, corte) + "…";
        }
        return valor;
    }

    /** Cópia do registro com todo valor de texto de primeiro nível no teto ({@link #noTeto}); conta os cortes. */
    private static Map<String, Object> comTextoNoTeto(Map<String, Object> item, int[] cortes) {
        Map<String, Object> copia = new LinkedHashMap<>();
        item.forEach((chave, valor) -> {
            Object noTeto = noTeto(valor);
            if (noTeto != valor) {
                cortes[0]++;
            }
            copia.put(chave, noTeto);
        });
        return copia;
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

    /**
     * Grava o histórico inteiro no volume.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico sobrevive ao reinício.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> gravação atômica (temporário + move) sob a trava; com a gravação suspensa
     * ({@link #gravacaoSuspensa}), nada é gravado — o arquivo que a quarentena não conseguiu mover não é
     * sobrescrito.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> erro de E/S lança {@link HistoricoPersistenciaException}; suspensa,
     * não lança (o ERROR já foi registrado na quarentena) e o conteúdo fica só em memória.</p>
     */
    public void persistir() {
        synchronized (trava) {
            if (gravacaoSuspensa) {
                long agora = System.currentTimeMillis();
                if (agora - ultimoAvisoSuspensa >= 3_600_000L) {   // um aviso por hora, para não sumir no log que gira
                    ultimoAvisoSuspensa = agora;
                    LOG.warnf("Histórico com gravação SUSPENSA: o arquivo %s não pôde ser movido para a quarentena; "
                            + "as consultas ficam só em memória até ele ser resolvido e o serviço reiniciado.", historyFile());
                }
                return;
            }
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

    /**
     * Registra uma consulta da Análise no histórico da sessão.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a pessoa reencontra e repete o que já consultou.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> todo texto que veio da entrada ou do resultado entra no teto por campo
     * ({@link #MAX_CARACTERES_CAMPO}); teto por sessão e teto do arquivo como descritos na classe; gravação em
     * lote, sob a trava; a consulta de geolocalização entra sem o IP da conexão e sem coordenadas
     * ({@link #CHAVES_PESSOAIS_GEO}).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> sem sessão ou sem resultado, não registra nada; não lança — erro
     * de disco aparece na gravação agendada.</p>
     */
    public void registrarConsulta(String sessao, Map<String, String> entrada, Map<String, Object> res) {
        if (res == null || res.isEmpty() || sessao == null || sessao.isBlank()) {
            return;
        }
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("id", UUID.randomUUID().toString().substring(0, 8));
        registro.put(CHAVE_SESSAO, sessao);
        registro.put("timestamp", Instant.now().toString());
        registro.put("modo", noTeto(entrada.getOrDefault("modo", "")));
        registro.put("ip_entrada", noTeto(entrada.getOrDefault("ip", "")));
        registro.put("ipv6_entrada", noTeto(entrada.getOrDefault("ipv6", "")));
        registro.put("cidr_entrada", noTeto(entrada.getOrDefault("cidr", "")));
        registro.put("mask_entrada", noTeto(entrada.getOrDefault("mask_decimal", "")));
        registro.put("wildcard_entrada", noTeto(entrada.getOrDefault("wildcard_mask", "")));
        registro.put("rede", noTeto(res.getOrDefault("rede", "")));
        registro.put("broadcast", noTeto(res.getOrDefault("broad", "")));
        registro.put("mask", noTeto(res.getOrDefault("mask", "")));
        registro.put("cidr", noTeto(res.getOrDefault("cidr", "")));
        registro.put("tema", noTeto(res.getOrDefault("nivel_tema", "")));
        Object gc = res.get("geo_consulta");
        if (gc instanceof Map<?, ?> geoMap) {
            registro.put("geo_consulta", geoSemDadoPessoal(geoMap));
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
