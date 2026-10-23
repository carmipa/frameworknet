package org.framework.net.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura da estrutura de um HTML já renderizado: títulos, {@code <main>} e imagens.
 *
 * <p><b>Propósito de negócio:</b> as guardas de página pública precisam perguntar sempre a mesma coisa
 * ao HTML que o servidor entregou — quantos {@code <h1>}, em que ordem vêm os níveis de título, se há
 * {@code <main>}, quais {@code <img>} existem. Um leitor só, usado por todas as guardas e calibrado
 * contra casos conhecidos, evita que cada teste reimplemente o critério e divirja dos outros.</p>
 *
 * <p><b>Invariantes do domínio:</b> comentário HTML, {@code <script>} e {@code <style>} são ignorados —
 * um {@code <h2>} dentro de uma string de JavaScript não é título da página; um salto de nível é
 * descer mais de um degrau de uma vez ({@code h2 -> h4}); subir qualquer número de degraus
 * ({@code h4 -> h2}) é estrutura legítima e não conta; o primeiro título da página tem de ser
 * {@code <h1>}.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> não lança; HTML vazio devolve zero títulos e zero imagens,
 * e cabe à guarda tratar zero como reprovação quando a página deveria ter conteúdo.</p>
 */
final class HtmlSemantica {

    private static final Pattern COMENTARIO = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT = Pattern.compile("<script\\b.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE = Pattern.compile("<style\\b.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITULO = Pattern.compile("<h([1-6])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIN = Pattern.compile("<main[\\s>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private HtmlSemantica() {
    }

    /** O HTML sem comentário, script e style — o que o leitor de tela e o buscador tratam como conteúdo. */
    static String conteudo(String html) {
        String semComentario = COMENTARIO.matcher(html).replaceAll("");
        String semScript = SCRIPT.matcher(semComentario).replaceAll("");
        return STYLE.matcher(semScript).replaceAll("");
    }

    /** Os níveis de título na ordem do documento. */
    static List<Integer> niveis(String html) {
        List<Integer> niveis = new ArrayList<>();
        Matcher m = TITULO.matcher(conteudo(html));
        while (m.find()) {
            niveis.add(Integer.parseInt(m.group(1)));
        }
        return niveis;
    }

    /**
     * Os saltos de nível, como {@code "h2->h4"}. O primeiro título diferente de {@code h1} aparece como
     * {@code "inicio->hN"}.
     */
    static List<String> saltos(List<Integer> niveis) {
        List<String> saltos = new ArrayList<>();
        if (!niveis.isEmpty() && niveis.get(0) != 1) {
            saltos.add("inicio->h" + niveis.get(0));
        }
        for (int i = 1; i < niveis.size(); i++) {
            if (niveis.get(i) > niveis.get(i - 1) + 1) {
                saltos.add("h" + niveis.get(i - 1) + "->h" + niveis.get(i));
            }
        }
        return saltos;
    }

    /** Saltos dentro de um trecho que não começa no topo da página (fragmento): só os internos contam. */
    static List<String> saltosInternos(List<Integer> niveis) {
        List<String> saltos = new ArrayList<>();
        for (int i = 1; i < niveis.size(); i++) {
            if (niveis.get(i) > niveis.get(i - 1) + 1) {
                saltos.add("h" + niveis.get(i - 1) + "->h" + niveis.get(i));
            }
        }
        return saltos;
    }

    static long quantidadeDeMain(String html) {
        return MAIN.matcher(conteudo(html)).results().count();
    }

    static List<String> imagens(String html) {
        return IMG.matcher(conteudo(html)).results().map(achado -> achado.group()).toList();
    }

    static boolean temAtributo(String tag, String atributo) {
        return Pattern.compile("\\s" + Pattern.quote(atributo) + "\\s*=", Pattern.CASE_INSENSITIVE)
                .matcher(tag).find();
    }
}
