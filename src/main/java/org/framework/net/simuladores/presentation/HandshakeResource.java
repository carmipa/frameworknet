package org.framework.net.simuladores.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.simuladores.application.HandshakeService;
import org.framework.net.simuladores.domain.ResultadoHandshake;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * API do simulador de handshake TCP (sub-aba "Handshake" da página Tráfego).
 * GET simples (sem efeitos colaterais), computação pura no pacote {@code simuladores}.
 */
@Path("/simuladores/api")
public class HandshakeResource {

    @Inject
    HandshakeService handshakeService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @GET
    @Path("/handshake")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultadoHandshake handshake(
            @QueryParam("dados") @DefaultValue("false") boolean dados,
            @QueryParam("encerramento") @DefaultValue("false") boolean encerramento) {
        ResultadoHandshake r = handshakeService.simular(dados, encerramento);
        telemetriaLogger.logEvent("info", "simuladores", "handshake", "ok",
                Map.of("passos", r.passos().size(), "dados", dados, "encerramento", encerramento));
        return r;
    }
}
