package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
@TestProfile(AdminSecurityTestProfile.class)
class AdminApiKeyHttpTest {

    /**
     * Regressão: a telemetria era pública e vazava o IP real do visitante.
     *
     * <p>Verificado em produção em 2026-08-03: {@code GET /telemetria/api/exportar}
     * devolvia 347 KB sem autenticação, contendo o IPv4 e os dois IPv6 do usuário.
     * {@code /api/resumo}, {@code /api/dashboard} e {@code /api/console} carregavam
     * o mesmo dado nas mensagens de evento.</p>
     */
    @Test
    void telemetriaSemChaveRetorna401() {
        for (String rota : new String[]{
                "/telemetria/api/resumo",
                "/telemetria/api/dashboard",
                "/telemetria/api/console",
                "/telemetria/api/exportar",
                "/telemetria/api/pasta"}) {
            given()
                    .header("Accept", "application/json")
                    .when().get(rota)
                    .then()
                    .statusCode(401);
        }
    }

    /**
     * SEC-04: o login de /admin guardava a CHAVE crua no cookie ADMIN_API_KEY por 8 h. Agora emite a
     * sessão assinada; o cookie da chave é apagado e deixa de valer, e a sessão abre a exportação.
     */
    @Test
    void loginAdminEmiteSessaoAssinadaNuncaAChave() {
        var resposta = given().redirects().follow(false)
                .formParam("api_key", "test-admin-secret").formParam("redirect", "/export/json")
                .when().post("/admin/login")
                .then().statusCode(303).extract();
        String sessao = resposta.cookie(SessaoTelemetriaService.COOKIE_NAME);
        org.junit.jupiter.api.Assertions.assertNotNull(sessao, "a sessão assinada precisa sair no login");
        org.junit.jupiter.api.Assertions.assertFalse(sessao.contains("test-admin-secret"), "a sessão não carrega a chave");
        String antigo = resposta.cookie(AdminApiKeyService.COOKIE_NAME);
        org.junit.jupiter.api.Assertions.assertTrue(antigo == null || antigo.isEmpty(), "o cookie da chave crua não é emitido");

        given().header("Accept", "application/json")
                .cookie(SessaoTelemetriaService.COOKIE_NAME, sessao)
                .when().get("/export/json").then().statusCode(200);
        given().header("Accept", "application/json")
                .cookie(AdminApiKeyService.COOKIE_NAME, "test-admin-secret")
                .when().get("/export/json").then().statusCode(401);
    }

    @Test
    void exportSemChaveRetorna401() {
        given()
                .header("Accept", "application/json")
                .when().get("/export/json")
                .then()
                .statusCode(401)
                .body(containsString("API key administrativa"));
    }

    @Test
    void exportComHeaderValidoRetorna200() {
        given()
                .header("X-Admin-Api-Key", "test-admin-secret")
                .when().get("/export/json")
                .then()
                .statusCode(200);
    }

    @Test
    void ipv6ExportSemChaveRetorna401() {
        given()
                .header("Accept", "application/json")
                .when().get("/ipv6/export/json?endereco=2001:db8::/48")
                .then()
                .statusCode(401)
                .body(containsString("API key administrativa"));
    }

    @Test
    void ipv6ExportComHeaderValidoRetorna200() {
        given()
                .header("X-Admin-Api-Key", "test-admin-secret")
                .when().get("/ipv6/export/json?endereco=2001:db8::/48")
                .then()
                .statusCode(200);
    }

    @Test
    void ipv6PaginaPublicaSegueAbertaSemChave() {
        given()
                .when().get("/ipv6")
                .then()
                .statusCode(200);
    }

    @Test
    void historyPublicoSemChaveRetorna200() {
        given()
                .header("Accept", "application/json")
                .when().get("/history")
                .then()
                .statusCode(200);
    }

    @Test
    void loginHtmlDisponivelSemChave() {
        given()
                .when().get("/admin/login")
                .then()
                .statusCode(200)
                .body(containsString("Acesso administrativo"));
    }

    /**
     * FRONT-03: o navegador que clica em "Exportar" vai ao login e volta ao MESMO export, com a consulta; antes
     * o redirect levava só o caminho e o IPv6 analisado se perdia. O & da consulta não pode virar parâmetro
     * do login.
     */
    @Test
    void navegadorSemChaveVaiAoLoginLevandoAConsulta() {
        given()
                .redirects().follow(false)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .when().get("/ipv6/export/json?endereco=2001:db8::1&modo=x")
                .then()
                .statusCode(303)
                // a consulta vai inteira dentro do redirect (o ":" chega já codificado pelo cliente, %3A → %253A)
                .header("Location", containsString("/admin/login?redirect=%2Fipv6%2Fexport%2Fjson%3Fendereco%3D2001"))
                .header("Location", containsString("%26modo%3Dx"))
                .header("Location", not(containsString("&modo")));
    }

    /** Destino externo ou malformado no logout vira o padrão; "/\x" dava HTTP 500. */
    @Test
    void logoutComDestinoForaDoSiteVoltaAoPadrao() {
        for (String destino : new String[]{"/%5Cexample.org", "//example.org", "https://example.org", "/%09/example.org"}) {
            given()
                    .redirects().follow(false)
                    .urlEncodingEnabled(false)
                    .when().get("/admin/logout?redirect=" + destino)
                    .then()
                    .statusCode(303)
                    .header("Location", endsWith("/telemetria"))
                    .header("Location", not(containsString("example.org")));
        }
        // A1: o destino local legítimo, com consulta, passa.
        given()
                .redirects().follow(false)
                .when().get("/admin/logout?redirect=/ipv6/analise?endereco=2001:db8::1")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/ipv6/analise?endereco=2001:db8::1"));
    }
}
