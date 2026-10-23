package org.framework.net.web;

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
 * Bandeira de país é local (auditoria FRONT-18: ícone local, nunca CDN). Com o flagcdn fora,
 * a imagem quebrava; agora é emoji pelo código ISO. Nenhum asset, template ou CSP volta a citar o CDN.
 */
class BandeiraLocalGuardTest {

    @Test
    void nenhumaBandeiraVemDeCdn() throws IOException {
        List<String> achados = new ArrayList<>();
        int lidos = 0;
        for (Path raiz : List.of(Path.of("src/main/resources/META-INF/resources"), Path.of("src/main/resources/templates"))) {
            try (Stream<Path> arquivos = Files.walk(raiz)) {
                for (Path p : arquivos.filter(f -> f.toString().matches(".*\\.(js|html|css)$"))
                        .filter(f -> !f.toString().replace('\\', '/').contains("/vendor/")).toList()) {
                    lidos++;
                    if (Files.readString(p, StandardCharsets.UTF_8).contains("flagcdn.com")) {
                        achados.add(p.toString());
                    }
                }
            }
        }
        String csp = Files.readAllLines(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8).stream()
                .filter(l -> l.startsWith("quarkus.http.header.Content-Security-Policy.value=")).findFirst().orElse("");
        assertTrue(lidos > 100 && !csp.isEmpty(), "instrumento cego: " + lidos + " arquivos, CSP lido=" + !csp.isEmpty());
        if (csp.contains("flagcdn.com")) {
            achados.add("CSP img-src");
        }
        assertEquals(List.of(), achados);
    }
}
