package org.framework.net.telemetria.exception;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.framework.net.telemetria.TelemetriaContext;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaRequestContext;

public final class TelemetriaExceptionSupport {

    private TelemetriaExceptionSupport() {
    }

    public static void registrar(
            TelemetriaLogger logger,
            String modulo,
            String evento,
            ContainerRequestContext requestContext,
            Throwable error) {
        TelemetriaRequestContext ctx = extrairContexto(requestContext);
        logger.logException(modulo, evento, ctx, error);
    }

    /**
     * Registra a exceção de domínio conforme o status HTTP que o mapeador vai devolver.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> recusa de entrada (4xx) é erro de quem enviou, não do servidor: registrada como
     * ERROR com a pilha inteira, cada pedido inválido gerava cerca de 20 linhas — sob o limite global de pedidos, a
     * janela de log girava em minutos e apagava a evidência de incidente real, e o dono via ERROR onde só houve
     * digitação errada.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> status de 400 a 499 vira UM evento WARN, com status {@code error} (o mesmo do
     * acesso HTTP 4xx e da operação que falhou: no dataset o incidente continua um só), {@code erro} = nome da classe, {@code mensagem} (saneada e com teto pelo logger) e {@code httpStatus}, sem
     * pilha; qualquer outro status (5xx ou desconhecido) segue o registro completo — ERROR com a pilha saneada.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; exceção nula registra só o evento.</p>
     */
    public static void registrar(
            TelemetriaLogger logger,
            String modulo,
            String evento,
            ContainerRequestContext requestContext,
            Throwable error,
            int statusHttp) {
        if (statusHttp >= 400 && statusHttp < 500 && error != null) {
            java.util.Map<String, Object> campos = new java.util.LinkedHashMap<>();
            campos.put("erro", error.getClass().getSimpleName());
            if (error.getMessage() != null) {
                campos.put("mensagem", error.getMessage());
            }
            campos.put("httpStatus", statusHttp);
            logger.logEvent("warn", modulo, evento, "error", extrairContexto(requestContext), campos, false, null);
            return;
        }
        registrar(logger, modulo, evento, requestContext, error);
    }

    public static TelemetriaRequestContext extrairContexto(ContainerRequestContext requestContext) {
        if (requestContext == null) {
            return null;
        }
        Object property = requestContext.getProperty(TelemetriaContext.REQUEST_CONTEXT_PROPERTY);
        if (property instanceof TelemetriaRequestContext ctx) {
            return ctx;
        }
        return null;
    }
}
