package org.framework.net.analiseDidatica.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.analiseDidatica.infrastructure.historico.HistoricoStore;
import org.framework.net.analiseDidatica.support.PdfSimplesService;
import org.framework.net.telemetria.TelemetriaKeys;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporta, em JSON ou PDF, o histórico de análises de QUEM CLICOU no botão.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno/professor baixa a evidência do próprio cálculo para a aula.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o export enxerga exatamente o que a tela de histórico mostra — a
 * sessão do navegador ({@link HistoricoStore#listar()}). Revisão pós-implementação (lente de boa-fé):
 * depois do F07 a tela passou a ser por sessão e o export ficou em todas as sessões, então o PDF saía
 * com a última consulta de QUALQUER visitante (IP e geolocalização de terceiro) sem o admin perceber.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> sessão sem histórico no PDF redireciona (303) para "/"; o
 * JSON devolve a lista vazia. Erro ao gerar o PDF propaga {@link IOException} (500 pelo mapper padrão).
 */
@Path("/export")
public class AnaliseExportResource {

    @Inject
    HistoricoStore historicoStore;

    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> exportarJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generated_at", Instant.now().toString());
        payload.put("history", historicoStore.listar());
        Object requestId = MDC.get(TelemetriaKeys.REQUEST_ID);
        payload.put("last_request_id", requestId == null ? "-" : String.valueOf(requestId));
        return payload;
    }

    @GET
    @Path("/pdf")
    public Response exportarPdf() throws IOException {
        List<Map<String, Object>> history = historicoStore.listar();
        if (history.isEmpty()) {
            return Response.seeOther(URI.create("/")).build();
        }
        Map<String, Object> last = history.getFirst();
        String entrada = String.valueOf(last.getOrDefault("ipv6_entrada", ""));
        if (entrada.isBlank()) {
            entrada = String.valueOf(last.getOrDefault("ip_entrada", "-"));
        }
        String texto = String.join("\n",
                "Relatorio Didatico de Rede (GRC)",
                "Gerado em: " + Instant.now(),
                "Consulta ID: " + last.getOrDefault("id", "-"),
                "Modo: " + last.getOrDefault("modo", "-"),
                "Entrada: " + entrada,
                "CIDR entrada: " + last.getOrDefault("cidr_entrada", "-"),
                "Mascara: " + last.getOrDefault("mask", "-"),
                "CIDR final: /" + last.getOrDefault("cidr", "-"),
                "Rede: " + last.getOrDefault("rede", "-"),
                "Broadcast: " + last.getOrDefault("broadcast", "-"),
                "Tema/Risco: " + last.getOrDefault("tema", "-"),
                "",
                "Objetivo: evidencia de calculo e contexto GRC para aula/auditoria."
        );
        byte[] pdf = PdfSimplesService.gerarPdfSimples(texto);
        return Response.ok(pdf)
                .type("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"relatorio_rede_grc.pdf\"")
                .build();
    }
}
