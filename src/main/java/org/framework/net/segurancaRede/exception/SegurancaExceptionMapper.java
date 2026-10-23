package org.framework.net.segurancaRede.exception;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.shared.HtmxRespostas;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.exception.TelemetriaExceptionSupport;

@Provider
public class SegurancaExceptionMapper implements ExceptionMapper<SegurancaException> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("segurancaRede/partials/erro.html")
    Template erroFragmento;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(SegurancaException exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "seguranca", "domain_exception", requestContext, exception);

        if (HtmxRespostas.veioDoHtmx(requestContext)) {
            return HtmxRespostas.erro(erroFragmento, exception.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
