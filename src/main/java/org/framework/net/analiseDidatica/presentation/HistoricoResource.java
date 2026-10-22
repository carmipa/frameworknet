package org.framework.net.analiseDidatica.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.analiseDidatica.exception.HistoricoPersistenciaException;
import org.framework.net.analiseDidatica.infrastructure.historico.HistoricoStore;

import java.util.Map;
import java.util.Set;

@Path("/history")
public class HistoricoResource {

    private static final Set<String> MODOS_CATALOGO = Set.of("portas", "protocolos");
    private static final int MAX_ENTRADA_LENGTH = 500;

    @Inject
    HistoricoStore historicoStore;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> listar() {
        return Map.of("items", historicoStore.listar());
    }

    @POST
    @Path("/catalog")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrarCatalogo(Map<String, Object> payload) {
        if (payload == null) {
            // Corpo JSON "null": era NPE e 500 genérico (BACK-06). É pedido malformado, como "{}" sem modo.
            return Response.status(400).entity(Map.of("ok", false, "erro", "corpo ausente")).build();
        }
        String modo = String.valueOf(payload.getOrDefault("modo", "")).strip().toLowerCase();
        if (!MODOS_CATALOGO.contains(modo)) {
            return Response.status(400).entity(Map.of("ok", false, "erro", "modo inválido")).build();
        }
        String entrada = String.valueOf(payload.getOrDefault("entrada", "")).strip();
        if (entrada.length() > MAX_ENTRADA_LENGTH) {
            return Response.status(400).entity(Map.of("ok", false, "erro", "entrada muito longa")).build();
        }
        try {
            historicoStore.registrarCatalogo(modo, entrada);
            return Response.ok(Map.of("ok", true)).build();
        } catch (HistoricoPersistenciaException ex) {
            return Response.status(503).entity(Map.of("ok", false, "erro", "persistencia_indisponivel")).build();
        }
    }
}
