package org.framework.net.certificados;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura HTTP do módulo de Certificados (catálogo de 3 tabelas + aprofundamentos).
 *
 * <p><b>Propósito de negócio:</b> as páginas de Certificados são conteúdo didático
 * — se abrirem em erro ou perderem uma seção, ninguém percebe pelo build. Prova que
 * a Geral traz as três tabelas e os cross-links, que cada aprofundamento abre e
 * marca a própria aba (sub-menu de 2 níveis), e que a Anatomia traz a régua ASN.1.</p>
 */
@QuarkusTest
@DisplayName("Certificados: catálogo (3 tabelas), aprofundamentos por grupo e cross-links")
class CertificadosAprofundamentoHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @ParameterizedTest(name = "{0} abre com menu, sub-menu e título \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "/certificados               | Certificados Digitais (X.509 / PKI)",
            "/certificados/x509          | Anatomia do X.509",
            "/certificados/cadeia        | Cadeia de confiança",
            "/certificados/tipos         | Tipos de certificado",
            "/certificados/formatos      | Formatos e conversões",
            "/certificados/ciclo-de-vida | Ciclo de vida",
            "/certificados/revogacao     | Revogação",
            "/certificados/usos          | Usos",
            "/certificados/ataques       | Ataques e erros comuns"
    })
    void paginasDoModuloAbrem(String rota, String titulo) {
        given()
                .when().get(rota.trim())
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString("protocolo-subnav"))
                .body(containsString(titulo));
    }

    @ParameterizedTest(name = "{0} marca o próprio aprofundamento como atual no sub-menu")
    @CsvSource({
            "/certificados/x509", "/certificados/cadeia", "/certificados/tipos", "/certificados/formatos",
            "/certificados/ciclo-de-vida", "/certificados/revogacao", "/certificados/usos", "/certificados/ataques"
    })
    void abaAtivaEMarcada(String rota) {
        given().when().get(rota).then().statusCode(200)
                .body(matchesPattern("(?s).*href=\"" + rota + "\"\\s+class=\"protocolo-subproto is-active\".*"));
    }

    @Test
    @DisplayName("Geral: as 3 tabelas e os cross-links (TLS, Inspetor) chegaram ao HTML")
    void catalogoTresTabelasECrossLinks() {
        given()
                .when().get("/certificados")
                .then()
                .statusCode(200)
                .body(containsString("id=\"formatos\""))
                .body(containsString("id=\"campos\""))
                .body(containsString("id=\"tipos\""))
                .body(containsString(".pem"))
                .body(containsString("SAN"))
                .body(containsString("href=\"/certificados/x509\""))    // Anatomia
                .body(containsString("href=\"/protocolos/tls\""))        // protocolo TLS
                .body(containsString("href=\"/seguranca\""));            // Inspetor TLS
    }

    @Test
    @DisplayName("Anatomia do X.509: diagrama Mermaid, régua ASN.1 e o termo ASN.1 chegaram ao HTML")
    void conteudoX509() {
        given().when().get("/certificados/x509").then().statusCode(200)
                .body(containsString("aprof-mermaid"))
                .body(containsString("aprof-regua-campo"))
                .body(containsString("ASN.1"));
    }

    @ParameterizedTest(name = "conteúdo do aprofundamento chegou ao HTML: {0}")
    @CsvSource(delimiter = '|', value = {
            "/certificados/cadeia        | raiz       | trust      | intermedi",
            "/certificados/tipos         | DV         | wildcard   | SAN",
            "/certificados/formatos      | PEM        | DER        | PKCS",
            "/certificados/ciclo-de-vida | CSR        | ACME       | renova",
            "/certificados/revogacao     | OCSP       | CRL        | stapling",
            "/certificados/usos          | mTLS       | EKU        | code signing",
            "/certificados/ataques       | MITM       | ERR_CERT   | expirad"
    })
    void conteudoDosAprofundamentos(String rota, String t1, String t2, String t3) {
        given()
                .when().get(rota.trim())
                .then()
                .statusCode(200)
                .body(containsString(t1.trim()))
                .body(containsString(t2.trim()))
                .body(containsString(t3.trim()));
    }

    @Test
    @DisplayName("slug inexistente responde 404, nunca a página de outro aprofundamento")
    void slugDesconhecidoNaoResolve() {
        given().when().get("/certificados/naoexiste").then().statusCode(404);
        given().when().get("/certificados/x5099").then().statusCode(404);
    }

    @ParameterizedTest(name = "telemetria: visita a {0} emite aprofundamento_view(pagina={1}) no módulo certificados")
    @CsvSource({
            "/certificados/x509, x509",
            "/certificados/revogacao, revogacao",
            "/certificados/ataques, ataques"
    })
    void visitaGeraEventoDeTelemetria(String rota, String pagina) {
        given().when().get(rota).then().statusCode(200);

        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "aprofundamento_view".equals(e.evento()))
                .filter(e -> e.fields() != null && pagina.equals(e.fields().get("pagina")))
                .findFirst();

        assertTrue(evento.isPresent(),
                "Sem evento aprofundamento_view para " + pagina + " no módulo certificados.");
        assertEquals("certificados", evento.get().modulo(),
                "Módulo divergente da atribuição de " + rota + ".");
    }
}
