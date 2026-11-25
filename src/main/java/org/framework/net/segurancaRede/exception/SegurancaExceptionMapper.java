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

    /**
     * Converte a exceção de domínio do módulo na resposta 400 e registra a recusa.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a pessoa digitou algo que o módulo não aceita; ela recebe a mensagem que
     * explica o que corrigir.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a resposta é sempre 400, com a mensagem da exceção; o registro é uma linha
     * WARN com o nome da classe e a mensagem saneada, sem pilha — recusa de entrada não é falha do servidor, e a
     * pilha a cada pedido girava o log e apagava a evidência de incidente real.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; pedido do htmx recebe o fragmento de erro, os demais o
     * texto puro.</p>
     */
    @Override
    public Response toResponse(SegurancaException exception) {
        TelemetriaExceptionSupport.registrar(telemetriaLogger, "seguranca", "domain_exception", requestContext,
                exception, Response.Status.BAD_REQUEST.getStatusCode());

        if (HtmxRespostas.veioDoHtmx(requestContext)) {
            return HtmxRespostas.erro(erroFragmento, exception.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
