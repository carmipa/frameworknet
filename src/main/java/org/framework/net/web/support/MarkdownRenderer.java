package org.framework.net.web.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UL = Pattern.compile("^- (.*)$");
    private static final Pattern OL = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");
    private static final Pattern CODE_INLINE = Pattern.compile("`([^`]+)`");
    private static final Pattern STRONG = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)");

    /**
     * Converte o README em HTML para a Central de Documentação, com o sumário lateral.
     *
     * <p>PROPÓSITO DE NEGÓCIO: a documentação do site é o próprio README do repositório, lido e
     * mostrado na página, para não existir uma segunda cópia que divirja.
     *
     * <p>INVARIANTES DO DOMÍNIO: texto do README sai escapado (código e tabela incluídos); o título
     * do Markdown desce um nível no HTML (o "#" vira h2), porque a página já tem o seu h1; o nível
     * original fica na classe {@code md-hN} e na seção do sumário.
     *
     * <p>COMPORTAMENTO EM CASO DE FALHA: entrada vazia devolve HTML vazio e sumário vazio; não lança.
     */
    public RenderResult render(String markdown) {
        String texto = normalizarLinhasBadges(markdown);
        String[] linhas = texto.split("\n", -1);
        List<String> blocos = new ArrayList<>();
        List<Map<String, Object>> secoes = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        boolean emCode = false;
        String lang = "";
        List<String> codeBuffer = new ArrayList<>();
        boolean emUl = false;
        boolean emOl = false;
        List<String> tabelaBuffer = new ArrayList<>();

        for (String linha : linhas) {
            String stripped = linha.strip();
            if (stripped.startsWith("```")) {
                fecharTabela(blocos, tabelaBuffer);
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                if (!emCode) {
                    emCode = true;
                    lang = stripped.substring(3).strip();
                    codeBuffer.clear();
                } else {
                    String code = String.join("\n", codeBuffer);
                    if ("mermaid".equalsIgnoreCase(lang)) {
                        blocos.add("<div class=\"mermaid\">" + escape(code) + "</div>");
                    } else {
                        blocos.add("<pre class='doc-pre'><code>" + escape(code) + "</code></pre>");
                    }
                    emCode = false;
                    lang = "";
                }
                continue;
            }
            if (emCode) {
                codeBuffer.add(linha);
                continue;
            }
            if (isTableLine(stripped)) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                tabelaBuffer.add(stripped);
                continue;
            }
            fecharTabela(blocos, tabelaBuffer);
            if (stripped.isEmpty()) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                continue;
            }
            if ("---".equals(stripped) || "***".equals(stripped)) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                blocos.add("<hr>");
                continue;
            }
            if (stripped.startsWith("<") && stripped.endsWith(">")) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                blocos.add(linha);
                continue;
            }
            if (stripped.startsWith(">")) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                blocos.add("<blockquote><p>" + inline(stripped.substring(1).strip()) + "</p></blockquote>");
                continue;
            }
            Matcher head = HEADING.matcher(stripped);
            if (head.matches()) {
                fecharListas(blocos, emUl, emOl);
                emUl = false;
                emOl = false;
                int nivel = head.group(1).length();
                String titulo = head.group(2).strip();
                String slug = slugify(titulo, slugs);
                Map<String, Object> secao = new LinkedHashMap<>();
                secao.put("id", slug);
                secao.put("titulo", titulo);
                secao.put("nivel", nivel);
                secao.put("tocTitulo", tituloToc(titulo));
                String grupoToc = grupoToc(titulo, nivel);
                secao.put("tocGrupo", grupoToc);
                secao.put("tocGrupoCss", grupoCss(grupoToc));
                secao.put("tocIcone", iconeToc(titulo));
                secao.put("tocNivel", nivel >= 3 ? 2 : 1);
                secao.put("tocVisivel", tocVisivel(titulo, nivel));
                secoes.add(secao);
                // A página já tem o seu h1 ("Central de Documentação"): o "#" do README desce um nível,
                // senão a página fica com dois h1 (FRONT-16). A classe guarda o nível do Markdown para o CSS.
                int tag = Math.min(nivel + 1, 6);
                blocos.add("<h" + tag + " id=\"" + slug + "\" class=\"md-h" + nivel + "\">" + inline(titulo) + "</h" + tag + ">");
                continue;
            }
            Matcher ol = OL.matcher(stripped);
            if (ol.matches()) {
                if (emUl) {
                    blocos.add("</ul>");
                    emUl = false;
                }
                if (!emOl) {
                    blocos.add("<ol>");
                    emOl = true;
                }
                blocos.add("<li>" + inline(ol.group(2)) + "</li>");
                continue;
            }
            Matcher ul = UL.matcher(stripped);
            if (ul.matches()) {
                if (emOl) {
                    blocos.add("</ol>");
                    emOl = false;
                }
                if (!emUl) {
                    blocos.add("<ul>");
                    emUl = true;
                }
                blocos.add("<li>" + inline(ul.group(1)) + "</li>");
                continue;
            }
            fecharListas(blocos, emUl, emOl);
            emUl = false;
            emOl = false;
            blocos.add("<p>" + inline(stripped) + "</p>");
        }
        fecharTabela(blocos, tabelaBuffer);
        if (emUl) blocos.add("</ul>");
        if (emOl) blocos.add("</ol>");
        return new RenderResult(String.join("\n", blocos), secoes);
    }

    private static void fecharListas(List<String> blocos, boolean emUl, boolean emOl) {
        if (emUl) blocos.add("</ul>");
        if (emOl) blocos.add("</ol>");
    }

    private String inline(String texto) {
        String escaped = escape(texto);
        escaped = LINK.matcher(escaped).replaceAll("<a href=\"$2\" target=\"_blank\" rel=\"noopener noreferrer\">$1</a>");
        escaped = CODE_INLINE.matcher(escaped).replaceAll("<code>$1</code>");
        escaped = STRONG.matcher(escaped).replaceAll("<strong>$1</strong>");
        return escaped;
    }

    private static boolean isTableLine(String stripped) {
        return stripped.startsWith("|") && stripped.endsWith("|") && stripped.indexOf('|', 1) > 0;
    }

    private void fecharTabela(List<String> blocos, List<String> tabelaBuffer) {
        if (tabelaBuffer.isEmpty()) {
            return;
        }
        if (tabelaBuffer.size() < 2 || !TABLE_SEPARATOR.matcher(tabelaBuffer.get(1)).matches()) {
            for (String linha : tabelaBuffer) {
                blocos.add("<p>" + inline(linha) + "</p>");
            }
            tabelaBuffer.clear();
            return;
        }

        List<String> cabecalho = tableCells(tabelaBuffer.get(0));
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-responsive\"><table>");
        html.append("<thead><tr>");
        for (String cell : cabecalho) {
            html.append("<th>").append(inline(cell)).append("</th>");
        }
        html.append("</tr></thead>");
        html.append("<tbody>");
        for (int i = 2; i < tabelaBuffer.size(); i++) {
            if (TABLE_SEPARATOR.matcher(tabelaBuffer.get(i)).matches()) {
                continue;
            }
            html.append("<tr>");
            List<String> cells = tableCells(tabelaBuffer.get(i));
            for (int col = 0; col < cabecalho.size(); col++) {
                String cell = col < cells.size() ? cells.get(col) : "";
                html.append("<td>").append(inline(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
        blocos.add(html.toString());
        tabelaBuffer.clear();
    }

    private static List<String> tableCells(String linha) {
        String body = linha;
        if (body.startsWith("|")) {
            body = body.substring(1);
        }
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        String[] partes = body.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String parte : partes) {
            cells.add(parte.strip());
        }
        return cells;
    }

    private static boolean tocVisivel(String titulo, int nivel) {
        String normalized = normalizarTituloParaComparacao(titulo);
        return nivel > 1 && !"sumario".equals(normalized);
    }

    private static String grupoToc(String titulo, int nivel) {
        String normalized = normalizarTituloParaComparacao(titulo);
        if (normalized.contains("visao geral")
                || normalized.contains("modulos e rotas")
                || normalized.contains("funcionalidades")) {
            return "SUMÁRIO";
        }
        if (normalized.startsWith("modulo ")
                || normalized.contains("exportacoes")
                || normalized.contains("importar turma")) {
            return "MÓDULOS";
        }
        if (normalized.contains("arquitetura")
                || normalized.contains("camadas por modulo")
                || normalized.startsWith("fluxo")
                || normalized.contains("utilitarios transversais")
                || normalized.contains("tratamento de excecoes")) {
            return "DIAGRAMAS";
        }
        if (normalized.contains("requisitos")
                || normalized.contains("execucao")
                || normalized.contains("variaveis")
                || normalized.contains("seguranca")
                || normalized.contains("telemetria")
                || normalized.contains("deploy docker")) {
            return "OPERAÇÃO";
        }
        if (normalized.contains("estrutura")
                || normalized.contains("testes")
                || normalized.contains("roadmap")
                || normalized.contains("autor")
                || normalized.contains("licenca")) {
            return "PROJETO";
        }
        return nivel >= 3 ? "DIAGRAMAS" : "SUMÁRIO";
    }

    private static String iconeToc(String titulo) {
        String normalized = normalizarTituloParaComparacao(titulo);
        if (normalized.contains("visao geral")) return "lightbulb";
        if (normalized.contains("modulos e rotas")) return "view_module";
        if (normalized.contains("funcionalidades")) return "rocket_launch";
        if (normalized.startsWith("modulo ")) return "category";
        if (normalized.contains("exportacoes")) return "download";
        if (normalized.contains("importar turma")) return "upload_file";
        if (normalized.contains("arquitetura")) return "account_tree";
        if (normalized.contains("camadas por modulo")) return "layers";
        if (normalized.contains("vlsm") || normalized.contains("wan")) return "hub";
        if (normalized.startsWith("fluxo")) return "sync_alt";
        if (normalized.contains("utilitarios")) return "widgets";
        if (normalized.contains("tratamento")) return "report";
        if (normalized.contains("deploy") || normalized.contains("execucao")) return "terminal";
        if (normalized.contains("requisitos")) return "checklist";
        if (normalized.contains("variaveis")) return "settings";
        if (normalized.contains("seguranca")) return "shield";
        if (normalized.contains("telemetria")) return "monitoring";
        if (normalized.contains("estrutura")) return "folder";
        if (normalized.contains("testes")) return "science";
        if (normalized.contains("roadmap")) return "flag";
        if (normalized.contains("autor")) return "person";
        if (normalized.contains("licenca")) return "workspace_premium";
        return "article";
    }

    private static String grupoCss(String grupo) {
        return Normalizer.normalize(grupo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();
    }

    private static String tituloToc(String titulo) {
        String limpo = titulo
                .replaceAll("^[^\\p{L}\\p{N}`]+\\s*", "")
                .replace("`", "")
                .replaceAll("\\s+\\(/[^)]*\\)", "")
                .strip();
        return limpo.isBlank() ? titulo : limpo;
    }

    private static String normalizarTituloParaComparacao(String titulo) {
        return Normalizer.normalize(tituloToc(titulo), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9\\s-]", " ")
                .strip()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private static String escape(String texto) {
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String slugify(String titulo, Set<String> slugs) {
        String normalized = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .strip()
                .toLowerCase()
                .replaceAll("[\\s-]+", "-");
        if (normalized.isBlank()) {
            normalized = "secao";
        }
        String slug = normalized;
        int idx = 2;
        while (slugs.contains(slug)) {
            slug = normalized + "-" + idx++;
        }
        slugs.add(slug);
        return slug;
    }

    private static String normalizarLinhasBadges(String texto) {
        Pattern badge = Pattern.compile("^\\[!\\[[^\\]]*\\]\\(https?://img\\.shields\\.io/[^\\)]*\\)\\]\\([^)]+\\)\\s*$");
        String[] linhas = texto.split("\n", -1);
        List<String> saida = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        for (String linha : linhas) {
            String stripped = linha.strip();
            if (badge.matcher(stripped).matches()) {
                buffer.add(stripped);
                continue;
            }
            if (!buffer.isEmpty()) {
                saida.add(String.join(" ", buffer));
                buffer.clear();
            }
            saida.add(linha);
        }
        if (!buffer.isEmpty()) {
            saida.add(String.join(" ", buffer));
        }
        return String.join("\n", saida);
    }

    public record RenderResult(String html, List<Map<String, Object>> secoes) {
    }
}
