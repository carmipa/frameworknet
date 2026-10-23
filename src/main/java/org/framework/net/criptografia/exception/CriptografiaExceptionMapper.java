package org.framework.net.criptografia.exception;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.exception.TelemetriaExceptionSupport;

@Provider
public class CriptografiaExceptionMapper implements ExceptionMapper<CriptografiaException> {
    @Inject TelemetriaLogger telemetriaLogger;
    @Context ContainerRequestContext requestContext;
    @Override public Response toResponse(CriptografiaException exception) {
        TelemetriaExceptionSupport.registrar(telemetriaLogger, "criptografia", "domain_exception", requestContext, exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exception.getMessage()).type(MediaType.TEXT_PLAIN).build();
    }
}
