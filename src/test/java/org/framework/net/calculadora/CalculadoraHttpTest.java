package org.framework.net.calculadora;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class CalculadoraHttpTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    void paginaCarregaComMenuEAbas() {
        given()
                .when().get("/calculadora")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Calculadora de Sub-redes e VLANs"))
                .body(containsString("data-calc-tab=\"dividir\""))
                .body(containsString("data-calc-tab=\"vlan\""))
                .body(containsString("data-calc-tab=\"agregar\""))
                .body(containsString("data-calc-tab=\"faixa\""))
                .body(containsString("/calculadora/css/calculadora.css"));
    }

    @Test
    void cssEJsDoModuloSaoServidosDaPastaPropria() {
        given()
                .when().get("/calculadora/css/calculadora.css")
                .then()
                .statusCode(200)
                .body(containsString(".calc-tab-panel"));

        given()
                .when().get("/calculadora/js/calculadora.js")
                .then()
                .statusCode(200)
                .body(containsString("calc-tab-trigger"));
    }

    @Test
    void camposDeCriterioNascemPreenchidosEComGrupoDeclarado() {
        // O JS liga required/disabled pelo data-alvo; sem essa marcação no HTML
        // o envio com campo vazio volta a vazar para o servidor (erro na telemetria).
        given()
                .when().get("/calculadora")
                .then()
                .statusCode(200)
                .body(containsString("data-alvo=\"grupo-prefixo\""))
                .body(containsString("data-alvo=\"grupo-quantidade\""))
                .body(containsString("data-alvo=\"grupo-hosts\""))
                .body(containsString("id=\"grupo-prefixo\""))
                .body(containsString("id=\"grupo-quantidade\""))
                .body(containsString("id=\"grupo-hosts\""))
                .body(containsString("name=\"quantidade\" inputmode=\"numeric\" placeholder=\"6\" value=\"6\""))
                .body(containsString("name=\"hosts\" inputmode=\"numeric\" placeholder=\"500\" value=\"500\""));
    }

    @Test
    void menuPrincipalTemOItemCalculadora() {
        given()
                .when().get("/calculadora")
                .then()
                .statusCode(200)
                .body(containsString("href=\"/calculadora\""))
                .body(containsString("aed-nav-link is-active"));
    }

    @Test
    void abaDaAnaliseApontaParaOEndpointDaCalculadora() {
        given()
                .when().get("/analise")
                .then()
                .statusCode(200)
                .body(containsString("data-tab=\"calculadora\""))
                .body(containsString("hx-post=\"/calculadora/api/dividir\""));
    }

    @Test
    void dividirBarra21EmBarra24DevolveOitoSubredes() {
        given()
                .contentType(FORM)
                .formParam("bloco", "192.168.0.0/21")
                .formParam("criterio", "prefixo")
                .formParam("prefixoAlvo", "24")
                .when().post("/calculadora/api/dividir")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("192.168.0.0/24"))
                .body(containsString("192.168.7.0/24"))
                .body(containsString("Sub-redes que cabem"))
                .body(containsString("Quantas cabem em cada prefixo"));
    }

    @Test
    void dividirPorHostsEscolheOPrefixoSuficiente() {
        given()
                .contentType(FORM)
                .formParam("bloco", "10.0.0.0/16")
                .formParam("criterio", "hosts")
                .formParam("hosts", "500")
                .when().post("/calculadora/api/dividir")
                .then()
                .statusCode(200)
                .body(containsString("/23"))
                .body(containsString("510"));
    }

    @Test
    void entradaInvalidaNoHtmxVolta400ComFragmentoDeErro() {
        given()
                .contentType(FORM)
                .header("HX-Request", "true")
                .formParam("bloco", "999.1.1.1/21")
                .formParam("criterio", "prefixo")
                .formParam("prefixoAlvo", "24")
                .when().post("/calculadora/api/dividir")
                .then()
                .statusCode(400)
                .contentType(containsString("text/html"))
                .body(containsString("Não foi possível calcular"))
                .body(containsString("0 a 255"));
    }

    @Test
    void entradaInvalidaSemHtmxVoltaTextoPuro() {
        given()
                .contentType(FORM)
                .formParam("bloco", "192.168.0.0/24")
                .formParam("criterio", "prefixo")
                .formParam("prefixoAlvo", "16")
                .when().post("/calculadora/api/dividir")
                .then()
                .statusCode(400)
                .contentType(containsString("text/plain"))
                .body(containsString("maior que o bloco base"));
    }

    @Test
    void planoDeVlanTrazMapaEConfiguracaoCisco() {
        given()
                .contentType(FORM)
                .formParam("bloco", "192.168.0.0/16")
                .formParam("prefixoPorVlan", "24")
                .formParam("vlanInicial", "10")
                .formParam("passoVlan", "10")
                .formParam("quantidade", "3")
                .formParam("nomes", "ADM,VENDAS,TI")
                .formParam("estrategia", "octeto")
                .when().post("/calculadora/api/vlan")
                .then()
                .statusCode(200)
                .body(containsString("192.168.10.0/24"))
                .body(containsString("192.168.30.0/24"))
                .body(containsString("switchport trunk allowed vlan 10,20,30"))
                .body(containsString("interface Vlan10"))
                .body(containsString("ip dhcp pool POOL_20"))
                .body(containsString("ADM"));
    }

    @Test
    void validadorDeVlanIdExplicaFaixaReservada() {
        given()
                .contentType(FORM)
                .formParam("vlanId", "1002")
                .when().post("/calculadora/api/vlan-id")
                .then()
                .statusCode(200)
                .body(containsString("Reservado Cisco"))
                .body(containsString("Token Ring"));
    }

    @Test
    void sumarizacaoDevolveRotaResumoEComandos() {
        given()
                .contentType(FORM)
                .formParam("redes", "192.168.0.0/24\n192.168.1.0/24\n192.168.2.0/24\n192.168.3.0/24")
                .when().post("/calculadora/api/sumarizar")
                .then()
                .statusCode(200)
                .body(containsString("192.168.0.0/22"))
                .body(containsString("Agregação exata"))
                .body(containsString("area 0 range 192.168.0.0 255.255.252.0"));
    }

    @Test
    void comparacaoDetectaContencao() {
        given()
                .contentType(FORM)
                .formParam("blocoA", "10.0.0.0/16")
                .formParam("blocoB", "10.0.5.0/24")
                .when().post("/calculadora/api/comparar")
                .then()
                .statusCode(200)
                .body(containsString("A contém B"))
                .body(containsString("A_CONTEM_B"));
    }

    @Test
    void comparacaoDeBlocosDisjuntosNaoAcusaConflito() {
        given()
                .contentType(FORM)
                .formParam("blocoA", "10.0.0.0/24")
                .formParam("blocoB", "10.0.1.0/24")
                .when().post("/calculadora/api/comparar")
                .then()
                .statusCode(200)
                .body(containsString("Sem conflito"))
                .body(not(containsString("A_CONTEM_B")));
    }

    @Test
    void faixaViraBlocosCidr() {
        given()
                .contentType(FORM)
                .formParam("inicio", "192.168.1.0")
                .formParam("fim", "192.168.1.255")
                .when().post("/calculadora/api/faixa")
                .then()
                .statusCode(200)
                .body(containsString("192.168.1.0/24"))
                .body(containsString("access-list 101 permit ip 192.168.1.0 0.0.0.255 any"));
    }

    /** Auditoria CALC-27: em /31 e /32 o "Último endereço do bloco" existe; saía "N/A". */
    @Test
    void faixaComBarra31E32MostraOUltimoEndereco() {
        given()
                .contentType(FORM)
                .formParam("inicio", "10.0.0.2")
                .formParam("fim", "10.0.0.4")
                .when().post("/calculadora/api/faixa")
                .then()
                .statusCode(200)
                .body(containsString("10.0.0.2/31"))
                .body(containsString("10.0.0.4/32"))
                .body(not(containsString("N/A (/31")))
                .body(not(containsString("N/A (/32")));
    }

    @Test
    void exportacaoCsvDaDivisaoTrazCabecalhoEContexto() {
        given()
                .queryParam("bloco", "192.168.0.0")
                .queryParam("prefixoBase", "21")
                .queryParam("criterio", "prefixo")
                .queryParam("prefixoAlvo", "24")
                .when().get("/calculadora/export/divisao.csv")
                .then()
                .statusCode(200)
                .contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("attachment"))
                .body(containsString("# total_subredes,8"))
                .body(containsString("indice,rede,prefixo,mascara,wildcard"))
                .body(containsString("192.168.7.0,24,255.255.255.0"));
    }

    @Test
    void exportacaoCsvDeVlansEscapaAListaDoTrunk() {
        given()
                .queryParam("bloco", "192.168.0.0")
                .queryParam("prefixoBase", "16")
                .queryParam("prefixoPorVlan", "24")
                .queryParam("vlanInicial", "10")
                .queryParam("passoVlan", "10")
                .queryParam("quantidade", "3")
                .queryParam("estrategia", "sequencial")
                .when().get("/calculadora/export/vlan.csv")
                .then()
                .statusCode(200)
                .contentType(containsString("text/csv"))
                .body(containsString("\"10,20,30\""))
                .body(containsString("vlan_id,nome,faixa,rede"));
    }
}
