package org.framework.net.simuladores.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.simuladores.application.EncapsulamentoService;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * API do simulador de encapsulamento (consumido pela sub-aba "Encapsulamento" da página Tráfego).
 * Lógica própria no pacote {@code simuladores}; computação pura, sem acesso privilegiado.
 */
@Path("/simuladores/api")
public class EncapsulamentoResource {

    @Inject
    EncapsulamentoService encapsulamentoService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @io.quarkus.qute.Location("simuladores/partials/resultado_encapsulamento.html")
    Template resultadoFragmento;

    /**
     * Encapsula a mensagem e devolve a pilha de camadas já renderizada como fragmento,
     * trocado pelo htmx na sub-aba. Entrada inválida também volta como fragmento (o
     * aviso), e não como erro HTTP.
     */
    @POST
    @Path("/encapsular")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance encapsular(
            @FormParam("mensagem") String mensagem,
            @FormParam("transporte") @DefaultValue("TCP") String transporte,
            @FormParam("ipOrigem") String ipOrigem,
            @FormParam("ipDestino") String ipDestino,
            @FormParam("portaOrigem") String portaOrigem,
            @FormParam("portaDestino") String portaDestino) {
        ResultadoEncapsulamento r = encapsulamentoService.encapsular(
                mensagem, transporte, ipOrigem, ipDestino, portaOrigem, portaDestino);
        telemetriaLogger.logEvent(r.ok() ? "info" : "warn", "simuladores", "encapsular",
                r.ok() ? "ok" : "error",
                Map.of("transporte", r.transporte(), "totalBytes", r.totalBytes(),
                        "camadas", r.camadas().size()));

        // A pilha aninhada é desenhada de fora para dentro (Enlace → Aplicação).
        List<ResultadoEncapsulamento.Camada> pilha = new ArrayList<>(r.camadas());
        Collections.reverse(pilha);

        return resultadoFragmento
                .data("resultado", r)
                .data("pilha", pilha)
                .data("bytesAplicacao", bytesDaAplicacao(r));
    }

    /** Bytes úteis entregues pela camada de aplicação (nível 7 do OSI). */
    private static int bytesDaAplicacao(ResultadoEncapsulamento r) {
        return r.camadas().stream()
                .filter(c -> c.nivel() == 7)
                .mapToInt(c -> c.payloadBytes())
                .findFirst()
                .orElse(0);
    }
}
