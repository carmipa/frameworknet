package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class WebIntegrationTest {

    @Test
    void raizDeveResponderComLandingPage() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Análise Didática Avançada"))
                .body(containsString("Módulos"));
    }

    @Test
    void analiseDeveResponderComAnaliseDidatica() {
        given()
                .when().get("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Análise Didática"))
                .body(containsString("CIDR"));
    }

    @Test
    void portasDeveResponderComCatalogo() {
        given()
                .when().get("/portas")
                .then()
                .statusCode(200)
                .body(containsString("Catálogo de Portas"))
                .body(containsString("SSH"));
    }

    @Test
    void protocolosDeveResponderComCatalogo() {
        given()
                .when().get("/protocolos")
                .then()
                .statusCode(200)
                .body(containsString("Catálogo de Protocolos"))
                .body(containsString("OSPF"));
    }

    @Test
    void resolucaoProblemasDeveResponder() {
        given()
                .when().get("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Resolução de Problemas"));
    }

    @Test
    void resolucaoDemoFiapDeveResponder() {
        given()
                .when().get("/resolucao-problemas?demo=fiap")
                .then()
                .statusCode(200)
                .body(containsString("172.42.0.0"));
    }

    @Test
    void documentacaoDeveResponder() {
        given()
                .when().get("/documentacao")
                .then()
                .statusCode(200)
                .body(containsString("Framework"))
                .body(containsString("NAVEGAÇÃO"))
                .body(containsString("doc-meta-pill"))
                .body(containsString("doc-toc-group"))
                .body(containsString("lightbulb"))
                .body(containsString("<table>"))
                .body(containsString("<blockquote>"))
                .body(not(containsString("shields.io")));
    }

    @Test
    void telemetriaDeveResponderComDashboard() {
        given()
                .when().get("/telemetria")
                .then()
                .statusCode(200)
                .body(containsString("Telemetria"))
                .body(containsString("Framework de Redes"));
    }

    @Test
    void informacoesDeveResponder() {
        given()
                .when().get("/informacoes")
                .then()
                .statusCode(200)
                .body(containsString("Região"));
    }

    @Test
    void iconeDeveResponderPngOu404() {
        // F23: aceitar 404 deixava o ícone da marca sumir de toda página com o teste verde.
        byte[] png = given()
                .when().get("/icone.png")
                .then()
                .statusCode(200)
                .contentType(containsString("image/png"))
                .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P'
                && png[2] == 'N' && png[3] == 'G', "corpo não é PNG");
    }

    @Test
    void apiGeoDeveResponderJson() {
        given()
                .queryParam("ip", "8.8.8.8")
                .when().get("/api/informacoes/geo")
                .then()
                .statusCode(200)
                .contentType(containsString("json"));
    }
}
