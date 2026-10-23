package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.framework.net.web.domain.PaginasPublicas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Estrutura das páginas públicas como o servidor as entrega: títulos, {@code <main>}, imagens, título da
 * aba, Open Graph e a resposta a quem navega direto ou pelo htmx.
 *
 * <p><b>Propósito de negócio:</b> estes defeitos não quebram a tela de quem enxerga — quebram a de quem
 * usa leitor de tela (que navega pelos níveis de título) e a leitura que o buscador faz da página. Um
 * {@code <h6>} logo abaixo do {@code <h1>} deixa o leitor de tela anunciar "nível 6" no primeiro
 * subtítulo; uma imagem sem {@code width}/{@code height} empurra o texto quando carrega; dois títulos de
 * aba iguais fazem o buscador tratar páginas diferentes como repetidas; e uma URL que responde corpos
 * diferentes sem {@code Vary} deixa um cache entregar o corpo errado. Por isso cada um tem guarda que
 * reprova o build, e não lembrete.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) toda página pública tem exatamente um {@code <h1>}, um
 * {@code <main>} e nenhum salto de nível descendo; (2) fragmento devolvido ao htmx não salta nível
 * internamente; (3) toda {@code <img>} tem {@code alt}, {@code width} e {@code height}, e a imagem
 * principal ({@code fetchpriority="high"}) nunca é {@code lazy}; (4) cada página tem título de aba
 * próprio; (5) {@code og:url} é o próprio canonical e {@code og:image} é absoluta; (6) a navegação direta
 * recebe documento completo, e a resposta que muda conforme {@code Accept}/{@code HX-Request} declara
 * {@code Vary}.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cada teste nomeia a rota e o trecho ofensor. Falha
 * <b>fechada</b>: lista de páginas vazia, página que não renderizou o layout ou leitor que não passa na
 * própria calibração reprovam — nunca aprovam por não ter olhado nada.</p>
 */
@QuarkusTest
@DisplayName("Estrutura das páginas públicas: títulos, imagens, head e negociação")
class SemanticaPaginaPublicaHttpTest {

    /** Marca do {@code shared/base.html}: prova que a resposta passou pelo layout. */
    private static final String MARCA_DO_LAYOUT = "aed-app";

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");

    private static final Pattern TITULO_DA_ABA = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern CANONICAL = Pattern.compile("<link rel=\"canonical\" href=\"([^\"]*)\">");
    private static final Pattern OG_URL = Pattern.compile("<meta property=\"og:url\" content=\"([^\"]*)\">");
    private static final Pattern OG_IMAGE = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]*)\">");
    private static final Pattern COMENTARIO_QUTE = Pattern.compile("\\{!.*?!}", Pattern.DOTALL);

    @Test
    @DisplayName("o leitor de estrutura distingue salto de nível de subida legítima")
    void leitorCalibrado() {
        String doente = "<h1>a</h1><h1>b</h1><h4>c</h4><img src=\"x.png\">";
        String sao = "<main><h1>a</h1><h2>b</h2><h3>c</h3><h2>d</h2><img src=\"x.png\" alt=\"\" width=\"1\" height=\"1\"></main>";
        // Fronteira: subir vários níveis de uma vez (h4 -> h2) é a mesma forma de "pular" degraus que o
        // defeito, mas é estrutura correta — fim de subseção. O leitor tem de aceitar.
        String subida = "<h1>a</h1><h2>b</h2><h3>c</h3><h4>d</h4><h2>e</h2>";
        String scriptComTitulo = "<h1>a</h1><script>const t = '<h4>falso</h4>';</script><h2>b</h2>";

        assertEquals(List.of("h1->h4"), HtmlSemantica.saltos(HtmlSemantica.niveis(doente)));
        assertEquals(0, HtmlSemantica.quantidadeDeMain(doente));
        assertTrue(HtmlSemantica.saltos(HtmlSemantica.niveis(sao)).isEmpty());
        assertEquals(1, HtmlSemantica.quantidadeDeMain(sao));
        assertTrue(HtmlSemantica.saltos(HtmlSemantica.niveis(subida)).isEmpty(),
                "Subir de h4 para h2 é estrutura legítima e não pode ser acusado como salto.");
        assertTrue(HtmlSemantica.saltos(HtmlSemantica.niveis(scriptComTitulo)).isEmpty(),
                "Título dentro de string de script não é título da página.");
        assertEquals(List.of("inicio->h3"), HtmlSemantica.saltos(List.of(3, 4)));
        assertTrue(HtmlSemantica.saltosInternos(List.of(3, 4, 3)).isEmpty());

        String semDimensao = HtmlSemantica.imagens(doente).get(0);
        String completa = HtmlSemantica.imagens(sao).get(0);
        assertFalse(HtmlSemantica.temAtributo(semDimensao, "alt"));
        assertFalse(HtmlSemantica.temAtributo(semDimensao, "width"));
        assertTrue(HtmlSemantica.temAtributo(completa, "alt"), "alt=\"\" é o correto na imagem decorativa.");
        assertTrue(HtmlSemantica.temAtributo(completa, "height"));
        assertFalse(HtmlSemantica.temAtributo("<img data-width=\"1\" src=\"x\">", "width"),
                "data-width não é width.");
    }

    @Test
    @DisplayName("toda página pública tem um h1, um main e nenhum salto de nível")
    void paginaTemUmH1UmMainESemSalto() {
        List<String> defeitos = new ArrayList<>();
        for (String rota : rotasComLayout()) {
            String html = corpo(rota);
            List<Integer> niveis = HtmlSemantica.niveis(html);
            long h1 = niveis.stream().filter(n -> n == 1).count();
            if (h1 != 1) {
                defeitos.add(rota + " tem " + h1 + " <h1>");
            }
            List<String> saltos = HtmlSemantica.saltos(niveis);
            if (!saltos.isEmpty()) {
                defeitos.add(rota + " salta nível: " + saltos);
            }
            long main = HtmlSemantica.quantidadeDeMain(html);
            if (main != 1) {
                defeitos.add(rota + " tem " + main + " <main>");
            }
        }
        assertTrue(defeitos.isEmpty(), () -> "Estrutura de títulos quebrada:\n" + String.join("\n", defeitos));
    }

    @Test
    @DisplayName("fragmento devolvido ao htmx não salta nível de título")
    void fragmentoNaoSaltaNivel() throws IOException {
        List<Path> partials;
        try (Stream<Path> arvore = Files.walk(TEMPLATES)) {
            partials = arvore
                    .filter(p -> p.toString().replace('\\', '/').contains("/partials/"))
                    .filter(p -> p.toString().endsWith(".html"))
                    .toList();
        }
        assertFalse(partials.isEmpty(), "Nenhum fragmento encontrado em " + TEMPLATES.toAbsolutePath()
                + ": a guarda não verificou nada.");

        List<String> defeitos = new ArrayList<>();
        for (Path partial : partials) {
            String texto = COMENTARIO_QUTE.matcher(Files.readString(partial)).replaceAll("");
            List<String> saltos = HtmlSemantica.saltosInternos(HtmlSemantica.niveis(texto));
            if (!saltos.isEmpty()) {
                defeitos.add(TEMPLATES.relativize(partial) + " " + saltos);
            }
        }
        assertTrue(defeitos.isEmpty(), () -> "Fragmento com salto de nível:\n" + String.join("\n", defeitos));
    }

    @Test
    @DisplayName("toda imagem tem alt e dimensões, e a imagem principal não é lazy")
    void imagensTemAltDimensoesEPrincipalSemLazy() {
        List<String> defeitos = new ArrayList<>();
        int principais = 0;
        for (String rota : rotasComLayout()) {
            for (String img : HtmlSemantica.imagens(corpo(rota))) {
                String curta = img.replaceAll("\\s+", " ");
                if (!HtmlSemantica.temAtributo(img, "alt")) {
                    defeitos.add(rota + " sem alt: " + curta);
                }
                if (!HtmlSemantica.temAtributo(img, "width") || !HtmlSemantica.temAtributo(img, "height")) {
                    defeitos.add(rota + " sem width/height: " + curta);
                }
                if (img.contains("fetchpriority=\"high\"")) {
                    principais++;
                    if (img.contains("loading=\"lazy\"")) {
                        defeitos.add(rota + " imagem principal com loading=lazy: " + curta);
                    }
                }
            }
        }
        // Controle positivo: a home e as páginas de ferramenta declaram imagem principal. Se nenhuma
        // apareceu, o critério de "principal" deixou de enxergar e a regra do lazy virou vacuidade.
        assertTrue(principais > 0, "Nenhuma imagem principal (fetchpriority=\"high\") encontrada.");
        assertTrue(defeitos.isEmpty(), () -> "Imagem fora do padrão:\n" + String.join("\n", defeitos));
    }

    @Test
    @DisplayName("cada página pública tem título de aba próprio")
    void tituloDaAbaNaoSeRepete() {
        Map<String, List<String>> porTitulo = new LinkedHashMap<>();
        for (String rota : rotasComLayout()) {
            Matcher m = TITULO_DA_ABA.matcher(corpo(rota));
            String titulo = m.find() ? m.group(1).strip() : "(ausente)";
            porTitulo.computeIfAbsent(titulo, chave -> new ArrayList<>()).add(rota);
        }
        List<String> problemas = porTitulo.entrySet().stream()
                .filter(e -> e.getValue().size() > 1 || e.getKey().isBlank() || "(ausente)".equals(e.getKey()))
                .map(e -> "\"" + e.getKey() + "\" em " + e.getValue())
                .toList();
        assertTrue(problemas.isEmpty(), () -> "Título de aba ausente ou repetido:\n" + String.join("\n", problemas));
    }

    @Test
    @DisplayName("og:url é o próprio canonical e og:image é absoluta")
    void openGraphCoerenteComCanonical() {
        List<String> defeitos = new ArrayList<>();
        for (String rota : rotasComLayout()) {
            String html = corpo(rota);
            String canonical = primeiro(CANONICAL, html);
            String ogUrl = primeiro(OG_URL, html);
            String ogImage = primeiro(OG_IMAGE, html);
            if (canonical == null || !canonical.equals(ogUrl)) {
                defeitos.add(rota + " og:url=" + ogUrl + " canonical=" + canonical);
            }
            if (ogImage == null || !ogImage.startsWith("http")) {
                defeitos.add(rota + " og:image não absoluta: " + ogImage);
            }
        }
        assertTrue(defeitos.isEmpty(), () -> "Open Graph incoerente:\n" + String.join("\n", defeitos));
    }

    @Test
    @DisplayName("navegação direta recebe documento completo; corpo que muda com HX-Request declara Vary")
    void navegacaoDiretaRecebeDocumentoCompleto() {
        List<String> defeitos = new ArrayList<>();
        for (String rota : rotasComLayout()) {
            Response direto = given().accept("text/html").when().get(rota);
            String corpo = direto.asString();
            if (!corpo.stripLeading().regionMatches(true, 0, "<!DOCTYPE html>", 0, 15) || !corpo.contains("<head")) {
                defeitos.add(rota + " sem documento completo na navegação direta");
                continue;
            }
            Response htmx = given().accept("text/html").header("HX-Request", "true").when().get(rota);
            String vary = String.valueOf(direto.getHeader("Vary")) + "|" + htmx.getHeader("Vary");
            if (!htmx.asString().equals(corpo) && !vary.toLowerCase().contains("hx-request")) {
                defeitos.add(rota + " responde outro corpo ao htmx sem Vary: HX-Request");
            }
        }
        assertTrue(defeitos.isEmpty(), () -> String.join("\n", defeitos));
    }

    @Test
    @DisplayName("resposta de erro negociada declara Vary: Accept, HX-Request; página 200 não fragmenta o cache")
    void erroNegociadoDeclaraVary() {
        Response navegacao = given().accept("text/html").when().get("/rota-que-nao-existe-estrutura");
        Response maquina = given().accept("application/json").when().get("/rota-que-nao-existe-estrutura");
        assertEquals(404, navegacao.statusCode());
        // Controle positivo: os dois corpos têm de ser de fato diferentes, senão o Vary não protege nada
        // e o teste estaria provando um cabeçalho sem motivo.
        assertFalse(navegacao.asString().equals(maquina.asString()),
                "Navegação e cliente de máquina receberam o mesmo corpo; a negociação mudou.");
        String vary = String.valueOf(navegacao.getHeader("Vary")).toLowerCase();
        assertTrue(vary.contains("accept") && vary.contains("hx-request"),
                () -> "Erro negociado sem Vary completo: " + navegacao.getHeader("Vary"));

        String varyPagina = String.valueOf(given().accept("text/html").when().get("/portas").getHeader("Vary"))
                .toLowerCase();
        assertFalse(varyPagina.contains("hx-request"),
                () -> "Página pública 200 não muda com HX-Request e não deve declarar Vary por ele: " + varyPagina);
    }

    private static List<String> rotasComLayout() {
        List<String> rotas = PaginasPublicas.rotas();
        assertFalse(rotas.isEmpty(), "A lista de páginas públicas está vazia: a guarda não verificou nada.");
        String home = corpo("/");
        assertTrue(home.contains(MARCA_DO_LAYOUT), "A home não renderizou o layout; as guardas não valem.");
        return rotas;
    }

    private static String corpo(String rota) {
        return given().accept("text/html").when().get(rota).then().statusCode(200).extract().asString();
    }

    private static String primeiro(Pattern padrao, String html) {
        Matcher m = padrao.matcher(html);
        return m.find() ? m.group(1) : null;
    }
}
