package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todo botão com rótulo tem ícone junto (auditoria FRONT-19 — 23 botões sem ícone:
 * Detalhes, Aplicar, Localizar, Anterior/Próxima…). Vale para button e para link com cara de botão
 * (.aed-btn/.btn). A Academia tem guarda própria e fica de fora.
 */
class BotaoComIconeGuardTest {

    private static final Pattern BOTAO = Pattern.compile("<(button|a)\\b([^>]*)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern CLASSE_BOTAO = Pattern.compile("class=\"[^\"]*\\b(aed-btn|btn)\\b");
    private static final Pattern SEM_ROTULO_PROPRIO =
            Pattern.compile("class=\"[^\"]*\\b(btn-close|navbar-toggler|dropdown-toggle|help-hint-btn|nav-link)\\b");
    private static final Pattern TEXTO = Pattern.compile("[A-Za-zÀ-ú]{3,}");

    static List<String> semIcone(String html) {
        List<String> achados = new ArrayList<>();
        Matcher m = BOTAO.matcher(html);
        while (m.find()) {
            String attrs = m.group(2);
            String corpo = m.group(3);
            if (m.group(1).equals("a") && !CLASSE_BOTAO.matcher(attrs).find()) {
                continue;
            }
            if (m.group(1).equals("button") && SEM_ROTULO_PROPRIO.matcher(attrs).find()) {
                continue;
            }
            if (corpo.contains("material-symbols") || corpo.contains("{#")) {
                continue;
            }
            String texto = corpo.replaceAll("<[^>]+>|\\{[^}]*\\}", "").strip();
            if (TEXTO.matcher(texto).find()) {
                achados.add(texto);
            }
        }
        return achados;
    }

    @Test
    void instrumentoDiscrimina() {
        assertEquals(List.of("Detalhes"), semIcone("<button type=\"button\" class=\"aed-btn\">Detalhes</button>"));
        assertEquals(List.of(), semIcone("<button class=\"aed-btn\"><span class=\"material-symbols-outlined\" "
                + "aria-hidden=\"true\">info</span> Detalhes</button>"));
        assertEquals(List.of(), semIcone("<a href=\"/x\">Detalhes</a>"), "link comum não é botão");
    }

    @Test
    void todoBotaoComRotuloTemIcone() throws IOException {
        List<String> achados = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> t = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path p : t.filter(f -> f.toString().endsWith(".html"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/academia/")).toList()) {
                lidos++;
                for (String texto : semIcone(Files.readString(p, StandardCharsets.UTF_8))) {
                    achados.add(p.getFileName() + ": " + texto);
                }
            }
        }
        assertTrue(lidos > 50, "instrumento cego: " + lidos + " templates");
        assertEquals(List.of(), achados);
    }
}
