package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.export.ExportClassZipService;
import org.framework.net.resolucaoProblemas.application.importing.BulkClassImportService;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.shared.InputLimits;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Pacote da turma" com teto de tamanho (Lote 1 da auditoria, SEC-03). O ZIP é montado na memória: 200 alunos × 50
 * localidades em malha davam 70 MB de ZIP e 30 s de CPU por pedido, e 10 pedidos simultâneos derrubavam a JVM.
 * Gabaritos medidos com o código de produção antes da correção: 200 × 4 (qualquer topologia) = 11,5 MB de arquivos;
 * 200 × 50 em malha ≈ 790 MB.
 */
@QuarkusTest
class PacoteDaTurmaTetoTest {

    @Inject
    BulkClassImportService bulkClassImportService;

    @Inject
    ExportClassZipService exportClassZipService;

    @Inject
    org.framework.net.telemetria.TelemetriaStore telemetriaStore;

    private List<ClassRosterRow> turma(int alunos, int localidades) {
        StringBuilder colagem = new StringBuilder();
        for (int a = 0; a < alunos; a++) {
            colagem.append("Aluno").append(a).append('\t').append("10.").append(a % 250).append(".0.0/16");
            for (int l = 0; l < localidades; l++) {
                colagem.append('\t').append("10");
            }
            colagem.append('\n');
        }
        return bulkClassImportService.parseClassRosterPaste(colagem.toString());
    }

    private static ResolucaoFormData parametros(String topologia) {
        ResolucaoFormData form = new ResolucaoFormData();
        form.setTopologyType(topologia);
        form.setWanPrefix("30");
        form.setRemoteAccess("ssh");
        form.setRoutingMode("auto");
        return form;
    }

    private static long bytesDescomprimidos(byte[] zip) throws IOException {
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            byte[] buf = new byte[65_536];
            while (in.getNextEntry() != null) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                }
            }
        }
        return total;
    }

    /**
     * A2: a turma da auditoria (200 × 50 em malha) para no teto, rápido e com mensagem que diz onde parou — antes
     * gerava 70 MB em 30 s. O teto é conferido antes de gravar cada arquivo: o pedido não calcula o resto da turma.
     */
    @Test
    void turmaQueNaoCabeERecusadaRapidoDizendoOndeParou() {
        List<ClassRosterRow> enorme = turma(200, 50);
        // O prazo largo só guarda contra travamento; quem prova que a montagem parou cedo é o aluno em que parou
        // (oráculo determinístico — relógio num build de CPU dividida daria falso vermelho).
        EntradaInvalidaException ex = assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> assertThrows(EntradaInvalidaException.class,
                        () -> exportClassZipService.generateClassRosterZipBuffer(enorme, parametros("mesh"))));
        assertTrue(ex.getMessage().contains("grande demais") && ex.getMessage().contains("de 200")
                && ex.getMessage().contains("(Aluno") && ex.getMessage().contains("lotes menores")
                && ex.getMessage().contains("pasta própria"), ex.getMessage());
        java.util.regex.Matcher onde = java.util.regex.Pattern.compile("no aluno (\\d+) de 200").matcher(ex.getMessage());
        assertTrue(onde.find(), ex.getMessage());
        int parou = Integer.parseInt(onde.group(1));
        assertTrue(parou <= 20, "a montagem só parou no aluno " + parou + " de 200: o teto não está cortando cedo");
    }

    /**
     * A1: a maior turma típica — 200 alunos (o teto de alunos) com 4 localidades, em malha — continua gerando, e o
     * pacote relido fica dentro do teto. É o mesmo sinal da suspeita (turma cheia), só que legítimo.
     */
    @Test
    void maiorTurmaTipicaContinuaGerando() throws IOException {
        byte[] zip = exportClassZipService.generateClassRosterZipBuffer(turma(200, 4), parametros("mesh"));
        long arquivos = bytesDescomprimidos(zip);
        assertTrue(arquivos > 1_000_000 && arquivos <= InputLimits.MAX_BYTES_PACOTE_TURMA,
                "pacote relido com " + arquivos + " bytes");
        assertTrue(zip.length <= InputLimits.MAX_BYTES_PACOTE_TURMA, "o ZIP montado passou do teto: " + zip.length);
    }

    /** A fronteira proposta pela auditoria: 40 alunos com 4 localidades em estrela. */
    @Test
    void turmaPequenaEmEstrelaContinuaGerando() throws IOException {
        byte[] zip = exportClassZipService.generateClassRosterZipBuffer(turma(40, 4), parametros("star"));
        assertTrue(bytesDescomprimidos(zip) > 0);
    }

    /**
     * Revisão adversarial (2ª passada): cada aluno gravava eventos info (cenário resolvido, roteiro gerado); 200 alunos
     * enchiam a janela de 500 e apagavam a evidência dos outros pedidos. O pacote grava UM evento de resumo, com o total
     * de alunos. A2: sem o escopo de silêncio, os eventos por aluno aparecem depois do início deste pacote.
     */
    @Test
    void pacoteGravaUmEventoDeResumoENaoUmPorAluno() {
        java.time.Instant antes = java.time.Instant.now();
        exportClassZipService.generateClassRosterZipBuffer(turma(12, 3), parametros("star"));
        List<org.framework.net.telemetria.TelemetriaEvent> depois = telemetriaStore.snapshotEventos().stream()
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .filter(e -> "resolucaoProblemas".equals(e.modulo()))
                .toList();
        long porAluno = depois.stream()
                .filter(e -> "INFO".equals(e.level()))
                .filter(e -> !"problem_export_class_zip".equals(e.evento()))
                .count();
        List<org.framework.net.telemetria.TelemetriaEvent> resumo = depois.stream()
                .filter(e -> "problem_export_class_zip".equals(e.evento()))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(0, porAluno, "eventos por aluno gravados: " + depois.stream()
                .map(org.framework.net.telemetria.TelemetriaEvent::evento).toList());
        org.junit.jupiter.api.Assertions.assertEquals(1, resumo.size(), "o resumo do pacote tem de sair uma vez");
        org.junit.jupiter.api.Assertions.assertEquals("12", String.valueOf(resumo.get(0).fields().get("students_count")));
    }
}
