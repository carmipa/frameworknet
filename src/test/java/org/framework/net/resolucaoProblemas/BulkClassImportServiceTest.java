package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.importing.BulkClassImportService;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BulkClassImportServiceTest {

    @Inject
    BulkClassImportService bulkClassImportService;

    @Test
    void parseClassRosterPasteTabSeparated() {
        String paste = """
                Nome\tRede\tHosts1\tHosts2
                João Silva\t172.51.0.0\t400\t390
                Maria Souza\t172.52.0.0/16\t350\t300
                """;
        List<ClassRosterRow> rows = bulkClassImportService.parseClassRosterPaste(paste);
        assertEquals(2, rows.size());
        assertEquals("João Silva", rows.get(0).getStudentName());
        assertEquals("172.51.0.0/16", rows.get(0).getBaseNetwork());
        assertEquals(2, rows.get(0).getLocations().size());
        assertEquals("400", rows.get(0).getLocations().get(0).getHosts());
        assertTrue(rows.get(0).getFolderSlug() != null && !rows.get(0).getFolderSlug().isBlank());
    }

    /**
     * F02: cada coluna de hosts vira uma localidade; sem teto, 200 colunas travavam um núcleo por
     * horas. O teto é o mesmo do formulário (InputLimits.MAX_LOCATION_ROWS = 50). Fronteira: 50 passa.
     */
    @Test
    void limitaColunasDeHostsAoTetoDeLocalidades() {
        StringBuilder cinquenta = new StringBuilder("Aluno;10.0.0.0/8");
        for (int i = 0; i < 50; i++) {
            cinquenta.append(";10");
        }
        assertEquals(50, bulkClassImportService.parseClassRosterPaste(cinquenta.toString())
                .get(0).getLocations().size());
        assertThrows(EntradaInvalidaException.class,
                () -> bulkClassImportService.parseClassRosterPaste(cinquenta + ";10"));
    }

    /**
     * Lote 1 da auditoria (SEC-02): a vírgula era cortada pela regex {@code \s*,\s*}, que numa sequência longa de
     * espaços sem vírgula retrocedia a cada posição — 250 mil espaços custavam 192,7 s de CPU. A2: a mesma entrada
     * da auditoria, com 100 mil espaços (~30 s na forma antiga), termina bem abaixo de 2 s e na recusa de sempre.
     */
    @Test
    void virgulaCortaEmTempoLinear() {
        String colagem = "a,x" + " ".repeat(100_000) + "b";
        EntradaInvalidaException ex = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2),
                () -> assertThrows(EntradaInvalidaException.class,
                        () -> bulkClassImportService.parseClassRosterPaste(colagem)));
        assertTrue(ex.getMessage().contains("2 coluna(s)"), ex.getMessage());
    }

    /** A1: o mesmo sinal (espaço em volta da vírgula) numa planilha legítima continua dando as quatro colunas. */
    @Test
    void virgulaComEspacosEmVoltaContinuaSeparandoAsColunas() {
        ClassRosterRow linha = bulkClassImportService.parseClassRosterPaste("Ana , 10.0.0.0/16 , 10 , 20").get(0);
        assertEquals("Ana", linha.getStudentName());
        assertEquals("10.0.0.0/16", linha.getBaseNetwork());
        assertEquals(2, linha.getLocations().size());
        assertEquals("10", linha.getLocations().get(0).getHosts());
        assertEquals("20", linha.getLocations().get(1).getHosts());
    }

    /**
     * Lote 1 (revisão adversarial): o nome do aluno vira pasta e vai no nome de cada arquivo do ZIP, duas vezes, sem
     * compressão — sem teto, três nomes de 65 mil letras davam ~21 MB de nomes no pacote. Fronteira (A1): 120
     * caracteres passam; 121 são recusados com a linha e o motivo.
     */
    @Test
    void nomeDoAlunoTemTeto() {
        String noTeto = "A".repeat(org.framework.net.shared.InputLimits.MAX_LABEL_LENGTH);
        assertEquals(noTeto, bulkClassImportService.parseClassRosterPaste(noTeto + "\t10.0.0.0/16\t10\t20")
                .get(0).getStudentName());
        EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                () -> bulkClassImportService.parseClassRosterPaste(noTeto + "B\t10.0.0.0/16\t10\t20"));
        assertTrue(ex.getMessage().contains("Linha 1") && ex.getMessage().contains("nome do aluno"), ex.getMessage());
    }

    @Test
    void parseClassRosterPasteVazioFalha() {
        assertThrows(EntradaInvalidaException.class, () -> bulkClassImportService.parseClassRosterPaste("  "));
    }

    @Test
    void parseClassRosterPasteColunasInsuficientes() {
        assertThrows(EntradaInvalidaException.class, () ->
                bulkClassImportService.parseClassRosterPaste("Aluno\t172.51.0.0\t400"));
    }
}
