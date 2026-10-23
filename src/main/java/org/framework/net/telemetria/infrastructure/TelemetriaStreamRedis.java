package org.framework.net.telemetria.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.telemetria.TelemetriaEvent;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Janela de telemetria em um Redis Stream.
 *
 * <p><b>Propósito de negócio:</b> ampliar a janela que o painel consegue mostrar
 * sem inchar a memória do processo. Hoje a aplicação guarda 500 eventos no heap e
 * perde a janela a cada deploy; o Stream mantém dezenas de milhares fora da JVM e
 * sobrevive ao restart do container. Este projeto já pagou caro por consumo de
 * memória e I/O de telemetria — a intenção aqui é aumentar o alcance sem repetir
 * aquele custo.</p>
 *
 * <p><b>Invariantes do domínio:</b> o Stream é <b>camada de leitura</b>, nunca a
 * fonte durável. A verdade continua sendo o arquivo JSONL em disco, porque um
 * {@code docker compose down -v} apaga o volume do Redis e levaria a telemetria
 * inteira junto. Por isso a escrita aqui é <b>best-effort</b>: acontece depois do
 * arquivo e jamais interrompe o registro do evento. Também não há
 * <i>consumer group</i>: ele existe para entregar a vários processos com garantia,
 * e aqui produtor e consumidor são o mesmo processo — seria estrutura para um
 * problema que não temos.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> degrada em silêncio, como o
 * {@code CacheDistribuido} já faz. Redis fora, comando recusado ou resposta
 * inesperada devolvem {@code false}/lista vazia, e quem chama volta para a janela
 * em memória. Telemetria é observabilidade: ela não pode derrubar a requisição que
 * está tentando observar.</p>
 */
@ApplicationScoped
public class TelemetriaStreamRedis {

    private static final Logger LOG = Logger.getLogger(TelemetriaStreamRedis.class);
    private static final String CAMPO = "json";

    @ConfigProperty(name = "framework.telemetry.stream.enabled", defaultValue = "true")
    boolean habilitado;

    @ConfigProperty(name = "framework.telemetry.stream.chave", defaultValue = "framework-net:telemetria")
    String chave;

    /** Teto aproximado de entradas: o {@code ~} deixa o Redis podar em bloco, que é barato. */
    @ConfigProperty(name = "framework.telemetry.stream.max-len", defaultValue = "50000")
    long maxLen;

    @Inject
    Instance<RedisDataSource> redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private volatile Boolean disponivel;

    /** Costuras de teste: a sonda de disponibilidade e o relógio. */
    java.util.function.BooleanSupplier sonda = this::testar;
    // Relógio MONOTÔNICO: com o de parede, um ajuste de NTP para trás adiava o reteste por horas.
    java.util.function.LongSupplier relogio = () -> System.nanoTime() / 1_000_000L;

    /** Uma thread sonda por vez; as outras seguem com o último estado em vez de esperar o Redis. */
    private final java.util.concurrent.locks.ReentrantLock sondando = new java.util.concurrent.locks.ReentrantLock();

    /**
     * Indisponível, o Stream volta a ser testado depois deste intervalo. Antes, uma falha só (Redis
     * reiniciado, recriado no deploy, OOM no teto de 64 MB) o desligava até a APLICAÇÃO reiniciar.
     * Entre um teste e outro não há sonda: o Redis não é martelado a cada evento.
     */
    static final long INTERVALO_RETESTE_MS = 60_000L;

    private volatile long proximaTentativaMs;

    /**
     * O Stream está utilizável agora? Disponível fica memorizado; indisponível é retestado a cada
     * {@link #INTERVALO_RETESTE_MS}. O log sai só na TROCA de estado (sem tempestade de log).
     *
     * <p>Só UMA thread sonda por vez, e sem bloquear as outras: a sonda roda na thread da requisição (todo
     * evento de telemetria passa aqui), e com o Redis pendurado — aceitando conexão sem responder — o PING
     * leva até o timeout do cliente (10 s). Antes, toda requisição que chegasse no minuto do reteste
     * esperava na fila do monitor. Quem chega durante a sonda segue com o último estado conhecido (sem
     * estado ainda = indisponível): telemetria é best-effort e o JSONL continua sendo a fonte durável.
     */
    public boolean ativo() {
        if (!habilitado) {
            return false;
        }
        Boolean cache = disponivel;
        if (cache != null && (cache || relogio.getAsLong() < proximaTentativaMs)) {
            return cache;
        }
        if (!sondando.tryLock()) {
            return Boolean.TRUE.equals(cache);
        }
        try {
            Boolean antes = disponivel;
            if (antes != null && (antes || relogio.getAsLong() < proximaTentativaMs)) {
                return antes;
            }
            boolean agora = sonda.getAsBoolean();
            disponivel = agora;
            if (!agora) {
                proximaTentativaMs = relogio.getAsLong() + INTERVALO_RETESTE_MS;
            }
            if (antes == null || antes != agora) {
                LOG.infof("Stream de telemetria no Redis: %s (chave=%s, maxlen=%d)",
                        agora ? "ativo" : "indisponivel (novo teste em 60 s)", chave, maxLen);
            }
            return agora;
        } finally {
            sondando.unlock();
        }
    }

    /** Falha em uso: marca indisponível e agenda o reteste (log só na primeira da sequência). */
    private void marcarFalha(String operacao, Exception ex) {
        boolean estavaAtivo = Boolean.TRUE.equals(disponivel);
        proximaTentativaMs = relogio.getAsLong() + INTERVALO_RETESTE_MS;
        disponivel = false;
        if (estavaAtivo) {
            LOG.warnf("Stream de telemetria indisponivel apos falha em %s (%s); novo teste em 60 s.",
                    operacao, ex.getClass().getSimpleName());
        }
    }

    private boolean testar() {
        try {
            if (!redisDataSource.isResolvable()) {
                return false;
            }
            redisDataSource.get().execute("PING");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Acrescenta o evento à janela.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code false} sem lançar e
     * sem log por evento — falha de telemetria não pode virar tempestade de log.
     * A primeira indisponibilidade já foi registrada em {@link #ativo()}.</p>
     */
    public boolean publicar(TelemetriaEvent evento) {
        if (!ativo() || evento == null) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(evento);
            redisDataSource.get().execute("XADD", chave,
                    "MAXLEN", "~", String.valueOf(maxLen), "*", CAMPO, json);
            return true;
        } catch (Exception ex) {
            marcarFalha("XADD", ex);
            return false;
        }
    }

    /**
     * Últimos eventos da janela, do mais recente para o mais antigo.
     *
     * <p><b>Invariantes do domínio:</b> a ordem devolvida é a mesma da janela em
     * memória (mais novo primeiro), para que trocar a fonte não mude o que o painel
     * mostra.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> lista vazia — quem chama trata
     * vazio como "usar a janela em memória", nunca como "não há eventos".</p>
     */
    public List<TelemetriaEvent> ultimos(int limite) {
        if (!ativo() || limite <= 0) {
            return List.of();
        }
        try {
            Response resposta = redisDataSource.get().execute("XREVRANGE", chave, "+", "-",
                    "COUNT", String.valueOf(limite));
            return converter(resposta);
        } catch (Exception ex) {
            marcarFalha("XREVRANGE", ex);
            return List.of();
        }
    }

    /** Quantas entradas o Stream guarda agora; -1 quando indisponível. */
    public long tamanho() {
        if (!ativo()) {
            return -1;
        }
        try {
            Response r = redisDataSource.get().execute("XLEN", chave);
            return r == null ? -1 : r.toLong();
        } catch (Exception ex) {
            return -1;
        }
    }

    /**
     * Converte a resposta do {@code XREVRANGE} em eventos.
     *
     * <p><b>Invariantes do domínio:</b> entrada que não puder ser desserializada é
     * <b>pulada</b>, não interrompe a leitura inteira — um registro corrompido não
     * pode apagar o painel.</p>
     */
    private List<TelemetriaEvent> converter(Response resposta) {
        List<TelemetriaEvent> eventos = new ArrayList<>();
        if (resposta == null) {
            return eventos;
        }
        for (Response entrada : resposta) {
            try {
                // Cada entrada e [id, [campo, valor, ...]].
                Response campos = entrada.get(1);
                if (campos == null) {
                    continue;
                }
                for (int i = 0; i + 1 < campos.size(); i += 2) {
                    if (CAMPO.equals(campos.get(i).toString())) {
                        eventos.add(objectMapper.readValue(
                                campos.get(i + 1).toString(), TelemetriaEvent.class));
                    }
                }
            } catch (Exception ignorado) {
                // Registro ilegivel nao invalida os demais.
            }
        }
        return eventos;
    }
}
