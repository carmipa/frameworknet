package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.infrastructure.historico.HistoricoStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D26 (privacidade): pela API de geolocalização, o histórico guarda só o IP que a pessoa digitou. A detecção da
 * própria ligação, a consulta digitada do próprio IP e o teste de vazamento ({@code registrar=nao}) não entram; o
 * que entra vai sem o IP da conexão. A1: a consulta digitada de outro IP continua entrando. Endereços privados não
 * saem para a rede (a geolocalização responde "local" sem consultar terceiros).
 */
@QuarkusTest
class HistoricoSemDadoPessoalHttpTest {

    @Inject
    HistoricoStore historicoStore;

    private static String ipPrivadoUnico() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(1, 255) + "." + r.nextInt(1, 255) + "." + r.nextInt(1, 255);
    }

    private boolean registrado(String ip) {
        return historicoStore.listarTodos().stream().anyMatch(r -> ip.equals(r.get("ip_entrada")));
    }

    /** Contagem, não presença: o histórico é compartilhado pela suíte e outros testes gravam 127.0.0.1. */
    private long quantos(String ip) {
        return historicoStore.listarTodos().stream().filter(r -> ip.equals(r.get("ip_entrada"))).count();
    }

    private Map<String, Object> registro(String ip) {
        return historicoStore.listarTodos().stream().filter(r -> ip.equals(r.get("ip_entrada"))).findFirst().orElse(null);
    }

    @Test
    void historicoGuardaSoOQueFoiDigitadoENuncaOProprioIp() {
        String clienteIp = given().when().get("/api/informacoes/geo")
                .then().statusCode(200).extract().path("cliente_ip");
        assertNotNull(clienteIp, "a API tem de dizer qual IP de conexão enxergou");
        long antes = quantos(clienteIp);
        given().when().get("/api/informacoes/geo").then().statusCode(200);
        assertEquals(antes, quantos(clienteIp), "a detecção da própria ligação foi ao histórico");

        given().queryParam("ip", clienteIp).when().get("/api/informacoes/geo").then().statusCode(200);
        assertEquals(antes, quantos(clienteIp), "a consulta digitada do próprio IP foi ao histórico");

        String outro = ipPrivadoUnico();
        given().queryParam("ip", outro).when().get("/api/informacoes/geo").then().statusCode(200);
        assertTrue(registrado(outro), "a consulta digitada de outro IP tem de ir ao histórico");
        @SuppressWarnings("unchecked")
        Map<String, Object> geo = (Map<String, Object>) registro(outro).get("geo_consulta");
        assertNotNull(geo, "a consulta de geolocalização tem de acompanhar o registro");
        assertFalse(geo.containsKey("cliente_ip"), "o IP da conexão foi ao histórico: " + geo.keySet());
        assertEquals(outro, geo.get("consultado"));

        String doTeste = ipPrivadoUnico();
        given().queryParam("ip", doTeste).queryParam("registrar", "nao")
                .when().get("/api/informacoes/geo").then().statusCode(200);
        assertFalse(registrado(doTeste), "o teste de vazamento (registrar=nao) foi ao histórico");
    }
}
