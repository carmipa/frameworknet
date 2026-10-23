package org.framework.net.simuladores;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class EncapsulamentoHttpTest {

    @Test
    void encapsulaViaHttp() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("mensagem", "GET / HTTP/1.1")
                .formParam("transporte", "TCP")
                .formParam("ipOrigem", "192.168.0.10")
                .formParam("ipDestino", "142.250.79.14")
                .formParam("portaOrigem", "51000")
                .formParam("portaDestino", "80")
                .when().post("/simuladores/api/encapsular")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("TCP sobre IPv4"))
                .body(containsString("Encapsulamento no emissor"))
                .body(containsString("Ethernet"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    /** As 4 camadas devem sair aninhadas: Enlace por fora, Aplicação no miolo. */
    @Test
    void pilhaSaiAninhadaEBalanceada() {
        String corpo = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("mensagem", "GET / HTTP/1.1")
                .formParam("transporte", "TCP")
                .formParam("ipOrigem", "192.168.0.10")
                .formParam("ipDestino", "142.250.79.14")
                .formParam("portaOrigem", "51000")
                .formParam("portaDestino", "80")
                .when().post("/simuladores/api/encapsular")
                .then()
                .statusCode(200)
                .extract().body().asString();

        int aberturas = contar(corpo, "<div class=\"encap-layer nivel-");
        int corpos = contar(corpo, "<div class=\"encap-layer-body\">");
        org.junit.jupiter.api.Assertions.assertEquals(4, aberturas, "deve abrir 4 camadas");
        org.junit.jupiter.api.Assertions.assertEquals(4, corpos, "cada camada deve ter um corpo");

        int miolo = corpo.indexOf("encap-payload");
        int primeiraCamada = corpo.indexOf("encap-layer nivel-");
        org.junit.jupiter.api.Assertions.assertTrue(primeiraCamada < miolo,
                "a mensagem tem de ficar dentro das camadas, não antes delas");
    }

    @Test
    void mensagemVaziaVoltaComoFragmentoDeAviso() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("mensagem", "")
                .formParam("transporte", "TCP")
                .when().post("/simuladores/api/encapsular")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("encap-erro"))
                .body(not(containsString("Encapsulamento no emissor")));
    }

    private static int contar(String texto, String trecho) {
        int total = 0;
        int idx = texto.indexOf(trecho);
        while (idx >= 0) {
            total++;
            idx = texto.indexOf(trecho, idx + trecho.length());
        }
        return total;
    }
}
