package org.framework.net.health.presentation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class HealthResourceTest {

    @Inject
    TelemetriaStore store;

    /**
     * Propósito de negócio: comprova que a supervisão do container não polui a telemetria funcional.
     * Invariantes do domínio: o endpoint responde UP sem alterar a quantidade de eventos em memória.
     * Comportamento em caso de falha: o teste falha se houver resposta não saudável ou qualquer novo evento.
     */
    @Test
    void respondeSemGerarEventoDeTelemetria() {
        int antes = store.gerarResumo(500).totalEventos();

        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.CoreMatchers.equalTo("UP"));

        assertEquals(antes, store.gerarResumo(500).totalEventos());
    }
}
