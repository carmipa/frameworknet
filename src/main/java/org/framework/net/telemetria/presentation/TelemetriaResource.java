package org.framework.net.telemetria.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.telemetria.TelemetriaDashboard;
import org.framework.net.telemetria.TelemetriaDashboardService;
import org.framework.net.telemetria.TelemetriaResumo;
import org.framework.net.telemetria.TelemetriaStore;
import org.framework.net.telemetria.application.DatasetPublicavelService;
import org.framework.net.telemetria.application.TelemetriaExportService;
import org.framework.net.telemetria.infrastructure.GitHubDatasetPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Path("/telemetria")
public class TelemetriaResource {

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaDashboardService dashboardService;

    @Inject
    DatasetPublicavelService datasetService;

    @Inject
    TelemetriaExportService exportService;

    @Inject
    GitHubDatasetPublisher datasetPublisher;

    @Inject
    @io.quarkus.qute.Location("telemetria/dashboard.html")
    Template dashboardTemplate;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return dashboardTemplate
                .data("activeMainMenu", "telemetria")
                .data("datasetConfigurado", datasetPublisher.configurado())
                .data("datasetDestino", datasetPublisher.destinoParaExibicao());
    }

    @GET
    @Path("/api/resumo")
    @Produces(MediaType.APPLICATION_JSON)
    public TelemetriaResumo resumo(@QueryParam("limit") @DefaultValue("100") int limit) {
        return store.gerarResumo(limit);
    }

    @GET
    @Path("/api/dashboard")
    @Produces(MediaType.APPLICATION_JSON)
    public TelemetriaDashboard dadosDashboard(
            @QueryParam("janela") @DefaultValue("60") int janela,
            @QueryParam("console") @DefaultValue("250") int console) {
        return dashboardService.montar(console, janela);
    }

    @GET
    @Path("/api/console")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> console(@QueryParam("limit") @DefaultValue("200") int limit) {
        return Map.of("linhas", dashboardService.montarConsole(limit));
    }

    @POST
    @Path("/api/console/limpar")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> limparConsole() {
        dashboardService.limparConsole();
        return Map.of("status", "ok");
    }

    /**
     * Propósito de negócio: entrega um retrato OTLP atualizado para compartilhamento e análise externa.
     * Invariantes do domínio: materializa todos os eventos da janela corrente imediatamente antes da leitura.
     * Comportamento em caso de falha: propaga {@link IOException} para a camada HTTP, sem entregar um arquivo
     * antigo como se fosse a exportação atual.
     */
    @GET
    @Path("/api/exportar")
    public Response exportar(@QueryParam("formato") String formatoPedido) throws IOException {
        var formato = TelemetriaExportService.Formato.de(formatoPedido);

        if (formato == TelemetriaExportService.Formato.JSON) {
            // JSON continua saindo do arquivo canonico OTLP, que e o formato
            // compartilhavel e a base do dataset publico.
            var arquivo = store.arquivoCompartilhado();
            store.flushOuFalhar();
            byte[] conteudo = Files.readAllBytes(arquivo);
            return Response.ok(conteudo)
                    .type(MediaType.APPLICATION_JSON)
                    .header("Content-Disposition",
                            "attachment; filename=\"telemetria_compartilhada.json\"")
                    .build();
        }

        return Response.ok(exportService.gerar(formato))
                .type(formato.tipoMime())
                .header("Content-Disposition",
                        "attachment; filename=\"" + exportService.nomeArquivo(formato) + "\"")
                .build();
    }

    /**
     * Sincroniza o dataset sanitizado com o repositorio publico.
     *
     * <p><b>Proposito de negocio:</b> publicar sem que nada fique guardado aqui —
     * o snapshot e gerado em memoria, enviado ao GitHub e descartado. O repositorio
     * e publico, entao ele passa a ser o unico lugar onde o dataset existe.</p>
     *
     * <p><b>Invariantes do dominio:</b> restrito ao dono (o filtro de seguranca
     * cobra o papel). A geracao falha fechada se a auditoria encontrar
     * identificador residual, e o envio nao sobrescreve snapshot ja publicado —
     * a API do GitHub so sobrescreve com o sha da versao atual, que este caminho
     * jamais envia.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> devolve 409 quando o snapshot do
     * dia ja existe, 422 quando a auditoria reprova o conteudo e 502 quando o
     * GitHub recusa — sempre com motivo em texto, nunca com detalhe de credencial.</p>
     */
    @POST
    @Path("/api/dataset/sincronizar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sincronizarDataset() {
        DatasetPublicavelService.Pacote pacote;
        try {
            pacote = datasetService.gerar();
        } catch (IllegalStateException auditoriaReprovou) {
            return Response.status(422).entity(Map.of(
                    "ok", false, "erro", auditoriaReprovou.getMessage())).build();
        }

        var resultado = datasetPublisher.publicar(pacote.data(), pacote.arquivos());
        if (resultado.ok()) {
            return Response.ok(Map.of(
                    "ok", true,
                    "snapshot", resultado.data(),
                    "registros", pacote.registros(),
                    "visitantes", pacote.visitantes(),
                    "arquivos", resultado.arquivos(),
                    "url", resultado.url(),
                    "mensagem", resultado.mensagem())).build();
        }
        int status = resultado.data().isEmpty() ? 502 : 409;
        return Response.status(status).entity(Map.of(
                "ok", false,
                "registros", pacote.registros(),
                "erro", resultado.mensagem())).build();
    }

    /**
     * O que ja esta publicado no repositorio publico.
     *
     * <p><b>Proposito de negocio:</b> permitir que a tela responda "foi
     * sincronizado?" consultando o repositorio, e nao o log da propria
     * aplicacao. Somente leitura e sobre repositorio publico, por isso nao e
     * acao de dono - leitor tambem pode ver o que ja foi publicado.</p>
     */
    @GET
    @Path("/api/dataset/estado")
    @Produces(MediaType.APPLICATION_JSON)
    public Response estadoDataset() {
        var estado = datasetPublisher.listarSnapshots();
        return Response.ok(Map.of(
                "consultado", estado.consultado(),
                "snapshots", estado.snapshots(),
                "destino", datasetPublisher.destinoParaExibicao(),
                "observacao", estado.observacao())).build();
    }

    @GET
    @Path("/api/pasta")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pasta() {
        return Response.ok(Map.of(
                "pasta", store.pastaLogs().toAbsolutePath().toString(),
                "arquivoCompartilhado", store.arquivoCompartilhado().toAbsolutePath().toString()
        )).build();
    }
}
