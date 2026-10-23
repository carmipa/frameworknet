package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapa do site para buscadores — o {@code /sitemap.xml}.
 *
 * <p><b>Propósito de negócio:</b> o sitemap é a lista do que existe para ser
 * encontrado. Entrada que morreu vira 404 no relatório do Google; página nova do
 * menu que não entra na lista simplesmente não é oferecida. Nenhum dos dois
 * quebra o site — só apagam o projeto do índice em silêncio.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) todo item do menu que o {@code robots.txt}
 * deixa aberto está no sitemap; (2) nenhuma URL do sitemap está fechada no
 * {@code robots.txt} — sitemap e robots em contradição é resolvido pelo buscador
 * contra o site; (3) toda URL listada responde 200; (4) atrás do proxy reverso a
 * URL sai com o host e o esquema externos, senão o Google descarta as entradas
 * como submissão cruzada.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste nomeia a página que faltou,
 * a que sobrou ou a que respondeu diferente de 200. As varreduras do menu e do
 * sitemap falham fechadas: lista vazia reprova, em vez de aprovar por cegueira.
 * Fora da raiz do projeto o cruzamento com o menu é ignorado via
 * {@code Assumptions}, nunca aprovado.</p>
 */
@QuarkusTest
@DisplayName("sitemap.xml: o que o site oferece para ser encontrado")
class SitemapHttpTest {

    private static final Path MENU =
            Path.of("src", "main", "resources", "templates", "shared", "main_menu.html");

    private static final Pattern HREF_INTERNO = Pattern.compile("href=\"(/[^\"#?]*)\"");

    private static final Pattern LOC = Pattern.compile("<loc>([^<]+)</loc>");

    @Test
    @DisplayName("responde 200 como XML de sitemap")
    void respondeComoSitemap() {
        given()
                .when().get("/sitemap.xml")
                .then()
                .statusCode(200)
                .contentType(containsString("xml"))
                .body(containsString("http://www.sitemaps.org/schemas/sitemap/0.9"))
                .body(containsString("<loc>"));
    }

    @Test
    @DisplayName("sem host canônico configurado, a URL segue a requisição")
    void semConfiguracaoAUrlSegueARequisicao() {
        // O outro lado da moeda do SitemapBaseUrlHttpTest: em desenvolvimento não
        // há host público, e apontar para o domínio de produção daria um sitemap
        // que descreve outro site.
        String xml = given().when().get("/sitemap.xml").then().statusCode(200).extract().asString();
        assertTrue(xml.contains("<loc>http://localhost"),
                () -> "Sem framework.site.base-url a URL tem de vir do UriInfo.\n" + xml);
    }

    @Test
    @DisplayName("toda página do menu aberta no robots.txt está no sitemap")
    void paginasDoMenuAbertasEstaoNoSitemap() {
        Set<String> menu = linksDoMenu();
        assertFalse(menu.isEmpty(), "A leitura do menu não achou link nenhum — instrumento cego.");

        List<String> disallows = disallowsDoGrupoGoogle();
        List<String> caminhos = caminhosDoSitemap();
        List<String> faltando = new ArrayList<>();

        for (String link : menu) {
            boolean fechadoParaRobo = disallows.stream().anyMatch(link::startsWith) || RobotsTxt.areaAutenticada(link);
            if (!fechadoParaRobo && !caminhos.contains(link)) {
                faltando.add(link);
            }
        }

        assertTrue(faltando.isEmpty(), () -> "Página do menu fora do sitemap — o buscador não a "
                + "oferece e ninguém percebe:\n  - " + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("nenhuma URL do sitemap está fechada no robots.txt")
    void sitemapNaoContradizORobots() {
        List<String> disallows = disallowsDoGrupoGoogle();
        assertFalse(disallows.isEmpty(), "O robots.txt não devolveu regra nenhuma — instrumento cego.");

        List<String> contradicoes = new ArrayList<>();
        for (String caminho : caminhosDoSitemap()) {
            if (disallows.stream().anyMatch(caminho::startsWith)) {
                contradicoes.add(caminho);
            }
        }

        assertTrue(contradicoes.isEmpty(), () -> "O sitemap oferece o que o robots.txt fecha; "
                + "o buscador resolve a contradição contra o site.\n  - "
                + String.join("\n  - ", contradicoes));
    }

    @Test
    @DisplayName("toda URL listada responde 200 sem redirecionar — sitemap não aponta para página morta")
    void todaUrlDoSitemapResponde() {
        List<String> caminhos = caminhosDoSitemap();
        assertFalse(caminhos.isEmpty(), "O sitemap veio vazio — instrumento cego, não site vazio.");

        List<String> quebradas = new ArrayList<>();
        for (String caminho : caminhos) {
            // Sem seguir redirecionamento: o cliente HTTP segue 301/302 por padrão, e uma URL do sitemap
            // que redireciona chegaria aqui como 200 da página de destino. O buscador trata URL de
            // sitemap com redirecionamento como erro de cobertura.
            int status = given().redirects().follow(false).when().get(caminho).then().extract().statusCode();
            if (status != 200) {
                quebradas.add(caminho + " respondeu " + status);
            }
        }

        assertTrue(quebradas.isEmpty(), () -> String.join("\n  - ", quebradas));
    }

    /**
     * F26: /laboratorios/aneis-e-rede era pública e linkada, mas fora do sitemap — o teste só cruzava
     * os links do MENU. Esta guarda cobre a classe: todo link interno simples encontrado nas páginas
     * do sitemap (sem query/âncora/extensão, fora de API, export e áreas fechadas) está no sitemap.
     */
    @Test
    @DisplayName("página pública alcançável por link está no sitemap, mesmo fora do menu")
    void paginaAlcancavelPorLinkEstaNoSitemap() {
        List<String> caminhos = caminhosDoSitemap();
        assertFalse(caminhos.isEmpty(), "O sitemap veio vazio — instrumento cego.");
        Set<String> noSitemap = new LinkedHashSet<>(caminhos);
        Pattern href = Pattern.compile("href=\"(/[a-z0-9][a-z0-9/-]*)\"");
        // Exclusões vêm do próprio robots.txt (fonte única): o que está fechado para robôs não
        // pertence ao sitemap — ex.: /informacoes, que dispara consulta externa a cada GET.
        // Só o grupo "User-agent: *" (o dos buscadores): os grupos de robôs de IA têm "Disallow: /",
        // que excluiria TUDO e deixaria esta guarda cega — aconteceu na primeira versão.
        Set<String> fechadas = new LinkedHashSet<>();
        boolean noGrupoGeral = false;
        boolean lendoAgentes = false;
        for (String linha : given().when().get("/robots.txt").then().extract().asString().split("\\R")) {
            String l = linha.strip();
            if (l.startsWith("User-agent:")) {
                String agente = l.substring("User-agent:".length()).strip();
                noGrupoGeral = (lendoAgentes && noGrupoGeral) || "*".equals(agente);
                lendoAgentes = true;
                continue;
            }
            lendoAgentes = false;
            if (noGrupoGeral && l.startsWith("Disallow:")) {
                String prefixo = l.substring("Disallow:".length()).strip();
                if (!prefixo.isEmpty() && !"/".equals(prefixo)) {
                    fechadas.add(prefixo);
                }
            }
        }
        assertFalse(fechadas.isEmpty(), "robots.txt sem Disallow lido: instrumento cego");
        Set<String> faltando = new java.util.TreeSet<>();
        int links = 0;
        for (String pagina : caminhos) {
            Matcher m = href.matcher(given().when().get(pagina).then().extract().asString());
            while (m.find()) {
                String alvo = m.group(1).replaceAll("/$", "");
                links++;
                boolean excluida = alvo.isEmpty() || alvo.contains("/api/") || alvo.contains("/export")
                        || fechadas.stream().anyMatch(alvo::startsWith) || RobotsTxt.areaAutenticada(alvo);
                if (!excluida && !noSitemap.contains(alvo)) {
                    faltando.add(alvo + "  (linkada em " + pagina + ")");
                }
            }
        }
        assertTrue(links > 100, "só " + links + " links lidos: instrumento cego");
        assertTrue(faltando.isEmpty(), () -> "Fora do sitemap:\n  - " + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("todo aprofundamento de protocolo registrado está no sitemap (guarda anti-regressão)")
    void aprofundamentosRegistradosEstaoNoSitemap() {
        List<String> caminhos = caminhosDoSitemap();
        assertFalse(caminhos.isEmpty(), "O sitemap veio vazio — instrumento cego.");

        List<String> faltando = new ArrayList<>();
        for (AprofundamentoProtocolo item : AprofundamentoProtocolo.disponiveis()) {
            if (!caminhos.contains(item.rota())) {
                faltando.add(item.rota());
            }
        }

        assertFalse(AprofundamentoProtocolo.disponiveis().isEmpty(),
                "Nenhum aprofundamento registrado — instrumento cego, não fonte vazia.");
        assertTrue(faltando.isEmpty(), () -> "Aprofundamento registrado e fora do sitemap — conteúdo "
                + "público que o buscador não vai indexar:\n  - " + String.join("\n  - ", faltando));
    }

    // --- leitura dos artefatos ----------------------------------------------

    /** Caminhos do sitemap, já sem o prefixo de host. */
    private static List<String> caminhosDoSitemap() {
        String xml = given().when().get("/sitemap.xml").then().statusCode(200).extract().asString();
        List<String> caminhos = new ArrayList<>();
        Matcher matcher = LOC.matcher(xml);
        while (matcher.find()) {
            String url = matcher.group(1);
            int inicio = url.indexOf("://");
            int barra = inicio < 0 ? -1 : url.indexOf('/', inicio + 3);
            caminhos.add(barra < 0 ? "/" : url.substring(barra));
        }
        return caminhos;
    }

    /**
     * Regras do grupo do Googlebot. O critério de leitura mora em
     * {@link RobotsTxt}: reimplementar aqui faria as duas leituras divergirem, e
     * a divergência apareceria como sitemap "correto" contra robots "correto".
     */
    private static List<String> disallowsDoGrupoGoogle() {
        String corpo = given().when().get("/robots.txt").then().statusCode(200).extract().asString();
        return RobotsTxt.grupos(corpo).getOrDefault("googlebot", List.of());
    }

    private static Set<String> linksDoMenu() {
        Assumptions.assumeTrue(Files.isRegularFile(MENU),
                "Template do menu não encontrado a partir de " + Path.of("").toAbsolutePath());
        try {
            Set<String> links = new LinkedHashSet<>();
            Matcher matcher = HREF_INTERNO.matcher(Files.readString(MENU));
            while (matcher.find()) {
                links.add(matcher.group(1));
            }
            return links;
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + MENU, ex);
        }
    }
}
