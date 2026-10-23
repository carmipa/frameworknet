package org.framework.net.web;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.resolucaoProblemas.application.parsing.CenarioExemploReversa;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tetos de entrada HTTP na fronteira do uso legítimo (auditoria de 01/10/2026: SEC-07/CALC-03, OPS-04).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o teto padrão de 2048 bytes por campo devolvia 413 para o uso que as
 * próprias telas pedem — "cole todas as configurações que você tiver" recusava 3 roteadores, e o
 * Decodificador recusava um quadro Ethernet cheio. Ao mesmo tempo o corpo podia ter 10 MB.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a configuração de três roteadores (alguns KB) é aceita; o quadro de
 * 1514 bytes (MTU 1500 + 14 de cabeçalho Ethernet) é decodificado; um campo acima de 256K e um corpo
 * acima de 1M continuam recusados com 413 — os dois lados de cada fronteira são exercitados.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção mostra o status e o trecho esperado.</p>
 */
@QuarkusTest
class LimitesDeEntradaHttpTest {

    @TestHTTPResource("/trafego/api/decodificar")
    URI decodificar;

    /** Quadro TCP SYN de 54 bytes completado com zeros até 1514 bytes. */
    private static final String QUADRO_CHEIO = "aabbccddeeff1122334455660800"
            + "450005dc1c4640004006b1e6c0a80001c0a80002"
            + "d4310050000000000000000050027210e5770000"
            + "00".repeat(1514 - 54);

    @Test
    void decodificadorAceitaQuadroEthernetCheio() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", QUADRO_CHEIO)
                .formParam("camada", "auto")
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(200)
                .body(containsString("1514 bytes decodificados"));
    }

    @Test
    void engenhariaReversaAceitaTresRoteadoresComComentarios() {
        // O cenário de exemplo (três AS) com o comentário que um show running-config traz: passa de 4 KB.
        String config = CenarioExemploReversa.BGP_TRES_AS + "\n" + "!\n".repeat(1200);
        given()
                .formParam("action_type", "reverse")
                .formParam("aba", "reversa")
                .formParam("config_paste", config)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Enlaces ponto a ponto"))
                .body(not(containsString("grande demais")));
    }

    @Test
    void campoAcimaDoTetoContinuaRecusado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hex", "a".repeat(270_000))
                .when().post("/trafego/api/decodificar")
                .then()
                .statusCode(413);
    }

    @Test
    void corpoAcimaDoTetoContinuaRecusado() throws Exception {
        // Com "Expect: 100-continue" o servidor responde ANTES de receber o corpo; sem isso ele fecha a
        // conexão no meio do envio e o cliente vê erro de protocolo em vez do 413 que de fato saiu.
        HttpRequest req = HttpRequest.newBuilder(decodificar)
                .expectContinue(true)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[1_100_000]))
                .build();
        HttpResponse<String> r = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, r.statusCode());
    }

    /** N roteadores mínimos, cada um com uma LAN própria. */
    private static String roteadores(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("hostname R").append(i).append('\n')
                    .append("interface GigabitEthernet0/0\n")
                    .append(" ip address 10.").append(i / 250).append('.').append(i % 250).append(".1 255.255.255.0\n")
                    .append(" no shutdown\n!\n");
        }
        return sb.toString();
    }

    @Test
    void engenhariaReversaLimitaRoteadoresNaFronteira() {
        // Com o campo aceitando 256 KB, 1200 roteadores viravam 6,9 MB de resposta: o teto é 50.
        given().formParam("action_type", "reverse").formParam("aba", "reversa")
                .formParam("config_paste", roteadores(50))
                .when().post("/resolucao-problemas")
                .then().statusCode(200)
                .body(not(containsString("roteadores por vez")))
                .body(containsString("LANs reconstruídas"));
        given().formParam("action_type", "reverse").formParam("aba", "reversa")
                .formParam("config_paste", roteadores(51))
                .when().post("/resolucao-problemas")
                .then().statusCode(200)
                .body(containsString("lê até 50 roteadores por vez; o texto colado tem 51"))
                .body(containsString("hostname R50"));
    }

    /** {@code roteadores} roteadores com {@code porRoteador} interfaces cada (uma LAN /24 distinta por interface). */
    private static String roteadoresComInterfaces(int roteadores, int porRoteador) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int r = 0; r < roteadores; r++) {
            sb.append("hostname R").append(r).append('\n');
            for (int i = 0; i < porRoteador; i++, n++) {
                sb.append("interface GigabitEthernet0/").append(i).append('\n')
                        .append(" ip address 10.").append(n / 250).append('.').append(n % 250)
                        .append(".1 255.255.255.0\n no shutdown\n!\n");
            }
        }
        return sb.toString();
    }

    /**
     * BACK-01 (auditoria de 22/10/2026): sem teto de interfaces, 50 roteadores × 66 interfaces custavam 4,2 s de CPU e
     * 9,2 MB por requisição. O teto é o mesmo do IPv6: 200 interfaces somadas passa, 201 é recusado com a contagem.
     */
    @Test
    void engenhariaIpv4LimitaInterfacesNaFronteira() {
        given().formParam("action_type", "reverse").formParam("aba", "reversa")
                .formParam("config_paste", roteadoresComInterfaces(4, 50))
                .when().post("/resolucao-problemas")
                .then().statusCode(200)
                .body(not(containsString("interfaces com endereço IP por vez")))
                .body(containsString("LANs reconstruídas"));
        given().formParam("action_type", "reverse").formParam("aba", "reversa")
                .formParam("config_paste", roteadoresComInterfaces(4, 50) + roteadoresComInterfaces(1, 1).replace("hostname R0", "hostname R9"))
                .when().post("/resolucao-problemas")
                .then().statusCode(200)
                .body(containsString("lê até 200 interfaces com endereço IP por vez, somando todos os roteadores; o texto colado tem 201"));
    }

    /** Fronteira de boa-fé: um switch empilhado com 210 portas, só 2 com IP, é um equipamento só e passa. */
    @Test
    void engenhariaIpv4NaoContaInterfaceSemIp() {
        StringBuilder cfg = new StringBuilder("hostname SW-PILHA\n");
        for (int i = 0; i < 208; i++) {
            cfg.append("interface GigabitEthernet").append(i / 52 + 1).append("/0/").append(i % 52 + 1)
                    .append("\n switchport\n!\n");
        }
        cfg.append("interface Vlan10\n ip address 10.0.10.1 255.255.255.0\n!\n")
                .append("interface Vlan20\n ip address 10.0.20.1 255.255.255.0\n!\n");
        given().formParam("action_type", "reverse").formParam("aba", "reversa")
                .formParam("config_paste", cfg.toString())
                .when().post("/resolucao-problemas")
                .then().statusCode(200)
                .body(not(containsString("interfaces com endereço IP por vez")));
    }

    @Test
    void engenhariaIpv6LimitaInterfacesNaFronteira() {
        StringBuilder cfg = new StringBuilder("hostname R1\nipv6 unicast-routing\n");
        for (int i = 0; i < 201; i++) {
            cfg.append("interface Loopback").append(i).append("\n ipv6 address 2001:db8:").append(Integer.toHexString(i))
                    .append("::1/64\n");
        }
        String com201 = cfg.toString();
        String com200 = com201.substring(0, com201.lastIndexOf("interface Loopback200"));
        given().contentType("application/x-www-form-urlencoded").formParam("config", com200)
                .when().post("/ipv6/api/engenharia")
                .then().statusCode(200);
        given().contentType("application/x-www-form-urlencoded").formParam("config", com201)
                .when().post("/ipv6/api/engenharia")
                .then().statusCode(400)
                .body(containsString("até 200 interfaces"));
    }
}
