package org.framework.net.telemetria;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Teto de eventos de erro de cliente gravados na telemetria, por origem e por minuto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a telemetria grava um evento por resposta. Sem teto, um único cliente
 * disparando 404, 401, 403 ou 429 em série enchia o arquivo durável (2 × 10 MiB), o Stream do Redis e o log
 * do container, empurrando para fora a trilha de todo o resto (auditoria OPS-01 e SEC-02). Com o teto, a
 * telemetria continua mostrando que a recusa aconteceu — só não guarda mil cópias dela.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a origem vem do endereço da conexão resolvido pelo Quarkus (mesma régua
 * do limite de requisições), nunca de cabeçalho do cliente. Cada (origem, categoria) grava no máximo
 * {@code framework.telemetria.teto-eventos-por-minuto} eventos por minuto. Nada é escondido: quantos
 * ficaram de fora num minuto vai no primeiro evento gravado do minuto seguinte para a mesma chave. O mapa é
 * podado a cada virada de minuto, então não cresce com a escolha de quem chama.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança. Sem endereço disponível, as chamadas caem no balde
 * {@code anonymous} (mais restritivo, nunca mais permissivo). Os suprimidos de um minuto sem evento
 * seguinte na mesma chave não são reportados (risco residual declarado).</p>
 */
@ApplicationScoped
public class TetoDeEventos {

    @ConfigProperty(name = "framework.telemetria.teto-eventos-por-minuto", defaultValue = "10")
    int tetoPorMinuto;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    /** Relógio em milissegundos; trocado só por teste do pacote para simular a virada de minuto. */
    java.util.function.LongSupplier relogio = System::currentTimeMillis;

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final AtomicLong ultimaPoda = new AtomicLong(-1);

    /** O que fazer com o evento: gravar ou não, e quantos da mesma chave ficaram de fora antes dele. */
    public record Decisao(boolean gravar, int suprimidosAntes) {
    }

    /**
     * Decide se o evento da categoria dada (ex.: {@code http-429}, {@code pagina-erro-404}) entra na
     * telemetria para a origem da requisição em curso.
     */
    public Decisao avaliar(String categoria) {
        long minuto = relogio.getAsLong() / 60_000L;
        long anterior = ultimaPoda.get();
        if (minuto != anterior && ultimaPoda.compareAndSet(anterior, minuto)) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto < minuto - 1);
        }
        String chave = origem() + "|" + categoria;
        int[] suprimidosDoMinutoAnterior = {0};
        Janela janela = janelas.compute(chave, (k, atual) -> {
            if (atual == null || atual.minuto != minuto) {
                if (atual != null && atual.minuto == minuto - 1) {
                    suprimidosDoMinutoAnterior[0] = Math.max(0, atual.contagem.get() - tetoPorMinuto);
                }
                return new Janela(minuto, new AtomicInteger(0), suprimidosDoMinutoAnterior[0]);
            }
            return atual;
        });
        int n = janela.contagem.incrementAndGet();
        boolean gravar = n <= tetoPorMinuto;
        int reportar = gravar && n == 1 ? janela.suprimidosPendentes : 0;
        return new Decisao(gravar, reportar);
    }

    private String origem() {
        try {
            if (currentVertxRequest != null && currentVertxRequest.getCurrent() != null
                    && currentVertxRequest.getCurrent().request() != null
                    && currentVertxRequest.getCurrent().request().remoteAddress() != null) {
                return currentVertxRequest.getCurrent().request().remoteAddress().host();
            }
        } catch (RuntimeException semRequisicao) {
            // cai no balde anônimo
        }
        return "anonymous";
    }

    private record Janela(long minuto, AtomicInteger contagem, int suprimidosPendentes) {
    }
}
