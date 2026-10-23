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
