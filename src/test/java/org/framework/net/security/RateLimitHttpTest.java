package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04: o limite era por (IP, caminho bruto). Rotas {@code /{slug}} davam um balde novo por slug
 * (limite inexistente e mapa crescendo com a escolha do atacante) e rotas caras tinham ficado fora do
 * "pesado". Os testes ligam o limitador com tetos pequenos e mandam o DOBRO do teto + 2 — mesmo que a
 * janela de um minuto vire no meio, sai ao menos um 429.
 *
 * <p>Revisão pós-implementação: este perfil deixa o teto GLOBAL alto de propósito. Antes os três testes
 * dividiam um global de 8 com o mesmo cliente, e depois que um deles o esgotava os outros recebiam 429
 * do global — passavam mesmo com o balde por rota quebrado. O global tem classe própria
 * ({@link RateLimitGlobalHttpTest}), com os tetos por rota altos.
 */
@QuarkusTest
@TestProfile(RateLimitHttpTest.Perfil.class)
class RateLimitHttpTest {

    static final int POR_ROTA = 5;
    static final int PESADO = 2;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", String.valueOf(POR_ROTA),
                    "framework.security.rate-limit-heavy-per-minute", String.valueOf(PESADO),
                    "framework.security.rate-limit-global-per-minute", "100000");
        }
    }

    @Test
    void slugAleatorioNaoGanhaBaldeNovo() {
        int limitados = 0;
        for (int i = 0; i < POR_ROTA * 2 + 2; i++) {
            int st = given().when().get("/protocolos/" + UUID.randomUUID()).then().extract().statusCode();
            if (st == 429) {
                limitados++;
            }
        }
        assertTrue(limitados > 0, "slugs distintos não podem burlar o limite por rota");
    }

    @Test
    void historicoDeCatalogoEhRotaPesada() {
        // Exatamente POR_ROTA requisições (A1): como rota comum, nenhuma daria 429; como pesada
        // (teto 2), dá ao menos um 429 mesmo com uma virada de janela (2 + 2 < 5).
        int limitados = 0;
        for (int i = 0; i < POR_ROTA; i++) {
            int st = given().contentType("application/json")
                    .body("{\"modo\":\"portas\",\"entrada\":\"443\"}")
                    .when().post("/history/catalog").then().extract().statusCode();
            if (st == 429) {
                limitados++;
            }
        }
        assertTrue(limitados > 0, "POST /history/catalog regrava o arquivo: tem de contar como pesado");
    }

    @Test
    void rotaComumDentroDoTetoNaoELimitada() {
        // Controle legítimo (A1): o mesmo cliente, numa rota comum, abaixo do teto por rota, passa —
        // prova que os 429 acima vêm do balde da rota, e não de um limitador que recusa tudo.
        for (int i = 0; i < POR_ROTA - 1; i++) {
            int st = given().when().get("/portas").then().extract().statusCode();
            assertTrue(st != 429, "rota comum abaixo do teto não pode receber 429 (requisição " + (i + 1) + ")");
        }
    }

    @Test
    void calculoIpv6LocalNaoEhPesadoMasResolucaoDnsE() {
        // Revisão de boa-fé (A1, mesma família /ipv6/api/): o cálculo local fica no limite comum — uma turma
        // atrás de NAT não pode travar em 30/min no exercício mais barato —, a resolução DNS continua pesada.
        for (int i = 0; i < POR_ROTA; i++) {
            int st = given().formParam("endereco", "2001:db8::1").when().post("/ipv6/api/calcular")
                    .then().extract().statusCode();
            assertTrue(st != 429, "cálculo IPv6 local limitado como pesado (requisição " + (i + 1) + ")");
        }
        int limitados = 0;
        for (int i = 0; i < POR_ROTA; i++) {
            if (given().formParam("dominio", "invalido..").when().post("/ipv6/api/dominio")
                    .then().extract().statusCode() == 429) {
                limitados++;
            }
        }
        assertTrue(limitados > 0, "resolução DNS IPv6 tem de continuar pesada");
    }
}
