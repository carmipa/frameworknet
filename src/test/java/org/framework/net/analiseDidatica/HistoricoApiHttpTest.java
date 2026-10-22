package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class HistoricoApiHttpTest {

    @Test
    void historyGet() {
        given()
                .when().get("/history")
                .then()
                .statusCode(200)
                .body("items", notNullValue());
    }

    @Test
    void mascaraReferencia() {
        given()
                .when().get("/mascara-referencia")
                .then()
                .statusCode(200)
                .body("table", notNullValue());
    }

    /** BACK-06: corpo JSON "null" é pedido malformado (400), não erro interno (era NPE e 500). */
    @Test
    void historyCatalogCorpoNullRecusaCom400() {
        given().contentType("application/json").body("null")
                .when().post("/history/catalog")
                .then().statusCode(400);
    }

    @Test
    void historyCatalogModoInvalido() {
        given()
                .contentType("application/json")
                .body("{\"modo\":\"invalido\",\"entrada\":\"teste\"}")
                .when().post("/history/catalog")
                .then()
                .statusCode(400);
    }

    /**
     * F07 (decisão de Paulo, 2026-08-04: histórico escopado por sessão). O que o visitante A consultou
     * não aparece para o visitante B nem para quem chega sem cookie; A continua vendo o seu (A1: o
     * legítimo com o mesmo sinal — ler /history — é preservado). O cookie é HttpOnly.
     */
    @Test
    void historicoEhIsoladoPorSessao() {
        String marca = "443 https-" + java.util.UUID.randomUUID();
        String sessaoA = "AAAAAAAAAAAAAAAAAAAAAA";
        String sessaoB = "BBBBBBBBBBBBBBBBBBBBBB";
        given()
                .cookie("fnet_hist", sessaoA)
                .contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"" + marca + "\"}")
                .when().post("/history/catalog")
                .then()
                .statusCode(200);

        given().cookie("fnet_hist", sessaoA)
                .when().get("/history")
                .then().statusCode(200)
                .body(org.hamcrest.CoreMatchers.containsString(marca));
        given().cookie("fnet_hist", sessaoB)
                .when().get("/history")
                .then().statusCode(200)
                .body(org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.containsString(marca)));
        given()
                .when().get("/history")
                .then().statusCode(200)
                .body(org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.containsString(marca)))
                .header("Set-Cookie", org.hamcrest.CoreMatchers.allOf(
                        org.hamcrest.CoreMatchers.containsString("fnet_hist="),
                        org.hamcrest.CoreMatchers.containsString("HttpOnly")));
    }

    /** O arquivo guarda o hash do identificador, nunca o identificador (que funciona como credencial). */
    @Test
    void identificadorDaSessaoNaoEhExpostoNoHistorico() {
        String sessao = "CCCCCCCCCCCCCCCCCCCCCC";
        given().cookie("fnet_hist", sessao).contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"22 ssh\"}")
                .when().post("/history/catalog").then().statusCode(200);
        given().cookie("fnet_hist", sessao)
                .when().get("/history")
                .then().statusCode(200)
                .body(org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.containsString(sessao)));
    }

    @Test
    void historyCatalogPortasValido() {
        given()
                .contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"443 https\"}")
                .when().post("/history/catalog")
                .then()
                .statusCode(200);
    }
}
