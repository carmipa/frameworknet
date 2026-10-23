package org.framework.net.analiseDidatica.exception;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.exception.TelemetriaExceptionSupport;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class AnaliseDidaticaExceptionMapper implements ExceptionMapper<AnaliseDidaticaException> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(AnaliseDidaticaException exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "analiseDidatica", "domain_exception", requestContext, exception);
        int status = resolveStatus(exception);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("erro", exception.getMessage());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private static int resolveStatus(AnaliseDidaticaException exception) {
        if (exception instanceof EntradaInvalidaException) {
            return Response.Status.BAD_REQUEST.getStatusCode();
        }
        if (exception instanceof DnsResolucaoException) {
            return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }
}
