package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Cabeçalhos de segurança e identificação de cliente no rate limit.
 *
 * <p><b>Propósito de negócio:</b> duas salvaguadas que falham em silêncio. Um
 * cabeçalho que some de uma resposta não quebra nada visível, e um rate limit
 * cuja chave o cliente escolhe continua respondendo 200 enquanto não limita
 * ninguém.</p>
 *
 * <p><b>Invariantes do domínio:</b> toda resposta HTML carrega o conjunto de
 * cabeçalhos de segurança; o CSP restringe as origens externas realmente usadas
 * pelo projeto; e o código do rate limiter não lê cabeçalho de encaminhamento —
 * quem decide se ele é confiável é a configuração de proxies do Quarkus.</p>
 */
@QuarkusTest
@DisplayName("Segurança: cabeçalhos e chave do rate limit")
class CabecalhosEProxyHttpTest {

    @Test
    void respostaTrazOsCabecalhosDeSeguranca() {
        given()
                .when().get("/calculadora")
                .then()
                .statusCode(200)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "SAMEORIGIN")
                .header("Referrer-Policy", containsString("strict-origin"))
                .header("Permissions-Policy", containsString("geolocation=(self)"))
                .header("Content-Security-Policy", containsString("default-src 'self'"));
    }

    @Test
    @DisplayName("o CSP cobre as origens externas que o projeto realmente usa")
    void cspCobreAsOrigensDoProjeto() {
        var csp = given().when().get("/").then().statusCode(200)
                .extract().header("Content-Security-Policy");

        // Quebrar qualquer uma destas some com um recurso da tela sem erro no servidor.
        for (String origem : new String[]{
                "https://fonts.googleapis.com",      // fontes de texto
                "https://fonts.gstatic.com",         // arquivos das fontes
                "tile.openstreetmap.org",            // tiles do mapa
                "https://translate.google.com"}) {   // widget de tradução
            org.junit.jupiter.api.Assertions.assertTrue(csp.contains(origem),
                    "CSP sem a origem " + origem + ": o recurso vai parar de carregar.\nCSP: " + csp);
        }
        // Bandeiras são locais (seletor desde 01/10, país da GeoIP desde FRONT-18): origem a menos no CSP.
        org.junit.jupiter.api.Assertions.assertFalse(csp.contains("flagcdn.com"), csp);
        // Bootstrap, mermaid, Chart.js e Leaflet são servidos pelo próprio site desde 22/10/2026 (web/vendor):
        // as duas origens de CDN saíram do CSP, e voltar com elas reabriria a dependência externa.
        org.junit.jupiter.api.Assertions.assertFalse(csp.contains("cdn.jsdelivr.net"), csp);
        org.junit.jupiter.api.Assertions.assertFalse(csp.contains("unpkg.com"), csp);

        // Trava as diretivas que independem de origem externa.
        org.junit.jupiter.api.Assertions.assertTrue(csp.contains("object-src 'none'"), csp);
        org.junit.jupiter.api.Assertions.assertTrue(csp.contains("frame-ancestors 'self'"), csp);
        org.junit.jupiter.api.Assertions.assertTrue(csp.contains("base-uri 'self'"), csp);
    }

    @Test
    @DisplayName("regressão: o rate limiter não pode ler cabeçalho de encaminhamento")
    void rateLimiterNaoLeCabecalhoDeEncaminhamento() throws Exception {
        // Antes, clientKey() lia X-Forwarded-For direto do request: bastava variar o
        // cabeçalho para ganhar um balde novo a cada chamada — bypass total, inclusive
        // para forca bruta da chave administrativa. Quem decide se o cabeçalho vale é
        // quarkus.http.proxy.trusted-proxies, não o código do limitador.
        var fonte = java.nio.file.Path.of(
                "src/main/java/org/framework/net/security/RequestRateLimiter.java");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(fonte));
        String codigo = java.nio.file.Files.readString(fonte);

        assertFalse(codigo.contains("getHeaderString(\"X-Forwarded-For\")"),
                "RequestRateLimiter voltou a confiar em X-Forwarded-For do cliente");
        assertFalse(codigo.contains("getHeaderString(\"X-Real-IP\")"),
                "RequestRateLimiter voltou a confiar em X-Real-IP do cliente");
    }

    @Test
    @DisplayName("o Bootstrap é servido pelo próprio site, com Subresource Integrity, e o arquivo responde")
    void bibliotecasDeCdnTemIntegrity() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("href=\"/web/vendor/bootstrap-5.3.0/bootstrap.min.css\""))
                .body(containsString("integrity=\"sha384-"))
                .body(not(containsString("cdn.jsdelivr.net")));
        given().when().get("/web/vendor/bootstrap-5.3.0/bootstrap.bundle.min.js").then().statusCode(200);
    }
}
