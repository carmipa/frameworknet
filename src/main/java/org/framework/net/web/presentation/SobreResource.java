package org.framework.net.web.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Página "Sobre" ({@code /sobre}): autor, formação, contatos e stack de tecnologias do projeto.
 */
@Path("/sobre")
public class SobreResource {

    @Inject
    @io.quarkus.qute.Location("sobre/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "sobre");
    }
}
