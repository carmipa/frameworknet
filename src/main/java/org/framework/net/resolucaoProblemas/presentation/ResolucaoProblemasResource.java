package org.framework.net.resolucaoProblemas.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.resolucaoProblemas.application.VlsmService;
import org.framework.net.resolucaoProblemas.application.export.ExportClassZipService;
import org.framework.net.resolucaoProblemas.application.export.ExportEngenhariaReversaService;
import org.framework.net.resolucaoProblemas.application.export.ExportTxtService;
import org.framework.net.resolucaoProblemas.application.export.ExportZipService;
import org.framework.net.resolucaoProblemas.application.importing.BulkClassImportService;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.parsing.CenarioExemploReversa;
import org.framework.net.resolucaoProblemas.application.parsing.EngenhariaReversaService;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.resolucaoProblemas.exception.ResolucaoProblemasException;
import org.framework.net.shared.InputLimits;
import org.framework.net.shared.IpCidrInputNormalizer;
import org.framework.net.shared.UserInputSanitizer;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.resteasy.reactive.RestForm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Path("/resolucao-problemas")
public class ResolucaoProblemasResource {

    private static final int DEFAULT_EIGRP_AS = 71;
    private static final int DEFAULT_OSPF_PROCESS = 1;

    private static final Map<String, ExportAction> EXPORT_ACTIONS = Map.of(
            "export", new ExportAction("config_packet_tracer_consolidado.txt", "export"),
            "export_entrega", new ExportAction("documentacao_cenario_rede.txt", "export_entrega")
    );

    @Inject
    VlsmService vlsmService;

    @Inject
    EngenhariaReversaService engenhariaReversaService;

    @Inject
    ExportEngenhariaReversaService exportEngenhariaReversaService;

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    ExportZipService exportZipService;

    @Inject
    ExportClassZipService exportClassZipService;

    @Inject
    BulkClassImportService bulkClassImportService;

    @Inject
    VlsmNormalizationService normalizationService;

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @io.quarkus.qute.Location("resolucaoProblemas/resolucao_problemas.html")
    Template page;

    /**
     * Abre a página no modo pedido pela URL.
     *
     * <p><b>Propósito de negócio:</b> as duas abas são o mesmo problema em
     * sentidos opostos — "Projetar" vai do requisito ao plano, "Engenharia
     * reversa" vai da configuração pronta de volta ao plano. Cada uma tem link
     * próprio para poder ser compartilhada e testada.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> valor desconhecido em
     * {@code aba} cai na aba "Projetar", que é o comportamento histórico da
     * rota — nunca em erro.</p>
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@QueryParam("demo") String demo, @QueryParam("aba") String aba) {
        if ("reversa".equalsIgnoreCase(aba)) {
            if ("bgp".equalsIgnoreCase(demo)) {
                String exemplo = CenarioExemploReversa.BGP_TRES_AS;
                return renderReversa(engenhariaReversaService.interpretar(exemplo), exemplo);
            }
            return renderReversa(null, "");
        }
        return getProjetar(demo);
    }

    private TemplateInstance getProjetar(String demo) {
        ResolucaoFormData formData;
        List<LocationInput> locations;
        if ("fiap".equals(demo)) {
            formData = formFromDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
            locations = copyLocations(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO.locations());
        } else if ("gs".equals(demo) || "mazola".equals(demo)) {
            formData = formFromDemo(VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO);
            locations = copyLocations(VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO.locations());
        } else if ("8".equals(demo)) {
            formData = formFromDemo(VlsmNormalizationService.EIGHT_ROUTERS_DEMO);
            locations = copyLocations(VlsmNormalizationService.EIGHT_ROUTERS_DEMO.locations());
        } else if ("1".equals(demo)) {
            formData = defaultFormData();
            locations = copyLocations(VlsmNormalizationService.DEFAULT_LOCATIONS);
        } else {
            formData = blankFormData();
            locations = List.of(new LocationInput("", ""));
        }
        return renderPage(null, formData, locations, Set.of(), null, "{}", "");
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, "application/zip"})
    public Response post(
            @RestForm("action_type") String actionType,
            @RestForm("config_paste") String configPaste,
            @RestForm("class_roster_paste") String classRosterPaste,
            @RestForm("base_network") String baseNetworkRaw,
            @RestForm("base_network_ip") String baseNetworkIp,
            @RestForm("base_network_cidr") String baseNetworkCidr,
            @RestForm("topology_type") String topologyType,
            @RestForm("wan_prefix") String wanPrefix,
            @RestForm("eigrp_as") String eigrpAs,
            @RestForm("ospf_process") String ospfProcess,
            @RestForm("remote_access") String remoteAccess,
            @RestForm("routing_mode") String routingMode,
            @RestForm("loc_name") List<String> locNames,
            @RestForm("loc_hosts") List<String> locHosts) {

        String action = actionType == null ? "calculate" : actionType.strip().toLowerCase(Locale.ROOT);

        if (action.startsWith("reverse")) {
            return postEngenhariaReversa(action, configPaste);
        }

        String rosterPaste = classRosterPaste == null ? "" : classRosterPaste;
        ResolucaoFormData formData = buildFormData(
                baseNetworkRaw, baseNetworkIp, baseNetworkCidr, topologyType,
                wanPrefix, eigrpAs, ospfProcess, remoteAccess, routingMode);

        if ("export_class_zip".equals(action)) {
            return handleClassZipExport(formData, rosterPaste);
        }

        LocationCollectionResult locationResult = collectLocations(locNames, locHosts);
        if (locationResult.validationError() != null) {
            Set<String> invalid = new HashSet<>(locationResult.invalidFields());
            applyErrorMapping(locationResult.validationError(), invalid);
            return Response.ok(renderPage(
                    locationResult.validationError(), formData, locationResult.locations(), invalid, null, "{}", rosterPaste
            ).render()).type(MediaType.TEXT_HTML).build();
        }

        Set<String> invalidFields = new HashSet<>(locationResult.invalidFields());
        invalidFields.addAll(markInvalidFields(formData, locationResult.locations()));
        List<LocationInput> formLocations = new ArrayList<>(locationResult.locations());

        String erro = null;
        NetworkScenarioResult scenario = null;
        if (!invalidFields.isEmpty()) {
            erro = buildValidationError(invalidFields);
            if (formLocations.isEmpty()) {
                formLocations = new ArrayList<>(List.of(new LocationInput("", "")));
            }
            String topologyJson = serializeTopologyDetails(null);
            TemplateInstance instance = renderPage(
                    erro, formData, formLocations, invalidFields, null, topologyJson, rosterPaste);
            return Response.ok(instance.render()).type(MediaType.TEXT_HTML).build();
        }

        try {
            int[] routingIds = resolveRoutingIds(formData);
            int wanPrefixI = parseWanPrefix(formData.getWanPrefix());
            scenario = vlsmService.solveNetworkProblem(
                    formData.getBaseNetwork(),
                    locationResult.locations(),
                    formData.getTopologyType(),
                    wanPrefixI,
                    routingIds[0],
                    formData.getRemoteAccess(),
                    formData.getRoutingMode(),
                    routingIds[1]
            );
            if (EXPORT_ACTIONS.containsKey(action)) {
                ExportAction cfg = EXPORT_ACTIONS.get(action);
                String content = "export".equals(cfg.type())
                        ? exportTxtService.generatePacketTracerScript(scenario)
                        : exportTxtService.generateEntregaRelatorioTxt(scenario);
                telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_resolution_export",
                        Map.of("export_type", cfg.type(), "status", "ok"));
                return Response.ok(content)
                        .type("text/plain; charset=utf-8")
                        .header("Content-Disposition", "attachment; filename=\"" + cfg.filename() + "\"")
                        .build();
            }
            if ("export_zip".equals(action)) {
                byte[] zip = exportZipService.generatePacketTracerZipBuffer(scenario);
                telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_resolution_export",
                        Map.of("export_type", "zip", "status", "ok"));
                return Response.ok(zip)
                        .type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"laboratorio_packet_tracer.zip\"")
                        .build();
            }
            telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_solve",
                    Map.of("status", "ok", "topology", formData.getTopologyType(),
                            "locations", locationResult.locations().size()));
        } catch (EntradaInvalidaException ex) {
            erro = ex.getMessage();
            invalidFields.addAll(markInvalidFields(formData, locationResult.locations()));
            applyErrorMapping(ex.getMessage(), invalidFields);
            if (isExportAction(action)) {
                erro = "Não foi possível exportar: " + erro;
            }
            telemetriaLogger.logEvent("warn", "resolucaoProblemas", "problem_solve",
                    Map.of("status", "invalid_input", "erro", ex.getMessage()));
        } catch (ResolucaoProblemasException ex) {
            erro = ex.getMessage();
            telemetriaLogger.logEvent("warn", "resolucaoProblemas", "problem_solve",
                    Map.of("status", "error", "erro", ex.getMessage()));
        } catch (Exception ex) {
            erro = "Erro interno ao processar a resolução de problemas. Tente novamente.";
            telemetriaLogger.logException("resolucaoProblemas", "problem_solve", null, ex);
        }

        String topologyJson = serializeTopologyDetails(scenario);
        TemplateInstance instance = renderPage(
                erro, formData, formLocations.isEmpty() ? locationResult.locations() : formLocations,
                invalidFields, scenario, topologyJson, rosterPaste);
        return Response.ok(instance.render()).type(MediaType.TEXT_HTML).build();
    }

    private Response handleClassZipExport(ResolucaoFormData formData, String rosterPaste) {
        Set<String> invalidFields = new HashSet<>(markExportSettings(formData));
        if (!invalidFields.isEmpty()) {
            String erro = buildValidationError(invalidFields);
            TemplateInstance instance = renderPage(
                    erro, formData, List.of(new LocationInput("", "")), invalidFields, null, "{}", rosterPaste);
            return Response.ok(instance.render()).type(MediaType.TEXT_HTML).build();
        }

        String erro = null;
        invalidFields.clear();
        try {
            List<ClassRosterRow> rows = bulkClassImportService.parseClassRosterPaste(rosterPaste);
            byte[] zip = exportClassZipService.generateClassRosterZipBuffer(rows, formData);
            return Response.ok(zip)
                    .type("application/zip")
                    .header("Content-Disposition", "attachment; filename=\"pacote_turma_packet_tracer.zip\"")
                    .build();
        } catch (EntradaInvalidaException ex) {
            erro = ex.getMessage();
            invalidFields.add("class_roster_paste");
            telemetriaLogger.logEvent("warn", "resolucaoProblemas", "problem_export_class_zip",
                    Map.of("status", "invalid_input", "erro", ex.getMessage()));
        } catch (Exception ex) {
            erro = "Erro ao gerar ZIP da turma. Verifique a colagem e os parâmetros WAN/roteamento.";
            invalidFields.add("class_roster_paste");
            telemetriaLogger.logException("resolucaoProblemas", "problem_export_class_zip", null, ex);
        }
        TemplateInstance instance = renderPage(
                erro, formData, List.of(new LocationInput("", "")), invalidFields, null, "{}", rosterPaste);
        return Response.ok(instance.render()).type(MediaType.TEXT_HTML).build();
    }

    private TemplateInstance renderPage(
            String erro,
            ResolucaoFormData formData,
            List<LocationInput> locations,
            Set<String> invalidFields,
            NetworkScenarioResult scenario,
            String topologyDetailsJson,
            String classRosterPaste) {
        return page
                .data("activeMainMenu", "resolucao")
                .data("erro", erro)
                .data("invalidFields", invalidFields)
                .data("formData", formData)
                .data("locations", locations)
                .data("scenario", scenario)
                .data("topologyDetailsJson", topologyDetailsJson)
                .data("classRosterPaste", classRosterPaste == null ? "" : classRosterPaste)
                .data("abaAtiva", "projetar")
                .data("cenario", null)
                .data("configPaste", "");
    }

    /**
     * Trata as ações da aba "Engenharia reversa".
     *
     * <p><b>Propósito de negócio:</b> "Executar" interpreta o texto colado;
     * "Limpar" esvazia a área; as duas exportações levam o resultado para fora.
     * A impressão NÃO passa por aqui — é a janela do navegador, acionada no
     * cliente, e nunca deve ser confundida com o botão que faz o trabalho.</p>
     *
     * <p><b>Invariantes do domínio:</b> a interpretação é sempre refeita a partir
     * do texto que veio no formulário, inclusive nas exportações. Guardar o
     * resultado entre requisições criaria uma segunda fonte de verdade que
     * poderia divergir do que está na tela.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> exportação sem nada interpretado
     * lança {@code ExportacaoException}, traduzida pelo mapper do módulo; erro
     * inesperado vira log de exceção e devolve a aba com o texto preservado, sem
     * derrubar a página.</p>
     */
    private Response postEngenhariaReversa(String action, String configPaste) {
        String texto = configPaste == null ? "" : configPaste;

        if ("reverse_limpar".equals(action)) {
            telemetriaLogger.logEvent("info", "resolucaoProblemas", "engenharia_reversa_limpar",
                    Map.of("status", "ok"));
            return Response.ok(renderReversa(null, "").render()).type(MediaType.TEXT_HTML).build();
        }

        CenarioReconstruido cenario;
        try {
            cenario = engenhariaReversaService.interpretar(texto);
        } catch (EntradaInvalidaException ex) {
            // Texto grande demais volta para a tela com o motivo, sem perder o que foi colado.
            return Response.ok(renderReversa(null, texto).data("erro", ex.getMessage()).render())
                    .type(MediaType.TEXT_HTML).build();
        }

        if ("reverse_export_scripts".equals(action)) {
            return arquivoTexto(exportEngenhariaReversaService.gerarScriptsCorrigidos(cenario),
                    "scripts_corrigidos.txt");
        }
        if ("reverse_export_relatorio".equals(action)) {
            return arquivoTexto(exportEngenhariaReversaService.gerarRelatorio(cenario),
                    "relatorio_engenharia_reversa.txt");
        }
        return Response.ok(renderReversa(cenario, texto).render()).type(MediaType.TEXT_HTML).build();
    }

    private Response arquivoTexto(String conteudo, String nomeArquivo) {
        return Response.ok(conteudo)
                .type("text/plain; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"")
                .build();
    }

    /**
     * Renderiza a aba "Engenharia reversa".
     *
     * <p><b>Invariantes do domínio:</b> o texto colado volta para a tela tal como
     * veio — a ferramenta nunca sobrescreve a entrada do usuário; o script
     * corrigido é material novo, exibido ao lado. Os dados da aba "Projetar" vão
     * preenchidos com o formulário em branco porque o template é o mesmo e o
     * bloco de scripts do rodapé referencia {@code topologyDetailsJson}.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@code cenario} nulo significa
     * "ainda não executou" e a tela mostra só o formulário — não é erro.</p>
     */
    private TemplateInstance renderReversa(CenarioReconstruido cenario, String configPaste) {
        return page
                .data("activeMainMenu", "resolucao")
                .data("erro", null)
                .data("invalidFields", Set.of())
                .data("formData", blankFormData())
                .data("locations", List.of(new LocationInput("", "")))
                .data("scenario", null)
                .data("topologyDetailsJson", "{}")
                .data("classRosterPaste", "")
                .data("abaAtiva", "reversa")
                .data("cenario", cenario)
                .data("configPaste", configPaste == null ? "" : configPaste);
    }

    private ResolucaoFormData formFromDemo(VlsmNormalizationService.DemoScenario demo) {
        String[] parts = demo.baseNetwork().split("/", 2);
        ResolucaoFormData form = new ResolucaoFormData();
        form.setBaseNetwork(demo.baseNetwork());
        form.setBaseNetworkIp(parts[0]);
        form.setBaseNetworkCidr(parts.length > 1 ? parts[1] : "");
        form.setTopologyType(demo.topologyType());
        form.setWanPrefix(demo.wanPrefix());
        form.setEigrpAs(demo.eigrpAs());
        form.setOspfProcess(demo.ospfProcess());
        form.setRemoteAccess(demo.remoteAccess());
        form.setRoutingMode(demo.routingMode());
        return form;
    }

    private ResolucaoFormData defaultFormData() {
        ResolucaoFormData form = new ResolucaoFormData();
        form.setBaseNetwork("172.21.0.0/16");
        form.setBaseNetworkIp("172.21.0.0");
        form.setBaseNetworkCidr("16");
        form.setTopologyType("extended_star");
        form.setWanPrefix("30");
        form.setEigrpAs("71");
        form.setOspfProcess("1");
        form.setRemoteAccess("telnet");
        form.setRoutingMode("auto");
        return form;
    }

    private ResolucaoFormData blankFormData() {
        ResolucaoFormData form = new ResolucaoFormData();
        form.setTopologyType("star");
        form.setRemoteAccess("ssh");
        form.setRoutingMode("auto");
        form.setWanPrefix("");
        return form;
    }

    private List<LocationInput> copyLocations(List<LocationInput> source) {
        List<LocationInput> copy = new ArrayList<>();
        for (LocationInput item : source) {
            copy.add(new LocationInput(item.getName(), item.getHosts()));
        }
        return copy;
    }

    private int[] resolveRoutingIds(ResolucaoFormData formData) {
        int eigrpAs = DEFAULT_EIGRP_AS;
        int ospfProcess = DEFAULT_OSPF_PROCESS;
        String eigrpTxt = formData.getEigrpAs() == null ? "" : formData.getEigrpAs().strip();
        String ospfTxt = formData.getOspfProcess() == null ? "" : formData.getOspfProcess().strip();
        if (!eigrpTxt.isEmpty()) {
            try {
                eigrpAs = org.framework.net.shared.NumeroAscii.parseIntEstrito(eigrpTxt);
            } catch (NumberFormatException ignored) {
                eigrpAs = DEFAULT_EIGRP_AS;
            }
        }
        if (!ospfTxt.isEmpty()) {
            try {
                ospfProcess = org.framework.net.shared.NumeroAscii.parseIntEstrito(ospfTxt);
            } catch (NumberFormatException ignored) {
                ospfProcess = DEFAULT_OSPF_PROCESS;
            }
        }
        return new int[]{eigrpAs, ospfProcess};
    }

    private ResolucaoFormData buildFormData(
            String baseNetworkRaw, String baseNetworkIp, String baseNetworkCidr,
            String topologyType, String wanPrefix, String eigrpAs, String ospfProcess,
            String remoteAccess, String routingMode) {

        String ip = baseNetworkIp == null ? "" : baseNetworkIp.strip();
        String cidr = baseNetworkCidr == null ? "" : baseNetworkCidr.strip();
        String raw = baseNetworkRaw == null ? "" : baseNetworkRaw.strip();
        String wan = wanPrefix == null || wanPrefix.isBlank() ? "30" : wanPrefix.strip();

        IpCidrInputNormalizer.SplitResult split = IpCidrInputNormalizer.splitIpAndCidr(ip, cidr);
        ip = split.ip();
        cidr = split.cidrRaw();

        if (!ip.isEmpty() && cidr.isEmpty()) {
            try {
                cidr = String.valueOf(ipv4Kernel.inferirCidrPorIp(ip).cidr());
            } catch (EntradaInvalidaException ignored) {
                // mantém vazio — validação posterior
            }
        }

        String baseNetworkValue;
        if (!ip.isEmpty() && !cidr.isEmpty()) {
            baseNetworkValue = ip + "/" + cidr;
        } else if (!raw.isEmpty()) {
            baseNetworkValue = raw;
        } else {
            baseNetworkValue = "";
        }

        ResolucaoFormData form = new ResolucaoFormData();
        form.setBaseNetwork(baseNetworkValue);
        form.setBaseNetworkIp(ip);
        form.setBaseNetworkCidr(cidr);
        form.setTopologyType(topologyType == null || topologyType.isBlank()
                ? "star" : topologyType.strip().toLowerCase(Locale.ROOT));
        form.setWanPrefix(wan);
        form.setEigrpAs(eigrpAs == null ? "" : eigrpAs.strip());
        form.setOspfProcess(ospfProcess == null ? "" : ospfProcess.strip());
        form.setRemoteAccess(normalizationService.normalizeRemoteAccess(remoteAccess));
        form.setRoutingMode(routingMode == null || routingMode.isBlank() ? "auto" : routingMode.strip().toLowerCase(Locale.ROOT));

        if (!form.getBaseNetwork().isEmpty()
                && (form.getBaseNetworkIp().isEmpty() || form.getBaseNetworkCidr().isEmpty())
                && form.getBaseNetwork().contains("/")) {
            String[] baseParts = form.getBaseNetwork().split("/", 2);
            if (form.getBaseNetworkIp().isEmpty()) {
                form.setBaseNetworkIp(baseParts[0].strip());
            }
            if (form.getBaseNetworkCidr().isEmpty()) {
                form.setBaseNetworkCidr(baseParts[1].strip());
            }
        }
        return form;
    }

    private LocationCollectionResult collectLocations(List<String> locNames, List<String> locHosts) {
        List<String> names = locNames == null ? List.of() : locNames;
        List<String> hosts = locHosts == null ? List.of() : locHosts;
        List<LocationInput> locations = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> invalidFields = new HashSet<>();
        int totalRows = Math.max(names.size(), hosts.size());
        int filledRows = 0;

        for (int index = 0; index < totalRows; index++) {
            int rowNumber = index + 1;
            String name = index < names.size() && names.get(index) != null
                    ? UserInputSanitizer.sanitizeLabel(names.get(index))
                    : "";
            String host = index < hosts.size() && hosts.get(index) != null ? hosts.get(index).strip() : "";
            if (name.isEmpty() && host.isEmpty()) {
                continue;
            }
            filledRows++;
            if (filledRows > InputLimits.MAX_LOCATION_ROWS) {
                return new LocationCollectionResult(
                        locations,
                        "Máximo de " + InputLimits.MAX_LOCATION_ROWS + " localidades por cenário.",
                        Set.of("loc_hosts")
                );
            }
            if (name.isEmpty()) {
                locations.add(new LocationInput("", host));
                return new LocationCollectionResult(
                        locations,
                        "Nome da localidade #" + rowNumber + " deve ser informado.",
                        Set.of("loc_name", "loc_hosts")
                );
            }
            if (host.isEmpty()) {
                locations.add(new LocationInput(name, ""));
                return new LocationCollectionResult(
                        locations,
                        "Hosts da localidade '" + name + "' devem ser informados.",
                        Set.of("loc_hosts")
                );
            }
            if (org.framework.net.shared.NumeroAscii.inteiro(host, org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT).isEmpty()) {
                locations.add(new LocationInput(name, host));
                return new LocationCollectionResult(
                        locations,
                        "Hosts de " + name + " deve ser um número inteiro positivo.",
                        Set.of("loc_hosts")
                );
            }
            int hostCount = org.framework.net.shared.NumeroAscii.parseIntEstrito(host);
            if (hostCount <= 0) {
                locations.add(new LocationInput(name, host));
                return new LocationCollectionResult(
                        locations,
                        "Hosts de " + name + " deve ser maior que zero.",
                        Set.of("loc_hosts")
                );
            }
            if (hostCount > InputLimits.MAX_HOSTS_PER_LOCATION) {
                locations.add(new LocationInput(name, host));
                return new LocationCollectionResult(
                        locations,
                        "Hosts de " + name + " excede o limite de " + InputLimits.MAX_HOSTS_PER_LOCATION + ".",
                        Set.of("loc_hosts")
                );
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (seenNames.contains(key)) {
                locations.add(new LocationInput(name, host));
                return new LocationCollectionResult(
                        locations,
                        "Localidade duplicada: '" + name + "' aparece mais de uma vez.",
                        Set.of("loc_name")
                );
            }
            seenNames.add(key);
            locations.add(new LocationInput(name, host));
        }
        return new LocationCollectionResult(locations, null, invalidFields);
    }

    private Set<String> markInvalidFields(ResolucaoFormData formData, List<LocationInput> locations) {
        Set<String> invalid = new HashSet<>(markFormSettings(formData));
        if (locations == null || locations.isEmpty()) {
            invalid.add("loc_hosts");
        } else {
            for (LocationInput location : locations) {
                String name = location.getName() == null ? "" : location.getName().strip();
                String host = location.getHosts() == null ? "" : location.getHosts().strip();
                if (name.isEmpty()) {
                    invalid.add("loc_name");
                }
                if (org.framework.net.shared.NumeroAscii.inteiro(host, org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT).orElse(0) <= 0) {
                    invalid.add("loc_hosts");
                }
            }
        }
        return invalid;
    }

    /** Valida WAN/topologia/roteamento para exportação em lote (sem rede base do formulário). */
    private Set<String> markExportSettings(ResolucaoFormData formData) {
        Set<String> invalid = new HashSet<>();
        Set<String> allowed = Set.of(
                "star", "extended_star", "mesh", "ring", "ring_redundant", "estrela", "estrela_estendida"
        );
        if (!allowed.contains(formData.getTopologyType())) {
            invalid.add("topology_type");
        }
        try {
            int wan = org.framework.net.shared.NumeroAscii.parseIntEstrito(formData.getWanPrefix());
            if (wan < 0 || wan > 30) {
                invalid.add("wan_prefix");
            }
        } catch (NumberFormatException ex) {
            invalid.add("wan_prefix");
        }
        String eigrpTxt = formData.getEigrpAs() == null ? "" : formData.getEigrpAs().strip();
        if (!eigrpTxt.isEmpty()) {
            try {
                int v = org.framework.net.shared.NumeroAscii.parseIntEstrito(eigrpTxt);
                if (v < 1 || v > 65535) {
                    invalid.add("eigrp_as");
                }
            } catch (NumberFormatException ex) {
                invalid.add("eigrp_as");
            }
        }
        String ospfTxt = formData.getOspfProcess() == null ? "" : formData.getOspfProcess().strip();
        if (!ospfTxt.isEmpty()) {
            try {
                int v = org.framework.net.shared.NumeroAscii.parseIntEstrito(ospfTxt);
                if (v < 1 || v > 65535) {
                    invalid.add("ospf_process");
                }
            } catch (NumberFormatException ex) {
                invalid.add("ospf_process");
            }
        }
        return invalid;
    }

    private Set<String> markFormSettings(ResolucaoFormData formData) {
        Set<String> invalid = new HashSet<>(markExportSettings(formData));
        if (formData.getBaseNetwork() == null || formData.getBaseNetwork().isBlank()) {
            invalid.add("base_network");
        } else {
            try {
                ipv4Kernel.parseNetwork(formData.getBaseNetwork().strip(), "Rede base");
            } catch (EntradaInvalidaException ex) {
                invalid.add("base_network");
            }
        }
        return invalid;
    }

    private String buildValidationError(Set<String> invalidFields) {
        List<String> missing = new ArrayList<>();
        if (invalidFields.contains("base_network")) {
            missing.add("rede base (IP)");
        }
        if (invalidFields.contains("loc_hosts")) {
            missing.add("localidades (hosts)");
        }
        if (invalidFields.contains("loc_name")) {
            missing.add("nome das localidades");
        }
        if (invalidFields.contains("eigrp_as")) {
            missing.add("AS EIGRP (1–65535, se informado)");
        }
        if (invalidFields.contains("ospf_process")) {
            missing.add("processo OSPF (1–65535, se informado)");
        }
        if (invalidFields.contains("topology_type")) {
            missing.add("topologia WAN");
        }
        if (invalidFields.contains("wan_prefix")) {
            missing.add("prefixo WAN");
        }
        return "Corrija os campos: " + String.join(", ", missing) + ".";
    }

    private int parseWanPrefix(String wanPrefix) {
        try {
            return org.framework.net.shared.NumeroAscii.parseIntEstrito(wanPrefix == null || wanPrefix.isBlank() ? "30" : wanPrefix.strip());
        } catch (NumberFormatException ex) {
            throw new EntradaInvalidaException("Prefixo WAN invalido. Informe um inteiro entre 0 e 30.");
        }
    }

    private String serializeTopologyDetails(NetworkScenarioResult scenario) {
        if (scenario == null || scenario.getTopologyDetails() == null) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(scenario.getTopologyDetails());
            return json.replace("<", "\\u003c");
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean isExportAction(String action) {
        return EXPORT_ACTIONS.containsKey(action) || "export_zip".equals(action) || "export_class_zip".equals(action);
    }

    private void applyErrorMapping(String erro, Set<String> invalidFields) {
        if (erro == null || erro.isBlank()) {
            return;
        }
        String lower = erro.toLowerCase(Locale.ROOT);
        if (lower.contains("rede base") || lower.contains("ipv4") || lower.contains("x.x.x.x")) {
            invalidFields.add("base_network");
        }
        if (lower.contains("duplicad") || (lower.contains("localidade") && lower.contains("nome"))) {
            invalidFields.add("loc_name");
        }
        if (lower.contains("hosts") || lower.contains("informe ao menos uma localidade")) {
            invalidFields.add("loc_hosts");
        }
        if (lower.contains("prefixo wan") || lower.contains("wan invalido")) {
            invalidFields.add("wan_prefix");
        }
        if (lower.contains("eigrp")) {
            invalidFields.add("eigrp_as");
        }
        if (lower.contains("ospf")) {
            invalidFields.add("ospf_process");
        }
        if (lower.contains("topologia")) {
            invalidFields.add("topology_type");
        }
    }

    private record ExportAction(String filename, String type) { }

    private record LocationCollectionResult(
            List<LocationInput> locations,
            String validationError,
            Set<String> invalidFields
    ) { }
}
