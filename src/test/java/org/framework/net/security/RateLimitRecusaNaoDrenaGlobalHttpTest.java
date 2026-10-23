package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Revisão de boa-fé: requisição RECUSADA pelo balde da rota também consumia o balde global. Um painel em
 * polling (Tráfego ao vivo, 1/s) aberto por meia turma atrás do mesmo NAT esgotava o global em segundos
 * e derrubava todos os outros módulos da turma até a janela virar.
 */
@QuarkusTest
@TestProfile(RateLimitRecusaNaoDrenaGlobalHttpTest.Perfil.class)
class RateLimitRecusaNaoDrenaGlobalHttpTest {

    static final int POR_ROTA = 2;
    static final int GLOBAL = 8;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", String.valueOf(POR_ROTA),
                    "framework.security.rate-limit-heavy-per-minute", String.valueOf(POR_ROTA),
                    "framework.security.rate-limit-global-per-minute", String.valueOf(GLOBAL));
        }
    }

    @Test
    void recusasDeUmaRotaNaoBloqueiamAsOutras() {
        // 12 na mesma rota: no máximo 2 aprovadas por janela (4 se a janela virar) — as recusadas não
        // podem somar no global. Com o defeito, 12 > 8 e a rota seguinte já nasce bloqueada.
        for (int i = 0; i < 12; i++) {
            given().when().get("/portas").then().extract().statusCode();
        }
        assertEquals(200, given().when().get("/camadas").then().extract().statusCode(),
                "recusas em /portas drenaram o teto global e bloquearam /camadas");
    }
}
