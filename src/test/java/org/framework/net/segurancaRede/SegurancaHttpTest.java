package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class SegurancaHttpTest {

    @Test
    void aclPermitDaMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("MATCH - PERMITIDO"))
                .body(containsString("check_circle"))
                .body(containsString("Origem: 192.168.1.5 -&gt; Destino: 10.0.0.1:80"));
    }

    /** F15: IP com ponto sobrando ("192.168.1.5.") é IPv4 malformado → 400; o controle legítimo acima dá 200. */
    @Test
    void aclRecusaIpComPontoSobrando() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5.")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp host 10.0.0.1. eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    @Test
    void aclDenyDaMatchBloqueado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "deny tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("MATCH - BLOQUEADO"))
                .body(containsString("cancel"));
    }

    @Test
    void pacoteForaDaRegraDaNoMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp host 10.0.0.5 any eq 443")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"))
                .body(containsString("help_center"));
    }

    @Test
    void fragmentoNaoTrazOLayoutCompleto() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(not(containsString("<!DOCTYPE html>")))
                .body(not(containsString("<body")));
    }

    @Test
    void paginaTrazOFormularioLigadoAoHtmx() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("hx-post=\"/seguranca/api/testar\""))
                .body(containsString("hx-target=\"#resultadoContainer\""))
                .body(containsString("/web/js/htmx.min.js"));
    }

    @Test
    void erroDeRequisicaoHtmxVoltaComoFragmento() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("ERRO"))
                .body(containsString("Porta"));
    }

    @Test
    void portaNaoNumericaRetorna400Amigavel() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "abc")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400)
                .body(containsString("Porta"));
    }

    @Test
    void portaVaziaRetorna400() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    @Test
    void rejeitaCaracteresPerigosos() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit <script>")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    // ---- Contraexemplos que reprovariam a avaliação antiga (parecer C01) ----

    @Test
    void portaComparaExatoNaoSubstring() {
        // Antes: contains("eq 80") casava com "eq 8080". Porta 80 NÃO deve casar 8080.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp any any eq 8080")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"))
                .body(not(containsString("PERMITIDO")));
    }

    @Test
    void destinoForaDoAlcanceDaNoMatch() {
        // Antes: o IP de destino não participava da decisão. host 10.0.0.1 != 10.0.0.2 -> NO MATCH.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit ip any host 10.0.0.1")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.2")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"));
    }

    @Test
    void destinoNoAlcanceDaMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit ip any host 10.0.0.1")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("PERMITIDO"));
    }

    @Test
    void curingaDeRedeCasaFaixa() {
        // 192.168.1.0 0.0.0.255 cobre 192.168.1.99, mas não 192.168.2.5.
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 192.168.1.0 0.0.0.255 any eq 80")
                .formParam("ipOrigem", "192.168.1.99")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("PERMITIDO"));
    }

    @Test
    void curingaDeRedeForaDaFaixaDaNoMatch() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit tcp 192.168.1.0 0.0.0.255 any eq 80")
                .formParam("ipOrigem", "192.168.2.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(200)
                .body(containsString("NO MATCH"));
    }

    @Test
    void regraNaoSuportadaRetorna400() {
        // Sintaxe fora do suportado é recusada, não reinterpretada como "no match".
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "allow tcp any eq 80")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "10.0.0.1")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then()
                .statusCode(400);
    }

    // ---- Inspeção TLS (aba nova) ----

    @Test
    void paginaTrazAbaDeInspecaoTls() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"tls\""))
                .body(containsString("data-tab-panel=\"tls\""))
                .body(containsString("hx-post=\"/seguranca/api/tls\""))
                .body(containsString("hx-post=\"/seguranca/api/testar\""))
                .body(containsString("/web/js/aed-tabs.js"));
    }

    private static io.restassured.specification.RequestSpecification tls(
            String host, String cert, String dias, String self, String versao, String cipher) {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("hostAcessado", host)
                .formParam("nomeCertificado", cert)
                .formParam("diasParaExpirar", dias)
                .formParam("autoassinado", self)
                .formParam("versaoTls", versao)
                .formParam("cipher", cipher);
    }

    @Test
    void tlsConfiavelQuandoTudoOk() {
        tls("frameworknet.carminati.dev.br", "frameworknet.carminati.dev.br", "60", "nao",
                "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("CONFIÁVEL"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void tlsRecusaNomeDivergente() {
        tls("banco.com.br", "phishing.evil.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("FALHA"))
                .body(containsString("Nome (SAN)"));
    }

    @Test
    void tlsRecusaCertificadoExpirado() {
        tls("example.com", "example.com", "-3", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("EXPIRADO"));
    }

    /** Auditoria CALC-37: vence hoje ainda vale (até o notAfter); "EXPIRADO, 0 dia atrás" era falso. Fronteira: -1 expira. */
    @Test
    void tlsVencendoHojeAindaNaoEstaExpirado() {
        tls("example.com", "example.com", "0", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("Vence HOJE"))
                .body(not(containsString("EXPIRADO")));
        tls("example.com", "example.com", "-1", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("EXPIRADO"));
    }

    @Test
    void tlsAtencaoComTls12ECipherCbc() {
        tls("example.com", "example.com", "60", "nao", "TLS 1.2", "ECDHE-RSA-AES128-CBC-SHA")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("ACEITO COM RESSALVAS"))
                .body(containsString("ATENÇÃO"));
    }

    @Test
    void tlsCuringaCasaUmRotulo() {
        tls("www.example.com", "*.example.com", "60", "nao", "TLS 1.3", "TLS_CHACHA20_POLY1305_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("CONFIÁVEL"));
    }

    @Test
    void tlsDiasNaoNumericoRetorna400() {
        tls("example.com", "example.com", "abc", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }

    @Test
    void tlsRejeitaCaracteresPerigosos() {
        tls("<script>", "example.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }

    @Test
    void tlsRecusaCadeiaAutoassinada() {
        tls("example.com", "example.com", "60", "sim", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Cadeia de confiança"))
                .body(containsString("autoassinado"));
    }

    @Test
    void tlsRecusaProtocoloObsoleto() {
        tls("example.com", "example.com", "60", "nao", "TLS 1.0", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Protocolo"))
                .body(containsString("obsoleta"));
    }

    @Test
    void tlsRecusaCipherQuebrada() {
        tls("example.com", "example.com", "60", "nao", "TLS 1.2", "RC4-MD5")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Cipher"))
                .body(containsString("quebrada"));
    }

    @Test
    void tlsCuringaNaoCasaMultiplosRotulos() {
        // O curinga cobre UM rótulo: *.example.com casa www.example.com, mas NÃO www.sub.example.com.
        tls("www.sub.example.com", "*.example.com", "60", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(200)
                .body(containsString("RECUSADO PELO NAVEGADOR"))
                .body(containsString("Nome (SAN)"));
    }

    @Test
    void tlsDiasForaDeFaixaRetorna400() {
        tls("example.com", "example.com", "999999", "nao", "TLS 1.3", "TLS_AES_128_GCM_SHA256")
                .when().post("/seguranca/api/tls")
                .then()
                .statusCode(400);
    }

    // ---- Firewall com estado × sem estado (P01) ----

    @Test
    void paginaTrazAbaDeFirewallComEstado() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"estado\""))
                .body(containsString("data-tab-panel=\"estado\""))
                .body(containsString("/seguranca/api/estado?cenario=conexao-saida"));
    }

    @Test
    void estadoComparaOsDoisModelosNoMesmoFluxo() {
        given()
                .when().get("/seguranca/api/estado?cenario=conexao-saida")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("PERMITIDO"))
                .body(containsString("BLOQUEADO"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    /** CONT-50: a direção do pacote aparecia como o código interno ("SAIDA", sem acento) na tela. */
    @Test
    void estadoMostraADirecaoEmPortugues() {
        given()
                .when().get("/seguranca/api/estado?cenario=conexao-saida")
                .then()
                .statusCode(200)
                .body(containsString(">Saída</span>"))
                .body(containsString(">Entrada</span>"))
                .body(not(containsString(">SAIDA</span>")))
                .body(not(containsString(">ENTRADA</span>")));
    }

    @Test
    void estadoCenarioDesconhecidoRetorna400() {
        given()
                .when().get("/seguranca/api/estado?cenario=inexistente")
                .then()
                .statusCode(400);
    }

    // ---- Handshake TLS 1.3 interativo (P03) ----

    @Test
    void paginaTrazAbaHandshakeTls() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"handshake\""))
                .body(containsString("/seguranca/api/tls-handshake?cenario=sucesso"));
    }

    @Test
    void handshakeSucessoPercorreOsPassos() {
        given()
                .when().get("/seguranca/api/tls-handshake?cenario=sucesso")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("ClientHello"))
                .body(containsString("CertificateVerify"))
                .body(containsString("cifrado"))
                .body(containsString("conexão estabelecida"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void handshakeVersaoIncompativelAborta() {
        given()
                .when().get("/seguranca/api/tls-handshake?cenario=versao-incompativel")
                .then()
                .statusCode(200)
                .body(containsString("abortado"))
                .body(containsString("protocol_version"));
    }

    @Test
    void handshakeCenarioDesconhecidoRetorna400() {
        given()
                .when().get("/seguranca/api/tls-handshake?cenario=inexistente")
                .then()
                .statusCode(400);
    }

    // ---- Diagnóstico de alcançabilidade de fluxo (P05 v1) ----

    @Test
    void paginaTrazAbaDeFluxo() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"fluxo\""))
                .body(containsString("/seguranca/api/fluxo?cenario=alcanca"));
    }

    @Test
    void fluxoAlcancaMostraCaminhoCompleto() {
        given()
                .when().get("/seguranca/api/fluxo?cenario=alcanca")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("alcança o destino"))
                .body(containsString("Servidor de destino"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void fluxoAclNegaMostraOndeBloqueia() {
        given()
                .when().get("/seguranca/api/fluxo?cenario=acl-nega")
                .then()
                .statusCode(200)
                .body(containsString("bloqueado em Firewall"));
    }

    @Test
    void fluxoCenarioDesconhecidoRetorna400() {
        given()
                .when().get("/seguranca/api/fluxo?cenario=inexistente")
                .then()
                .statusCode(400);
    }

    // ---- Montador de topologia (P05 fase 2) ----

    private static final String TOPO =
            "host H1 vlan=10 gw=R1\n"
            + "switchl3 R1 vlans=10,20\n"
            + "firewall FW deny=tcp/23\n"
            + "server S1 vlan=20 porta=443\n"
            + "link H1 R1\nlink R1 FW\nlink FW S1";

    @Test
    void paginaTrazAbaMontarTopologia() {
        given()
                .when().get("/seguranca")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"topologia\""))
                .body(containsString("hx-post=\"/seguranca/api/topologia\""))
                .body(containsString("id=\"topo-texto\""));
    }

    @Test
    void topologiaAlcancaMostraDiagramaECaminho() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("topologia", TOPO)
                .formParam("origem", "H1")
                .formParam("destino", "S1")
                .formParam("porta", "443")
                .when().post("/seguranca/api/topologia")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("alcança o destino"))
                .body(containsString("graph LR"))
                .body(not(containsString("<!DOCTYPE html>")));
    }

    @Test
    void topologiaAclNegaBloqueiaNoFirewall() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("topologia", TOPO)
                .formParam("origem", "H1")
                .formParam("destino", "S1")
                .formParam("porta", "23")
                .when().post("/seguranca/api/topologia")
                .then()
                .statusCode(200)
                .body(containsString("bloqueado em"))
                .body(containsString("FW"));
    }

    @Test
    void topologiaInvalidaRetorna400() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("topologia", "gizmo X")
                .formParam("origem", "X")
                .formParam("destino", "X")
                .formParam("porta", "80")
                .when().post("/seguranca/api/topologia")
                .then()
                .statusCode(400);
    }

    // ---- Sintaxe e semântica do IOS (auditoria de 01/10/2026, CALC-10 / CONT-01) ----
    // Gabarito: Cisco IOS Security Command Reference, "access-list (IP extended/standard)" —
    // destino obrigatório na estendida; operador depois da ORIGEM compara a porta de ORIGEM;
    // ACL padrão (1–99) só tem origem; regra icmp não captura TCP.

    private static String testar(String regra, String protocolo, String portaOrigem, String portaDestino, int status) {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", regra)
                .formParam("protocolo", protocolo)
                .formParam("ipOrigem", "192.168.1.15")
                .formParam("portaOrigem", portaOrigem)
                .formParam("ipDestino", "8.8.8.8")
                .formParam("portaDestino", portaDestino)
                .when().post("/seguranca/api/testar")
                .then().statusCode(status)
                .extract().asString();
    }

    @Test
    void estendidaSemDestinoERecusada() {
        // A forma que o exemplo antigo da tela ensinava e o IOS recusa.
        String r = testar("permit tcp any eq 80", "tcp", "49152", "80", 400);
        org.junit.jupiter.api.Assertions.assertTrue(r.contains("exige o destino"), r);
    }

    @Test
    void portaDepoisDaOrigemEPortaDeOrigem() {
        // "permit tcp any eq 80 any": origem saindo da 80 — um cliente comum (49152 → 80) NÃO casa.
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any eq 80 any", "tcp", "49152", "80", 200).contains("NO MATCH"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any eq 80 any", "tcp", "80", "50000", 200).contains("MATCH - PERMITIDO"));
        // e a porta de destino vem depois do destino
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any any eq 80", "tcp", "49152", "80", 200).contains("MATCH - PERMITIDO"));
    }

    @Test
    void aclPadraoSoComOrigem() {
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("access-list 10 permit 192.168.1.0 0.0.0.255", "tcp", "49152", "80", 200).contains("MATCH - PERMITIDO"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("access-list 10 permit 10.0.0.0 0.255.255.255", "tcp", "49152", "80", 200).contains("NO MATCH"));
        String r = testar("access-list 10 permit tcp any any", "tcp", "49152", "80", 400);
        org.junit.jupiter.api.Assertions.assertTrue(r.contains("ACL padrão"), r);
    }

    @Test
    void protocoloDaRegraPrecisaCasarODoPacote() {
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("deny icmp any any", "tcp", "49152", "80", 200).contains("NO MATCH"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("deny icmp any any", "icmp", "", "", 200).contains("MATCH - BLOQUEADO"));
        // ip casa qualquer protocolo
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("deny ip any any", "udp", "5353", "53", 200).contains("MATCH - BLOQUEADO"));
    }

    @Test
    void operadoresDePortaNaFronteira() {
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any any range 20 21", "tcp", "49152", "21", 200).contains("MATCH - PERMITIDO"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any any range 20 21", "tcp", "49152", "22", 200).contains("NO MATCH"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any any gt 1023", "tcp", "49152", "1024", 200).contains("MATCH - PERMITIDO"));
        org.junit.jupiter.api.Assertions.assertTrue(
                testar("permit tcp any any gt 1023", "tcp", "49152", "1023", 200).contains("NO MATCH"));
    }
}
