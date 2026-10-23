package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestProfile(TelemetryDisabledTestProfile.class)
class TelemetryDisabledHttpTest {

    @Test
    void telemetriaDesabilitadaRetorna404() {
        given()
                .when().get("/telemetria")
                .then()
                .statusCode(404)
                .body(containsString("indisponível"));
    }

    @Test
    void apiTelemetriaDesabilitadaRetorna404() {
        given()
                .when().get("/telemetria/api/resumo")
                .then()
                .statusCode(404);
    }
}
