package org.framework.net.wifi.presentation;

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
import org.framework.net.wifi.application.WifiAprofundamentoService;
import org.framework.net.wifi.domain.WifiAprofundamento;

/** Entrada HTTP do módulo Wifi: catálogo (Geral) e aprofundamentos. Sub-menu FLAT; slug inexistente = 404. */
@Path("/wifi")
public class WifiResource {

    private static final String MENU_ATIVO = "wifi";

    @Inject WifiAprofundamentoService service;
    @Inject @Location("wifi/index.html") Template index;
    @Inject @Location("wifi/aprofundamento.html") Template aprofundamento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", WifiAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "geral")
                .data("conteudo", service.carregarCatalogo());
    }

    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        WifiAprofundamento item = WifiAprofundamento.porSlug(slug).orElseThrow(NotFoundException::new);
        return aprofundamento.data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", WifiAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", item.slug())
                .data("item", item)
                .data("conteudo", service.carregarParaExibicao(item.slug()));
    }
}
