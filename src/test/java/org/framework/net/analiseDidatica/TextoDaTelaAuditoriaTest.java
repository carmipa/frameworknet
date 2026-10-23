package org.framework.net.analiseDidatica;

import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Texto da tela (auditoria FRONT-21): concordância das mensagens e acentos da Telemetria. */
class TextoDaTelaAuditoriaTest {

    @Test
    void mensagemConcordaComORotulo() {
        Ipv4Kernel kernel = new Ipv4Kernel();
        String fem = assertThrows(RuntimeException.class, () -> kernel.parseIpv4Parts("1.2.3", "Máscara decimal")).getMessage();
        String masc = assertThrows(RuntimeException.class, () -> kernel.parseIpv4Parts("1.2.3", "IP")).getMessage();
        assertTrue(fem.startsWith("Máscara decimal inválida"), fem);
        assertTrue(masc.startsWith("IP inválido"), masc);
        String vazia = assertThrows(RuntimeException.class, () -> kernel.parseIpv4Parts(" ", "Wildcard mask")).getMessage();
        assertEquals("Wildcard mask vazia.", vazia);
    }

    private static final Pattern SEM_ACENTO = Pattern.compile(
            "\\b(Nao|nao|possivel|repositorio|relatorio|compartilhavel|memoria|publico|PUBLICO|ultimo)\\b");
    private static final Pattern LITERAL_JS = Pattern.compile("\"([^\"\\\\]*)\"");

    @Test
    void instrumentoReconheceTextoSemAcento() {
        assertTrue(SEM_ACENTO.matcher("Nao foi possivel publicar").find(), "A2: o caso doente precisa ser visto");
        assertTrue(!SEM_ACENTO.matcher("Não foi possível publicar").find(), "A1: o texto certo passa");
    }

    @Test
    void textoVisivelDaTelemetriaTemAcento() throws IOException {
        List<String> achados = new ArrayList<>();
        Path js = Path.of("src/main/resources/META-INF/resources/telemetria/js/dashboard.js");
        int literais = 0;
        for (String linha : Files.readAllLines(js, StandardCharsets.UTF_8)) {
            String t = linha.strip();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) {
                continue;
            }
            Matcher m = LITERAL_JS.matcher(linha);
            while (m.find()) {
                literais++;
                if (SEM_ACENTO.matcher(m.group(1)).find()) {
                    achados.add("dashboard.js: " + m.group(1));
                }
            }
        }
        Path html = Path.of("src/main/resources/templates/telemetria/dashboard.html");
        String semComentario = Files.readString(html, StandardCharsets.UTF_8)
                .replaceAll("(?s)\\{!.*?!\\}|<!--.*?-->", "");
        Matcher texto = Pattern.compile(">([^<>{]+)<|title=\"([^\"]+)\"").matcher(semComentario);
        while (texto.find()) {
            String visivel = texto.group(1) != null ? texto.group(1) : texto.group(2);
            if (SEM_ACENTO.matcher(visivel).find()) {
                achados.add("dashboard.html: " + visivel.strip());
            }
        }
        assertTrue(literais > 50, "instrumento cego: " + literais + " literais lidos");
        assertEquals(List.of(), achados);
    }
}
