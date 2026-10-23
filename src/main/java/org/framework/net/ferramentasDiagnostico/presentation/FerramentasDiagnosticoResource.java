package org.framework.net.ferramentasDiagnostico.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.ferramentasDiagnostico.application.DiagnosticoService;

@Path("/diagnostico")
public class FerramentasDiagnosticoResource {

    @Inject
    DiagnosticoService diagnosticoService;

    @Inject
    @io.quarkus.qute.Location("ferramentasDiagnostico/index.html")
    Template index;

    @Inject
    @io.quarkus.qute.Location("ferramentasDiagnostico/partials/resultado_didatico.html")
    Template resultadoFragmento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial() {
        return index.data("activeMainMenu", "diagnostico");
    }

    /** Ping simulado, dissecado, como fragmento trocado pelo htmx. */
    @POST
    @Path("/api/ping")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarPing(@FormParam("host") String host) {
        return resultadoFragmento.data("r", diagnosticoService.executarPingSimulado(host));
    }

    /** Resolução DNS (dig) simulada, dissecada, como fragmento trocado pelo htmx. */
    @POST
    @Path("/api/dns")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarDns(@FormParam("dominio") String dominio) {
        return resultadoFragmento.data("r", diagnosticoService.executarDnsSimulado(dominio));
    }

    /** Traceroute simulado (manipulação de TTL), dissecado. */
    @POST
    @Path("/api/traceroute")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarTraceroute(@FormParam("host") String host) {
        return resultadoFragmento.data("r", diagnosticoService.executarTracerouteSimulado(host));
    }

    /** Ping sweep simulado (descoberta de hosts vivos), dissecado. */
    @POST
    @Path("/api/ping-sweep")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarPingSweep(@FormParam("rede") String rede) {
        return resultadoFragmento.data("r", diagnosticoService.executarPingSweepSimulado(rede));
    }

    /** Varredura de portas SYN stealth simulada, dissecada. */
    @POST
    @Path("/api/scan")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarScan(@FormParam("host") String host) {
        return resultadoFragmento.data("r", diagnosticoService.executarScanSimulado(host));
    }

    /** Simulação didática de DNS spoofing (corrida de respostas), dissecada. */
    @POST
    @Path("/api/dns-spoofing")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance executarDnsSpoofing(@FormParam("dominio") String dominio) {
        return resultadoFragmento.data("r", diagnosticoService.executarDnsSpoofingSimulado(dominio));
    }
}
