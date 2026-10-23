package org.framework.net.protocolos;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura HTTP das páginas de aprofundamento por protocolo.
 *
 * <p><b>Propósito de negócio:</b> as páginas de aprofundamento são conteúdo
 * didático — se abrirem em erro ou perderem uma seção, ninguém percebe pelo
 * build. Este teste prova que cada uma abre, que traz o menu principal e o
 * sub-menu de dois níveis do módulo (camada → protocolo), que a aba correta está
 * marcada e que o conteúdo essencial chegou ao HTML — tanto as páginas dedicadas
 * (BGP, SSH, DNS, TLS) quanto as genéricas (HTTP, FTP, SMTP, Telnet, Handshake).</p>
 *
 * <p><b>Invariantes do domínio:</b> a aba Geral continua respondendo em
 * {@code /protocolos} depois da chegada das sub-rotas — é a regressão que o
 * projeto já viu no módulo de Tráfego, quando dois {@code @Path} sob o mesmo
 * prefixo derrubaram uma das rotas para 404. A rota curinga {@code /{slug}} não
 * pode engolir as rotas dedicadas nem responder a slug inexistente com a página
 * de outro protocolo: slug desconhecido é 404.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota e o trecho
 * ausente no corpo da resposta.</p>
 */
@QuarkusTest
@DisplayName("Protocolos: páginas de aprofundamento por camada (dedicadas e genéricas)")
class ProtocolosAprofundamentoHttpTest {

    @Inject
    TelemetriaStore telemetriaStore;

    @ParameterizedTest(name = "{0} abre com o menu, o sub-menu e o título \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "/protocolos          | Catálogo de Protocolos de Rede",
            "/protocolos/bgp      | Border Gateway Protocol",
            "/protocolos/ssh      | Secure Shell",
            "/protocolos/dns      | Domain Name System",
            "/protocolos/tls      | Transport Layer Security",
            "/protocolos/http     | HTTP / HTTPS",
            "/protocolos/ftp      | FTP / TFTP",
            "/protocolos/smtp     | SMTP / POP3 / IMAP",
            "/protocolos/telnet   | Telnet",
            "/protocolos/handshake| Handshake",
            "/protocolos/tcp      | Transmission Control Protocol",
            "/protocolos/udp      | User Datagram Protocol",
            "/protocolos/ipv4     | Internet Protocol v4",
            "/protocolos/ipv6     | Internet Protocol v6",
            "/protocolos/arp      | Address Resolution Protocol",
            "/protocolos/icmp     | ICMP / ICMPv6"
    })
    void paginasDoModuloAbrem(String rota, String titulo) {
        given()
                .when().get(rota.trim())
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString("protocolo-subnav"))
                .body(containsString(titulo));
        // O statusCode(200) já prova que não é a página de erro do servidor; não
        // se checa a frase "Internal Server Error" porque o aprofundamento de HTTP
        // documenta, de propósito, o status 500 na tabela de códigos.
    }

    @ParameterizedTest(name = "{0} oferece a navegação por camada completa")
    @CsvSource({
            "/protocolos", "/protocolos/bgp", "/protocolos/ssh", "/protocolos/dns", "/protocolos/tls",
            "/protocolos/http", "/protocolos/ftp", "/protocolos/smtp", "/protocolos/telnet", "/protocolos/handshake",
            "/protocolos/tcp", "/protocolos/udp", "/protocolos/ipv4", "/protocolos/ipv6", "/protocolos/arp", "/protocolos/icmp"
    })
    void toda_pagina_do_modulo_leva_as_camadas(String rota) {
        // O nível 1 (camadas) aparece em TODA página: a aba Geral e uma aba por
        // camada. O nível 2 (protocolos) só mostra os da camada ativa — por isso
        // aqui se exige a estrutura de camadas, não o href de cada protocolo. A
        // asserção é independente da ordem de registro (não fixa qual é o 1º item).
        given()
                .when().get(rota)
                .then()
                .statusCode(200)
                .body(containsString("href=\"/protocolos\""))     // aba Geral
                .body(containsString("protocolo-subnav-camada"))  // abas de camada renderizaram
                .body(containsString("Aplicação"))
                .body(containsString("Transporte"))
                .body(containsString("Rede"));
    }

    @ParameterizedTest(name = "{0} marca o próprio protocolo como atual no sub-menu")
    @CsvSource({
            "/protocolos/bgp", "/protocolos/ssh", "/protocolos/dns", "/protocolos/tls",
            "/protocolos/http", "/protocolos/ftp", "/protocolos/smtp", "/protocolos/telnet", "/protocolos/handshake",
            "/protocolos/tcp", "/protocolos/udp", "/protocolos/ipv4", "/protocolos/ipv6", "/protocolos/arp", "/protocolos/icmp"
    })
    void abaAtivaEMarcada(String rota) {
        // Regex tolerante à quebra de linha do template: o href da rota visitada
        // carrega a classe is-active no nível 2 (protocolo-subproto).
        given().when().get(rota).then().statusCode(200)
                .body(matchesPattern("(?s).*href=\"" + rota + "\"\\s+class=\"protocolo-subproto is-active\".*"));
    }

    @Test
    @DisplayName("regressão: a aba Geral não pode ser engolida pelas sub-rotas")
    void catalogoContinuaRespondendoEOferecendoAprofundar() {
        given()
                .when().get("/protocolos")
                .then()
                .statusCode(200)
                .body(containsString("data-grid-table=\"protocolos\""))
                .body(containsString("Aprofundar"));
    }

    @Test
    @DisplayName("slug inexistente responde 404, nunca a página de outro protocolo")
    void slugDesconhecidoNaoResolve() {
        given().when().get("/protocolos/naoexiste").then().statusCode(404);
        given().when().get("/protocolos/bgp2").then().statusCode(404);
    }

    @Test
    @DisplayName("BGP: o conteúdo operacional chegou ao HTML")
    void conteudoDoBgp() {
        given()
                .when().get("/protocolos/bgp")
                .then()
                .statusCode(200)
                .body(containsString("AS_PATH"))
                .body(containsString("LOCAL_PREF"))
                .body(containsString("Established"))
                .body(containsString("maximum-prefix"))
                .body(containsString("next-hop-self"))
                .body(containsString("Route Reflector"))
                .body(containsString("RPKI"));
    }

    @Test
    @DisplayName("SSH: o conteúdo operacional chegou ao HTML")
    void conteudoDoSsh() {
        given()
                .when().get("/protocolos/ssh")
                .then()
                .statusCode(200)
                .body(containsString("known_hosts"))
                .body(containsString("ed25519"))
                .body(containsString("ProxyJump"))
                .body(containsString("PermitRootLogin"))
                .body(containsString("AuthenticationMethods"))
                .body(containsString("sshd_config"));
    }

    @Test
    @DisplayName("DNS: resolução recursiva, ataques e defesas chegaram ao HTML")
    void conteudoDoDns() {
        given()
                .when().get("/protocolos/dns")
                .then()
                .statusCode(200)
                .body(containsString("autoritativo"))
                .body(containsString("cache poisoning"))
                .body(containsString("Kaminsky"))
                .body(containsString("DNSSEC"))
                .body(containsString("RRSIG"))
                .body(containsString("dig +trace"));
    }

    @Test
    @DisplayName("TLS: handshake, cadeia e ciphers chegaram ao HTML")
    void conteudoDoTls() {
        given()
                .when().get("/protocolos/tls")
                .then()
                .statusCode(200)
                .body(containsString("ClientHello"))
                .body(containsString("forward secrecy"))
                .body(containsString("Certificate Transparency"))
                .body(containsString("HSTS"))
                .body(containsString("openssl s_client"))
                .body(containsString("TLS 1.3"));
    }

    @ParameterizedTest(name = "conteúdo genérico chegou ao HTML: {0}")
    @CsvSource(delimiter = '|', value = {
            "/protocolos/http     | stateless    | Set-Cookie | CSRF",
            "/protocolos/ftp      | PASV         | TFTP       | opcode",
            "/protocolos/smtp     | SPF          | DKIM       | DMARC",
            "/protocolos/telnet   | Telnet       | SSH        | texto plano",
            "/protocolos/handshake| SYN          | ACK        | LISTEN",
            "/protocolos/tcp      | SYN          | ESTABLISHED| sequência",
            "/protocolos/udp      | datagrama    | amplifica  | 53",
            "/protocolos/ipv4     | TTL          | fragment   | NAT",
            "/protocolos/ipv6     | SLAAC        | 128        | salto",
            "/protocolos/arp      | MAC          | spoofing   | broadcast",
            "/protocolos/icmp     | traceroute   | ping       | echo"
    })
    void conteudoDosGenericos(String rota, String t1, String t2, String t3) {
        given()
                .when().get(rota.trim())
                .then()
                .statusCode(200)
                .body(containsString(t1.trim()))
                .body(containsString(t2.trim()))
                .body(containsString(t3.trim()));
    }

    @Test
    @DisplayName("genéricos: o diagrama Mermaid e a régua de bits (quando há) chegam ao HTML")
    void diagramaEReguaChegam() {
        // Handshake tem diagrama de sequência (Mermaid); TCP e IPv4 têm o cabeçalho
        // binário completo (régua de bits proporcional).
        given().when().get("/protocolos/handshake").then().statusCode(200)
                .body(containsString("aprof-mermaid"))
                .body(containsString("sequenceDiagram"));
        given().when().get("/protocolos/tcp").then().statusCode(200)
                .body(containsString("aprof-regua"))
                .body(containsString("aprof-regua-campo"));
        given().when().get("/protocolos/ipv4").then().statusCode(200)
                .body(containsString("aprof-regua-campo"))
                .body(containsString("TTL"));
    }

    @ParameterizedTest(name = "bespoke {0} ganhou diagrama Mermaid e régua de bits")
    @CsvSource({"/protocolos/dns", "/protocolos/bgp", "/protocolos/ssh", "/protocolos/tls"})
    void bespokeGanharamDiagramaEReguaDeBits(String rota) {
        // As 4 páginas dedicadas passaram a incluir os partials compartilhados de
        // diagrama (Mermaid) e cabeçalho binário (régua) — mesma decomposição das
        // páginas genéricas, via a fonte única DiagramaArquitetura/CabecalhoBinario.
        given()
                .when().get(rota)
                .then()
                .statusCode(200)
                .body(containsString("aprof-mermaid"))
                .body(containsString("sequenceDiagram"))
                .body(containsString("aprof-regua-campo"));
    }

    @ParameterizedTest(name = "telemetria: visita a {0} emite aprofundamento_view(protocolo={1}) no módulo protocolos")
    @CsvSource({
            "/protocolos/bgp, bgp",
            "/protocolos/ssh, ssh",
            "/protocolos/dns, dns",
            "/protocolos/tls, tls",
            "/protocolos/http, http",
            "/protocolos/ftp, ftp",
            "/protocolos/smtp, smtp",
            "/protocolos/telnet, telnet",
            "/protocolos/handshake, handshake",
            "/protocolos/tcp, tcp",
            "/protocolos/udp, udp",
            "/protocolos/ipv4, ipv4",
            "/protocolos/ipv6, ipv6",
            "/protocolos/arp, arp",
            "/protocolos/icmp, icmp"
    })
    void visitaGeraEventoDeTelemetria(String rota, String protocolo) {
        given().when().get(rota).then().statusCode(200);

        Optional<TelemetriaEvent> evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "aprofundamento_view".equals(e.evento()))
                .filter(e -> e.fields() != null && protocolo.equals(e.fields().get("protocolo")))
                .findFirst();

        assertTrue(evento.isPresent(),
                "Sem evento aprofundamento_view para " + protocolo + ", o dashboard não distingue quem abriu "
                        + "o catálogo de quem abriu o aprofundamento.");
        assertEquals("protocolos", evento.get().modulo(),
                "Módulo divergente da atribuição de " + rota + " contaria a mesma visita duas vezes.");
    }
}
