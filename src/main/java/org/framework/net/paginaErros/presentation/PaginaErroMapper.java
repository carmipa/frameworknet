package org.framework.net.paginaErros.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.security.RequestRateLimiter;
import org.framework.net.telemetria.TelemetriaLogger;

/**
 * Traduz qualquer falha não tratada na página de erro do Framework.
 *
 * <p><b>Propósito de negócio:</b> URL digitada errada, sessão expirada ou rota
 * inexistente devolviam a tela padrão do servidor — branca, em inglês e sem
 * caminho de volta. Este mapper entrega, no lugar, a página no mesmo desenho do
 * app, com o que aconteceu, o {@code trace_id} real e atalhos para os módulos.</p>
 *
 * <p><b>Invariantes do domínio:</b> HTML é para gente, JSON é para máquina. A
 * página só é devolvida quando o cliente pede {@code text/html} <b>e</b> a rota
 * não é de API — do contrário devolver HTML quebraria em silêncio todo
 * {@code fetch()} do frontend, que faria {@code response.json()} sobre uma página
 * inteira. Resposta de erro já formatada por outro componente (o 429 do rate
 * limit, por exemplo) é preservada tal como veio.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> se a própria renderização do template
 * falhar, cai para uma resposta de texto simples com o código correto — o mapper
 * jamais pode ser a origem de um segundo erro, que produziria recursão. Detalhe
 * interno da exceção (classe, mensagem, stack) nunca vai para a tela: o que liga
 * o usuário ao diagnóstico é o {@code trace_id}, e vazar interno de exceção em
 * página pública é entregar mapa da aplicação a quem estiver sondando.</p>
 */
@Provider
public class PaginaErroMapper implements ExceptionMapper<Throwable> {

    @Inject
    PaginaErroResposta paginaErroResposta;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders httpHeaders;

    @Context
    Request request;

    @Inject
    RequestRateLimiter rateLimiter;

    @Context
    ResourceInfo resourceInfo;

    /** Balde único do limite para rota inexistente: o caminho é escolha de quem chama, não identidade. */
    static final String CHAVE_ROTA_INEXISTENTE = "rota-inexistente";

    @Override
    public Response toResponse(Throwable exception) {
        int status = statusDe(exception);
        String caminho = caminhoAtual();
        String metodo = metodoAtual();

        // Rota inexistente não passa pelos filtros JAX-RS (não são @PreMatching): sem isto, 404 em série
        // escapava do limite de requisições, e cada um renderizava a página e gravava evento (SEC-02).
        if (status == Response.Status.NOT_FOUND.getStatusCode() && semRecursoResolvido()
                && !rateLimiter.allow(null, CHAVE_ROTA_INEXISTENTE, false)) {
            int recusa = Response.Status.TOO_MANY_REQUESTS.getStatusCode();
            if (!querHtml(caminho)) {
                return Response.status(recusa).type(MediaType.APPLICATION_JSON)
                        .entity("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}").build();
            }
            return paginaErroResposta.pagina(recusa, caminho, metodo);
        }

        if (status >= 500) {
            telemetriaLogger.logException("paginaErros", "falha_nao_tratada", null, exception);
        }

        if (!querHtml(caminho)) {
            return respostaTecnica(exception, status);
        }
        return paginaErroResposta.pagina(status, caminho, metodo);
    }

    /**
     * Status a apresentar.
     *
     * <p><b>Comportamento em caso de falha:</b> exceção sem status próprio vira
     * 500 — falhar para o código mais grave evita apresentar um erro de servidor
     * como se fosse culpa do usuário.</p>
     */
    private int statusDe(Throwable exception) {
        if (exception instanceof WebApplicationException wae && wae.getResponse() != null) {
            return wae.getResponse().getStatus();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    /**
     * O cliente quer uma página?
     *
     * <p><b>Invariantes do domínio:</b> rota de API nunca recebe HTML, mesmo que o
     * cabeçalho {@code Accept} peça — é o caminho por onde o frontend conversa com
     * o backend, e uma página inteira no lugar do JSON quebra o
     * {@code response.json()} sem mensagem de erro compreensível.</p>
     */
    private boolean querHtml(String caminho) {
        try {
            return paginaErroResposta.querPagina(caminho, httpHeaders.getAcceptableMediaTypes());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Resposta para cliente que não é navegador.
     *
     * <p><b>Invariantes do domínio:</b> resposta já montada por outro componente
     * (rate limit, filtros de segurança) é devolvida intacta — reescrevê-la
     * apagaria a mensagem que aquele componente escolheu dar.</p>
     */
    private Response respostaTecnica(Throwable exception, int status) {
        if (exception instanceof WebApplicationException wae && wae.getResponse() != null
                && wae.getResponse().hasEntity()) {
            return wae.getResponse();
        }
        return Response.status(status)
                .entity("{\"status\":" + status + ",\"erro\":\""
                        + org.framework.net.paginaErros.domain.CatalogoErros.porCodigo(status).titulo()
                        + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String caminhoAtual() {
        try {
            String caminho = uriInfo.getPath();
            if (caminho == null || caminho.isBlank()) {
                return "/";
            }
            return caminho.startsWith("/") ? caminho : "/" + caminho;
        } catch (RuntimeException ex) {
            return "/";
        }
    }

    /** Nenhum método de recurso casou com a requisição (rota inexistente). */
    private boolean semRecursoResolvido() {
        try {
            return resourceInfo == null || resourceInfo.getResourceMethod() == null;
        } catch (RuntimeException semContexto) {
            return true;
        }
    }

    private String metodoAtual() {
        try {
            return request.getMethod();
        } catch (RuntimeException ex) {
            return "GET";
        }
    }
}
