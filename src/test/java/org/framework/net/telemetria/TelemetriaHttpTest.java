package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.security.SessaoTelemetriaService;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaHttpTest {

    @Inject
    SessaoTelemetriaService sessao;

    /** A Telemetria deixou de ser publica; estes testes autenticam como dono. */
    private String cookieDeDono() {
        return SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirCookie(
                "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB,
                SessaoTelemetriaService.PAPEL_DONO).getValue();
    }

    @Test
    void resumoJson() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/resumo")
                .then()
                .statusCode(200)
                .body("projeto", notNullValue())
                .body("totalEventos", notNullValue());
    }

    @Test
    void dashboardJsonInformaACoberturaReal() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard?janela=10080")
                .then()
                .statusCode(200)
                .body("coberturaDesde", notNullValue())
                .body("coberturaParcial", notNullValue());
    }

    @Test
    void dashboardJson() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard")
                .then()
                .statusCode(200)
                .body("resumo.projeto", notNullValue())
                .body("consoleLinhas", notNullValue());
    }

    @Test
    void paginaHtml() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria")
                .then()
                .statusCode(200)
                .body(containsString("Telemetria do Framework"))
                .body(containsString("console-telemetria"))
                .body(containsString("chart-modulos"));
    }

    @Test
    void dashboardJsonExpoeTopRotas() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard")
                .then()
                .statusCode(200)
                .body("topRotas", notNullValue())
                .body("porModulo", notNullValue())
                .body("paises", notNullValue())
                .body("clientes", notNullValue());
    }

    @Test
    void paginaTrazSecaoOrigemDoTrafego() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria")
                .then()
                .statusCode(200)
                .body(containsString("Origem do tráfego"))
                .body(containsString("chart-origem"))
                .body(containsString("tabela-origem-modulos"))
                .body(containsString("tabela-origem-rotas"))
                .body(containsString("tabela-origem-paises"))
                .body(containsString("chart-clientes"));
    }

    @Test
    void exportarArquivoCompartilhado() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/exportar")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("telemetria_compartilhada"));
    }

    @Test
    void requestIncluiTraceId() {
        given()
                .header("Cookie", cookieDeDono())
                .when().get("/documentacao")
                .then()
                .statusCode(200)
                .header("X-Request-Id", notNullValue())
                .header("X-Trace-Id", notNullValue());
    }

    /**
     * D18: o painel "só pessoas" tira os robôs dos números de acesso; o padrão continua sendo a carga. Os acessos são
     * feitos de verdade, passando pelo filtro que classifica o cliente pelo User-Agent (A6): um de robô e um de
     * navegador. A1: o acesso de pessoa continua no modo só pessoas; valor de "pessoas" que não é true deixa o painel
     * de carga.
     */
    @Test
    void dashboardSoPessoasTiraOsRobos() {
        given().header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
                .when().get("/portas").then().statusCode(200);
        given().header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/131.0 Safari/537.36")
                .when().get("/portas").then().statusCode(200);

        java.util.Map<String, Object> carga = given().header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard?janela=60")
                .then().statusCode(200).body("soPessoas", is(false))
                .extract().jsonPath().getMap("clientes");
        assertTrue(carga.containsKey("bot") && carga.containsKey("humano"),
                "o painel de carga não viu os dois acessos: " + carga);

        java.util.Map<String, Object> pessoas = given().header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard?janela=60&pessoas=true")
                .then().statusCode(200).body("soPessoas", is(true))
                .extract().jsonPath().getMap("clientes");
        assertFalse(pessoas.containsKey("bot"), "o painel só pessoas contou robô: " + pessoas);
        assertTrue(pessoas.containsKey("humano"), "o painel só pessoas perdeu a pessoa: " + pessoas);

        given().header("Cookie", cookieDeDono())
                .when().get("/telemetria/api/dashboard?janela=60&pessoas=abc")
                .then().statusCode(200).body("soPessoas", is(false));
    }
}
