package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hora exibida não depende do fuso da máquina (auditoria OPS-10): a JVM de produção roda em UTC e a de
 * desenvolvimento em BRT. Nenhum fonte usa o fuso padrão da JVM nem LocalDateTime.now() para apresentar.
 */
class FusoDoSiteGuardTest {

    @Test
    void formatadorUsaBrasiliaQualquerQueSejaAMaquina() {
        // 22:32:07 UTC é 19:32:07 em Brasília (sem horário de verão desde 2019).
        assertEquals("19:32:07", FusoDoSite.formato("HH:mm:ss").format(Instant.parse("2026-10-01T22:32:07Z")));
    }

    @Test
    void nenhumFonteUsaOFusoDaMaquina() throws IOException {
        List<String> achados = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> fontes = Files.walk(Path.of("src/main/java"))) {
            for (Path p : fontes.filter(f -> f.toString().endsWith(".java")).toList()) {
                lidos++;
                String s = Files.readString(p, StandardCharsets.UTF_8);
                if (s.contains("ZoneId.systemDefault()") || s.contains("LocalDateTime.now()") || s.contains("LocalTime.now()")) {
                    achados.add(p.toString());
                }
            }
        }
        assertTrue(lidos > 100, "instrumento cego: " + lidos + " fontes lidos");
        assertEquals(List.of(), achados);
    }
}
