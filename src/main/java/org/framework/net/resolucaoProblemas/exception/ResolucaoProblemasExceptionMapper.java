package org.framework.net.resolucaoProblemas.exception;

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
public class ResolucaoProblemasExceptionMapper implements ExceptionMapper<ResolucaoProblemasException> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(ResolucaoProblemasException exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "resolucaoProblemas", "domain_exception", requestContext, exception);
        int status = exception instanceof EntradaInvalidaException
                ? Response.Status.BAD_REQUEST.getStatusCode()
                : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("erro", exception.getMessage());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
