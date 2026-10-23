package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * correspondente na landing; a documentação renderizada contém as seções que
 * descrevem os módulos; a sonda {@code /health} responde e não é registrada na
 * telemetria.</p>
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
