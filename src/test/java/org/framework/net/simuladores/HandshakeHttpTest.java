package org.framework.net.simuladores;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class HandshakeHttpTest {

    @Test
    void handshakePadraoTresPassos() {
        given()
                .when().get("/simuladores/api/handshake")
                .then()
                .statusCode(200)
                .body("passos.size()", equalTo(3))
                .body("passos[0].flags", equalTo("SYN"));
    }

    @Test
    void handshakeCompletoNovePassos() {
        given()
                .queryParam("dados", true)
                .queryParam("encerramento", true)
                .when().get("/simuladores/api/handshake")
                .then()
                .statusCode(200)
                .body("passos.size()", equalTo(9));
    }
}
