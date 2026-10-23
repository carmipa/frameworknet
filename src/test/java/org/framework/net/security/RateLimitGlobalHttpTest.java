package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04: sem teto global, o total por cliente era o limite × número de rotas. Perfil próprio, com os tetos
 * POR ROTA altos: aqui só o balde global pode produzir 429, então o teste não passa por carona do limite
 * por rota (o defeito que a revisão pós-implementação achou no teste anterior, que misturava os dois).
 */
@QuarkusTest
@TestProfile(RateLimitGlobalHttpTest.Perfil.class)
class RateLimitGlobalHttpTest {

    static final int GLOBAL = 8;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", "100000",
                    "framework.security.rate-limit-heavy-per-minute", "100000",
                    "framework.security.rate-limit-global-per-minute", String.valueOf(GLOBAL));
        }
    }

    @Test
    void tetoGlobalPorClienteSomaTodasAsRotas() {
        String[] rotas = {"/", "/portas", "/protocolos", "/camadas", "/wifi", "/criptografia",
                "/ferramentas", "/certificados", "/sobre", "/documentacao"};
        int limitados = 0;
        for (int rep = 0; rep < 2; rep++) {
            for (String r : rotas) {
                if (given().when().get(r).then().extract().statusCode() == 429) {
                    limitados++;
                }
            }
        }
        // 20 requisições, 2 por rota: nenhuma rota chega perto do próprio teto; só a soma passa de 8
        // (mesmo com uma virada de janela no meio, 20 > 8 + 8).
        assertTrue(limitados > 0, "20 requisições em 10 rotas com teto global " + GLOBAL + " precisam gerar 429");
    }
}
