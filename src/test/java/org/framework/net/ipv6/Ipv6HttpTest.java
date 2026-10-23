package org.framework.net.ipv6;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Guarda HTTP da Calculadora IPv6: a página abre, os assets são servidos, e os dois endpoints
 * (analisar e dividir) renderizam o fragmento com o conteúdo calculado — não só status 200.
 */
@QuarkusTest
class Ipv6HttpTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    void paginaCalculadoraCarregaComMenuEFormularios() {
        given()
                .when().get("/ipv6")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Calculadora IPv6 (CIDR)"))
                .body(containsString("hx-post=\"/ipv6/api/dividir\""))
                .body(containsString("data-tab=\"eui64\""))
                .body(containsString("data-tab=\"ula\""))
                .body(containsString("/ipv6/css/ipv6.css"));
    }

    @Test
    void paginaAnaliseCarregaComAnaliseComparadorEDominio() {
        given()
                .when().get("/ipv6/analise")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Análise Didática IPv6"))
                .body(containsString("data-active-tab=\"analise\""))
                .body(containsString("hx-post=\"/ipv6/api/calcular\""))
                .body(containsString("data-tab=\"comparador\""))
                .body(containsString("data-tab=\"dominio\""))
                .body(containsString("data-tab=\"nibbles\""))
                .body(containsString("data-tab=\"conceitos\""))
                .body(containsString("Histórico (neste navegador)"))
                .body(containsString("IPv4 → IPv6: o que muda"))
                .body(containsString("Hosts úteis = total − 2"));
    }

    @Test
    void paginaResolucaoCarregaComAbasProjetarEEngenharia() {
        given()
                .when().get("/ipv6/resolucao")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Resolução IPv6"))
                .body(containsString("data-active-tab=\"projetar\""))
                .body(containsString("data-tab=\"engenharia\""))
                .body(containsString("hx-post=\"/ipv6/api/projetar\""))
                .body(containsString("hx-post=\"/ipv6/api/engenharia\""));
    }

    @Test
    void projetarGeraPlanoComLansWansECli() {
        given()
                .contentType(FORM)
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoLan", "64")
                .formParam("prefixoWan", "127")
                .formParam("topologia", "estrela")
                .formParam("local", "Matriz")
                .formParam("local", "Filial")
                .formParam("local", "DataCenter")
                .formParam("ospfProc", "1")
                .formParam("eigrpAs", "100")
                .when().post("/ipv6/api/projetar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Plano de rede IPv6"))
                .body(containsString("2001:db8::/64"))
                .body(containsString("ipv6 unicast-routing"))
                .body(containsString("ipv6 router ospf"))
                .body(containsString("Diagrama de arquitetura"))
                .body(containsString("Baixar plano"));
    }

    @Test
    void projetarLanNaoMaisEspecificoVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoLan", "48")
                .formParam("prefixoWan", "127")
                .formParam("topologia", "estrela")
                .formParam("local", "A")
                .when().post("/ipv6/api/projetar")
                .then()
                .statusCode(400)
                .body(containsString("específico"));
    }

    @Test
    void engenhariaReversaLeConfigCiscoIpv6() {
        given()
                .contentType(FORM)
                .formParam("config", "hostname R1\nipv6 unicast-routing\ninterface GigabitEthernet0/0\n ipv6 address 2001:db8:0:1::1/64\nipv6 route 2001:db8:0:2::/64 2001:db8:0:ffff::1\nipv6 router ospf 1")
                .when().post("/ipv6/api/engenharia")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("GigabitEthernet0/0"))
                .body(containsString("2001:db8:0:1::1/64"))
                .body(containsString("OSPFv3"));
    }

    @Test
    void engenhariaReversaVaziaVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("config", "")
                .when().post("/ipv6/api/engenharia")
                .then()
                .statusCode(400)
                .body(containsString("Cole uma configuração"));
    }

    @Test
    void menuTemDropdownIpv4EIpv6() {
        given()
                .when().get("/ipv6")
                .then()
                .statusCode(200)
                .body(containsString(">IPv4<"))
                .body(containsString(">IPv6<"))
                .body(containsString("href=\"/ipv6\""))
                .body(containsString("aed-nav-drop-toggle is-active"));
    }

    @Test
    void cssEJsServidosDaPastaPropria() {
        given().when().get("/ipv6/css/ipv6.css").then().statusCode(200).body(containsString(".ipv6-hextet"));
        given().when().get("/ipv6/js/ipv6.js").then().statusCode(200).body(containsString("data-limpar"));
    }

    @Test
    void analisarPrefixoDevolveTipoRedeEContagem() {
        given()
                .contentType(FORM)
                .formParam("endereco", "2001:db8::/48")
                .when().post("/ipv6/api/calcular")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Documentação"))
                .body(containsString("2^80"))
                .body(containsString("2001:db8::"))
                .body(containsString("Decomposição dos 128 bits"))
                .body(containsString("Delegação de prefixo"))
                .body(containsString("65536"));
    }

    @Test
    void dividirContaEListaSubredes() {
        given()
                .contentType(FORM)
                .formParam("bloco", "2001:db8::/32")
                .formParam("prefixoAlvo", "48")
                .when().post("/ipv6/api/dividir")
                .then()
                .statusCode(200)
                .body(containsString("65536"))
                .body(containsString("2001:db8::/48"))
                .body(containsString("Sub-redes que cabem"));
    }

    @Test
    void eui64RenderizaEnderecoSlaac() {
        given()
                .contentType(FORM)
                .formParam("prefixo", "2001:db8:0:1::/64")
                .formParam("mac", "00:1a:2b:3c:4d:5e")
                .when().post("/ipv6/api/eui64")
                .then()
                .statusCode(200)
                .body(containsString("2001:db8:0:1:21a:2bff:fe3c:4d5e"))   // RFC 5952 §4.2.2
                .body(containsString("EUI-64"));
    }

    @Test
    void ulaGeraPrefixoFd() {
        given()
                .contentType(FORM)
                .formParam("subnetId", "1")
                .when().post("/ipv6/api/ula")
                .then()
                .statusCode(200)
                .body(containsString("RFC 4193"))
                .body(containsString("/48"));
    }

    @Test
    void resolverPlanejaDelegacaoDePrefixo() {
        given()
                .contentType(FORM)
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoAlvo", "64")
                .formParam("nomes", "Matriz\nFilial\nServidores")
                .when().post("/ipv6/api/resolver")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Plano de endereçamento"))
                .body(containsString("Matriz"))
                .body(containsString("2001:db8::/64"))
                .body(containsString("2001:db8:0:1::/64"));
    }

    @Test
    void resolverAlvoNaoMaisEspecificoVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoAlvo", "48")
                .formParam("nomes", "A")
                .when().post("/ipv6/api/resolver")
                .then()
                .statusCode(400)
                .body(containsString("específico"));
    }

    @Test
    void compararDoisEnderecosMostraMesmaLanEContencao() {
        given()
                .contentType(FORM)
                .formParam("a", "2001:db8:0:1::/64")
                .formParam("b", "2001:db8:0:1:abcd::1")
                .when().post("/ipv6/api/comparar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Comparação de endereços"))
                .body(containsString("Mesma /64"))
                .body(containsString("contém"));
    }

    @Test
    void dominioVazioVolta400ComFragmento() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("dominio", "")
                .when().post("/ipv6/api/dominio")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("Informe um domínio"));
    }

    /**
     * F22: a recusa do kernel (shared.EnderecoBloqueadoException) é traduzida na fronteira do módulo;
     * o aluno continua recebendo 400 didático com o MOTIVO (A7 — a causa não vira "erro interno").
     */
    @Test
    void hostnameInternoVolta400ComMotivo() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("dominio", "metadata.google.internal")
                .when().post("/ipv6/api/dominio")
                .then()
                .statusCode(400)
                .body(containsString("não permitido"));
    }

    @Test
    void exportJsonDaAnaliseIpv6() {
        given()
                .when().get("/ipv6/export/json?endereco=2001:db8::/48")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body(containsString("\"tipo\""))
                .body(containsString("Documentação"))
                .body(containsString("2^80"));
    }

    @Test
    void exportPdfDaAnaliseIpv6() {
        given()
                .when().get("/ipv6/export/pdf?endereco=2001:db8::/48")
                .then()
                .statusCode(200)
                .contentType(containsString("application/pdf"));
    }

    @Test
    void sumarizarQuatroContiguasMostraSupernet() {
        given()
                .contentType(FORM)
                .formParam("prefixos", "2001:db8:0:0::/64\n2001:db8:0:1::/64\n2001:db8:0:2::/64\n2001:db8:0:3::/64")
                .when().post("/ipv6/api/sumarizar")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Supernet"))
                .body(containsString("2001:db8::/62"));
    }

    @Test
    void faixaParaCidrCobreComUmBloco() {
        given()
                .contentType(FORM)
                .formParam("inicio", "2001:db8::")
                .formParam("fim", "2001:db8::ffff")
                .when().post("/ipv6/api/faixa")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("2001:db8::/112"));
    }

    @Test
    void faixaInvertidaVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("inicio", "2001:db8::ffff")
                .formParam("fim", "2001:db8::")
                .when().post("/ipv6/api/faixa")
                .then()
                .statusCode(400)
                .body(containsString("invertida"));
    }

    @Test
    void nibblesRenderizaExpandidoENibbles() {
        given()
                .contentType(FORM)
                .formParam("endereco", "2001:db8::1")
                .when().post("/ipv6/api/nibbles")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("2001:0db8:0000"))
                .body(containsString("32 nibbles"))
                .body(containsString("ip6.arpa"));
    }

    @Test
    void vlanPlanejaTresVlansComSviETrunk() {
        given()
                .contentType(FORM)
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoLan", "64")
                .formParam("vlanId", "10", "20", "30")
                .formParam("vlanNome", "Servidores", "Wi-Fi", "Voz")
                .when().post("/ipv6/api/vlan")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Plano de VLANs IPv6"))
                .body(containsString("interface Vlan10"))
                .body(containsString("2001:db8:0:1::"))
                .body(containsString("switchport trunk allowed vlan 10,20,30"));
    }

    @Test
    void vlanIdForaDeFaixaNoHtmxVolta400() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("base", "2001:db8::/48")
                .formParam("prefixoLan", "64")
                .formParam("vlanId", "5000")
                .formParam("vlanNome", "estoura")
                .when().post("/ipv6/api/vlan")
                .then()
                .statusCode(400)
                .body(containsString("VLAN ID"));
    }

    @Test
    void entradaInvalidaNoHtmxVolta400ComFragmento() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("endereco", "nao-e-ipv6")
                .when().post("/ipv6/api/calcular")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("Não foi possível calcular"));
    }

    @Test
    void alvoMaisAmploQueBaseVolta400() {
        given()
                .contentType(FORM)
                .formParam("bloco", "2001:db8::/48")
                .formParam("prefixoAlvo", "32")
                .when().post("/ipv6/api/dividir")
                .then()
                .statusCode(400)
                .body(containsString("amplo"));
    }
}
