package org.framework.net.criptografia.presentation;

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
import org.framework.net.criptografia.application.CriptografiaAprofundamentoService;
import org.framework.net.criptografia.domain.CriptografiaAprofundamento;

/** Entrada HTTP do módulo Criptografia: catálogo (Geral) e aprofundamentos. Sub-menu FLAT; slug inexistente = 404. */
@Path("/criptografia")
public class CriptografiaResource {

    private static final String MENU_ATIVO = "criptografia";

    @Inject CriptografiaAprofundamentoService service;
    @Inject @Location("criptografia/index.html") Template index;
    @Inject @Location("criptografia/aprofundamento.html") Template aprofundamento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", CriptografiaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "geral")
                .data("conteudo", service.carregarCatalogo());
    }

    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        CriptografiaAprofundamento item = CriptografiaAprofundamento.porSlug(slug).orElseThrow(NotFoundException::new);
        return aprofundamento.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", CriptografiaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", item.slug())
                .data("item", item)
                .data("conteudo", service.carregarParaExibicao(item.slug()));
    }
}
