package org.framework.net.localizacao.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.localizacao.application.LocalizacaoService;

/**
 * Módulo Localização: localiza por IP (aproximado, cidade/ISP) ou por CEP (endereço + mapa).
 */
@Path("/localizacao")
public class LocalizacaoResource {

    @Inject
    LocalizacaoService localizacaoService;

    @Inject
    @io.quarkus.qute.Location("localizacao/index.html")
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "localizacao");
    }

    @GET
    @Path("/api/ip")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porIp(
            @QueryParam("ip") String ip,
            @Context HttpHeaders headers,
            @Context HttpServerRequest request) {
        String alvo = ip == null ? "" : ip.strip();
        if (alvo.isBlank()) {
            alvo = clienteIp(headers, request);
        }
        return Response.ok(localizacaoService.localizarPorIp(alvo)).build();
    }

    @GET
    @Path("/api/cep")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porCep(@QueryParam("cep") String cep) {
        return Response.ok(localizacaoService.localizarPorCep(cep)).build();
    }

    /** Localização exata via GPS do navegador (coordenadas informadas com consentimento do usuário). */
    @GET
    @Path("/api/gps")
    @Produces(MediaType.APPLICATION_JSON)
    public Response porGps(@QueryParam("lat") Double lat, @QueryParam("lon") Double lon) {
        // NaN passa por qualquer comparação (sempre false) e saía como sucesso: Double.isFinite fecha (BACK-11).
        if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return Response.ok(java.util.Map.of("ok", false,
                    "mensagem", "Coordenadas inválidas.")).build();
        }
        return Response.ok(localizacaoService.localizarPorCoordenadas(lat, lon)).build();
    }

    /**
     * Inspeção da própria requisição (autoteste de privacidade): mostra a cadeia de cabeçalhos
     * de proxy e qual IP foi escolhido como "real". Não persiste nada.
     */
    @GET
    @Path("/api/inspecao")
    @Produces(MediaType.APPLICATION_JSON)
    public Response inspecao(@Context HttpHeaders headers, @Context HttpServerRequest request) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        String ipConexao = (request != null && request.remoteAddress() != null)
                ? request.remoteAddress().host() : null;
        String xff = headers.getHeaderString("X-Forwarded-For");
        String realIp = headers.getHeaderString("X-Real-IP");

        java.util.List<String> cadeia = new java.util.ArrayList<>();
        if (xff != null && !xff.isBlank()) {
            for (String parte : xff.split(",")) {
                String p = parte.strip();
                if (!p.isEmpty()) {
                    cadeia.add(p);
                }
            }
        }

        boolean atrasProxy = (xff != null && !xff.isBlank()) || (realIp != null && !realIp.isBlank());
        out.put("ipReal", clienteIp(headers, request));
        out.put("ipConexao", ipConexao);
        out.put("xForwardedFor", xff);
        out.put("xForwardedForChain", cadeia);
        out.put("xRealIp", realIp);
        out.put("cfConnectingIp", headers.getHeaderString("CF-Connecting-IP"));
        out.put("forwarded", headers.getHeaderString("Forwarded"));
        out.put("via", headers.getHeaderString("Via"));
        out.put("userAgent", headers.getHeaderString("User-Agent"));
        out.put("acceptLanguage", headers.getHeaderString("Accept-Language"));
        out.put("atrasDeProxy", atrasProxy);
        return Response.ok(out).build();
    }

    private static String clienteIp(HttpHeaders headers, HttpServerRequest request) {
        String xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        String realIp = headers.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host();
        }
        return "";
    }
}
