package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class TrafegoHttpTest {

    private static final String FRAME_SYN =
            "aabbccddeeff1122334455660800"
                    + "450000281c4640004006b1e6c0a80001c0a80002"
                    + "d4310050000000000000000050027210e5770000";

    @Test
    void paginaTrafegoResponde() {
        given()
                .when().get("/trafego")
                .then()
                .statusCode(200)
                .body(containsString("Decodificador"))
                .body(containsString("trafego-hex"))
                .body(containsString("hx-post=\"/trafego/api/decodificar\""))
                .body(containsString("hx-target=\"#trafego-resultado\""));
    }

    @Test
    void paginaTrafegoTrazAbaDeAnomalias() {
        given()
                .when().get("/trafego")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"anomalias\""))
                .body(containsString("data-tab-panel=\"anomalias\""))
                .body(containsString("Anomalias TCP"))
                .body(containsString("form-anom"))
                .body(containsString("/simuladores/js/anomalias.js"));
    }

    @Test
    void decodificaFrameViaApi() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", FRAME_SYN)
                .formParam("camada", "auto")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("54 bytes decodificados"))
                .body(containsString("IPv4"))
                .body(containsString("trafego-camada"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    /** O hex que já vem pré-prencido no campo do Decodificador (com espaços e quebras de linha). */
    private static final String EXEMPLO_PRE_PREENCHIDO =
            "aabb ccdd eeff 1122 3344 5566 0800\n"
                    + "4500 0028 1c46 4000 4006 b1e6 c0a8 0001 c0a8 0002\n"
                    + "d431 0050 0000 0000 0000 0000 5002 7210 e577 0000";

    @Test
    void exemploPrePreenchidoDoDecodificadorDecodifica() {
        // Prova que o valor que o campo já traz (o usuário só clica Decodificar) é válido —
        // espaços e quebras de linha incluídos.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", EXEMPLO_PRE_PREENCHIDO)
                .formParam("camada", "auto")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .body(containsString("54 bytes decodificados"))
                .body(containsString("IPv4"));
    }

    @Test
    void hexInvalidoVoltaComoFragmentoDeAviso() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", "isto não é hex")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("trafego-erro"))
                .body(containsString("Não foi possível decodificar"));
    }

    // ---- Construtor de pacotes (P02) ----

    @Test
    void paginaTrazAbaConstrutorDePacotes() {
        given()
                .when().get("/trafego")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"construtor\""))
                .body(containsString("hx-post=\"/trafego/api/construir\""))
                .body(containsString("name=\"ipOrigem\""));
    }

    @Test
    void construirTcpDevolvePacoteComBotaoDeDecodificar() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("protocolo", "tcp")
                .formParam("ipOrigem", "192.168.0.10")
                .formParam("ipDestino", "203.0.113.5")
                .formParam("portaOrigem", "51000")
                .formParam("portaDestino", "443")
                .formParam("flags", "SYN")
                .formParam("ttl", "64")
                .formParam("seq", "1000")
                .formParam("window", "64240")
                .formParam("checksum", "valido")
                .when().post("/trafego/api/construir")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Pacote TCP"))
                .body(containsString("checksum válido"))
                .body(containsString("hx-post=\"/trafego/api/decodificar\""))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void construirProtocoloInvalidoMostraErroNoFragmento() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("protocolo", "icmp")
                .formParam("ipOrigem", "1.1.1.1")
                .formParam("ipDestino", "2.2.2.2")
                .when().post("/trafego/api/construir")
                .then()
                .statusCode(200)
                .body(containsString("Protocolo"));
    }

    // ---- Laboratório DNS/ICMP (P04) ----

    @Test
    void paginaTrazAbaLabDnsIcmp() {
        given()
                .when().get("/trafego")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"lab\""))
                .body(containsString("/trafego/api/lab?cenario=dns"));
    }

    @Test
    void labDnsMostraNormalSuspeitoEFalsoPositivo() {
        given()
                .when().get("/trafego/api/lab?cenario=dns")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("NORMAL"))
                .body(containsString("SUSPEITO"))
                .body(containsString("Falso positivo"))
                .body(containsString("fictícios"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void labCenarioDesconhecidoRetorna400() {
        given()
                .when().get("/trafego/api/lab?cenario=inexistente")
                .then()
                .statusCode(400);
    }
}
