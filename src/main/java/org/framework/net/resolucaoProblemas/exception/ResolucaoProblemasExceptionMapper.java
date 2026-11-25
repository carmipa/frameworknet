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

    /**
     * Converte a exceção de domínio do Resolução de Problemas na resposta JSON e registra conforme o status.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> entrada inválida volta como 400 com o que corrigir; qualquer outra falha do
     * módulo volta como 500.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o status é decidido antes do registro: 4xx vira uma linha WARN sem pilha
     * (erro de quem enviou); 5xx continua ERROR com a pilha saneada (falha nossa precisa da pilha).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; o corpo é sempre JSON com {@code erro}.</p>
     */
    @Override
    public Response toResponse(ResolucaoProblemasException exception) {
        int status = exception instanceof EntradaInvalidaException
                ? Response.Status.BAD_REQUEST.getStatusCode()
                : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        TelemetriaExceptionSupport.registrar(
                telemetriaLogger, "resolucaoProblemas", "domain_exception", requestContext, exception, status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("erro", exception.getMessage());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
