package org.framework.net.calculadora.exception;

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
 * Converte {@link CalculadoraException} na resposta certa para cada cliente.
 *
 * <p><b>Propósito de negócio:</b> garantir que um erro de plano de endereçamento
 * apareça dentro do painel de resultado da tela, sem recarregar a página e sem
 * perder o que o usuário já digitou no formulário.</p>
 *
 * <p><b>Invariantes do domínio:</b> toda ocorrência é registrada na telemetria
 * antes de responder; requisições do htmx (cabeçalho {@code HX-Request})
 * recebem HTML, qualquer outro cliente recebe texto puro.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> sempre responde HTTP 400 com a
 * mensagem da exceção — nunca propaga a exceção adiante nem devolve 500.</p>
 */
@Provider
public class CalculadoraExceptionMapper implements ExceptionMapper<CalculadoraException> {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("calculadora/partials/erro.html")
    Template erroFragmento;

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(CalculadoraException exception) {
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "calculadora", "domain_exception", requestContext, exception);

        if (HtmxRespostas.veioDoHtmx(requestContext)) {
            return HtmxRespostas.erro(erroFragmento, exception.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
