package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Polling do Tráfego ao vivo fora da telemetria por requisição (auditoria OPS-06). Conta antes e
 * depois, porque a telemetria de teste persiste entre execuções; o controle positivo (/sobre) prova
 * que o mesmo instrumento enxerga evento novo.
 */
@QuarkusTest
class TelemetriaPollingHttpTest {

    @Inject
    TelemetriaStore store;

    private long acessosEm(String caminho) {
        return store.snapshotEventos().stream()
                .filter(e -> "http_access".equals(e.evento()) && caminho.equals(e.httpPath()))
                .count();
    }

    @Test
    void pollingNaoGeraEventoPorRequisicao() {
        long antes = acessosEm("/trafego/api/aovivo");
        for (int i = 0; i < 5; i++) {
            given().accept("application/json").when().get("/trafego/api/aovivo").then().statusCode(200);
        }
        assertEquals(antes, acessosEm("/trafego/api/aovivo"), "o polling não pode gravar evento por requisição");

        long sobreAntes = acessosEm("/sobre");
        given().when().get("/sobre").then().statusCode(200);
        assertTrue(acessosEm("/sobre") > sobreAntes, "controle positivo: rota comum continua gravando");
    }
}
