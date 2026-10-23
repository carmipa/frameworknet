package org.framework.net.telemetria;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class TelemetriaLogger {

    private static final Logger LOG = Logger.getLogger("framework.telemetria");

    @Inject
    TelemetriaContext context;

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaConsoleBuffer consoleBuffer;

    @ConfigProperty(name = "framework.telemetry.enabled", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes @Priority(20) StartupEvent event) {
        logEvent("info", "sistema", "app_start", Map.of("status", "ok"));
    }

    public void logEvent(String level, String modulo, String evento, Map<String, Object> fields) {
        logEvent(level, modulo, evento, "ok", null, fields, false, null);
    }

    public void logEvent(String level, String modulo, String evento, String status, Map<String, Object> fields) {
        logEvent(level, modulo, evento, status, null, fields, false, null);
    }

    public void logEvent(
            String level,
            String modulo,
            String evento,
            String status,
            TelemetriaRequestContext requestContext,
            Map<String, Object> fields,
            boolean excInfo,
            Throwable error) {

        if (!enabled) {
            return;
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (value != null && !String.valueOf(value).isBlank()) {
                    cleaned.put(key, value);
                }
            });
        }

        // Serviços de aplicação chamam logEvent sem o contexto do JAX-RS em mãos.
        // Nesse caso a correlação é resgatada do MDC da própria thread da requisição,
        // senão o evento sai órfão e não há como ligá-lo ao request no dataset.
        TelemetriaRequestContext ctx = requestContext != null ? requestContext : context.contextoDoMdc();
        String message = montarMensagem(evento, status, cleaned);
        context.aplicarEvento(modulo, evento, status);

        TelemetriaEvent telemetriaEvent = new TelemetriaEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                level.toUpperCase(),
                modulo,
                evento,
                status,
                ctx != null ? ctx.requestId() : null,
                ctx != null ? ctx.traceId() : null,
                ctx != null ? ctx.httpMethod() : null,
                ctx != null ? ctx.httpPath() : null,
                null,
                // Duração só sai do contexto ORIGINAL: o resgatado do MDC nasce agora
                // e reportaria 0 ms para uma requisição que já está em andamento.
                requestContext != null ? requestContext.elapsedMillis() : null,
                message,
                cleaned
        );
        store.registrar(telemetriaEvent);
        consoleBuffer.append(level, message);

        switch (level.toLowerCase()) {
            case "debug" -> {
                if (excInfo && error != null) {
                    LOG.debug(message, error);
                } else {
                    LOG.debug(message);
                }
            }
            case "warn", "warning" -> {
                if (excInfo && error != null) {
                    LOG.warn(message, error);
                } else {
                    LOG.warn(message);
                }
            }
            case "error" -> {
                if (excInfo && error != null) {
                    LOG.error(message, error);
                } else {
                    LOG.error(message);
                }
            }
            default -> {
                if (excInfo && error != null) {
                    LOG.info(message, error);
                } else {
                    LOG.info(message);
                }
            }
        }
    }

    public void logHttpAccess(TelemetriaRequestContext ctx, int httpStatus, String pais, String clienteTipo) {
        logHttpAccess(ctx, httpStatus, pais, clienteTipo, 0);
    }

    /**
     * Evento de acesso HTTP de uma requisição.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> alimenta o painel (volume por módulo, rotas, país, bots) e a trilha
     * de investigação.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nunca o IP; quando o teto por origem suprimiu eventos iguais no
     * minuto anterior, a quantidade vai no campo {@code suprimidosAntes} — o painel conta a recusa, não a
     * esconde.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> telemetria desligada ou contexto nulo não grava nada.</p>
     */
    public void logHttpAccess(TelemetriaRequestContext ctx, int httpStatus, String pais, String clienteTipo,
                              int suprimidosAntes) {
        if (!enabled || ctx == null) {
            return;
        }
        String status = httpStatus >= 400 ? "error" : "ok";
        String level = httpStatus >= 500 ? "error" : httpStatus >= 400 ? "warn" : "info";
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("httpStatus", httpStatus);
        fields.put("durationMs", ctx.elapsedMillis());
        // Origem agregavel, nunca o IP: pais (CF-IPCountry) e bot/humano (User-Agent).
        fields.put("pais", pais == null ? "??" : pais);
        fields.put("clienteTipo", clienteTipo == null ? "desconhecido" : clienteTipo);
        if (suprimidosAntes > 0) {
            fields.put("suprimidosAntes", suprimidosAntes);
        }

        TelemetriaEvent telemetriaEvent = new TelemetriaEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                level.toUpperCase(),
                "web",
                "http_access",
                status,
                ctx.requestId(),
                ctx.traceId(),
                ctx.httpMethod(),
                ctx.httpPath(),
                httpStatus,
                ctx.elapsedMillis(),
                "HTTP " + ctx.httpMethod() + " " + ctx.httpPath() + " status=" + httpStatus,
                fields
        );
        store.registrar(telemetriaEvent);
        context.aplicarEvento("web", "http_access", status);
        String httpMsg = "HTTP " + ctx.httpMethod() + " " + ctx.httpPath()
                + " status=" + httpStatus + " durationMs=" + ctx.elapsedMillis()
                + " requestId=" + ctx.requestId();
        consoleBuffer.append(level, httpMsg);
        LOG.infof("evento=http_access status=%s method=%s path=%s httpStatus=%d durationMs=%d requestId=%s",
                status, ctx.httpMethod(), ctx.httpPath(), httpStatus, ctx.elapsedMillis(), ctx.requestId());
    }

    public void logException(String modulo, String evento, TelemetriaRequestContext ctx, Throwable error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (error != null) {
            fields.put("erro", error.getClass().getSimpleName());
            if (error.getMessage() != null) {
                fields.put("mensagem", truncar(error.getMessage(), 300));
            }
        }
        logEvent("error", modulo, evento, "error", ctx, fields, true, error);
    }

    public <T> T medir(String modulo, String evento, Supplier<T> acao) {
        long inicio = System.currentTimeMillis();
        try {
            T resultado = acao.get();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("durationMs", System.currentTimeMillis() - inicio);
            logEvent("info", modulo, evento, "ok", fields);
            return resultado;
        } catch (RuntimeException ex) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("durationMs", System.currentTimeMillis() - inicio);
            fields.put("erro", ex.getClass().getSimpleName());
            logEvent("error", modulo, evento, "error", fields);
            throw ex;
        }
    }

    public void medirVoid(String modulo, String evento, Runnable acao) {
        medir(modulo, evento, () -> {
            acao.run();
            return null;
        });
    }

    private static String montarMensagem(String evento, String status, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("evento=").append(evento).append(" status=").append(status);
        fields.entrySet().stream()
                .filter(entry -> !"status".equals(entry.getKey())) // 'status' já vai no prefixo — evita "status=ok status=ok"
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(' ').append(entry.getKey()).append('=').append(entry.getValue()));
        return sb.toString();
    }

    private static String truncar(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
