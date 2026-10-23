package org.framework.net.analiseDidatica.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.analiseDidatica.application.mascara.MascaraReferenciaService;

import java.util.Map;

@Path("/mascara-referencia")
public class MascaraReferenciaResource {

    @Inject
    MascaraReferenciaService mascaraReferenciaService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> referencia() {
        return Map.of("table", mascaraReferenciaService.tabela());
    }
}
