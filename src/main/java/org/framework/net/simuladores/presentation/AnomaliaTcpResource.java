package org.framework.net.simuladores.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.simuladores.application.AnomaliaTcpService;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * API do simulador de anomalias TCP (sub-aba "Anomalias" da página Tráfego).
 *
 * <p><b>Propósito de negócio:</b> entregar em JSON o cenário didático de SYN
 * flood ou de sequestro por previsão de sequência, para o front-end animar passo
 * a passo. GET simples, sem efeitos colaterais — computação pura no pacote
 * {@code simuladores}, como o handshake e o encapsulamento.</p>
 *
 * <p><b>Invariantes do domínio:</b> resource próprio sob {@code /simuladores/api}
 * com um único {@code @Path} de sub-recurso ({@code /anomalia-tcp}); convive com
 * o Handshake e o Encapsulamento porque cada método tem caminho completo distinto
 * — o casamento de rota do JAX-RS é pelo caminho inteiro, não pelo prefixo.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> tipo desconhecido não gera erro — o
 * serviço cai no cenário padrão (SYN flood), então a sub-aba nunca fica vazia.</p>
 */
@Path("/simuladores/api")
public class AnomaliaTcpResource {

    @Inject
    AnomaliaTcpService anomaliaTcpService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @GET
    @Path("/anomalia-tcp")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultadoAnomaliaTcp anomaliaTcp(
            @QueryParam("tipo") @DefaultValue("syn-flood") String tipo) {
        String solicitado = tipo == null ? "" : tipo.trim();
        ResultadoAnomaliaTcp r = anomaliaTcpService.simular(tipo);
        // Fallback para syn-flood é aceitável, mas não silencioso: registra o tipo SOLICITADO
        // e sinaliza quando não foi reconhecido, para o dashboard não mascarar um parâmetro errado.
        boolean reconhecido = solicitado.isEmpty() || solicitado.equalsIgnoreCase(r.tipo());
        telemetriaLogger.logEvent(reconhecido ? "info" : "warn", "simuladores", "anomalia_tcp",
                reconhecido ? "ok" : "tipo_desconhecido",
                Map.of("tipoSolicitado", solicitado.isEmpty() ? "(padrão)" : solicitado,
                        "tipoServido", r.tipo(), "passos", r.passos().size()));
        return r;
    }
}
