package org.framework.net.resolucaoProblemas.application.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class ExportZipService {

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public byte[] generatePacketTracerZipBuffer(NetworkScenarioResult scenario) {
        if (scenario == null) {
            throw new EntradaInvalidaException("Cenário vazio para exportação.");
        }
        if (scenario.getLanBlocks() == null || scenario.getLanBlocks().isEmpty()) {
            throw new EntradaInvalidaException("Não há localidades no cenário para exportação.");
        }

        String consolidatedScript = exportTxtService.generatePacketTracerScript(scenario);
        String montagemGuide = exportTxtService.generatePacketTracerMontagemGuide(scenario);
        Map<String, String> routerBlocks = exportTxtService.generateRouterLabBlocks(scenario);
        String topologyMermaid = scenario.getTopologyMermaid() == null ? "" : scenario.getTopologyMermaid();
        String routingSummary = exportTxtService.scenarioRoutingExportLines(scenario).stream()
                .map(line -> "  " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String validation = String.join(" | ", exportTxtService.scenarioValidationHintLines(scenario));
        String readme = exportTxtService.packetTracerHardwareNotePlainBlock()
                + "PRIMEIRO: abra GUIA_MONTAGEM_PACKET_TRACER.txt — explica todo o pacote "
                + "e como aplicar no Packet Tracer (o PT nao importa configuracao automatica).\n\n"
                + "INSTRUCOES DE USO DO LABORATORIO\n"
                + "===============================\n"
                + "1. Abra o Cisco Packet Tracer.\n"
                + "2. Leia GUIA_MONTAGEM_PACKET_TRACER.txt e monte a topologia física conforme "
                + "LAB_TOPOLOGY.mermaid.\n"
                + "3. Para cada roteador, abra o CLI e cole o conteúdo do arquivo "
                + "em configs_individuais/.\n"
                + "4. Roteamento deste cenario:\n"
                + routingSummary + "\n"
                + "5. Aguarde convergencia EIGRP/OSPF (alguns segundos no PT).\n"
                + "6. Em cada LAN, use pelo menos 2 PCs no switch para testar ping entre filiais.\n"
                + "7. Valide: " + validation + "\n";

        try (ByteArrayOutputStream memoryFile = new ByteArrayOutputStream();
             ZipOutputStream zf = new ZipOutputStream(memoryFile)) {
            writeEntry(zf, "GUIA_MONTAGEM_PACKET_TRACER.txt", montagemGuide);
            writeEntry(zf, "config_packet_tracer_consolidado.txt", consolidatedScript);
            java.util.Set<String> arquivos = new java.util.HashSet<>();
            for (Map.Entry<String, String> entry : routerBlocks.entrySet()) {
                String filename = ExportTxtService.nomeUnico(
                        exportTxtService.routerExportFilename(entry.getKey()), arquivos);
                writeEntry(zf, "configs_individuais/" + filename, entry.getValue() + "\n");
            }
            writeEntry(zf, "LAB_TOPOLOGY.mermaid", topologyMermaid);
            writeEntry(zf, "README_LAB.txt", readme);
            zf.finish();
            telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_export_zip",
                    Map.of("status", "ok", "locations", scenario.getTotalLocations()));
            return memoryFile.toByteArray();
        } catch (IOException ex) {
            throw new EntradaInvalidaException("Erro ao gerar pacote ZIP: " + ex.getMessage(), ex);
        }
    }

    private void writeEntry(ZipOutputStream zf, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zf.putNextEntry(entry);
        zf.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zf.closeEntry();
    }
}
