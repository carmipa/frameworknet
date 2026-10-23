package org.framework.net.referencias;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura HTTP dos quatro módulos de referência novos: Camadas, Criptografia,
 * Wi-Fi e Ferramentas.
 *
 * <p><b>Propósito de negócio:</b> cada um segue o padrão catálogo (Geral) +
 * aprofundamentos, com um interativo client-side. Este teste prova que todas as
 * páginas abrem com menu e sub-menu, que os interativos e as tabelas chegaram ao
 * HTML, que slug inexistente é 404 e que a visita emite telemetria no módulo certo.</p>
 */
@QuarkusTest
@DisplayName("Referências: Camadas, Criptografia, Wi-Fi e Ferramentas")
class ModulosReferenciaHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @ParameterizedTest(name = "{0} abre com menu e sub-menu")
    @ValueSource(strings = {
            "/camadas", "/camadas/osi", "/camadas/tcpip", "/camadas/encapsulamento", "/camadas/dispositivos",
            "/criptografia", "/criptografia/simetrica", "/criptografia/assimetrica", "/criptografia/hash",
            "/criptografia/troca-de-chaves", "/criptografia/assinatura",
            "/wifi", "/wifi/padroes", "/wifi/canais", "/wifi/seguranca", "/wifi/ataques",
            "/ferramentas", "/ferramentas/conectividade", "/ferramentas/dns-tools", "/ferramentas/captura",
            "/ferramentas/varredura", "/ferramentas/http-tls", "/ferramentas/sockets"
    })
    void paginasAbrem(String rota) {
        given().when().get(rota).then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString("protocolo-subnav"));
    }

    @Test
    @DisplayName("os interativos (client-side) chegaram ao HTML da aba Geral")
    void interativosPresentes() {
        given().when().get("/criptografia").then().statusCode(200)
                .body(containsString("cripto-in")).body(containsString("forca-alg"))
                .body(containsString("/criptografia/js/criptografia.js"));
        given().when().get("/wifi").then().statusCode(200)
                .body(containsString("wifi-canais")).body(containsString("/wifi/js/wifi.js"));
        given().when().get("/ferramentas").then().statusCode(200)
                .body(containsString("cmd-tool")).body(containsString("/ferramentas/js/ferramentas.js"));
    }

    @ParameterizedTest(name = "aba Geral de {0} traz a tabela com \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "/camadas      | Aplicação",
            "/criptografia | AES",
            "/wifi         | 802.11",
            "/ferramentas  | tcpdump"
    })
    void catalogoGeralTemTabela(String rota, String token) {
        given().when().get(rota.trim()).then().statusCode(200)
                .body(containsString("aprof-tabela"))
                .body(containsString(token.trim()));
    }

    @ParameterizedTest(name = "slug inexistente em {0} responde 404")
    @ValueSource(strings = {"/camadas/naoexiste", "/criptografia/naoexiste", "/wifi/naoexiste", "/ferramentas/naoexiste"})
    void slugDesconhecidoNaoResolve(String rota) {
        given().when().get(rota).then().statusCode(404);
    }

    @ParameterizedTest(name = "telemetria: {0} emite aprofundamento_view no módulo {1}")
    @CsvSource({
            "/camadas/osi, camadas, osi",
            "/criptografia/hash, criptografia, hash",
            "/wifi/canais, wifi, canais",
            "/ferramentas/captura, ferramentas, captura"
    })
    void visitaGeraTelemetria(String rota, String modulo, String pagina) {
        given().when().get(rota).then().statusCode(200);
        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "aprofundamento_view".equals(e.evento()))
                .filter(e -> e.fields() != null && pagina.equals(e.fields().get("pagina")))
                .filter(e -> modulo.equals(e.modulo()))
                .findFirst();
        assertTrue(evento.isPresent(), "Sem aprofundamento_view para " + pagina + " no módulo " + modulo);
        assertEquals(modulo, evento.get().modulo());
    }
}
