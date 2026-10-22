package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Força bruta da chave administrativa pelo cabeçalho (auditoria SEC-06): a tentativa errada conta no
 * limite e vira 429; a chave certa continua passando depois disso (A1).
 */
@QuarkusTest
@TestProfile(ChaveAdminForcaBrutaHttpTest.Perfil.class)
class ChaveAdminForcaBrutaHttpTest {

    static final int LIMITE = 4;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.admin-api-key", "test-admin-secret",
                    "framework.security.admin-api-key-required", "true",
                    "framework.security.csrf-enabled", "false",
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", "100000",
                    "framework.security.rate-limit-heavy-per-minute", String.valueOf(LIMITE),
                    "framework.security.rate-limit-global-per-minute", "100000");
        }
    }

    @Test
    void chaveErradaEmSerieRecebe429EAChaveCertaContinua() {
        int recusas = 0;
        for (int i = 0; i < 3 * LIMITE; i++) {
            int status = given().header("Accept", "application/json").header("X-Admin-Api-Key", "errada-" + i)
                    .when().get("/export/json").then().extract().statusCode();
            if (status == 429) {
                recusas++;
            } else {
                assertEquals(401, status);
            }
        }
        assertTrue(recusas > 0, "tentativa errada em série precisa chegar ao limite");
        given().header("X-Admin-Api-Key", "test-admin-secret").when().get("/export/json").then().statusCode(200);
    }

    /**
     * BACK-05: o formulário de /admin/login testa a mesma chave e ficava no limite comum (40 tentativas sem 429,
     * medido). Agora conta no limite pesado: com o limite de teste em 4, a série chega ao 429.
     */
    @Test
    void formularioDoAdminEntraNoLimitePesado() {
        int recusas = 0;
        for (int i = 0; i < 3 * LIMITE; i++) {
            int status = given().redirects().follow(false).formParam("api_key", "errada-" + i)
                    .when().post("/admin/login").then().extract().statusCode();
            if (status == 429) {
                recusas++;
            }
        }
        assertTrue(recusas > 0, "tentativa errada em série no formulário precisa chegar ao limite");
    }
}
