package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A telemetria e o limite de requisições diante de quem dispara erro em série (auditoria SEC-02, SEC-03,
 * OPS-01, OPS-11). Perfil próprio com tetos baixos; cada teste usa marca única no caminho, porque a
 * telemetria de teste persiste entre execuções.
 */
@QuarkusTest
@TestProfile(TelemetriaTetoHttpTest.Perfil.class)
class TelemetriaTetoHttpTest {

    static final int TETO = 3;
    static final int LIMITE_POR_ROTA = 6;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.telemetria.teto-eventos-por-minuto", String.valueOf(TETO),
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", String.valueOf(LIMITE_POR_ROTA),
                    "framework.security.rate-limit-heavy-per-minute", "100000",
                    "framework.security.rate-limit-global-per-minute", "100000");
        }
    }

    @Inject
    TelemetriaStore store;

    private static String marca() {
        return "tt" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long eventosCom(String evento, String marca) {
        return store.snapshotEventos().stream()
                .filter(e -> evento.equals(e.evento()))
                .filter(e -> String.valueOf(e.httpPath()).contains(marca)
                        || String.valueOf(e.fields()).contains(marca))
                .count();
    }

    @Test
    void rotaInexistenteEmSerieRecebe429() {
        String m = marca();
        int recusas = 0;
        for (int i = 0; i < 2 * LIMITE_POR_ROTA + 2; i++) {
            int status = given().accept("text/html").when().get("/" + m + "-" + i).then().extract().statusCode();
            if (status == 429) {
                recusas++;
            } else {
                assertEquals(404, status);
            }
        }
        assertTrue(recusas > 0, "rota inexistente precisa passar pelo limite de requisições");
    }

    @Test
    void paginaDeErroEmSerieGravaNoMaximoOTeto() {
        String m = marca();
        int requisicoes = 4 * TETO;
        for (int i = 0; i < requisicoes; i++) {
            given().accept("text/html").when().get("/" + m + "-" + i);
        }
        long gravados = eventosCom("pagina_erro_exibida", m);
        assertTrue(gravados >= 1, "o primeiro erro aparece na telemetria");
        // 404 e 429 têm teto próprio: no máximo 2 × TETO, contra as 12 requisições sem teto.
        assertTrue(gravados <= 2L * TETO, requisicoes + " requisições gravaram " + gravados);
    }

    @Test
    void acessoComErroEmRotaExistenteGravaNoMaximoOTeto() {
        String m = marca();
        int requisicoes = 4 * TETO;
        for (int i = 0; i < requisicoes; i++) {
            given().when().get("/protocolos/" + m + "-" + i);
        }
        long gravados = eventosCom("http_access", m);
        assertTrue(gravados >= 1, "o primeiro erro aparece na telemetria");
        assertTrue(gravados <= 2L * TETO, requisicoes + " requisições gravaram " + gravados);
    }

    @Test
    void requestIdDoClienteGiganteNaoEhEcoadoNemGravado() {
        // Marca única: a telemetria de teste persiste entre execuções, e um evento de outra execução
        // com o mesmo valor reprovaria este teste sem relação com o código atual.
        String gigante = marca() + "x".repeat(15_000);
        String devolvido = given().header("X-Request-Id", gigante).when().get("/sobre")
                .then().statusCode(200).extract().header("X-Request-Id");
        assertTrue(devolvido != null && devolvido.length() <= 64, "devolvido: " + (devolvido == null ? null : devolvido.length()));
        assertTrue(store.snapshotEventos().stream().noneMatch(e -> gigante.equals(e.requestId())));
    }

    @Test
    void quebraDeLinhaNoCaminhoNaoForjaLinha() {
        String m = marca();
        given().urlEncodingEnabled(false).when().get("/protocolos/" + m + "%0Alinha-forjada");
        var eventos = store.snapshotEventos().stream()
                .filter(e -> String.valueOf(e.httpPath()).contains(m)).toList();
        assertFalse(eventos.isEmpty(), "a requisição precisa ter gerado evento para o teste valer");
        eventos.forEach(e -> {
            assertFalse(e.httpPath().contains("\n"), e.httpPath());
            assertTrue(e.httpPath().contains("%0A"), e.httpPath());
            assertFalse(String.valueOf(e.message()).contains("\n"));
        });
    }
}
