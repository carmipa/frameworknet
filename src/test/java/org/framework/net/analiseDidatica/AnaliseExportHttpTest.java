package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class AnaliseExportHttpTest {

    @Test
    void exportJson() {
        given()
                .when().get("/export/json")
                .then()
                .statusCode(200)
                .body("generated_at", notNullValue())
                .body("history", notNullValue());
    }

    @Test
    void exportEnxergaSoASessaoDeQuemClicou() {
        // Revisão de boa-fé: a tela de histórico é por sessão; o export tem de ser a MESMA lista. Antes o
        // PDF saía com a última consulta de qualquer visitante (IP/geo de terceiro).
        String sessaoA = "AAAAAAAAAAAAAAAAAAAAAA";
        String sessaoB = "BBBBBBBBBBBBBBBBBBBBBB";
        String marcaA = "22 ssh-" + java.util.UUID.randomUUID();
        String marcaB = "3389 rdp-" + java.util.UUID.randomUUID();
        for (String[] par : new String[][]{{sessaoA, marcaA}, {sessaoB, marcaB}}) {
            given().cookie("fnet_hist", par[0]).contentType("application/json")
                    .body("{\"modo\":\"portas\",\"entrada\":\"" + par[1] + "\"}")
                    .when().post("/history/catalog").then().statusCode(200);
        }
        // A consulta de B é a mais recente do servidor: é ela que o defeito punha no PDF de A.
        String json = given().cookie("fnet_hist", sessaoA).when().get("/export/json")
                .then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertTrue(json.contains(marcaA), "o export tem de trazer a consulta de A");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains(marcaB), "o export de A vazou a consulta de B");
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"sessao\""), "chave de sessão não sai no export");

        String idA = io.restassured.path.json.JsonPath.from(json).getString("history[0].id");
        org.junit.jupiter.api.Assertions.assertNotNull(idA);
        byte[] pdf = given().cookie("fnet_hist", sessaoA).redirects().follow(false).when().get("/export/pdf")
                .then().statusCode(200).extract().asByteArray();
        String textoPdf = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        org.junit.jupiter.api.Assertions.assertTrue(textoPdf.contains("Consulta ID: " + idA),
                "o PDF de A tem de ser a última consulta de A, não a mais recente do servidor");
    }

    @Test
    void exportPdfSemHistoricoRedirecionaHome() {
        // Sem cookie a sessão nasce vazia: o único desfecho correto é 303 para "/" (antes o export era
        // global e outros testes podiam tê-lo preenchido, por isso aceitava dois desfechos).
        var r = given().redirects().follow(false).when().get("/export/pdf").then().extract();
        org.junit.jupiter.api.Assertions.assertEquals(303, r.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(r.header("Location").endsWith("/"), r.header("Location"));
    }
}
