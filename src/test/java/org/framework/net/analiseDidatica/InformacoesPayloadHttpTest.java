package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F09: o JSON inicial de /informacoes saía escapado pelo Qute (&amp;quot;) dentro de um
 * {@code <script type="application/json">}, onde entidade HTML não é decodificada — o JSON.parse
 * falhava em silêncio e o relatório aparecia vazio. O JSON agora sai cru, mas com {@code <}, {@code >}
 * e {@code &} escapados no servidor: o campo "consultado" ecoa o que o visitante digitou.
 */
@QuarkusTest
class InformacoesPayloadHttpTest {

    private static String payload(String html) {
        Matcher m = Pattern.compile("<script id=\"geo-initial-payload\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL).matcher(html);
        assertTrue(m.find(), "bloco geo-initial-payload não encontrado: instrumento cego");
        return m.group(1);
    }

    @Test
    void payloadInicialEhJsonValido() throws Exception {
        String html = given().when().get("/informacoes?ip=127.0.0.1").then().statusCode(200).extract().asString();
        String json = payload(html);
        assertFalse(json.contains("&quot;"), "JSON escapado como HTML não passa no JSON.parse: " + json);
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);   // lança se não for JSON
    }

    @Test
    void entradaHostilNaoFechaOScript() {
        String hostil = "</script><script>alert(1)</script>";
        String html = given().queryParam("ip", hostil)
                .when().get("/informacoes").then().statusCode(200).extract().asString();
        String json = payload(html);
        assertFalse(json.toLowerCase().contains("<script"), "entrada hostil saiu crua dentro do script");
        assertFalse(html.contains("<script>alert(1)</script>"), "XSS refletido na página");
    }
}
