package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Revisão de boa-fé: o histórico é por navegador (F07). Um link {@code /analise?replay=<id>} copiado de
 * outra pessoa, ou de uma sessão já fechada, abria o formulário vazio sem explicação. A mesma consulta,
 * aberta no navegador que a fez, continua funcionando sem aviso (A1: o legítimo com o mesmo sinal — um
 * replay com id real).
 */
@QuarkusTest
class HistoricoReplayHttpTest {

    private static final String AVISO = "não pertence a este navegador";

    @Test
    void replayDeOutraSessaoAvisaEODaPropriaNao() {
        String dono = "DDDDDDDDDDDDDDDDDDDDDD";
        String outro = "OOOOOOOOOOOOOOOOOOOOOO";
        given().cookie("fnet_hist", dono).contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"25 smtp-" + UUID.randomUUID() + "\"}")
                .when().post("/history/catalog").then().statusCode(200);
        String lista = given().cookie("fnet_hist", dono).accept("application/json")
                .when().get("/history").then().statusCode(200).extract().asString();
        String id = JsonPath.from(lista).getString("items[0].id");
        assertNotNull(id, "o /history do dono tem de devolver a consulta: " + lista);

        String doDono = given().cookie("fnet_hist", dono).when().get("/analise?replay=" + id)
                .then().statusCode(200).extract().asString();
        assertFalse(doDono.contains(AVISO), "o replay no navegador que fez a consulta não pode avisar erro");

        String deOutro = given().cookie("fnet_hist", outro).when().get("/analise?replay=" + id)
                .then().statusCode(200).extract().asString();
        assertTrue(deOutro.contains(AVISO), "replay de outra sessão tem de explicar por que o formulário veio vazio");
    }
}
