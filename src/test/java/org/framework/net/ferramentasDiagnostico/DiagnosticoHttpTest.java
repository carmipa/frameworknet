package org.framework.net.ferramentasDiagnostico;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DiagnosticoHttpTest {

    @Test
    void pingSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("8.8.8.8"))
                .body(containsString("Simulado"))
                // <pre>/<code> carimbados com translate="no" (regra de i18n): o que importa é o bloco
                // de saída existir, não a ausência de atributos.
                .body(org.hamcrest.Matchers.matchesPattern("(?s).*<pre[^>]*><code[^>]*>.*"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void dnsSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "example.com")
                .when().post("/diagnostico/api/dns")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("example.com"))
                .body(containsString("ANSWER SECTION"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void paginaTrazOsDoisFormulariosLigadosAoHtmx() {
        given()
                .when().get("/diagnostico")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/diagnostico/api/ping\""))
                .body(containsString("hx-post=\"/diagnostico/api/dns\""))
                .body(containsString("hx-target=\"#pingSaida\""))
                .body(containsString("hx-target=\"#dnsSaida\""))
                .body(not(containsString("diagnostico.js")));
    }

    @Test
    void pingTrazDissecacaoDidatica() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .body(containsString("diag-kpis"))          // indicadores
                .body(containsString("comando real"))        // o comando que representa
                .body(containsString("Como funciona"))       // explicação passo a passo
                .body(containsString("O que observar"))       // leitura
                .body(containsString("Legenda dos termos"))   // glossário
                .body(containsString("RTT"))                  // termo dissecado
                .body(containsString("Saída bruta"));          // terminal preservado
    }

    @Test
    void scanTrazTabelaDissecadaComEstados() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "scanme.example.com")
                .when().post("/diagnostico/api/scan")
                .then()
                .statusCode(200)
                .body(containsString("diag-tabela"))
                .body(containsString("diag-linha-success"))   // porta aberta destacada
                .body(containsString("diag-linha-warning"))   // porta filtrada destacada
                .body(containsString("Legenda dos termos"))
                .body(containsString("half-open"));
    }

    @Test
    void paginaTrazAsNovasAbasDeAnalista() {
        given()
                .when().get("/diagnostico")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"traceroute\""))
                .body(containsString("data-tab=\"sweep\""))
                .body(containsString("data-tab=\"scan\""))
                .body(containsString("data-tab=\"spoofing\""))
                .body(containsString("hx-post=\"/diagnostico/api/traceroute\""))
                .body(containsString("hx-post=\"/diagnostico/api/ping-sweep\""))
                .body(containsString("hx-post=\"/diagnostico/api/scan\""))
                .body(containsString("hx-post=\"/diagnostico/api/dns-spoofing\""))
                .body(containsString("/web/js/aed-tabs.js"));
    }

    @Test
    void tracerouteSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "1.1.1.1")
                .when().post("/diagnostico/api/traceroute")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("1.1.1.1"))
                .body(containsString("TTL"))
                .body(containsString("Simulado"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void pingSweepSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("rede", "192.168.1.0/24")
                .when().post("/diagnostico/api/ping-sweep")
                .then()
                .statusCode(200)
                .body(containsString("ping sweep"))
                .body(containsString("ATIVO"))
                .body(containsString("192.168.1"));
    }

    @Test
    void scanSynSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "scanme.example.com")
                .when().post("/diagnostico/api/scan")
                .then()
                .statusCode(200)
                .body(containsString("SYN stealth"))
                .body(containsString("filtered"))
                .body(containsString("22/tcp"));
    }

    @Test
    void dnsSpoofingSimuladoResponde() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "banco.example.com")
                .when().post("/diagnostico/api/dns-spoofing")
                .then()
                .statusCode(200)
                .body(containsString("spoofing"))
                .body(containsString("Transaction ID"))
                .body(containsString("DNSSEC"));
    }

    /**
     * CONT-08: no envenenamento de cache a resposta forjada vai ao RESOLVER, na corrida com o autoritativo
     * (RFC 5452 §3). A simulação mandava a forjada ao cliente e ainda dizia "cache envenenado".
     */
    @Test
    void envenenamentoMostraARespostaForjadaIndoAoResolver() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "banco.example.com")
                .when().post("/diagnostico/api/dns-spoofing")
                .then()
                .statusCode(200)
                .body(containsString("Resolver → Autoritativo"))
                .body(containsString("Atacante → Resolver"))
                .body(not(containsString("Atacante → Cliente")))
                .body(containsString("Autoritativo → Resolver"));
    }

    @ParameterizedTest(name = "{0} rejeita injeção de comando com 400")
    @CsvSource({
            "/diagnostico/api/ping,         host",
            "/diagnostico/api/dns,          dominio",
            "/diagnostico/api/traceroute,   host",
            "/diagnostico/api/ping-sweep,   rede",
            "/diagnostico/api/scan,         host",
            "/diagnostico/api/dns-spoofing, dominio"
    })
    void todosOsEndpointsRejeitamInjecao(String rota, String campo) {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam(campo.trim(), "1.1.1.1; rm -rf /")
                .when().post(rota.trim())
                .then()
                .statusCode(400);
    }

    @ParameterizedTest(name = "{0} rejeita <script> com 400")
    @CsvSource({
            "/diagnostico/api/ping,         host",
            "/diagnostico/api/dns,          dominio",
            "/diagnostico/api/traceroute,   host",
            "/diagnostico/api/ping-sweep,   rede",
            "/diagnostico/api/scan,         host",
            "/diagnostico/api/dns-spoofing, dominio"
    })
    void todosOsEndpointsRejeitamXss(String rota, String campo) {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam(campo.trim(), "<script>alert(1)</script>")
                .when().post(rota.trim())
                .then()
                .statusCode(400);
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmentoDeTerminal() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("terminal"))
                .body(containsString("Erro:"));
    }

    @Test
    void rejeitaInjecaoDeComando() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "8.8.8.8; rm -rf /")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresXss() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "<script>alert(1)</script>")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaHostVazio() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400);
    }

    /**
     * FRONT-05: a lista negra antiga deixava alvo impossível receber "4 de 4 respostas". Cada caso é
     * recusado com a regra que quebrou — a mensagem é a aula.
     */
    @ParameterizedTest(name = "{0} recusa {2}")
    @CsvSource(delimiter = '|', value = {
            "/diagnostico/api/ping         | host    | xyz!@#             | letras sem acento",
            "/diagnostico/api/ping         | host    | 999.999.999.999    | de 0 a 255",
            "/diagnostico/api/ping         | host    | 1.2.3              | 4 octetos",
            "/diagnostico/api/ping         | host    | 010.1.1.1          | zero à esquerda",
            "/diagnostico/api/traceroute   | host    | a..b.com           | parte vazia",
            "/diagnostico/api/traceroute   | host    | -a.example.com     | hífen",
            "/diagnostico/api/scan         | host    | abc.123            | só número",
            "/diagnostico/api/scan         | host    | 2001:db8::1        | IPv6 não entra",
            "/diagnostico/api/dns          | dominio | 8.8.8.8            | dig -x",
            "/diagnostico/api/dns          | dominio | exemplo_1.com      | letras sem acento",
            "/diagnostico/api/dns-spoofing | dominio | 203.0.113.10       | dig -x",
            "/diagnostico/api/ping-sweep   | rede    | 192.168.1.0/33     | /0 a /32",
            "/diagnostico/api/ping-sweep   | rede    | 2001:db8::/64      | 2^64",
            "/diagnostico/api/ping-sweep   | rede    | scanme.nmap.org/24 | rede IPv4"
    })
    void alvoImpossivelERecusadoComARegraQueQuebrou(String rota, String campo, String valor, String regra) {
        String corpo = recusa(rota, campo, valor);
        assertTrue(corpo.contains(regra), "esperava a regra '" + regra + "' em: " + corpo);
    }

    /** A1: o legítimo com o mesmo sinal do defeito passa — números e pontos no limite, hífen, ponto final. */
    @ParameterizedTest(name = "{0} aceita {2}")
    @CsvSource(delimiter = '|', value = {
            "/diagnostico/api/ping       | host    | 255.255.255.255",
            "/diagnostico/api/ping       | host    | 0.0.0.0",
            "/diagnostico/api/traceroute | host    | a-b.example.com",
            "/diagnostico/api/scan       | host    | scanme.nmap.org",
            "/diagnostico/api/dns        | dominio | xn--caf-dma.com",
            "/diagnostico/api/dns        | dominio | localhost",
            "/diagnostico/api/ping-sweep | rede    | 10.0.0.0/8"
    })
    void alvoLegitimoNaFronteiraPassa(String rota, String campo, String valor) {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam(campo, valor)
                .when().post(rota)
                .then()
                .statusCode(200);
    }

    /** A1 no limite do rótulo: 63 caracteres é o máximo da RFC 1035; 64 é recusado. */
    @Test
    void rotuloDe63PassaE64ERecusado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "a".repeat(63) + ".example.com")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200);
        String corpo = recusa("/diagnostico/api/ping", "host", "a".repeat(64) + ".example.com");
        assertTrue(corpo.contains("63 caracteres"), corpo);
    }

    /** Espaço colado nas pontas é descartado, e o ponto final do FQDN não vira "example.com..". */
    @Test
    void entradaENormalizadaAntesDeSerEcoada() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "  8.8.8.8  ")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(200)
                .body(containsString("ping 8.8.8.8"));
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "example.com.")
                .when().post("/diagnostico/api/dns")
                .then()
                .statusCode(200)
                .body(containsString("example.com."))
                .body(not(containsString("example.com..")));
    }

    /** O dig sorteava 1.0.0.0–254.x (multicast, loopback); agora responde da faixa RFC 5737, sempre a mesma por nome. */
    @Test
    void digRespondeDaFaixaDeDocumentacaoESempreIgual() {
        String primeira = given().contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "example.com")
                .when().post("/diagnostico/api/dns").then().statusCode(200).extract().asString();
        String segunda = given().contentType("application/x-www-form-urlencoded")
                .formParam("dominio", "example.com")
                .when().post("/diagnostico/api/dns").then().statusCode(200).extract().asString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("IN\\s+A\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(primeira);
        assertTrue(m.find(), "a ANSWER SECTION não trouxe registro A");
        String ip = m.group(1);
        assertTrue(ip.startsWith("203.0.113."), "IP fora da faixa de documentação: " + ip);
        assertTrue(segunda.contains("A\t" + ip) || segunda.contains(ip), "o mesmo nome deu outro IP");
    }

    /** A amostra do ping sweep fica dentro da faixa: um /30 tem só dois hosts, e a rede é calculada. */
    @Test
    void pingSweepAmostraSoEnderecosDaFaixa() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("rede", "192.168.1.77/30")
                .when().post("/diagnostico/api/ping-sweep")
                .then()
                .statusCode(200)
                .body(containsString("192.168.1.76/30"))
                .body(containsString("192.168.1.77"))
                .body(containsString("192.168.1.78"))
                .body(not(containsString("192.168.1.79")))
                .body(not(containsString("192.168.1.225")));
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("rede", "192.168.1.5")
                .when().post("/diagnostico/api/ping-sweep")
                .then()
                .statusCode(200)
                .body(containsString("192.168.1.5/32"))
                .body(not(containsString("192.168.1.6")));
    }

    /** Corpo da recusa em UTF-8 (pedido htmx, como a página faz); reprova se não vier 400. */
    private static String recusa(String rota, String campo, String valor) {
        byte[] corpo = given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam(campo, valor)
                .when().post(rota)
                .then()
                .statusCode(400)
                .extract().asByteArray();
        return new String(corpo, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * FRONT-03: a recusa em texto simples saía {@code text/plain} sem charset e o navegador mostrava
     * "inválido" como mojibake. O filtro de charset declara UTF-8 em toda resposta de texto.
     */
    @Test
    void recusaEmTextoSimplesDeclaraUtf8() {
        byte[] corpo = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("host", "xyz!@#")
                .when().post("/diagnostico/api/ping")
                .then()
                .statusCode(400)
                .contentType(containsString("text/plain"))
                .contentType(containsString("charset=UTF-8"))
                .extract().asByteArray();
        assertTrue(new String(corpo, java.nio.charset.StandardCharsets.UTF_8).contains("hífen"));
    }
}
