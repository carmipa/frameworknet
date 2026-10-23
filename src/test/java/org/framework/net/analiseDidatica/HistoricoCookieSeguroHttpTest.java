package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Revisão pós-implementação (adversarial): em produção o cookie do histórico precisa do prefixo
 * {@code __Host-}. Sem ele, um subdomínio irmão (ou quem controla a rede num http://x.carminati.dev.br)
 * planta um identificador conhecido no navegador da vítima e depois lê o IP e a geolocalização que a
 * vítima consultou — o vazamento que o F07 fechou, reaberto por fixação de sessão. O servidor só
 * confia no nome com prefixo; o cookie sem prefixo é ignorado.
 */
@QuarkusTest
@TestProfile(HistoricoCookieSeguroHttpTest.Perfil.class)
class HistoricoCookieSeguroHttpTest {

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("framework.security.cookie-secure", "true");
        }
    }

    @Test
    void cookieSemPrefixoPlantadoNaoAbreSessaoDeOutro() {
        String plantado = "PPPPPPPPPPPPPPPPPPPPPP";
        String marca = "80 http-" + UUID.randomUUID();

        // A vítima navega com o cookie plantado (sem prefixo): o servidor não pode usá-lo.
        given().cookie("fnet_hist", plantado).contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"" + marca + "\"}")
                .when().post("/history/catalog").then().statusCode(200)
                .header("Set-Cookie", containsString("__Host-fnet_hist="));

        // O atacante, com o mesmo valor, não enxerga nada.
        given().cookie("fnet_hist", plantado)
                .when().get("/history").then().statusCode(200)
                .body(not(containsString(marca)));
    }

    @Test
    void cookieComPrefixoContinuaFuncionando() {
        String sessao = "HHHHHHHHHHHHHHHHHHHHHH";
        String marca = "443 https-" + UUID.randomUUID();
        given().cookie("__Host-fnet_hist", sessao).contentType("application/json")
                .body("{\"modo\":\"portas\",\"entrada\":\"" + marca + "\"}")
                .when().post("/history/catalog").then().statusCode(200);
        given().cookie("__Host-fnet_hist", sessao)
                .when().get("/history").then().statusCode(200)
                .body(containsString(marca));
    }
}
