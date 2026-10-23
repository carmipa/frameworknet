package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.security.SessaoTelemetriaService;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

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
}
