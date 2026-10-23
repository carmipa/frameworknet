package org.framework.net.analiseDidatica.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.vertx.core.http.HttpServerRequest;
import org.framework.net.analiseDidatica.application.regiaoGeografica.GeoService;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("")
public class RegiaoGeograficaResource {

    @Inject
    GeoService geoService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @io.quarkus.qute.Location("analiseDidatica/geo/informacoes.html")
    Template informacoes;

    @GET
    @Path("/informacoes")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInformacoes(
            @QueryParam("ip") String ip,
            @Context HttpHeaders headers,
            @Context HttpServerRequest request) {
        String remote = request.remoteAddress() != null ? request.remoteAddress().host() : "";
        String clienteIp = geoService.clienteIpEfetivo(
                headers.getHeaderString("X-Forwarded-For"),
                headers.getHeaderString("X-Real-IP"),
                remote);
        Map<String, Object> model = geoService.montarPaginaInformacoes(clienteIp, ip);
        return informacoes
                .data("activeMainMenu", "informacoes")
                .data("clienteIp", model.get("cliente_ip"))
                .data("consultado", model.get("consultado"))
                .data("modoGeo", model.get("modo_geo"))
                .data("geo", model.get("geo"))
                .data("ipDigitadoPrefill", model.get("ip_digitado_prefill"))
                .data("geoPayloadJson", montarGeoPayloadJson(model));
    }

    @SuppressWarnings("unchecked")
    private String montarGeoPayloadJson(Map<String, Object> model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cliente_ip", model.get("cliente_ip"));
        payload.put("consultado", model.get("consultado"));
        payload.put("modo", model.get("modo_geo"));
        Object geo = model.get("geo");
        if (geo instanceof Map<?, ?> geoMap) {
            payload.putAll((Map<String, Object>) geoMap);
        }
        try {
            // Vai CRU (.raw) para <script type="application/json">, onde entidade HTML não é
            // decodificada. <, > e & viram escapes JSON (válidos só dentro de string, único lugar em
            // que aparecem): "</script>" digitado pelo visitante não fecha o bloco.
            return objectMapper.writeValueAsString(payload)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    @GET
    @Path("/api/informacoes/geo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response apiInformacoesGeo(
            @QueryParam("ip") String ip,
            @Context HttpHeaders headers,
            @Context HttpServerRequest request) {
        String remote = request.remoteAddress() != null ? request.remoteAddress().host() : "";
        String clienteIp = geoService.clienteIpEfetivo(
                headers.getHeaderString("X-Forwarded-For"),
                headers.getHeaderString("X-Real-IP"),
                remote);
        return Response.ok(geoService.executarApiInformacoesGeo(clienteIp, ip)).build();
    }
}
