package org.framework.net.camadas.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.camadas.application.CamadasAprofundamentoService;
import org.framework.net.camadas.domain.CamadaAprofundamento;

/**
 * Entrada HTTP do módulo de Camadas: catálogo (Geral) e aprofundamentos.
 *
 * <p><b>Invariantes do domínio:</b> todas as rotas vivem NESTA classe; o sub-menu
 * FLAT vem de {@link CamadaAprofundamento#disponiveis()}. Slug inexistente é 404,
 * nunca a página de outro aprofundamento.</p>
 */
@Path("/camadas")
public class CamadasResource {

    private static final String MENU_ATIVO = "camadas";

    @Inject
    CamadasAprofundamentoService service;

    @Inject
    @Location("camadas/index.html")
    Template index;

    @Inject
    @Location("camadas/aprofundamento.html")
    Template aprofundamento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index
                .data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", CamadaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "geral")
                .data("conteudo", service.carregarCatalogo());
    }

    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        CamadaAprofundamento item = CamadaAprofundamento.porSlug(slug)
                .orElseThrow(NotFoundException::new);
        return aprofundamento
                .data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", CamadaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", item.slug())
                .data("item", item)
                .data("conteudo", service.carregarParaExibicao(item.slug()));
    }
}
