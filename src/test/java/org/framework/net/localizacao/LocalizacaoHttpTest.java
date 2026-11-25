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

    /**
     * D17 (privacidade): o CEP digitado não entra no histórico de digitação do navegador (localStorage), que a próxima
     * pessoa do mesmo PC de laboratório veria. A1: o campo de IP da mesma página continua com histórico (decisão de
     * 04/08: o histórico de IP é o que dá utilidade ao recurso).
     */
    @Test
    void cepForaDoHistoricoDeDigitacaoEIpDentro() {
        String html = io.restassured.RestAssured.given().when().get("/localizacao").then().statusCode(200)
                .extract().asString();
        java.util.regex.Matcher cep = java.util.regex.Pattern.compile("<input[^>]*id=\"loc-cep\"[^>]*>").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(cep.find(), "campo de CEP não encontrado na página");
        org.junit.jupiter.api.Assertions.assertTrue(cep.group().contains("data-history=\"off\""),
                "o CEP entra no histórico de digitação: " + cep.group());
        java.util.regex.Matcher ip = java.util.regex.Pattern.compile("<input[^>]*type=\"text\"[^>]*>").matcher(html);
        boolean algumCampoComHistorico = false;
        while (ip.find()) {
            if (!ip.group().contains("data-history=\"off\"")) {
                algumCampoComHistorico = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(algumCampoComHistorico,
                "nenhum campo de texto ficou com histórico: o instrumento não discrimina");
    }
}
