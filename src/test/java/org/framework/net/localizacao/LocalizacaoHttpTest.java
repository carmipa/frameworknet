package org.framework.net.localizacao;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class LocalizacaoHttpTest {

    @Test
    void paginaLocalizacaoResponde() {
        given()
                .when().get("/localizacao")
                .then()
                .statusCode(200)
                .body(containsString("Localização"))
                .body(containsString("loc-map"));
    }

    @Test
    void cepInvalidoRetornaOkFalseSemRede() {
        given()
                .queryParam("cep", "abc")
                .when().get("/localizacao/api/cep")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("ok", equalTo(false))
                .body("motivo", equalTo("invalid"));
    }

    @Test
    void gpsCoordenadasInvalidasRetornaOkFalse() {
        given()
                .queryParam("lat", "999")
                .queryParam("lon", "0")
                .when().get("/localizacao/api/gps")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("ok", equalTo(false));
    }

    /** BACK-11: NaN não é coordenada (IEEE 754 × WGS84) e não pode sair como sucesso; Infinity já era recusado. */
    @Test
    void gpsNaNRecusa() {
        for (String v : new String[]{"NaN", "Infinity"}) {
            given().queryParam("lat", v).queryParam("lon", "0")
                    .when().get("/localizacao/api/gps")
                    .then().statusCode(200).body("ok", equalTo(false));
        }
    }

    @Test
    void ipReservadoRetornaJsonSemRede() {
        given()
                .queryParam("ip", "127.0.0.1")
                .when().get("/localizacao/api/ip")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("origem", equalTo("ip"));
    }
}
