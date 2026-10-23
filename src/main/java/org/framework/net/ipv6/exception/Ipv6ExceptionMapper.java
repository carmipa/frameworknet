package org.framework.net.ipv6.exception;

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

/**
 * Converte {@link Ipv6Exception} na resposta certa para cada cliente (espelha o mapper da
 * Calculadora IPv4).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> mostrar o erro dentro do painel de resultado da tela IPv6, sem
 * recarregar a página nem perder o que o usuário digitou.</p>
 * <p><b>INVARIANTES DO DOMÍNIO:</b> toda ocorrência é registrada na telemetria antes de responder;
 * cliente htmx recebe HTML, os demais recebem texto puro.</p>
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> sempre HTTP 400 com a mensagem — nunca 500.</p>
 */
@Provider
public class Ipv6ExceptionMapper implements ExceptionMapper<Ipv6Exception> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("ipv6/partials/erro.html")
    Template erroFragmento;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(Ipv6Exception exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "ipv6", "domain_exception", requestContext, exception);

        if (HtmxRespostas.veioDoHtmx(requestContext)) {
            return HtmxRespostas.erro(erroFragmento, exception.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
