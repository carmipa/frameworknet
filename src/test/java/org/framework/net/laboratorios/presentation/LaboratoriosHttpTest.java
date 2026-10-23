package org.framework.net.laboratorios.presentation;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/** Guarda HTTP das experiências publicadas pela área Laboratórios. */
@QuarkusTest
class LaboratoriosHttpTest {
    /**
     * PROPÓSITO DE NEGÓCIO: impede publicar uma experiência anunciada cujo
     * template ou script não renderize.
     * INVARIANTES DO DOMÍNIO: a página mantém simulação local e painel de dados.
     * COMPORTAMENTO EM CASO DE FALHA: resposta diferente de 200 reprova o build.
     */
    @Test
    void aneisERedeRenderizaComFluxoEDados() {
        given().when().get("/laboratorios/aneis-e-rede").then().statusCode(200)
                .body(containsString("Da aplicação até a placa de rede"))
                .body(containsString("rings-dados-reais"))
                .body(containsString("rings-seta-3"))
                .body(containsString("aneis-e-rede.js"));
    }

    /**
     * PROPÓSITO DE NEGÓCIO: a página não é só um simulador — ensina o conceito. Esta guarda
     * afirma que o conteúdo didático (mapa de anéis, capabilities, contextos "negativos" e a
     * honestidade sobre kernel-bypass) está renderizado, para que um edit futuro que o esvazie
     * reprove o build em vez de publicar uma página rasa.
     * INVARIANTES DO DOMÍNIO: conceito de Ring 3/Ring 0, capability de rede e limites da metáfora
     * dos "anéis negativos" permanecem na página.
     * COMPORTAMENTO EM CASO DE FALHA: ausência de qualquer bloco didático reprova o build.
     */
    @Test
    void aneisERedeTemProfundidadeDidatica() {
        given().when().get("/laboratorios/aneis-e-rede").then().statusCode(200)
                .body(containsString("O que são anéis de privilégio"))
                .body(containsString("Ring&nbsp;0"))
                .body(containsString("CAP_NET_RAW"))
                .body(containsString("CAP_NET_BIND_SERVICE"))
                .body(containsString("Intel ME"))
                .body(containsString("kernel bypass"));
    }
}
