package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class AnaliseDidaticaHttpTest {

    @Test
    void postCidrExibeBlocoCompletoIpv4() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "192.168.1.10")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Decomposição bitwise AND"))
                .body(containsString("Linha do tempo do bloco"))
                .body(containsString("Régua de sub-redes"));
    }

    @Test
    void postCidr31() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.0")
                .formParam("cidr", "31")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/31"));
    }

    @Test
    void postCidrSemBarraInfereClassful() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.5.5.5")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/8"));
    }

    @Test
    void postCidrClasseCInfere24() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "200.1.1.1")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/24"));
    }

    @Test
    void postWildcardInvalida() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "wildcard")
                .formParam("ip", "172.16.8.8")
                .formParam("wildcard_mask", "0.0.255.0")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Wildcard"));
    }

    @Test
    void postDominioInvalido() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "dominio")
                .formParam("ip", "dominio@@invalido")
                .when().post("/analise")
                .then()
                .statusCode(200)
                // F23: "Domínio" é texto fixo da página; só a mensagem de validação prova a recusa.
                .body(containsString("Domínio/hostname inválido"));
    }

    @Test
    void postMascaraSomente2552551920() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "mask")
                .formParam("mask_decimal", "255.255.192.0")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/18"));
    }

    @Test
    void postIpv6() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "ipv6")
                .formParam("ipv6", "2001:db8::1")
                .when().post("/analise")
                .then()
                .statusCode(200)
                // F23: "IPv6" está no menu e "2001" é eco da entrada. A expansão e a classificação só
                // existem se o cálculo rodou (gabarito: RFC 4291 / RFC 3849).
                .body(containsString("2001:0db8:0000:0000:0000:0000:0000:0001"))
                .body(containsString("Documentação"));
    }

    @Test
    void postCidrComIpBarraNoCampoIp() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "172.16.0.10/24")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("/24"))
                .body(not(containsString("Não foi possível resolver o domínio")));
    }

    @Test
    void postComparadorCidrInvalidoMarcaCampo() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "comparador")
                .formParam("ip", "10.0.0.1")
                .formParam("comparador_cidr_a", "99")
                .formParam("comparador_cidr_b", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("is-invalid"))
                .body(containsString("entre 0 e 32"));
    }

    /**
     * Auditoria CALC-01: 128.0.0.0 tem forma de máscara contígua (/1). Antes virava "/1" e o /16 digitado
     * sumia; agora é a primeira rede classe B, 65.536 endereços, com nota dizendo o que foi feito.
     */
    @Test
    void enderecoComFormaDeMascaraContinuaSendoEndereco() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "128.0.0.0")
                .formParam("cidr", "16")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("128.0.255.255"))
                .body(containsString("prefixo em uso /16"))
                .body(containsString("foi analisado como endereço"));
    }

    @Test
    void mascaraNoCampoDeEnderecoContinuaVirandoMascara() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "255.255.255.0")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("reconhecido como máscara pontuada"))
                .body(not(containsString("foi analisado como endereço")));
    }

    /**
     * Auditoria CONT-02/CALC-22: multicast e loopback renderizam (strict) sem gateway, DHCP, broadcast nem
     * o exemplo de roteamento montado com o próprio endereço (a página tem um exemplo fixo 172.16.0.0, que
     * não conta); o unicast logo ao lado continua com tudo.
     */
    @Test
    void enderecoQueNaoEHostRenderizaSemConfiguracaoDeHost() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "224.0.0.5")
                .formParam("cidr", "")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("multicast não tem broadcast"))
                .body(containsString("não recebe endereço por DHCP"))
                .body(not(containsString("network 224.0.0.")));
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "127.0.0.1")
                .formParam("cidr", "8")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("Loopback (localhost)"))
                .body(not(containsString("network 127.0.0.0")));
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.5")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("network 10.0.0.0 0.0.0.255"))
                .body(containsString("10.0.0.2 até 10.0.0.254"));
    }

    /** Auditoria CALC-06: IPv6 com prefixo na aba IPv6 dava 500. */
    @Test
    void ipv6ComPrefixoNaoDerrubaAPagina() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "ipv6")
                .formParam("ipv6", "2001:db8::1/64")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("2001:db8::/64"))
                .body(not(containsString("/64/64")));
    }

    /** Auditoria CALC-35: o "/8" digitado junto do endereço vence o prefixo que sobrou no outro campo. */
    @Test
    void barraNoCampoDeEnderecoVenceOPrefixoAntigoComAviso() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.0/8")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("10.255.255.255"))
                .body(containsString("prefixo em uso /8"))
                .body(containsString("O /8 digitado junto do endereço prevaleceu sobre o /24"));
    }

    @Test
    void postComparador() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "comparador")
                .formParam("ip", "10.0.0.1")
                .formParam("comparador_cidr_a", "20")
                .formParam("comparador_cidr_b", "24")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(not(containsString("Erro:")));
    }
}
