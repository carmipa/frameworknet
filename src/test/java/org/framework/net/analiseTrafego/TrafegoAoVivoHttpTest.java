package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class TrafegoAoVivoHttpTest {

    @Test
    void snapshotDemoResponde() {
        given()
                .when().get("/trafego/api/aovivo")
                .then()
                .statusCode(200)
                .body("modo", equalTo("demo"))
                .body("redesAbertas", greaterThanOrEqualTo(1));
    }
}
