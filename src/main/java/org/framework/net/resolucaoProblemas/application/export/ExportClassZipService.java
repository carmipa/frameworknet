package org.framework.net.resolucaoProblemas.application.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.VlsmService;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class ExportClassZipService {

    @Inject
    VlsmService vlsmService;

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public byte[] generateClassRosterZipBuffer(List<ClassRosterRow> rosterRows, ResolucaoFormData formData) {
        if (rosterRows == null || rosterRows.isEmpty()) {
            throw new EntradaInvalidaException("Nenhum aluno na importação da turma.");
        }

        ClassLabSettings settings = ClassLabSettings.from(formData);
        List<String> indexLines = new ArrayList<>();
        indexLines.add("PACOTE DA TURMA — FRAMEWORK DE REDES");
        indexLines.add("=".repeat(60));
        indexLines.add("Total de alunos: " + rosterRows.size());
        indexLines.add("");
        indexLines.add("Pastas (um laboratorio por aluno):");

        try (ByteArrayOutputStream memoryFile = new ByteArrayOutputStream();
             ZipOutputStream zf = new ZipOutputStream(memoryFile)) {

            for (ClassRosterRow row : rosterRows) {
                NetworkScenarioResult scenario;
                try {
                    scenario = vlsmService.solveNetworkProblem(
                            row.getBaseNetwork(),
                            row.getLocations(),
                            settings.topologyType(),
                            settings.wanPrefix(),
                            settings.eigrpAs(),
                            settings.remoteAccess(),
                            settings.routingMode(),
                            settings.ospfProcess()
                    );
                } catch (EntradaInvalidaException ex) {
                    // Com 40 alunos, "Rede base invalida" sem dizer de quem é obriga a caçar a linha (CALC-39).
                    throw new EntradaInvalidaException("Aluno " + row.getStudentName() + " (rede "
                            + row.getBaseNetwork() + "): " + ex.getMessage(), ex);
                }
                String folder = row.getFolderSlug();
                String prefix = "por_aluno/" + folder + "/";

                writeEntry(zf, prefix + "GUIA_MONTAGEM_PACKET_TRACER.txt",
                        exportTxtService.generatePacketTracerMontagemGuide(scenario));
                writeEntry(zf, prefix + "config_packet_tracer_consolidado.txt",
                        exportTxtService.generatePacketTracerScript(scenario));
                writeEntry(zf, prefix + "documentacao_cenario_rede.txt",
                        exportTxtService.generateEntregaRelatorioTxt(scenario));
                writeEntry(zf, prefix + "LAB_TOPOLOGY.mermaid",
                        scenario.getTopologyMermaid() == null ? "" : scenario.getTopologyMermaid());

                java.util.Set<String> arquivos = new java.util.HashSet<>();
                for (Map.Entry<String, String> block : exportTxtService.generateRouterLabBlocks(scenario).entrySet()) {
                    String filename = ExportTxtService.nomeUnico(
                            exportTxtService.routerExportFilename(block.getKey()), arquivos);
                    writeEntry(zf, prefix + "configs_individuais/" + filename, block.getValue() + "\n");
                }

                indexLines.add("  " + folder + "/ — " + row.getStudentName() + " | " + row.getBaseNetwork()
                        + " | " + row.getLocations().size() + " localidade(s)");
            }

            indexLines.add("");
            indexLines.add("Como usar:");
            indexLines.add("  1) Abra a pasta do aluno.");
            indexLines.add("  2) Leia GUIA_MONTAGEM_PACKET_TRACER.txt.");
            indexLines.add("  3) Cole configs_individuais/R-*.txt no CLI de cada roteador.");
            indexLines.add("");
            indexLines.add("Parametros comuns desta turma:");
            indexLines.add("  Topologia WAN: " + settings.topologyType());
            indexLines.add("  Prefixo WAN: /" + settings.wanPrefix());
            indexLines.add("  Roteamento: " + settings.routingMode());
            indexLines.add("  AS EIGRP: " + settings.eigrpAs());
            indexLines.add("  OSPF process: " + settings.ospfProcess());
            indexLines.add("  Acesso remoto: " + settings.remoteAccess());

            writeEntry(zf, "LEIA-ME_TURMA.txt", String.join("\n", indexLines) + "\n");
            zf.finish();

            telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_export_class_zip",
                    Map.of("status", "ok", "students_count", rosterRows.size()));
            return memoryFile.toByteArray();
        } catch (IOException ex) {
            throw new EntradaInvalidaException("Erro ao gerar ZIP da turma: " + ex.getMessage(), ex);
        }
    }

    private void writeEntry(ZipOutputStream zf, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zf.putNextEntry(entry);
        zf.write(content.getBytes(StandardCharsets.UTF_8));
        zf.closeEntry();
    }

    private record ClassLabSettings(
            String topologyType,
            int wanPrefix,
            int eigrpAs,
            String remoteAccess,
            String routingMode,
            int ospfProcess) {

        static ClassLabSettings from(ResolucaoFormData formData) {
            String topology = formData.getTopologyType() == null || formData.getTopologyType().isBlank()
                    ? "extended_star" : formData.getTopologyType().strip();
            int wan = 30;
            if (formData.getWanPrefix() != null && !formData.getWanPrefix().isBlank()) {
                wan = org.framework.net.shared.NumeroAscii.parseIntEstrito(formData.getWanPrefix().strip());
            }
            int eigrp = 71;
            if (formData.getEigrpAs() != null && !formData.getEigrpAs().isBlank()) {
                eigrp = org.framework.net.shared.NumeroAscii.parseIntEstrito(formData.getEigrpAs().strip());
            }
            int ospf = 1;
            if (formData.getOspfProcess() != null && !formData.getOspfProcess().isBlank()) {
                ospf = org.framework.net.shared.NumeroAscii.parseIntEstrito(formData.getOspfProcess().strip());
            }
            String remote = formData.getRemoteAccess() == null || formData.getRemoteAccess().isBlank()
                    ? "ssh" : formData.getRemoteAccess().strip();
            String routing = formData.getRoutingMode() == null || formData.getRoutingMode().isBlank()
                    ? "auto" : formData.getRoutingMode().strip();
            return new ClassLabSettings(topology, wan, eigrp, remote, routing, ospf);
        }
    }
}
