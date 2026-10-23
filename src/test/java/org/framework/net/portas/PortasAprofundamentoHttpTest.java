package org.framework.net.portas;

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
 * Cobertura HTTP dos aprofundamentos do módulo de Portas.
 *
 * <p><b>Propósito de negócio:</b> as páginas de aprofundamento de portas (Anatomia
 * e as famílias) são conteúdo didático — se abrirem em erro ou perderem uma seção,
 * ninguém percebe pelo build. Este teste prova que cada uma abre, traz o menu e o
 * sub-menu, marca a própria aba, e que o conteúdo essencial chegou ao HTML; prova
 * também o cross-link do catálogo para os aprofundamentos de protocolo.</p>
 *
 * <p><b>Invariantes do domínio:</b> a aba Geral continua respondendo em
 * {@code /portas} depois das sub-rotas; slug inexistente é 404, nunca a página de
 * outra família; cada visita emite {@code aprofundamento_view} no módulo
 * {@code portas}.</p>
 */
@QuarkusTest
@DisplayName("Portas: catálogo, aprofundamentos e cross-link com Protocolos")
class PortasAprofundamentoHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @ParameterizedTest(name = "{0} abre com menu, sub-menu e título \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "/portas                | Catálogo de Portas TCP/UDP",
            "/portas/anatomia       | Anatomia das portas",
            "/portas/web            | Portas Web (HTTP/HTTPS)",
            "/portas/email          | Portas de E-mail",
            "/portas/acesso-remoto  | Portas de Acesso Remoto",
            "/portas/arquivos       | Portas de Arquivos e Compartilhamento",
            "/portas/banco-de-dados | Portas de Banco de Dados",
            "/portas/infra          | Portas de Infraestrutura de Rede"
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

    @ParameterizedTest(name = "{0} marca a própria aba como atual no sub-menu")
    @CsvSource({
            "/portas/anatomia", "/portas/web", "/portas/email", "/portas/acesso-remoto",
            "/portas/arquivos", "/portas/banco-de-dados", "/portas/infra"
    })
    void abaAtivaEMarcada(String rota) {
        given().when().get(rota).then().statusCode(200)
                .body(matchesPattern("(?s).*href=\"" + rota + "\"\\s+class=\"protocolo-subnav-item is-active\".*"));
    }

    @Test
    @DisplayName("Anatomia: diagrama Mermaid, régua de bits e o número 65.536 chegaram ao HTML")
    void conteudoAnatomia() {
        given().when().get("/portas/anatomia").then().statusCode(200)
                .body(containsString("aprof-mermaid"))
                .body(containsString("sequenceDiagram"))
                .body(containsString("aprof-regua-campo"))
                .body(containsString("socket"))
                .body(containsString("65536"));
    }

    @ParameterizedTest(name = "conteúdo da família chegou ao HTML: {0}")
    @CsvSource(delimiter = '|', value = {
            "/portas/web            | HTTPS      | 8080       | HSTS",
            "/portas/email          | 587        | STARTTLS   | open relay",
            "/portas/acesso-remoto  | RDP        | BlueKeep   | Telnet",
            "/portas/arquivos       | 445        | EternalBlue| SMB",
            "/portas/banco-de-dados | 6379       | MongoDB    | 127.0.0.1",
            "/portas/infra          | SNMP       | amplifica  | 53"
    })
    void conteudoDasFamilias(String rota, String t1, String t2, String t3) {
        given()
                .when().get(rota.trim())
                .then()
                .statusCode(200)
                .body(containsString(t1.trim()))
                .body(containsString(t2.trim()))
                .body(containsString(t3.trim()));
    }

    @Test
    @DisplayName("catálogo cruza para os aprofundamentos de protocolo (Aprofundar)")
    void catalogoTemCrossLinkParaProtocolos() {
        given()
                .when().get("/portas")
                .then()
                .statusCode(200)
                .body(containsString("data-grid-table=\"portas\""))
                .body(containsString("href=\"/portas/anatomia\""))     // sub-menu por família
                .body(containsString("href=\"/protocolos/ssh\""))       // 22 → SSH
                .body(containsString("href=\"/protocolos/dns\""))       // 53 → DNS
                .body(containsString("href=\"/protocolos/ftp\""))       // 20/21 → FTP
                .body(containsString("href=\"/protocolos/smtp\""))      // 25 → SMTP
                .body(containsString("href=\"/protocolos/tls\""));      // 443 → TLS
    }

    @Test
    @DisplayName("slug inexistente responde 404, nunca a página de outra família")
    void slugDesconhecidoNaoResolve() {
        given().when().get("/portas/naoexiste").then().statusCode(404);
        given().when().get("/portas/anatomia2").then().statusCode(404);
    }

    @ParameterizedTest(name = "telemetria: visita a {0} emite aprofundamento_view(pagina={1}) no módulo portas")
    @CsvSource({
            "/portas/anatomia, anatomia",
            "/portas/web, web",
            "/portas/banco-de-dados, banco-de-dados"
    })
    void visitaGeraEventoDeTelemetria(String rota, String pagina) {
        given().when().get(rota).then().statusCode(200);

        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "aprofundamento_view".equals(e.evento()))
                .filter(e -> e.fields() != null && pagina.equals(e.fields().get("pagina")))
                .findFirst();

        assertTrue(evento.isPresent(),
                "Sem evento aprofundamento_view para " + pagina + " — o dashboard não distingue "
                        + "quem abriu o catálogo de quem abriu o aprofundamento.");
        assertEquals("portas", evento.get().modulo(),
                "Módulo divergente da atribuição de " + rota + " contaria a visita no módulo errado.");
    }
}
