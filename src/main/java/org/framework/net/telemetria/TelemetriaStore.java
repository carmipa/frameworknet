package org.framework.net.telemetria;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.telemetria.infrastructure.TelemetriaStreamRedis;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class TelemetriaStore {

    private static final Logger LOG = Logger.getLogger(TelemetriaStore.class);
    private static final String ARQUIVO_COMPARTILHADO = "telemetria_compartilhada.json";
    private static final String ARQUIVO_EVENTOS = "framework-net-eventos.jsonl";
    private static final String ARQUIVO_EVENTOS_ANTERIOR = "framework-net-eventos.jsonl.1";

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<TelemetriaEvent> eventos = new ArrayDeque<>();
    private final ObjectMapper objectMapper;
    private final ObjectMapper compactMapper;

    @ConfigProperty(name = "framework.logs.base-dir", defaultValue = "logs")
    String baseDir;

    @ConfigProperty(name = "framework.telemetry.max-events", defaultValue = "5000")
    int maxEvents;

    /**
     * Quantos eventos o painel, o dataset e a exportação leem do Stream do Redis. Era o mesmo
     * {@code max-events} da memória (500 em produção) enquanto o Stream guardava 50.000: "últimos 7 dias"
     * era calculado sobre 500 eventos sem aviso (auditoria OPS-05). Agora a leitura alcança o que o
     * Stream guarda ({@code framework.telemetry.stream.max-len}, mesmo padrão).
     */
    @ConfigProperty(name = "framework.telemetry.stream.janela-leitura", defaultValue = "5000")
    int janelaLeitura;

    @ConfigProperty(name = "framework.telemetry.jsonl-max-bytes", defaultValue = "10485760")
    long jsonlMaxBytes;

    @ConfigProperty(name = "framework.telemetry.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "framework-net-java-quarkus")
    String appName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0-SNAPSHOT")
    String appVersion;

    @Inject
    TelemetriaStreamRedis stream;

    static final int FILA_STREAM = 2_000;

    private final java.util.concurrent.atomic.AtomicLong descartadosStream = new java.util.concurrent.atomic.AtomicLong();

    private final java.util.concurrent.ThreadPoolExecutor publicadorStream = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(FILA_STREAM),
            r -> {
                Thread t = new Thread(r, "telemetria-stream");
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    @Inject
    public TelemetriaStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.compactMapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
    }

    void onStart(@Observes @Priority(1) StartupEvent event) {
        if (!enabled) {
            return;
        }
        try {
            garantirPasta();
            carregarPersistido();
            LOG.infof("Telemetria carregada de %s (%d eventos)", pastaLogs(), eventos.size());
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao carregar telemetria persistida em %s", pastaLogs());
        }
    }

    @PreDestroy
    void onStop() {
        if (!enabled) {
            return;
        }
        try {
            persistirCanonico();
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao persistir telemetria ao encerrar");
        }
    }

    /**
     * Propósito de negócio: registra um evento operacional para o painel e para a trilha incremental.
     * Invariantes do domínio: mantém no máximo {@code maxEvents} em memória e nunca reconstrói o OTLP
     * completo no caminho crítico de cada evento.
     * Comportamento em caso de falha: preserva a aplicação em execução e registra um aviso quando a
     * persistência incremental não puder ser concluída.
     */
    public void registrar(TelemetriaEvent evento) {
        if (!enabled || evento == null) {
            return;
        }
        lock.lock();
        try {
            eventos.addFirst(evento);
            while (eventos.size() > maxEvents) {
                eventos.removeLast();
            }
            appendJsonl(evento);
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao persistir evento de telemetria %s", evento.evento());
        } finally {
            lock.unlock();
        }
        // FORA do lock e DEPOIS do arquivo, de proposito: o Stream e camada de
        // leitura, nao a fonte duravel. Segurar o lock durante I/O de rede
        // transformaria uma lentidao do Redis em fila em todas as requisicoes.
        // Ausencia tolerada: o Stream e camada opcional. Fora do CDI (teste
        // unitario) ou com o recurso desligado, o registro segue igual — a
        // verdade duravel ja foi para o arquivo acima.
        if (stream != null) {
            publicarNoStream(evento);
        }
    }

    /**
     * Publica no Stream fora da thread da requisição (auditoria OPS-17: o XADD era síncrono no filtro de
     * resposta, e com o Redis lento toda resposta esperava). Uma thread só, na ordem de chegada; fila de
     * no máximo {@link #FILA_STREAM} eventos — cheia, o evento não vai ao Stream (a verdade durável já está
     * no arquivo) e o descarte é contado.
     */
    private void publicarNoStream(TelemetriaEvent evento) {
        try {
            publicadorStream.execute(() -> stream.publicar(evento));
        } catch (java.util.concurrent.RejectedExecutionException cheia) {
            descartadosStream.incrementAndGet();
        }
    }

    /** Eventos que não foram ao Stream porque a fila estava cheia (o arquivo os tem). */
    public long descartadosStream() {
        return descartadosStream.get();
    }

    @jakarta.annotation.PreDestroy
    void encerrarPublicador() {
        publicadorStream.shutdown();
    }

    /**
     * Propósito de negócio: materializa sob demanda o retrato OTLP compartilhável dos eventos atuais.
     * Invariantes do domínio: a escrita é serializada e representa somente a janela limitada em memória.
     * Comportamento em caso de falha: mantém o último arquivo íntegro e registra um aviso, sem interromper
     * o atendimento da aplicação.
     */
    public void flush() {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            persistirCanonico();
        } catch (IOException ex) {
            LOG.warnf(ex, "Falha ao flush da telemetria");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Materializa o retrato OTLP agora, para quem vai ENTREGAR o arquivo.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a exportação JSON lia o arquivo logo depois do {@link #flush()}; com
     * a falha engolida ali, ela entregava o retrato anterior como se fosse o atual (auditoria OPS-12).</p>
     * <p><b>INVARIANTES DO DOMÍNIO:</b> mesma escrita serializada do {@link #flush()}.</p>
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> propaga a {@link IOException}; telemetria desligada não
     * grava e não lança.</p>
     */
    public void flushOuFalhar() throws IOException {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            persistirCanonico();
        } finally {
            lock.unlock();
        }
    }

    public TelemetriaResumo gerarResumo(int limiteUltimos) {
        lock.lock();
        try {
            int limite = Math.max(1, Math.min(limiteUltimos, maxEvents));
            List<TelemetriaEvent> copia = new ArrayList<>(eventos);
            Map<String, Long> porModulo = new LinkedHashMap<>();
            Map<String, Long> porNivel = new LinkedHashMap<>();
            for (TelemetriaEvent evento : copia) {
                porModulo.merge(evento.modulo(), 1L, Long::sum);
                porNivel.merge(evento.level(), 1L, Long::sum);
            }
            List<TelemetriaEvent> ultimos = copia.stream().limit(limite).toList();
            return new TelemetriaResumo(
                    appName,
                    appVersion,
                    Instant.now(),
                    copia.size(),
                    porModulo,
                    porNivel,
                    ultimos
            );
        } finally {
            lock.unlock();
        }
    }

    /** Maior número de eventos que uma leitura devolve (memória ou Stream). */
    public int janelaDeLeitura() {
        return Math.max(maxEvents, janelaLeitura);
    }

    public int maxEventos() {
        return maxEvents;
    }

    /**
     * Eventos para leitura (mais recentes primeiro): a janela maior entre a memória ({@code maxEvents}) e o
     * Stream ({@code janela-leitura}).
     */
    public List<TelemetriaEvent> snapshotEventos() {
        List<TelemetriaEvent> memoria;
        lock.lock();
        try {
            memoria = new ArrayList<>(eventos);
        } finally {
            lock.unlock();
        }

        // Vence quem tiver a janela MAIOR. Todo evento vai para os dois lugares,
        // entao o Stream e superconjunto da memoria em regime — mas logo depois
        // de ligar o recurso ele comeca vazio enquanto a memoria ja veio do
        // arquivo. Comparar tamanho resolve os dois casos sem caso especial e
        // sem nunca devolver menos do que ja se devolvia.
        if (stream == null) {
            return memoria;
        }
        List<TelemetriaEvent> doStream = stream.ultimos(Math.max(maxEvents, janelaLeitura));
        return doStream.size() > memoria.size() ? doStream : memoria;
    }

    public Path arquivoCompartilhado() {
        return pastaLogs().resolve(ARQUIVO_COMPARTILHADO);
    }

    public Path pastaLogs() {
        return Path.of(baseDir);
    }

    public List<String> lerUltimasLinhasArquivoLog(int limite) {
        int max = Math.max(1, Math.min(limite, 500));
        Path arquivo = pastaLogs().resolve("framework-net.log");
        if (!Files.exists(arquivo)) {
            return List.of();
        }
        try {
            List<String> todas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            int inicio = Math.max(0, todas.size() - max);
            return List.copyOf(todas.subList(inicio, todas.size()));
        } catch (IOException ex) {
            LOG.debugf(ex, "Falha ao ler tail do log %s", arquivo);
            return List.of();
        }
    }

    private void carregarPersistido() throws IOException {
        Path arquivo = arquivoCompartilhado();
        if (!Files.exists(arquivo)) {
            return;
        }
        Map<String, Object> root = objectMapper.readValue(arquivo.toFile(), new TypeReference<>() {});
        List<TelemetriaEvent> carregados = TelemetriaOtlpMapper.fromLogsData(root);
        eventos.clear();
        for (TelemetriaEvent evento : carregados) {
            eventos.addLast(evento);
        }
        while (eventos.size() > maxEvents) {
            eventos.removeLast();
        }
    }

    /**
     * Persiste o arquivo canônico compartilhado em OpenTelemetry OTLP/JSON (Logs data model).
     */
    private void persistirCanonico() throws IOException {
        garantirPasta();
        TelemetriaResumo resumo = gerarResumo(maxEvents);
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                resumo.projeto(), resumo.versao(), resumo.ultimosEventos());

        Path destino = arquivoCompartilhado();
        Path temporario = destino.resolveSibling(destino.getFileName() + ".tmp");
        objectMapper.writeValue(temporario.toFile(), root);
        Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Propósito de negócio: mantém uma trilha NDJSON incremental dos eventos para diagnóstico operacional.
     * Invariantes do domínio: grava um {@code LogRecord} por linha e limita o arquivo ativo por rotação.
     * Comportamento em caso de falha: propaga {@link IOException} ao registrador, que preserva a aplicação
     * e emite um aviso no log principal.
     */
    private void appendJsonl(TelemetriaEvent evento) throws IOException {
        garantirPasta();
        Path arquivo = pastaLogs().resolve(ARQUIVO_EVENTOS);
        byte[] linha = (compactMapper.writeValueAsString(TelemetriaOtlpMapper.toLogRecord(evento))
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        rotacionarJsonlSeNecessario(arquivo, linha.length);
        Files.write(arquivo, linha,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    /**
     * Propósito de negócio: impede crescimento ilimitado da trilha incremental no volume persistente.
     * Invariantes do domínio: conserva somente o arquivo ativo e uma geração anterior, usando no mínimo
     * 1 KiB como limite defensivo.
     * Comportamento em caso de falha: propaga {@link IOException} sem remover o arquivo ativo existente.
     */
    private void rotacionarJsonlSeNecessario(Path arquivo, int proximaLinhaBytes) throws IOException {
        long limiteEfetivo = Math.max(1024L, jsonlMaxBytes);
        if (!Files.exists(arquivo) || Files.size(arquivo) + proximaLinhaBytes <= limiteEfetivo) {
            return;
        }
        Path anterior = pastaLogs().resolve(ARQUIVO_EVENTOS_ANTERIOR);
        Files.move(arquivo, anterior, StandardCopyOption.REPLACE_EXISTING);
    }

    private void garantirPasta() throws IOException {
        Files.createDirectories(pastaLogs());
    }
}
