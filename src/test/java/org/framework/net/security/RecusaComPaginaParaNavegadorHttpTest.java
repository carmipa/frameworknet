package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRONT-01: o 429 do limite e o 403 do CSRF eram abortados com JSON também para a NAVEGAÇÃO — o aluno via
 * {@code {"erro":"Muitas requisições..."}} cru na janela. Navegador (Accept text/html) recebe a página de
 * erro do site; fetch/API (Accept genérico ou JSON) recebe o JSON de sempre.
 */
@QuarkusTest
@TestProfile(RecusaComPaginaParaNavegadorHttpTest.Perfil.class)
class RecusaComPaginaParaNavegadorHttpTest {

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.csrf-enabled", "true",
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", "1000",
                    "framework.security.rate-limit-heavy-per-minute", "2",
                    "framework.security.rate-limit-global-per-minute", "100000");
        }
    }

    @Test
    void formularioSemTokenRecebeAPaginaDeErro() {
        given()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .contentType("application/x-www-form-urlencoded")
                .formParam("x", "1")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(403)
                .contentType(containsString("text/html"))
                .body(containsString("AÇÃO NÃO LIBERADA"))
                .body(containsString("token de segurança"))
                .body(not(containsString("{\"erro\"")));
    }

    @Test
    void fetchSemTokenContinuaRecebendoJson() {
        given()
                .header("Accept", "*/*")
                .contentType("application/x-www-form-urlencoded")
                .formParam("x", "1")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(403)
                .contentType(containsString("application/json"))
                .body(containsString("Token CSRF inválido ou ausente"));
    }

    /** A1: o htmx manda Accept text/html como o navegador, mas troca o corpo por aviso próprio — fica no JSON. */
    @Test
    void htmxSemTokenContinuaRecebendoJson() {
        given()
                .header("Accept", "text/html,*/*;q=0.8")
                .header("HX-Request", "true")
                .contentType("application/x-www-form-urlencoded")
                .formParam("x", "1")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(403)
                .contentType(containsString("application/json"))
                .body(containsString("Token CSRF inválido ou ausente"));
    }

    @Test
    void navegacaoLimitadaRecebeAPaginaDeErro() {
        Response recusa = primeiraRecusa("text/html,application/xhtml+xml,*/*;q=0.8", "/calculadora/");
        assertTrue(recusa.contentType().contains("text/html"), "429 de navegação veio como " + recusa.contentType());
        String corpo = recusa.asString();
        assertTrue(corpo.contains("LIMITE EXCEDIDO") && corpo.contains("Muitas requisições em pouco tempo"),
                "429 de navegação sem a página do catálogo");
        assertTrue(!corpo.contains("{\"erro\""), "429 de navegação ainda traz o JSON cru");
    }

    @Test
    void apiLimitadaContinuaRecebendoJson() {
        Response recusa = primeiraRecusa("*/*", "/calculadora/");
        assertTrue(recusa.contentType().contains("application/json"), "429 de fetch veio como " + recusa.contentType());
        assertTrue(recusa.asString().contains("Muitas requisições. Aguarde um minuto"), "429 de fetch sem o JSON de sempre");
    }

    /** Pede a rota pesada (teto 2/min) até o primeiro 429; reprova se ele não vier. */
    private static Response primeiraRecusa(String accept, String rota) {
        for (int i = 0; i < 6; i++) {
            Response r = given().header("Accept", accept).when().get(rota);
            if (r.statusCode() == 429) {
                return r;
            }
        }
        throw new AssertionError("nenhum 429 em 6 pedidos a uma rota com teto 2/min: o limite não ligou");
    }
}
