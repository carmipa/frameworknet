package org.framework.net.shared;

import io.quarkus.qute.Template;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Apoio comum aos mapeadores de exceção que atendem requisições do htmx.
 *
 * <p>O htmx troca no alvo o corpo da resposta, então um erro de domínio precisa
 * voltar como fragmento HTML já renderizado. Para qualquer outro cliente (API,
 * testes, curl) a resposta continua sendo texto puro.</p>
 */
public final class HtmxRespostas {

    private HtmxRespostas() {
    }

    /** Toda requisição disparada pelo htmx traz o cabeçalho {@code HX-Request}. */
    public static boolean veioDoHtmx(ContainerRequestContext requestContext) {
        return requestContext != null && requestContext.getHeaderString("HX-Request") != null;
    }

    /** 400 com o fragmento de erro renderizado, pronto para o htmx trocar no alvo. */
    public static Response erro(Template fragmento, String mensagem) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(fragmento.data("mensagem", mensagem))
                .type(MediaType.TEXT_HTML)
                .build();
    }
}
