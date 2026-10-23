package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Compressão das respostas.
 *
 * <p><b>Propósito de negócio:</b> o globo 3D passou a ser servido pelo site (SEC-05) e tem 2,0 MB; sem
 * gzip, cada visitante novo baixava isso inteiro (o esm.sh entregava comprimido).</p>
 *
 * <p><b>Invariantes do domínio:</b> JS e CSS saem comprimidos para quem aceita gzip; HTML NÃO sai
 * comprimido, porque carrega o token de CSRF junto de texto que o cliente escolhe (ataque BREACH).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção nomeia o cabeçalho que faltou ou sobrou.</p>
 */
@QuarkusTest
class CompressaoHttpTest {

    /** Sem decodificar: o teste olha o cabeçalho que o servidor mandou, não o corpo já descomprimido. */
    private static final RestAssuredConfig CRU = RestAssuredConfig.config()
            .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());

    @Test
    void globoLocalSaiComprimido() {
        given().config(CRU).header("Accept-Encoding", "gzip")
                .when().get("/localizacao/vendor/globo-three.mjs")
                .then().statusCode(200).header("Content-Encoding", equalTo("gzip"));
    }

    @Test
    void htmlComTokenNaoSaiComprimido() {
        given().config(CRU).header("Accept-Encoding", "gzip")
                .when().get("/calculadora")
                .then().statusCode(200).header("Content-Encoding", nullValue());
    }
}
