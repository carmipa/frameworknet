package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.academia.trilha.domain.CatalogoTrilha;
import org.framework.net.academia.trilha.domain.Nivel;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landing, documentação e sonda de saúde.
 *
 * <p><b>Propósito de negócio:</b> a home é a vitrine — módulo entregue que não
 * aparece nela é módulo que ninguém encontra. E a página de documentação renderiza
 * o README técnico completo, então uma seção que deixou de existir no arquivo
 * some silenciosamente da documentação publicada.</p>
 *
 * <p><b>Invariantes do domínio:</b> todo módulo do menu principal tem bloco
 * correspondente na landing, no grupo de mesmo nome e com o mesmo ícone do menu;
 * nenhum selo "NOVO" escrito à mão (não expira); o que os cartões dizem existir
 * (níveis da Academia, aprofundamentos de Protocolos) existe nos catálogos; a
 * documentação renderizada contém as seções que descrevem os módulos; a sonda
 * {@code /health} responde e não é registrada na telemetria.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cada teste reprova nomeando o grupo, a
 * rota, o glifo ou o termo que divergiu.</p>
 */
@QuarkusTest
@DisplayName("Home, documentação e sonda de saúde")
class HomeEDocumentacaoHttpTest {

    /**
     * Itens do menu principal que precisam de bloco na landing.
     *
     * <p>Não inclui {@code /} — Início é a própria landing e não vira bloco. A
     * home também expõe blocos que não estão no menu ({@code /informacoes},
     * {@code /admin/login}), o que é aceito: menu ⊆ home, não igualdade.</p>
     */
    private static final List<String> MODULOS_DO_MENU = List.of(
            "/analise", "/calculadora", "/ipv6/analise", "/ipv6", "/ipv6/resolucao",
            "/laboratorios", "/academia", "/portas", "/protocolos", "/certificados",
            "/camadas", "/criptografia", "/wifi", "/resolucao-problemas",
            "/localizacao", "/trafego", "/ferramentas", "/diagnostico", "/seguranca", "/telemetria",
            "/documentacao", "/sobre");

    @Test
    @DisplayName("a landing tem bloco para cada módulo, incluindo a Calculadora")
    void homeExpoeTodosOsModulos() {
        String html = given().when().get("/").then().statusCode(200).extract().asString();

        for (String rota : MODULOS_DO_MENU) {
            assertTrue(html.contains("href=\"" + rota + "\""),
                    "A home não tem bloco para " + rota);
        }
        assertTrue(html.contains("href=\"/informacoes\""),
                "O bloco de GeoIP existe só na home e deve continuar lá");
        assertTrue(html.contains("Calculadora de Sub-redes"),
                "O bloco da Calculadora deveria estar na landing");
        assertTrue(html.contains("FLSM / Sub-redes") && html.contains("VLAN 802.1Q"),
                "O hero deveria anunciar os recursos novos");
    }

    @Test
    @DisplayName("regressão: módulo novo no menu sem bloco na home quebra o build")
    void menuEHomeNaoDivergem() {
        String home = given().when().get("/").then().statusCode(200).extract().asString();

        // A invariante é de CONTENÇÃO, não de contagem: todo item de menu (fora
        // Início, que é a própria landing) precisa de bloco. A home pode ter blocos
        // a mais — /informacoes e /admin/login existem só aqui.
        // O contador serve para pegar item de menu que nunca foi listado abaixo.
        int itensDeMenu = contarOcorrencias(home, "class=\"aed-nav-link ");
        assertEquals(MODULOS_DO_MENU.size() + 1, itensDeMenu,
                "O menu tem " + itensDeMenu + " item(ns), mas este teste conhece "
                        + MODULOS_DO_MENU.size() + " + Início. Um item novo entrou no menu: "
                        + "adicione o bloco na landing e a rota em MODULOS_DO_MENU.");

        for (String rota : MODULOS_DO_MENU) {
            assertTrue(home.contains("href=\"" + rota + "\""),
                    "Item de menu sem bloco na landing: " + rota);
        }
    }

    @Test
    void documentacaoRenderizaAsSecoesDosModulos() {
        given()
                .when().get("/documentacao")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Calculadora de Sub-redes e VLANs"))
                .body(containsString("Módulos e Rotas"))
                .body(containsString("/calculadora/api/dividir"))
                .body(containsString("framework.calculadora.max-linhas"))
                .body(containsString("Dataset público"))
                .body(containsString("Correlação dos eventos de negócio"))
                .body(containsString("ArquiteturaCamadasTest"))
                .body(containsString("Academia: fatias, peers e kernel"))
                .body(containsString("TelemetriaAcademiaPort"));
    }

    @Test
    @DisplayName("a sonda de saúde responde e não polui a telemetria")
    void sondaDeSaudeResponde() {
        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body("status", org.hamcrest.CoreMatchers.is("UP"));
    }

    @Test
    @DisplayName("a sonda não recebe cabeçalho de correlação — sinal de que não foi registrada")
    void sondaNaoEntraNaTelemetria() {
        String traceId = given()
                .when().get("/health")
                .then().statusCode(200)
                .extract().header("X-Trace-Id");

        // O filtro de telemetria é quem devolve X-Trace-Id. Ausência do cabeçalho
        // prova que a requisição não passou pelo registro — sem isso, o healthcheck
        // do container voltaria a gerar ~2.880 eventos por dia.
        assertEquals(null, traceId,
                "A sonda /health não deveria ser registrada na telemetria");
    }

    /** Um grupo do menu ou da Início: o glifo do título e, por rota, o glifo do item ou do cartão. */
    record Grupo(String glifo, Map<String, String> rotas) {
    }

    private static final Pattern GRUPO_DO_MENU = Pattern.compile(
            "<div class=\"dropdown aed-nav-drop\">\\s*<button[^>]*>\\s*<span[^>]*translate=\"no\">([a-z0-9_]+)</span>"
                    + "\\s*<span>([^<]+)</span>(.*?)</div>\\s*</div>", Pattern.DOTALL);
    private static final Pattern ITEM_DO_MENU = Pattern.compile(
            "<a href=\"([^\"]+)\"[^>]*class=\"aed-nav-link[^\"]*\"[^>]*>\\s*<span[^>]*translate=\"no\">([a-z0-9_]+)</span>");
    private static final Pattern GRUPO_DA_HOME = Pattern.compile(
            "<section class=\"home-group\"[^>]*>\\s*<h2[^>]*>\\s*<span[^>]*translate=\"no\">([a-z0-9_]+)</span>"
                    + "\\s*([^<]+?)\\s*</h2>(.*?)</section>", Pattern.DOTALL);
    private static final Pattern CARTAO_DA_HOME = Pattern.compile(
            "<span class=\"home-module-icon[^\"]*\"[^>]*>([a-z0-9_]+)</span>\\s*<h3[^>]*>\\s*<a href=\"([^\"]+)\"");

    static Map<String, Grupo> gruposDoMenu(String html) {
        Map<String, Grupo> grupos = new LinkedHashMap<>();
        Matcher m = GRUPO_DO_MENU.matcher(html);
        while (m.find()) {
            grupos.put(m.group(2).strip(), new Grupo(m.group(1), pares(ITEM_DO_MENU, m.group(3), 1, 2)));
        }
        return grupos;
    }

    static Map<String, Grupo> gruposDaHome(String html) {
        Map<String, Grupo> grupos = new LinkedHashMap<>();
        Matcher m = GRUPO_DA_HOME.matcher(html);
        while (m.find()) {
            grupos.put(m.group(2).strip(), new Grupo(m.group(1), pares(CARTAO_DA_HOME, m.group(3), 2, 1)));
        }
        return grupos;
    }

    private static Map<String, String> pares(Pattern padrao, String trecho, int chave, int valor) {
        Map<String, String> pares = new LinkedHashMap<>();
        Matcher m = padrao.matcher(trecho);
        while (m.find()) {
            pares.put(m.group(chave), m.group(valor));
        }
        return pares;
    }

    /**
     * Onde a Início diverge do menu.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> quem conhece um módulo pelo menu acha o mesmo módulo no grupo de mesmo nome da
     * Início, com o mesmo ícone (mesmo conceito, mesmo glifo).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> cada grupo do menu existe na Início com o mesmo glifo; cada rota do grupo do
     * menu está no grupo de mesmo nome; cada cartão usa o glifo do item de menu da mesma rota (também os itens soltos
     * do menu, passados em {@code soltos}). Cartão a mais no grupo da Início é legítimo (ex.: GeoIP em Operações).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> devolve uma linha por divergência; lista vazia quando batem.</p>
     */
    static List<String> divergencias(Map<String, Grupo> menu, Map<String, String> soltos, Map<String, Grupo> home) {
        List<String> defeitos = new ArrayList<>();
        Map<String, String> cartoes = new LinkedHashMap<>();
        home.values().forEach(g -> cartoes.putAll(g.rotas()));
        menu.forEach((nome, grupo) -> {
            Grupo daHome = home.get(nome);
            if (daHome == null) {
                defeitos.add("grupo do menu sem grupo na Início: " + nome);
                return;
            }
            if (!daHome.glifo().equals(grupo.glifo())) {
                defeitos.add(nome + ": ícone do grupo " + daHome.glifo() + ", no menu " + grupo.glifo());
            }
            grupo.rotas().keySet().stream().filter(r -> !daHome.rotas().containsKey(r))
                    .forEach(r -> defeitos.add(nome + ": " + r + " fora do grupo na Início"));
        });
        Map<String, String> glifoDoMenu = new LinkedHashMap<>(soltos);
        menu.values().forEach(g -> glifoDoMenu.putAll(g.rotas()));
        glifoDoMenu.forEach((rota, glifo) -> {
            String doCartao = cartoes.get(rota);
            if (doCartao != null && !doCartao.equals(glifo)) {
                defeitos.add(rota + ": cartão com " + doCartao + ", menu com " + glifo);
            }
        });
        return defeitos;
    }

    @Test
    @DisplayName("os grupos da Início seguem os do menu: mesmo nome, mesmo ícone, mesmas rotas e mesmo glifo por rota")
    void gruposDaHomeSeguemOMenu() {
        String home = given().when().get("/").then().statusCode(200).extract().asString();
        int inicioDoMenu = home.indexOf("<nav class=\"aed-topnav\"");
        assertTrue(inicioDoMenu >= 0, "a Início não renderizou o menu principal");
        String nav = home.substring(inicioDoMenu, home.indexOf("</nav>", inicioDoMenu));
        Map<String, Grupo> menu = gruposDoMenu(nav);
        assertEquals(List.of("IPv4", "IPv6", "Topologia", "Operações"), List.copyOf(menu.keySet()),
                "o leitor do menu não achou os quatro grupos");
        Map<String, String> todos = pares(ITEM_DO_MENU, nav, 1, 2);
        assertTrue(todos.size() >= MODULOS_DO_MENU.size(), "o leitor do menu viu só " + todos.size() + " itens");
        Map<String, Grupo> daHome = gruposDaHome(home);
        assertTrue(daHome.size() >= 6, "o leitor da Início viu só os grupos " + daHome.keySet());

        assertEquals(List.of(), divergencias(menu, todos, daHome));
    }

    @Test
    @DisplayName("calibração: rota fora do grupo, ícone trocado e grupo ausente reprovam; cartão a mais passa")
    void divergenciasCalibrada() {
        String menuHtml = """
                <div class="dropdown aed-nav-drop">
                    <button type="button" class="aed-nav-drop-toggle " title="x">
                        <span class="material-symbols-outlined" aria-hidden="true" translate="no">filter_4</span>
                        <span>IPv4</span>
                        <span class="material-symbols-outlined aed-nav-drop-caret" translate="no">expand_more</span>
                    </button>
                    <div class="dropdown-menu aed-nav-drop-menu">
                        <a href="/analise"
                           class="aed-nav-link ">
                            <span class="material-symbols-outlined" aria-hidden="true" translate="no">bar_chart</span>
                            <span>Análise</span>
                        </a>
                        <a href="/calculadora" rel="nofollow" class="aed-nav-link is-active">
                            <span class="material-symbols-outlined" aria-hidden="true" translate="no">calculate</span>
                        </a>
                    </div>
                </div>""";
        Map<String, Grupo> menu = gruposDoMenu(menuHtml);
        assertEquals(Map.of("IPv4", new Grupo("filter_4", Map.of("/analise", "bar_chart", "/calculadora", "calculate"))),
                menu, "o leitor do menu leu errado");
        String cartaoAnalise = cartaoSintetico("bar_chart", "/analise");
        String cartaoCalc = cartaoSintetico("calculate", "/calculadora");
        Map<String, Grupo> sao = gruposDaHome(grupoSintetico("filter_4", "IPv4",
                cartaoAnalise + cartaoCalc + cartaoSintetico("public", "/informacoes")));
        assertEquals(3, sao.get("IPv4").rotas().size(), "o leitor da Início leu errado: " + sao);

        assertEquals(List.of(), divergencias(menu, Map.of(), sao), "A1: cartão a mais no grupo é legítimo");
        assertEquals(1, divergencias(menu, Map.of(), gruposDaHome(grupoSintetico("filter_4", "IPv4", cartaoAnalise)))
                .size(), "A2: rota do menu fora do grupo");
        assertEquals(1, divergencias(menu, Map.of(), gruposDaHome(grupoSintetico("filter_4", "IPv4",
                cartaoSintetico("insights", "/analise") + cartaoCalc))).size(), "A2: glifo diferente do menu");
        assertEquals(1, divergencias(menu, Map.of(), gruposDaHome(grupoSintetico("lan", "IPv4",
                cartaoAnalise + cartaoCalc))).size(), "A2: ícone do grupo diferente do menu");
        assertEquals(1, divergencias(menu, Map.of(), gruposDaHome(grupoSintetico("filter_4", "Endereços",
                cartaoAnalise + cartaoCalc))).size(), "A2: grupo do menu sem grupo de mesmo nome");
        assertEquals(1, divergencias(Map.of(), Map.of("/sobre", "badge"), gruposDaHome(grupoSintetico("info",
                "O projeto", cartaoSintetico("info", "/sobre")))).size(), "A2: item solto do menu com outro glifo");
    }

    private static String grupoSintetico(String glifo, String nome, String cartoes) {
        return "<section class=\"home-group\" aria-labelledby=\"g\">\n<h2 class=\"home-group-title\" id=\"g\">"
                + "<span class=\"material-symbols-outlined\" aria-hidden=\"true\" translate=\"no\">" + glifo + "</span> "
                + nome + "</h2>\n<div class=\"home-grid\">" + cartoes + "</div>\n</section>";
    }

    private static String cartaoSintetico(String glifo, String rota) {
        return "<article class=\"home-module\">\n<span class=\"home-module-icon material-symbols-outlined\" "
                + "aria-hidden=\"true\" translate=\"no\">" + glifo + "</span>\n<h3 class=\"home-module-title\">"
                + "<a href=\"" + rota + "\" class=\"home-module-link\">x</a></h3></article>";
    }

    private static final Pattern SELO = Pattern.compile(
            "<span class=\"[^\"]*\\bbadge\\b[^\"]*\"[^>]*>\\s*(<span[^>]*translate=\"no\">[a-z0-9_]+</span>)?\\s*([^<]+?)\\s*</span>");

    @Test
    @DisplayName("sem selo NOVO escrito à mão; o selo ADMIN continua, com o ícone de login")
    void semSeloNovoEscritoAMao() {
        String home = given().when().get("/").then().statusCode(200).extract().asString();
        Map<String, String> selos = pares(SELO, home, 2, 1);
        assertTrue(selos.containsKey("ADMIN"), "o leitor de selos não achou o ADMIN: " + selos.keySet());
        assertTrue(String.valueOf(selos.get("ADMIN")).contains(">lock</span>"), "o selo ADMIN perdeu o ícone de login");
        assertFalse(selos.containsKey("NOVO"),
                "selo NOVO escrito à mão não expira (já marcou módulos com 55 a 114 dias); use data de validade");
        assertTrue(pares(SELO, "<span class=\"position-absolute badge rounded-pill\" style=\"x\">NOVO</span>", 2, 1)
                .containsKey("NOVO"), "o leitor de selos não enxerga o selo antigo (com style)");
    }

    @Test
    @DisplayName("o cartão da Academia cita os níveis abertos do catálogo e nenhum fechado")
    void cartaoDaAcademiaSegueOCatalogo() {
        String cartao = cartao("academia");
        int abertos = 0;
        for (Nivel nivel : CatalogoTrilha.niveis()) {
            boolean citado = cartao.contains("<strong>" + nivel.titulo() + "</strong>");
            if (nivel.aberto() && !nivel.licoes().isEmpty()) {
                abertos++;
                assertTrue(citado, "o cartão da Academia não cita o nível aberto " + nivel.titulo());
            } else {
                assertFalse(citado, "o cartão da Academia cita como aberto o nível fechado " + nivel.titulo());
            }
        }
        assertTrue(abertos >= 3, "o catálogo tem só " + abertos + " níveis abertos: a conferência não vale");
    }

    @Test
    @DisplayName("os aprofundamentos citados no cartão de Protocolos existem no registro")
    void aprofundamentosCitadosExistem() {
        Matcher m = Pattern.compile("<strong>([^<]+)</strong>").matcher(cartao("protocolos"));
        int citados = 0;
        while (m.find()) {
            citados++;
            String slug = m.group(1).strip().toLowerCase(Locale.ROOT);
            assertTrue(AprofundamentoProtocolo.porSlug(slug).isPresent(),
                    "o cartão de Protocolos cita " + m.group(1) + ", que não é aprofundamento registrado");
        }
        assertTrue(citados >= 4, "o leitor achou só " + citados + " aprofundamentos citados");
    }

    @Test
    @DisplayName("o topo da Início tem os dois caminhos: Academia e Análise")
    void heroTemOsDoisCaminhos() {
        String home = given().when().get("/").then().statusCode(200).extract().asString();
        int ini = home.indexOf("class=\"home-hero-actions\"");
        assertTrue(ini > 0, "o topo da Início perdeu a área dos botões");
        String acoes = home.substring(ini, home.indexOf("</div>", ini));
        assertTrue(acoes.contains("href=\"/academia\""), "o topo da Início não leva à Academia");
        assertTrue(acoes.contains("href=\"/analise\""), "o topo da Início não leva à Análise");
    }

    private static String cartao(String modulo) {
        String home = given().when().get("/").then().statusCode(200).extract().asString();
        Matcher m = Pattern.compile("<article[^>]*data-modulo=\"" + modulo + "\"[^>]*>(.*?)</article>", Pattern.DOTALL)
                .matcher(home);
        assertTrue(m.find(), "cartão " + modulo + " não encontrado na Início");
        return m.group(1);
    }

    private static int contarOcorrencias(String texto, String agulha) {
        int total = 0;
        int idx = texto.indexOf(agulha);
        while (idx >= 0) {
            total++;
            idx = texto.indexOf(agulha, idx + agulha.length());
        }
        return total;
    }
}
