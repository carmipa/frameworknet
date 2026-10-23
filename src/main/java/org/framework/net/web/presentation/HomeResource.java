package org.framework.net.web.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Página inicial (landing) do framework em {@code /}.
 *
 * <p>Serve como portal de entrada com visão geral e atalhos para os módulos, evitando
 * que o usuário caia diretamente na Análise Didática (movida para {@code /analise}).
 */
@Path("/")
public class HomeResource {

    @Inject
    @io.quarkus.qute.Location("home/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        return index.data("activeMainMenu", "home");
    }
}
