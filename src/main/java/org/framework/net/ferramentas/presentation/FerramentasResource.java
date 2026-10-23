package org.framework.net.ferramentas.presentation;

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
import org.framework.net.ferramentas.application.FerramentasAprofundamentoService;
import org.framework.net.ferramentas.domain.ComandoRede;
import org.framework.net.ferramentas.domain.FerramentasAprofundamento;

/** Entrada HTTP do módulo Ferramentas: catálogo (Geral) e aprofundamentos. Sub-menu FLAT; slug inexistente = 404. */
@Path("/ferramentas")
public class FerramentasResource {

    private static final String MENU_ATIVO = "ferramentas";

    @Inject FerramentasAprofundamentoService service;
    @Inject @Location("ferramentas/index.html") Template index;
    @Inject @Location("ferramentas/aprofundamento.html") Template aprofundamento;
    @Inject @Location("ferramentas/rede.html") Template rede;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", FerramentasAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "geral")
                .data("conteudo", service.carregarCatalogo());
    }

    /**
     * Aba "Rede: Windows × Linux" — comparação didática por intenção. Rota literal,
     * casada antes de {@code /{slug}} pelo JAX-RS; só leitura, nada é executado.
     */
    @GET
    @Path("/rede")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance redeWindowsLinux() {
        return rede.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", FerramentasAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "rede")
                .data("categorias", ComandoRede.categorias())
                .data("comandos", ComandoRede.catalogo());
    }

    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        FerramentasAprofundamento item = FerramentasAprofundamento.porSlug(slug).orElseThrow(NotFoundException::new);
        return aprofundamento.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", FerramentasAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", item.slug())
                .data("item", item)
                .data("conteudo", service.carregarParaExibicao(item.slug()));
    }
}
