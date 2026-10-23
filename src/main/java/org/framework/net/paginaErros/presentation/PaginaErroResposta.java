package org.framework.net.paginaErros.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.paginaErros.application.PaginaErroService;
import org.framework.net.paginaErros.application.PaginaErroService.DadosPaginaErro;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.List;

/**
 * A página de erro do site, para quem a pede — usada pelo mapper de exceções e pelos filtros que recusam a
 * requisição antes do recurso (limite de requisições, CSRF).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> uma regra só para decidir "página ou JSON" e um jeito só de montar a página.
 * Os filtros abortavam com JSON também para a navegação, e o aluno via {@code {"erro":...}} cru na janela
 * (auditoria FRONT-01); o mapper já sabia decidir, mas a regra era privada dele.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> HTML é para gente, JSON é para máquina. A página só sai quando o cliente
 * pede {@code text/html} explicitamente (curinga não conta) <b>e</b> a rota não é de API — página inteira no
 * lugar do JSON quebraria o {@code response.json()} de todo {@code fetch()}. O código HTTP é sempre o real.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> se a renderização do template falhar, devolve texto simples com o
 * código correto e registra a falha — a página de erro nunca pode gerar um segundo erro. Cabeçalho
 * {@code Accept} ilegível conta como "não quer página".</p>
 */
@ApplicationScoped
public class PaginaErroResposta {

    private static final String TIPO_HTML = MediaType.TEXT_HTML + ";charset=UTF-8";

    @Inject
    PaginaErroService paginaErroService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("paginaErros/erro.html")
    Template pagina;

    /** O cliente quer uma página? Rota de API nunca recebe HTML, mesmo que o {@code Accept} peça. */
    public boolean querPagina(String caminho, List<MediaType> aceitos) {
        if (caminho == null || caminho.contains("/api/") || caminho.endsWith("/api")) {
            return false;
        }
        try {
            return aceitos != null && aceitos.stream()
                    .anyMatch(tipo -> tipo.isCompatible(MediaType.TEXT_HTML_TYPE) && !tipo.isWildcardType());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * A recusa de um filtro que barra a requisição antes do recurso (limite, CSRF).
     *
     * <p>Navegação recebe a página do catálogo para o código; fetch, htmx ({@code HX-Request}) e API recebem o
     * JSON dado, que é a mensagem que cada filtro já devolvia. O htmx fica no JSON porque o
     * {@code htmx-setup.js} troca o corpo por um aviso próprio pelo código — página inteira ali não teria uso.</p>
     */
    public Response recusa(ContainerRequestContext pedido, int status, String json) {
        String caminho = pedido.getUriInfo().getPath();
        if (caminho == null || caminho.isBlank()) {
            caminho = "/";
        } else if (!caminho.startsWith("/")) {
            caminho = "/" + caminho;
        }
        boolean htmx = "true".equalsIgnoreCase(pedido.getHeaderString("HX-Request"));
        List<MediaType> aceitos;
        try {
            aceitos = pedido.getAcceptableMediaTypes();
        } catch (RuntimeException ilegivel) {
            aceitos = List.of();
        }
        if (!htmx && querPagina(caminho, aceitos)) {
            return pagina(status, caminho, pedido.getMethod());
        }
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(json).build();
    }

    /** A página de erro do catálogo para o código, o caminho e o método dados. */
    public Response pagina(int status, String caminho, String metodo) {
        try {
            DadosPaginaErro dados = paginaErroService.montar(status, caminho, metodo);
            String html = pagina
                    .data("erro", dados.erro())
                    .data("dados", dados)
                    .render();
            return Response.status(status).entity(html).type(TIPO_HTML).build();
        } catch (RuntimeException falhaNaPagina) {
            telemetriaLogger.logException("paginaErros", "falha_na_pagina_de_erro", null, falhaNaPagina);
            return Response.status(status)
                    .entity("HTTP " + status + " — não foi possível renderizar a página de erro.")
                    .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                    .build();
        }
    }
}
