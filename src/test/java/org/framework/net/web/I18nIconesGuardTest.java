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
 * Guarda das regras canônicas de i18n (§4/§6) e de ícones (blindagens 3, 5 e 6) — auditoria F17/F18/F19.
 *
 * <ul>
 *   <li>100% dos ícones Material Symbols com {@code translate="no"}: o Google Translate traduz o nome do
 *       glifo ("delete" → "borrar") e APAGA o ícone;</li>
 *   <li>100% dos ícones decorativos fora da árvore de acessibilidade ({@code aria-hidden}), senão o leitor
 *       de tela anuncia "content_copy Copiar";</li>
 *   <li>100% de {@code <code>/<pre>/<kbd>/<samp>} com {@code translate="no"} (IP, CLI, hex);</li>
 *   <li>fonte de ícone LOCAL — nenhum link para o CDN do Google Fonts de Material Symbols;</li>
 *   <li>nosso JS de i18n antes do element.js, e o {@code lang} do documento no idioma real.</li>
 * </ul>
 */
class I18nIconesGuardTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path ESTATICOS = Path.of("src/main/resources/META-INF/resources");

    private static final Pattern ICONE = Pattern.compile(
            "<span(\\s[^<>]*?class=([\"'])[^\"'<>]*material-symbols-outlined[^\"'<>]*\\2[^<>]*)>");
    private static final Pattern TECNICO = Pattern.compile("<(code|pre|kbd|samp)(\\s[^<>]*)?>");

    private static List<Path> arquivos() throws IOException {
        List<Path> todos = new ArrayList<>();
        try (Stream<Path> t = Files.walk(TEMPLATES); Stream<Path> e = Files.walk(ESTATICOS)) {
            t.filter(p -> p.toString().endsWith(".html")).forEach(todos::add);
            e.filter(p -> p.toString().endsWith(".js") && !p.toString().endsWith(".min.js")).forEach(todos::add);
        }
        return todos;
    }

    /** Violações num texto; usada nos arquivos reais E nos casos de calibração (mesmo instrumento). */
    static List<String> violacoes(String texto) {
        List<String> out = new ArrayList<>();
        Matcher m = ICONE.matcher(texto);
        while (m.find()) {
            String attrs = m.group(1);
            if (!attrs.contains("translate=")) {
                out.add("ícone sem translate=no: " + m.group());
            }
            if (!attrs.contains("aria-hidden") && !attrs.contains("aria-label") && !attrs.contains("role=")) {
                out.add("ícone sem aria-hidden: " + m.group());
            }
        }
        Matcher t = TECNICO.matcher(texto);
        while (t.find()) {
            String attrs = t.group(2) == null ? "" : t.group(2);
            if (!attrs.contains("translate=")) {
                out.add("<" + t.group(1) + "> sem translate=no: " + t.group());
            }
        }
        return out;
    }

    @Test
    void instrumentoDiscriminaDoenteDeLegitimo() {
        // A1: o doente é reprovado e o legítimo com o MESMO sinal (span de ícone, <code>) passa.
        assertEquals(2, violacoes("<span class=\"material-symbols-outlined\">home</span>").size());
        assertEquals(0, violacoes("<span class=\"material-symbols-outlined\" aria-hidden=\"true\" translate=\"no\">home</span>").size());
        assertEquals(1, violacoes("<code>10.0.0.1</code>").size());
        assertEquals(0, violacoes("<code translate='no'>10.0.0.1</code>").size());
    }

    @Test
    void iconesECodigoProtegidosDoTradutorEDoLeitorDeTela() throws IOException {
        int icones = 0;
        List<String> todas = new ArrayList<>();
        for (Path p : arquivos()) {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = ICONE.matcher(s);
            while (m.find()) {
                icones++;
            }
            for (String v : violacoes(s)) {
                todas.add(p + ": " + v);
            }
        }
        assertTrue(icones > 1000, "instrumento cego: só " + icones + " ícones encontrados");
        assertEquals(List.of(), todas);
    }

    @Test
    void fonteDeIconeLocalEOrdemDoI18n() throws IOException {
        for (String shell : List.of("shared/base.html", "login/index.html", "paginaErros/erro.html")) {
            String s = Files.readString(TEMPLATES.resolve(shell), StandardCharsets.UTF_8);
            assertTrue(!s.contains("family=Material+Symbols"), shell + " ainda carrega ícone do CDN");
            assertTrue(s.contains("/web/css/material-symbols.css"), shell + " sem a fonte de ícone local");
            int nosso = s.indexOf("i18n-translate.js");
            int google = s.indexOf("translate_a/element.js");
            assertTrue(nosso > 0 && google > nosso, shell + ": i18n-translate.js precisa vir ANTES do element.js");
            // O element.js é de terceiro: síncrono, um Google que não responde segura o DOMContentLoaded
            // e nenhum script do site que espera esse evento roda (medido em 01/10/2026).
            String tagGoogle = s.substring(s.lastIndexOf("<script", google), s.indexOf(">", google) + 1);
            assertTrue(tagGoogle.matches("(?s)<script[^>]*\\sasync[\\s>].*"),
                    shell + ": o element.js precisa de async — " + tagGoogle);
        }
        assertTrue(Files.size(ESTATICOS.resolve("web/fonts/material-symbols-outlined.woff2")) > 100_000,
                "fonte local ausente ou truncada");
    }

    /**
     * As bandeiras do seletor de idioma são ícones de interface: servidas pelo próprio site.
     * Com o CDN fora, o seletor virava três imagens quebradas — justamente o controle que
     * quem não lê português procura. As bandeiras de PAÍS da análise de GeoIP (uma por país, montadas no
     * JS) não são o seletor e ficam fora desta guarda.
     */
    @Test
    void bandeirasDoSeletorDeIdiomaSaoLocais() throws IOException {
        int seletores = 0;
        for (String shell : List.of("shared/main_menu.html", "login/index.html", "paginaErros/erro.html")) {
            String s = Files.readString(TEMPLATES.resolve(shell), StandardCharsets.UTF_8);
            assertTrue(!s.contains("flagcdn.com"), shell + " ainda busca bandeira no CDN");
            for (String pais : List.of("br", "us", "es")) {
                assertTrue(s.contains("/web/img/bandeiras/" + pais + ".png"), shell + " sem a bandeira local " + pais);
                seletores++;
            }
        }
        assertEquals(9, seletores, "instrumento cego: seletores de idioma não encontrados");
        for (String pais : List.of("br", "us", "es")) {
            assertTrue(Files.size(ESTATICOS.resolve("web/img/bandeiras/" + pais + ".png")) > 200,
                    "bandeira " + pais + " ausente ou truncada");
        }
    }
}
