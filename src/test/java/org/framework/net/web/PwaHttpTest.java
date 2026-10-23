package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Requisitos de instalabilidade do PWA.
 *
 * <p><b>Propósito de negócio:</b> o botão "Instalar" na barra de endereço e o
 * atalho na área de trabalho só aparecem quando o navegador encontra manifest
 * válido, ícones <b>quadrados</b> de 192 e 512 px e um service worker com
 * handler de fetch. Falta qualquer um e a opção simplesmente não surge — sem
 * erro visível em lugar nenhum, que é o que torna a regressão difícil de notar.</p>
 *
 * <p><b>Invariantes do domínio:</b> o service worker não pode cachear rota
 * autenticada: o cache do navegador sobrevive ao logout e guardaria conteúdo
 * protegido no disco do visitante.</p>
 */
@QuarkusTest
@DisplayName("PWA: manifest, ícones e service worker")
class PwaHttpTest {

    @Test
    @DisplayName("o manifest é servido com Content-Type de manifest, não sem tipo")
    void manifestExisteEDeclaraOMinimoParaInstalar() {
        // Como estático o Quarkus o entregava SEM Content-Type — navegador estrito
        // descarta o manifest nessa condição e a instalação nunca é oferecida.
        given()
                .when().get("/manifest.webmanifest")
                .then()
                .statusCode(200)
                .contentType(containsString("application/manifest+json"))
                .body("name", containsString("Framework de Redes"))
                .body("short_name", is("Framework Net"))
                .body("start_url", is("/"))
                .body("scope", is("/"))
                .body("display", is("standalone"))
                .body("icons.size()", is(4));
    }

    @Test
    @DisplayName("os ícones do manifest existem e são quadrados de 192 e 512")
    void iconesQuadradosExistem() {
        for (String icone : new String[]{
                "/pwa/icone-192.png",
                "/pwa/icone-512.png",
                "/pwa/icone-192-maskable.png",
                "/pwa/icone-512-maskable.png"}) {
            given()
                    .when().get(icone)
                    .then()
                    .statusCode(200)
                    .contentType(containsString("image/png"));
        }
    }

    @Test
    void serviceWorkerEhServidoNaRaiz() {
        // Precisa estar na raiz: um sw.js servido de subpasta só teria escopo
        // daquela subpasta, e o PWA não cobriria o site inteiro.
        given()
                .when().get("/sw.js")
                .then()
                .statusCode(200)
                .body(containsString("addEventListener('fetch'"));
    }

    @Test
    @DisplayName("regressão: o service worker não pode cachear rota autenticada")
    void serviceWorkerNaoCacheiaRotaProtegida() {
        String sw = given().when().get("/sw.js").then().statusCode(200).extract().asString();

        for (String rota : new String[]{"/telemetria", "/admin", "/export", "/history"}) {
            org.junit.jupiter.api.Assertions.assertTrue(sw.contains("'" + rota + "'"),
                    "O service worker deve excluir " + rota + " do cache: "
                            + "o cache do navegador sobrevive ao logout.");
        }
        // Network-first é deliberado: cache-first serviria a versão anterior
        // depois de cada deploy até o usuário limpar o cache a mão.
        org.junit.jupiter.api.Assertions.assertTrue(sw.contains("fetch(req)"),
                "A estratégia deve ser network-first");
    }

    @Test
    void paginaOfflineExiste() {
        given()
                .when().get("/offline.html")
                .then()
                .statusCode(200)
                .body(containsString("Sem conexão"));
    }

    @Test
    @DisplayName("o layout referencia o manifest e registra o service worker")
    void layoutLigaOPwa() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("rel=\"manifest\""))
                .body(containsString("/web/js/pwa.js"))
                .body(containsString("name=\"theme-color\""))
                // apple-touch-icon tem de ser quadrado; o icone.png do projeto é
                // um banner 1376x768 e não serve.
                .body(containsString("apple-touch-icon\" href=\"/pwa/icone-192.png"));
    }

    @Test
    @DisplayName("o ícone da página é PNG quadrado com lado múltiplo de 48, como o Google exige no resultado de busca")
    void iconeAceitoPeloBuscador() {
        // O icone.png do projeto é um banner 1376x768: o serviço de favicons do Google
        // devolvia 404 para o host, e o resultado de busca mostrava o globo genérico.
        String html = given().when().get("/").then().statusCode(200).extract().asString();
        var m = java.util.regex.Pattern.compile("<link rel=\"icon\"[^>]*href=\"([^\"]+)\"").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "a página não declara <link rel=\"icon\">");
        byte[] png = given().when().get(m.group(1)).then().statusCode(200).extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(png.length > 24 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
                "o ícone declarado não é PNG: " + m.group(1));
        int largura = java.nio.ByteBuffer.wrap(png, 16, 4).getInt();
        int altura = java.nio.ByteBuffer.wrap(png, 20, 4).getInt();
        org.junit.jupiter.api.Assertions.assertTrue(largura == altura && largura % 48 == 0,
                "ícone " + largura + "x" + altura + " — o buscador exige quadrado com lado múltiplo de 48");
    }

    @Test
    @DisplayName("a CSP permite manifest e service worker")
    void cspPermiteOPwa() {
        var csp = given().when().get("/").then().statusCode(200)
                .extract().header("Content-Security-Policy");
        org.junit.jupiter.api.Assertions.assertTrue(csp.contains("manifest-src 'self'"), csp);
        org.junit.jupiter.api.Assertions.assertTrue(csp.contains("worker-src 'self'"), csp);
    }
}
