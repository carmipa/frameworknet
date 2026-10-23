package org.framework.net.protocolos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todo catálogo de aprofundamento carrega e valida no BOOT (auditoria OPS-20): BGP e SSH estavam sem
 * {@code @Startup} e a falha fechada que o Javadoc promete ("erro no boot, não página pela metade") só
 * aparecia na primeira visita.
 */
class CatalogosNoBootGuardTest {

    @Test
    void todoCatalogoDeAprofundamentoTemStartup() throws IOException {
        List<String> sem = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> fontes = Files.walk(Path.of("src/main/java"))) {
            for (Path p : fontes.filter(f -> f.getFileName().toString().endsWith("AprofundamentoCatalog.java")).toList()) {
                lidos++;
                if (Files.readAllLines(p, StandardCharsets.UTF_8).stream().noneMatch(l -> l.strip().equals("@Startup"))) {
                    sem.add(p.getFileName().toString());
                }
            }
        }
        assertTrue(lidos >= 10, "instrumento cego: " + lidos + " catálogos encontrados");
        assertEquals(List.of(), sem);
    }
}
