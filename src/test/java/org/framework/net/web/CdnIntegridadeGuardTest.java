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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F20: script/CSS de CDN roda com acesso ao cookie do token CSRF da página. Sem {@code integrity}, um
 * CDN comprometido injeta código; com versão flutuante ({@code mermaid@11}), uma release nova muda o
 * comportamento sem ninguém saber. A guarda antiga só olhava a rota "/". Esta varre TODOS os templates.
 *
 * <p>Isentos, com motivo: {@code translate.google.com/translate_a/element.js} (servido dinamicamente,
 * SRI impossível) e o CSS de fontes de TEXTO do Google Fonts (varia por navegador; sem ele a página cai
 * na fonte do sistema — a fonte de ÍCONE é local, ver I18nIconesGuardTest).</p>
 */
class CdnIntegridadeGuardTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern EXTERNO = Pattern.compile(
            "<(script|link)\\b[^>]*?(?:src|href)=\"(https?://[^\"]+)\"[^>]*>");
    /** Bloco importmap e as URLs externas dentro dele (SEC-05: a guarda só via script src e link href). */
    private static final Pattern IMPORTMAP = Pattern.compile("(?s)<script\s+type=\"importmap\"[^>]*>(.*?)</script>");
    private static final Pattern URL_NO_IMPORTMAP = Pattern.compile("\"(https?://[^\"]+)\"");

    /**
     * Linha de base (catraca, A4) das URLs de CDN em importmap. Nasceu com as três do esm.sh do globo 3D;
     * em 02/10/2026, com o aval do Paulo, o globo passou a ser servido pelo próprio site (scripts/vendor-globo)
     * e a dívida zerou. Continua vazia: módulo ES de CDN não tem integrity por URL.
     */
    static final java.util.Set<String> IMPORTMAP_LINHA_DE_BASE = java.util.Set.of();

    /** import/export estático ou import() dinâmico apontando para fora do site. */
    private static final Pattern IMPORT_EXTERNO = Pattern.compile(
            "(?:\\bfrom\\s*|\\bimport\\s*\\(?\\s*)[\"'](https?:)?//");

    static boolean importaDeFora(String js) {
        return IMPORT_EXTERNO.matcher(js).find();
    }

    static List<String> violacoesImportmap(String texto) {
        List<String> out = new ArrayList<>();
        Matcher bloco = IMPORTMAP.matcher(texto);
        while (bloco.find()) {
            Matcher url = URL_NO_IMPORTMAP.matcher(bloco.group(1));
            while (url.find()) {
                if (!IMPORTMAP_LINHA_DE_BASE.contains(url.group(1))) {
                    out.add("importmap com CDN fora da linha de base: " + url.group(1));
                }
            }
        }
        return out;
    }

    private static final Pattern VERSAO_FLUTUANTE = Pattern.compile("@\\d+(?:\\.\\d+)?/");

    static List<String> violacoes(String texto) {
        List<String> out = new ArrayList<>();
        Matcher m = EXTERNO.matcher(texto);
        while (m.find()) {
            String tag = m.group();
            String url = m.group(2);
            boolean stylesheetOuScript = m.group(1).equals("script") || tag.contains("stylesheet");
            if (!stylesheetOuScript || url.contains("translate.google.com/translate_a/element.js")
                    || url.startsWith("https://fonts.googleapis.com/css2?family=Space")) {
                continue;
            }
            if (!tag.contains("integrity=\"sha384-")) {
                out.add("sem integrity: " + url);
            }
            if (VERSAO_FLUTUANTE.matcher(url).find()) {
                out.add("versão flutuante: " + url);
            }
        }
        return out;
    }

    @Test
    void instrumentoDiscriminaDoenteDeLegitimo() {
        assertEquals(2, violacoes("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js\"></script>").size());
        assertEquals(0, violacoes("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11.17.2/dist/mermaid.min.js\" "
                + "integrity=\"sha384-abc\" crossorigin=\"anonymous\"></script>").size());
        assertEquals(0, violacoes("<script src=\"https://translate.google.com/translate_a/element.js?cb=x\"></script>").size());
        // importmap (SEC-05): URL de CDN nova reprova; a da linha de base e o caminho local passam.
        assertEquals(1, violacoesImportmap("<script type=\"importmap\">{\"imports\":{\"x\":\"https://esm.sh/x@1.0.0\"}}</script>").size());
        assertEquals(1, violacoesImportmap("<script type=\"importmap\">{\"imports\":{\"three\":\"https://esm.sh/three@0.185.1\"}}</script>").size());
        assertEquals(0, violacoesImportmap("<script type=\"importmap\">{\"imports\":{\"y\":\"/localizacao/vendor/y.mjs\"}}</script>").size());
        // módulo ES: import de CDN reprova; o mesmo import com caminho do site passa (fronteira, A1).
        assertTrue(importaDeFora("import { THREE } from \"https://esm.sh/three@0.185.1\";"));
        assertTrue(importaDeFora("const m = await import('//cdn.example/x.mjs');"));
        assertFalse(importaDeFora("import { THREE, Globe } from \"/localizacao/vendor/globo-three.mjs?v=1\";"));
        assertFalse(importaDeFora("var EARTH = \"https://exemplo.invalid/textura.jpg\"; // não é import"));
    }

    /**
     * SEC-05: o globo 3D vinha do esm.sh por import de módulo, que a guarda de &lt;script src&gt; não via.
     * Nenhum JS do site (fora o pacote gerado em vendor/) importa de outra origem.
     */
    @Test
    void moduloDoSiteNaoImportaDeCdn() throws IOException {
        Path raiz = Path.of("src/main/resources/META-INF/resources");
        List<String> ruins = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> t = Files.walk(raiz)) {
            for (Path p : t.filter(x -> x.toString().matches(".*\\.m?js$")).toList()) {
                if (p.toString().replace('\\', '/').contains("/vendor/")) continue;
                lidos++;
                if (importaDeFora(Files.readString(p))) ruins.add(raiz.relativize(p).toString());
            }
        }
        assertTrue(lidos > 20, "instrumento cego: só " + lidos + " arquivos JS lidos");
        assertEquals(List.of(), ruins, "JS importando módulo de CDN");
    }

    @Test
    void todoAssetDeCdnTemVersaoExataEIntegrity() throws IOException {
        List<String> todas = new ArrayList<>();
        int externos = 0;
        try (Stream<Path> t = Files.walk(TEMPLATES)) {
            for (Path p : t.filter(x -> x.toString().endsWith(".html")).toList()) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = EXTERNO.matcher(s);
                while (m.find()) {
                    externos++;
                }
                for (String v : violacoes(s)) {
                    todas.add(p + ": " + v);
                }
                for (String v : violacoesImportmap(s)) {
                    todas.add(p + ": " + v);
                }
            }
        }
        // Desde 22/10/2026 Bootstrap, mermaid, Chart.js e Leaflet são locais (web/vendor); sobram as fontes de texto
        // do Google e o tradutor. Zero externos seria o instrumento cego (não leu template nenhum).
        assertTrue(externos > 0, "instrumento cego: só " + externos + " assets externos encontrados");
        assertEquals(List.of(), todas);
    }
}
