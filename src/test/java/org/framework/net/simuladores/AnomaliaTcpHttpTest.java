package org.framework.net.simuladores;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura HTTP da API de anomalias TCP.
 *
 * <p><b>Propósito de negócio:</b> a sub-aba "Anomalias" busca o cenário deste
 * endpoint. Se ele parar de responder ou trocar o cenário, a aba anima o passo a
 * passo errado. Prova que a rota convive com o handshake e o encapsulamento sob
 * {@code /simuladores/api} sem colisão.</p>
 */
@QuarkusTest
class AnomaliaTcpHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @Test
    @DisplayName("telemetria: a simulação vira evento anomalia_tcp no módulo simuladores")
    void geraEventoDeTelemetria() {
        given().when().get("/simuladores/api/anomalia-tcp?tipo=sequence-hijack").then().statusCode(200);

        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "anomalia_tcp".equals(e.evento()))
                .findFirst();

        assertTrue(evento.isPresent(), "Sem evento anomalia_tcp o dashboard não conta o uso da aba.");
        assertEquals("simuladores", evento.get().modulo(),
                "O evento precisa ser atribuído a 'simuladores', coerente com /simuladores/api/*.");
    }

    @Test
    @DisplayName("padrão é SYN flood com passos e defesas")
    void padraoSynFlood() {
        given()
                .when().get("/simuladores/api/anomalia-tcp")
                .then()
                .statusCode(200)
                .body("tipo", equalTo("syn-flood"))
                .body("passos.size()", greaterThan(0))
                .body("mitigacoes.size()", greaterThan(0));
    }

    @Test
    @DisplayName("tipo=sequence-hijack devolve o cenário de sequestro")
    void cenarioSequestro() {
        given()
                .queryParam("tipo", "sequence-hijack")
                .when().get("/simuladores/api/anomalia-tcp")
                .then()
                .statusCode(200)
                .body("tipo", equalTo("sequence-hijack"))
                .body("passos.size()", greaterThan(0));
    }

    @Test
    @DisplayName("tipo desconhecido não quebra — cai no SYN flood")
    void tipoDesconhecidoCaiNoPadrao() {
        given()
                .queryParam("tipo", "xpto")
                .when().get("/simuladores/api/anomalia-tcp")
                .then()
                .statusCode(200)
                .body("tipo", equalTo("syn-flood"));
    }
}
