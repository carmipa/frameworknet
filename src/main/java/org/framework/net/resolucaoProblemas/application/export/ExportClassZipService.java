package org.framework.net.resolucaoProblemas.application.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.VlsmService;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException;
import org.framework.net.shared.InputLimits;
import org.framework.net.telemetria.TelemetriaLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
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

    /** Bytes fixos de cada entrada no ZIP: cabeçalho local (30), descritor (16) e diretório central (46), com folga. */
    static final int CABECALHO_POR_ENTRADA = 100;

    /**
     * Pacotes montados ao mesmo tempo, no processo inteiro. Um só: com duas montagens a cota de CPU do contêiner
     * (cpus: 2.0) inteira ficava presa nelas e todo o resto do site esperava.
     */
    static final int MAX_PACOTES_SIMULTANEOS = 1;

    /** Quanto o pedido espera pela vaga antes de desistir (a montagem típica leva menos de 2 s). */
    static final long ESPERA_VAGA_SEGUNDOS = 5;

    /**
     * Vagas de montagem. O teto por pedido não limita a soma: 60 pedidos no teto chegando juntos (30 no fim de um minuto,
     * 30 no começo do seguinte) seguravam centenas de MB contra um heap de ~499 MB. Sem vaga na espera, recusa.
     */
    final Semaphore vagas = new Semaphore(MAX_PACOTES_SIMULTANEOS);

    /**
     * Pedidos esperando a vaga ao mesmo tempo. Cada um segura uma thread de trabalho por até
     * {@link #ESPERA_VAGA_SEGUNDOS} s: sem limite, uma rajada de pedidos prendia o pool de threads e o resto do site
     * esperava junto. Acima disso, a recusa é na hora.
     */
    static final int MAX_NA_FILA = 2;

    /** Quantos pedidos estão esperando a vaga agora. */
    final java.util.concurrent.atomic.AtomicInteger naFila = new java.util.concurrent.atomic.AtomicInteger();

    private static final String MENSAGEM_OCUPADO =
            "Outro pacote da turma está sendo montado agora. Tente de novo em alguns segundos.";

    /**
     * Monta o "Pacote da turma": um laboratório completo por aluno, num ZIP só.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o professor cola a planilha da turma e baixa, de uma vez, o guia de montagem,
     * as configurações e a documentação de cada aluno.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o ZIP é montado na memória; o que limita cada pedido é o orçamento de
     * {@link InputLimits#MAX_BYTES_PACOTE_TURMA}, que soma o conteúdo de cada arquivo ANTES da compressão e o nome de
     * cada entrada (que o ZIP grava duas vezes, sem compressão) com o cabeçalho — então o ZIP final nunca passa dele.
     * Sem teto, 34 KB de pedido viravam 70 MB de heap e 30 s de CPU. O orçamento é conferido antes de gravar cada
     * arquivo: o pedido grande para no aluno em que estourou, sem calcular o resto. No máximo
     * {@link #MAX_PACOTES_SIMULTANEOS} pacote é montado por vez, no processo inteiro; o pedido espera a vaga até
     * {@link #ESPERA_VAGA_SEGUNDOS} s, com no máximo {@link #MAX_NA_FILA} esperando — o seguinte é recusado na hora.
     * Os eventos {@code info} que cada aluno produziria (cenário resolvido, roteiro gerado) não são gravados
     * ({@code TelemetriaLogger.semEventosInfo}): o pacote grava um evento de resumo, com o total de alunos; falha
     * ({@code warn}/{@code error}) continua sendo gravada.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link EntradaInvalidaException} com mensagem para quem usa
     * quando a turma está vazia, quando a rede de um aluno é inválida (dizendo qual aluno) e quando o pacote passa do
     * teto (dizendo em qual aluno parou e como dividir); falha de E/S do ZIP também vira essa exceção. Sem vaga na
     * espera (ou com a fila cheia), lança {@link PacoteTurmaOcupadoException} (subtipo: "tente de novo", sem culpar a
     * colagem). A vaga e o lugar na fila são sempre devolvidos.</p>
     */
    public byte[] generateClassRosterZipBuffer(List<ClassRosterRow> rosterRows, ResolucaoFormData formData) {
        if (rosterRows == null || rosterRows.isEmpty()) {
            throw new EntradaInvalidaException("Nenhum aluno na importação da turma.");
        }
        boolean obteve = vagas.tryAcquire();
        if (!obteve) {
            if (naFila.incrementAndGet() > MAX_NA_FILA) {
                naFila.decrementAndGet();
                throw new PacoteTurmaOcupadoException(MENSAGEM_OCUPADO);
            }
            try {
                obteve = vagas.tryAcquire(ESPERA_VAGA_SEGUNDOS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
                obteve = false;
            } finally {
                naFila.decrementAndGet();
            }
        }
        if (!obteve) {
            throw new PacoteTurmaOcupadoException(MENSAGEM_OCUPADO);
        }
        try {
            byte[] zip = TelemetriaLogger.semEventosInfo(() -> montarPacote(rosterRows, formData));
            telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_export_class_zip",
                    Map.of("status", "ok", "students_count", rosterRows.size()));
            return zip;
        } finally {
            vagas.release();
        }
    }

    /**
     * Monta o ZIP do pacote da turma, aluno por aluno, dentro do orçamento.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> é o trabalho de verdade do "Pacote da turma": um laboratório completo por aluno
     * e o LEIA-ME da turma.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> cada arquivo passa pelo orçamento antes de ser gravado; a rede inválida de um
     * aluno interrompe o pacote dizendo de quem é. Quem chama segura a vaga e grava o evento de resumo.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link EntradaInvalidaException} (rede de aluno inválida,
     * orçamento estourado ou falha de E/S do ZIP), sem ZIP parcial.</p>
     */
    private byte[] montarPacote(List<ClassRosterRow> rosterRows, ResolucaoFormData formData) {
        OrcamentoPacote orcamento = new OrcamentoPacote(InputLimits.MAX_BYTES_PACOTE_TURMA, rosterRows.size());
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
                orcamento.proximoAluno(row.getStudentName());
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

                writeEntry(zf, orcamento, prefix + "GUIA_MONTAGEM_PACKET_TRACER.txt",
                        exportTxtService.generatePacketTracerMontagemGuide(scenario));
                writeEntry(zf, orcamento, prefix + "config_packet_tracer_consolidado.txt",
                        exportTxtService.generatePacketTracerScript(scenario));
                writeEntry(zf, orcamento, prefix + "documentacao_cenario_rede.txt",
                        exportTxtService.generateEntregaRelatorioTxt(scenario));
                writeEntry(zf, orcamento, prefix + "LAB_TOPOLOGY.mermaid",
                        scenario.getTopologyMermaid() == null ? "" : scenario.getTopologyMermaid());

                java.util.Set<String> arquivos = new java.util.HashSet<>();
                for (Map.Entry<String, String> block : exportTxtService.generateRouterLabBlocks(scenario).entrySet()) {
                    String filename = ExportTxtService.nomeUnico(
                            exportTxtService.routerExportFilename(block.getKey()), arquivos);
                    writeEntry(zf, orcamento, prefix + "configs_individuais/" + filename, block.getValue() + "\n");
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

            writeEntry(zf, orcamento, "LEIA-ME_TURMA.txt", String.join("\n", indexLines) + "\n");
            zf.finish();
            return memoryFile.toByteArray();
        } catch (IOException ex) {
            throw new EntradaInvalidaException("Erro ao gerar ZIP da turma: " + ex.getMessage(), ex);
        }
    }

    private void writeEntry(ZipOutputStream zf, OrcamentoPacote orcamento, String name, String content)
            throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        // O nome da entrada vai duas vezes no ZIP (cabeçalho local e diretório central), sem compressão.
        orcamento.consumir(bytes.length + 2L * name.getBytes(StandardCharsets.UTF_8).length + CABECALHO_POR_ENTRADA);
        ZipEntry entry = new ZipEntry(name);
        zf.putNextEntry(entry);
        zf.write(bytes);
        zf.closeEntry();
    }

    /**
     * Quanto do teto do pacote já foi gasto, e em qual aluno.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> recusar a turma que não cabe dizendo ao professor onde parou, para ele
     * saber de que tamanho fazer os lotes.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a soma consumida nunca passa do teto: o arquivo que estouraria é recusado
     * antes de ser gravado.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #consumir} lança {@link EntradaInvalidaException}.</p>
     */
    private static final class OrcamentoPacote {
        /** O nome entra na mensagem só para o professor achar a linha; nome colado enorme não vira mensagem enorme. */
        private static final int MAX_NOME_NA_MENSAGEM = 60;

        private final long teto;
        private final int totalAlunos;
        private long usados;
        private int alunoAtual;
        private String nomeAtual = "";

        OrcamentoPacote(long teto, int totalAlunos) {
            this.teto = teto;
            this.totalAlunos = totalAlunos;
        }

        void proximoAluno(String nome) {
            alunoAtual++;
            String n = nome == null ? "" : nome.strip();
            if (n.length() > MAX_NOME_NA_MENSAGEM) {
                int corte = MAX_NOME_NA_MENSAGEM - 1;
                if (Character.isHighSurrogate(n.charAt(corte - 1))) {
                    corte--;   // não parte um emoji ao meio
                }
                n = n.substring(0, corte) + "…";
            }
            nomeAtual = n;
        }

        void consumir(long bytes) {
            if (usados + bytes > teto) {
                throw new EntradaInvalidaException("Pacote da turma grande demais: os arquivos passariam de "
                        + (teto / (1024 * 1024)) + " MB no aluno " + alunoAtual + " de " + totalAlunos
                        + (nomeAtual.isEmpty() ? "" : " (" + nomeAtual + ")")
                        + ". Divida a turma em lotes menores — e extraia cada ZIP numa pasta própria, porque os nomes "
                        + "das pastas se repetem entre lotes — ou use menos localidades por aluno.");
            }
            usados += bytes;
        }
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
