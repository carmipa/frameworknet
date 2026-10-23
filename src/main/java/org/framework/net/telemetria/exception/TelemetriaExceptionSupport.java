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
