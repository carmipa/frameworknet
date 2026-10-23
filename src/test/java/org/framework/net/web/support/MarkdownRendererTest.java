package org.framework.net.web.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void deveRenderizarTabelaMarkdownComoHtml() {
        String markdown = """
                | Modulo | Rota |
                |--------|------|
                | Documentacao | `/documentacao` |
                """;

        String html = renderer.render(markdown).html();

        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Modulo</th>"));
        assertTrue(html.contains("<td><code>/documentacao</code></td>"));
        assertFalse(html.contains("<p>| Modulo | Rota |</p>"));
    }

    /** FRONT-16: a página já tem o seu h1; o "#" do README desce para h2 e a classe guarda o nível. */
    @Test
    void tituloDoReadmeNaoViraSegundoH1DaPagina() {
        String html = renderer.render("# Framework\n\n## Seção\n\n###### Fundo\n").html();

        assertFalse(html.contains("<h1"), html);
        assertTrue(html.contains("<h2 id=\"framework\" class=\"md-h1\">"), html);
        assertTrue(html.contains("<h3 id=\"secao\" class=\"md-h2\">"), html);
        assertTrue(html.contains("<h6 id=\"fundo\" class=\"md-h6\">"), html);
    }

    @Test
    void deveRenderizarBlocosComunsDoReadmeTecnico() {
        String markdown = """
                > **Versao Java/Quarkus**

                ---

                texto com **negrito** e `codigo`.
                """;

        String html = renderer.render(markdown).html();

        assertTrue(html.contains("<blockquote><p><strong>Versao Java/Quarkus</strong></p></blockquote>"));
        assertTrue(html.contains("<hr>"));
        assertTrue(html.contains("<strong>negrito</strong>"));
        assertTrue(html.contains("<code>codigo</code>"));
    }

    @Test
    void deveGerarMetadadosCuradosParaIndiceDaDocumentacao() {
        String markdown = """
                # 🛡️ Framework de Redes

                ## 🎯 Visão Geral

                ### Módulo 1 — Análise Didática (`/`)

                ### Fluxo do módulo VLSM/WAN
                """;

        var secoes = renderer.render(markdown).secoes();
        Map<String, Object> tituloPrincipal = secoes.get(0);
        Map<String, Object> visaoGeral = secoes.get(1);
        Map<String, Object> modulo = secoes.get(2);
        Map<String, Object> fluxo = secoes.get(3);

        assertFalse((Boolean) tituloPrincipal.get("tocVisivel"));
        assertTrue((Boolean) visaoGeral.get("tocVisivel"));
        assertTrue((Boolean) modulo.get("tocVisivel"));
        assertTrue((Boolean) fluxo.get("tocVisivel"));
        assertTrue(visaoGeral.get("tocTitulo").equals("Visão Geral"));
        assertTrue(visaoGeral.get("tocGrupo").equals("SUMÁRIO"));
        assertTrue(visaoGeral.get("tocIcone").equals("lightbulb"));
        assertTrue(modulo.get("tocTitulo").equals("Módulo 1 — Análise Didática"));
        assertTrue(modulo.get("tocGrupo").equals("MÓDULOS"));
        assertTrue(fluxo.get("tocGrupo").equals("DIAGRAMAS"));
        assertTrue(fluxo.get("tocIcone").equals("hub"));
    }
}
