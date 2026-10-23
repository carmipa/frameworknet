package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda da REGRA 27 do Paulo (canônica desde 15/10/2026): largura cheia e unidade relativa em toda tela.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o site tem de aproveitar a tela inteira, do celular de 320 px ao monitor de
 * 2560 px, e acompanhar o tamanho de fonte que a pessoa escolheu no navegador. Contêiner com {@code max-width} e
 * {@code margin: auto} vira uma coluna "pingando no meio" do monitor largo, e {@code px} fixo em fonte,
 * espaçamento ou tamanho não escala com a preferência de fonte de quem lê. Documento prova que a regra foi lida;
 * só esta guarda prova que ela foi aplicada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b></p>
 * <ul>
 *   <li>{@code px} só aparece em borda, contorno, sombra e filtro (e nos breakpoints de {@code @media}, que não são
 *       declaração e por isso não são lidos); {@code 0px} não conta;</li>
 *   <li>nenhum {@code 100vw} (inclui a barra de rolagem e cria rolagem lateral);</li>
 *   <li>nenhuma regra com {@code max-width} (fora de {@code none}, {@code 100%} e da medida de leitura em {@code ch})
 *       junto de margem automática — e nenhum {@code max-width} em seletor {@code .container}, que o Bootstrap já
 *       centraliza com margem automática;</li>
 *   <li>vale para todo CSS próprio em {@code META-INF/resources} (fora {@code vendor} e {@code .min.css}) e para os
 *       atributos {@code style="…"} dos templates Qute;</li>
 *   <li>a dívida herdada fica na linha de base por IDENTIDADE (arquivo|seletor|propriedade|valor) e só desce: item
 *       novo reprova, e item da linha de base que deixou de existir também reprova até sair do arquivo.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando cada violação nova com arquivo, seletor, propriedade
 * e valor; reprova quando a linha de base guarda item já corrigido; e reprova quando leu menos alvos do que o
 * mínimo esperado — alvo vazio não é aprovação.</p>
 */
class CssLarguraPorcentagemGuardTest {

    private static final Path ESTATICOS = Path.of("src/main/resources/META-INF/resources");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path LINHA_DE_BASE = Path.of("src/test/resources/regra27-linha-base.txt");

    /** Propriedades em que px é permitido pela regra: borda fina, contorno, sombra e filtro. */
    private static final Pattern PROPRIEDADE_ISENTA = Pattern.compile(
            "^(-webkit-)?(border(-(top|right|bottom|left|block|inline)(-(start|end))?)?(-width)?"
                    + "|outline(-width|-offset)?|box-shadow|text-shadow|filter|backdrop-filter"
                    + "|-webkit-text-stroke(-width)?|column-rule(-width)?)$");

    private static final Pattern PX = Pattern.compile("(?<![\\w.#-])(-?\\d*\\.?\\d+)px\\b");
    private static final Pattern VW_CHEIO = Pattern.compile("\\b100vw\\b");
    private static final Pattern FONTE_REM = Pattern.compile("(?<![\\w.])(\\d*\\.?\\d+)rem\\b");
    private static final Pattern COMENTARIO = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STYLE_ATTR = Pattern.compile("\\sstyle=\"([^\"]*)\"");

    /**
     * Violações de um texto CSS, como identidades {@code arquivo|seletor|propriedade|valor}.
     * Usada nos arquivos reais E nos casos de calibração — o instrumento é o mesmo.
     */
    static List<String> violacoesCss(String arquivo, String css) {
        String limpo = COMENTARIO.matcher(css).replaceAll(m -> m.group().replaceAll("[^\\n]", " "));
        List<String> out = new ArrayList<>();
        List<Integer> pilha = new ArrayList<>();
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            if (c == '{') {
                pilha.add(i);
            } else if (c == '}' && !pilha.isEmpty()) {
                int abre = pilha.remove(pilha.size() - 1);
                String corpo = limpo.substring(abre + 1, i);
                if (corpo.indexOf('{') >= 0) {
                    continue;
                }
                int j = abre - 1;
                while (j >= 0 && "{};".indexOf(limpo.charAt(j)) < 0) {
                    j--;
                }
                String seletor = limpo.substring(j + 1, abre).trim().replaceAll("\\s+", " ");
                if (seletor.startsWith("@")) {
                    continue;
                }
                out.addAll(violacoesDeclaracoes(arquivo, seletor, corpo));
            }
        }
        return out;
    }

    /**
     * Classes que os templates põem no MESMO elemento que {@code container} (ex.: {@code container py-5 lab-page}).
     * Um {@code max-width} nelas centraliza a página igual ao do próprio {@code .container}, porque a margem
     * automática vem do Bootstrap. Preenchida antes da varredura dos CSS.
     */
    private static final Set<String> CLASSES_DO_CONTAINER = new TreeSet<>();

    /** {@code container} como classe inteira — {@code container-fluid} não tem margem automática e não conta. */
    private static final Pattern CLASSE_COM_CONTAINER = Pattern.compile("class=\"([^\"]*(?<![\\w-])container(?![\\w-])[^\"]*)\"");

    static void registrarClassesDoContainer(String html) {
        Matcher m = CLASSE_COM_CONTAINER.matcher(html);
        while (m.find()) {
            for (String c : m.group(1).trim().split("\\s+")) {
                if (c.matches("[a-zA-Z][\\w-]*") && !c.equals("container") && !c.matches("(p|m)[trblxy]?-\\d|mb-\\d|py-\\d")) {
                    CLASSES_DO_CONTAINER.add(c);
                }
            }
        }
    }

    /** O SUJEITO do seletor (o último composto de cada item da lista) é o contêiner da página? */
    private static boolean seletorDoContainer(String seletor) {
        for (String item : seletor.split(",")) {
            String[] partes = item.trim().split("[\\s>+~]+");
            String sujeito = partes[partes.length - 1];
            if (sujeito.matches(".*\\.container(?![\\w-]).*")) {
                return true;
            }
            for (String c : CLASSES_DO_CONTAINER) {
                if (sujeito.matches(".*\\." + Pattern.quote(c) + "(?![\\w-]).*")) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> violacoesDeclaracoes(String arquivo, String seletor, String corpo) {
        List<String> out = new ArrayList<>();
        String maxWidth = null;
        boolean margemAuto = false;
        for (String d : corpo.split(";")) {
            int dois = d.indexOf(':');
            if (dois < 0) {
                continue;
            }
            String prop = d.substring(0, dois).trim().toLowerCase(Locale.ROOT);
            String valor = d.substring(dois + 1).trim().replaceAll("\\s+", " ").replace("!important", "").trim();
            if (prop.isEmpty() || prop.contains("{")) {
                continue;
            }
            String id = arquivo + "|" + seletor + "|" + prop + "|" + valor;
            if (VW_CHEIO.matcher(valor).find()) {
                out.add(id + "  [100vw]");
            }
            if (!PROPRIEDADE_ISENTA.matcher(prop).matches()) {
                Matcher m = PX.matcher(valor);
                while (m.find()) {
                    if (Double.parseDouble(m.group(1)) != 0) {
                        out.add(id + "  [px]");
                        break;
                    }
                }
            }
            // Espaço ENTRE LINHAS em %: a altura da grade é indefinida, o % não entra no cálculo da altura e o
            // conteúdo vaza por cima do rodapé (medido em 22/10/2026: a home vazava 3067 px em 320 px).
            if ((prop.equals("gap") && valor.split("\\s+")[0].endsWith("%"))
                    || (prop.equals("row-gap") && valor.endsWith("%"))
                    || (prop.equals("grid-row-gap") && valor.endsWith("%"))) {
                out.add(id + "  [espaço entre linhas em %]");
            }
            // Piso de leitura: fonte abaixo de .75rem (12 px) — medido em 22/10/2026, 78 regras entre 8 e 11,5 px.
            if (prop.equals("font-size")) {
                Matcher rem = FONTE_REM.matcher(valor);
                while (rem.find()) {
                    if (Double.parseDouble(rem.group(1)) < 0.75) {
                        out.add(id + "  [fonte abaixo de .75rem]");
                        break;
                    }
                }
            }
            if (prop.equals("max-width")) {
                maxWidth = valor;
            }
            if ((prop.equals("margin") || prop.equals("margin-left") || prop.equals("margin-right")
                    || prop.equals("margin-inline")) && valor.matches(".*\\bauto\\b.*")) {
                margemAuto = true;
            }
        }
        if (maxWidth != null && !maxWidth.equals("none") && !maxWidth.equals("100%") && !maxWidth.matches("[0-9.]+ch")) {
            if (margemAuto) {
                out.add(arquivo + "|" + seletor + "|max-width|" + maxWidth + "  [coluna centralizada]");
            } else if (seletorDoContainer(seletor)) {
                out.add(arquivo + "|" + seletor + "|max-width|" + maxWidth + "  [container do Bootstrap centralizado]");
            }
        }
        return out;
    }

    /**
     * Violações nos atributos style dos templates; o seletor da identidade é a linha do template. A classe
     * {@code mx-auto}/{@code m-auto} do Bootstrap conta como margem automática da mesma etiqueta.
     */
    static List<String> violacoesTemplate(String arquivo, String html) {
        List<String> out = new ArrayList<>();
        String[] linhas = html.split("\n", -1);
        for (int n = 0; n < linhas.length; n++) {
            Matcher m = STYLE_ATTR.matcher(linhas[n]);
            while (m.find()) {
                String corpo = m.group(1).replaceAll("\\{[^}]*}", " ");
                Matcher tag = ETIQUETA.matcher(linhas[n]);
                while (tag.find()) {
                    if (tag.start() <= m.start() && m.end() <= tag.end() && MX_AUTO.matcher(tag.group()).find()) {
                        corpo = corpo + ";margin-inline:auto";
                    }
                }
                out.addAll(violacoesDeclaracoes(arquivo, "style@" + (n + 1), corpo));
            }
        }
        return out;
    }

    private static final Pattern ETIQUETA = Pattern.compile("<[a-zA-Z][^>]*>");
    private static final Pattern MX_AUTO = Pattern.compile("\\sclass=\"[^\"]*\\b(mx-auto|m-auto)\\b[^\"]*\"");

    @Test
    void instrumentoDiscriminaDoenteDeLegitimo() {
        // px: o mesmo sinal reprovado em espaçamento e aceito em borda/sombra
        assertEquals(1, violacoesCss("t.css", ".a{padding:12px}").size());
        assertEquals(0, violacoesCss("t.css", ".a{padding:.75rem;border:1px solid #fff;box-shadow:0 2px 4px #000}").size());
        assertEquals(0, violacoesCss("t.css", ".a{margin:0px;outline-offset:2px;border-left-width:3px}").size());
        assertEquals(1, violacoesCss("t.css", ".a{font-size:13px}").size());
        // breakpoint de @media é a exceção da regra; a regra dentro dele continua medida
        assertEquals(0, violacoesCss("t.css", "@media (max-width: 576px){.a{padding:1rem}}").size());
        assertEquals(1, violacoesCss("t.css", "@media (max-width: 576px){.a{gap:8px}}").size());
        // px dentro de nome (classe, id, url) não é unidade
        assertEquals(0, violacoesCss("t.css", ".a{background:url(icone-24px.png)}").size());
        // 100vw
        assertEquals(1, violacoesCss("t.css", ".a{width:100vw}").size());
        // coluna centralizada × medida de leitura
        assertEquals(1, violacoesCss("t.css", ".pagina{max-width:80rem;margin:0 auto}").size());
        assertEquals(0, violacoesCss("t.css", ".texto{max-width:70ch;margin:0 auto}").size());
        assertEquals(0, violacoesCss("t.css", ".img{max-width:100%;margin:0 auto}").size());
        assertEquals(0, violacoesCss("t.css", ".selo{max-width:40%}").size());
        // .container do Bootstrap já tem margem automática; .container-fluid não
        assertEquals(1, violacoesCss("t.css", "body .container{max-width:98%}").size());
        assertEquals(0, violacoesCss("t.css", ".container-fluid{max-width:98%}").size());
        // classe que divide o elemento com .container herda a margem automática do Bootstrap
        registrarClassesDoContainer("<div class=\"container py-5 calib-pagina\">");
        registrarClassesDoContainer("<footer class=\"calib-rodape container-fluid\">");
        assertEquals(0, violacoesCss("t.css", ".calib-rodape{max-width:70rem}").size());
        assertEquals(1, violacoesCss("t.css", ".calib-pagina{max-width:70rem}").size());
        assertEquals(0, violacoesCss("t.css", ".calib-pagina-titulo{max-width:70rem}").size());
        // fronteira: a mesma classe como ANCESTRAL não é o contêiner — o campo dentro dele pode ter limite
        assertEquals(0, violacoesCss("t.css", ".calib-pagina .campo{max-width:20rem}").size());
        assertEquals(0, violacoesCss("t.css", ".container .campo{max-width:20rem}").size());
        // espaço entre linhas em % vaza da grade; entre colunas em % é a regra
        assertEquals(1, violacoesCss("t.css", ".g{gap:3%}").size());
        assertEquals(0, violacoesCss("t.css", ".g{gap:1.25rem 3%}").size());
        assertEquals(1, violacoesCss("t.css", ".g{row-gap:2%}").size());
        assertEquals(0, violacoesCss("t.css", ".g{column-gap:2%}").size());
        // piso de fonte: .65rem reprova, .75rem passa, e o mínimo do clamp conta
        assertEquals(1, violacoesCss("t.css", ".k{font-size:.65rem}").size());
        assertEquals(0, violacoesCss("t.css", ".k{font-size:.75rem}").size());
        assertEquals(1, violacoesCss("t.css", ".k{font-size:clamp(.5rem, 1vw, 1rem)}").size());
        assertEquals(0, violacoesCss("t.css", ".k{font-size:1.25rem;letter-spacing:.05rem}").size());
        // comentário não é código
        assertEquals(0, violacoesCss("t.css", "/* .a{padding:12px} */ .b{padding:1rem}").size());
        // atributo style de template, com expressão Qute dentro
        assertEquals(1, violacoesTemplate("t.html", "<p style=\"width: 140px;\">").size());
        assertEquals(0, violacoesTemplate("t.html", "<p style=\"border-left: 3px solid #f00;{#if x} width:2rem{/if}\">").size());
        // classe mx-auto do Bootstrap + max-width no style é a mesma coluna centralizada; em ch é medida de leitura
        assertEquals(1, violacoesTemplate("t.html", "<div class=\"card mx-auto\" style=\"max-width:48rem;\">").size());
        assertEquals(0, violacoesTemplate("t.html", "<p class=\"small mx-auto\" style=\"max-width:90ch;\">").size());
        assertEquals(0, violacoesTemplate("t.html", "<select class=\"form-select\" style=\"max-width:24rem;\">").size());
    }

    @Test
    void cssETemplatesSeguemARegra27() throws IOException {
        Set<String> atuais = new TreeSet<>();
        int css = 0;
        int html = 0;
        try (Stream<Path> s = Files.walk(TEMPLATES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".html")).toList()) {
                registrarClassesDoContainer(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        assertTrue(CLASSES_DO_CONTAINER.size() > 2, "nenhuma classe acompanhando .container nos templates — o "
                + "instrumento não enxergou o que devia: " + CLASSES_DO_CONTAINER);
        try (Stream<Path> s = Files.walk(ESTATICOS)) {
            for (Path p : s.filter(CssLarguraPorcentagemGuardTest::cssProprio).toList()) {
                css++;
                String rel = ESTATICOS.relativize(p).toString().replace('\\', '/');
                atuais.addAll(violacoesCss(rel, Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        try (Stream<Path> s = Files.walk(TEMPLATES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".html")).toList()) {
                html++;
                String rel = "templates/" + TEMPLATES.relativize(p).toString().replace('\\', '/');
                atuais.addAll(violacoesTemplate(rel, Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(css > 20, "a guarda leu " + css + " CSS — alvo vazio não é aprovação");
        assertTrue(html > 50, "a guarda leu " + html + " templates — alvo vazio não é aprovação");

        Set<String> base = new LinkedHashSet<>();
        if (Files.exists(LINHA_DE_BASE)) {
            for (String l : Files.readAllLines(LINHA_DE_BASE, StandardCharsets.UTF_8)) {
                if (!l.isBlank() && !l.startsWith("#")) {
                    base.add(l.strip());
                }
            }
        }
        List<String> novas = atuais.stream().filter(v -> !base.contains(v)).toList();
        List<String> resolvidas = base.stream().filter(v -> !atuais.contains(v)).toList();
        assertTrue(novas.isEmpty(), "violação NOVA de largura cheia (px só em borda/sombra):\n  "
                + String.join("\n  ", novas));
        assertTrue(resolvidas.isEmpty(), "item da linha de base já corrigido — tire de " + LINHA_DE_BASE + ":\n  "
                + String.join("\n  ", resolvidas));
    }

    /**
     * O tooltip compartilhado é cópia SEM edição da fonte do componente: os 8px da ponte invisível e o
     * calc(100vw - 1rem) do balão de posição fixa são dele, e não mexem no layout da página. Ele fica fora da
     * varredura, e o teste abaixo garante que continua sendo a cópia fiel — editado no projeto, reprova.
     */
    private static final String CANONICO_CSS = "web/css/dica.css";

    private static boolean cssProprio(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.endsWith(".css") && !s.endsWith(".min.css") && !s.contains("/vendor/") && !s.endsWith("/" + CANONICO_CSS);
    }

    @Test
    void tooltipCanonicoEhCopiaFiel() throws Exception {
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        String[][] esperados = {
            {CANONICO_CSS, "3f70f155507ff5e1ee0dec67c6900c89036c5fd3d49330d65861d453373ffc53"},
            {"web/js/dica.js", "3ec55c49cbb8ab15ce8c9b17053aeac27daf972bf9f8421d77b26b6238bf2114"},
        };
        for (String[] e : esperados) {
            // Quebra de linha normalizada para LF: o git do Windows pode entregar CRLF no checkout.
            String conteudo = Files.readString(ESTATICOS.resolve(e[0]), StandardCharsets.UTF_8).replace("\r\n", "\n");
            String h = java.util.HexFormat.of().formatHex(sha.digest(conteudo.getBytes(StandardCharsets.UTF_8)));
            assertEquals(e[1], h, e[0] + " foi editado no projeto: é cópia do componente canônico de tooltip, que se altera na fonte e se recopia");
        }
    }
}
