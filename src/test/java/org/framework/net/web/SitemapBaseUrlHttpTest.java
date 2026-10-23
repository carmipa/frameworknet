package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O sitemap com host canônico — o que produção vai servir.
 *
 * <p><b>Propósito de negócio:</b> se o sitemap sair em {@code http://}, o Google
 * o trata como de um <i>site diferente</i> daquele {@code https://} pelo qual o
 * buscou, e descarta as entradas: o arquivo existe, responde 200 e não indexa
 * nada. É a falha que nenhum teste de status pega.</p>
 *
 * <p><b>Invariantes do domínio:</b> configurado {@code framework.app.base-url},
 * <b>toda</b> URL sai naquele host e esquema, mesmo quando a requisição chega em
 * {@code http://localhost} — porque é assim que o container recebe a requisição
 * atrás do Nginx.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste mostra o XML inteiro. Falha
 * aqui significa sitemap apontando para o host errado, que é pior do que não ter
 * sitemap: o Google registra as URLs como não pertencentes ao site.</p>
 */
@QuarkusTest
@TestProfile(SitemapBaseUrlTestProfile.class)
@DisplayName("sitemap.xml: host canônico configurado vence o da requisição")
class SitemapBaseUrlHttpTest {

    private static final String CANONICO = "https://frameworknet.carminati.dev.br";

    @Test
    @DisplayName("mesmo chegando por http://localhost, as URLs saem no host público")
    void urlsSaemNoHostCanonico() {
        String xml = given()
                .when().get("/sitemap.xml")
                .then().statusCode(200)
                .extract().asString();

        assertTrue(xml.contains("<loc>" + CANONICO + "/</loc>"),
                () -> "A raiz precisa sair como " + CANONICO + "/\n" + xml);
        assertTrue(xml.contains("<loc>" + CANONICO + "/analise</loc>"),
                () -> "As demais páginas seguem o mesmo host.\n" + xml);
        // Só as entradas: o xmlns do próprio formato é http:// e não é URL de página.
        assertFalse(xml.contains("<loc>http://"),
                () -> "Nenhuma entrada pode sair em http:// — o Google trata como outro site.\n" + xml);
        assertFalse(xml.contains("localhost"),
                () -> "O host da requisição não pode vazar para o sitemap de produção.\n" + xml);
    }
}
