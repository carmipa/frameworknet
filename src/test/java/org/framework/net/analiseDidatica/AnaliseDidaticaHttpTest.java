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

    /**
     * Lote 1 da auditoria: o POST /analise lê o corpo como mapa, caminho em que o teto de 256 KB por campo do Quarkus
     * não vale — cada campo ia até 1 MB e chegava ao DNS, ao log, à telemetria e ao histórico. Campo acima de 8192
     * caracteres é recusado com mensagem e campo marcado; o valor NÃO volta na página, e os campos que cabiam voltam
     * (a pessoa não redigita o CIDR).
     */
    @Test
    void campoAcimaDoTetoERecusadoSemEcoarOValor() {
        String colado = "zq".repeat(4097);
        String corpo = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "dominio")
                .formParam("ip", colado)
                .formParam("cidr", "27")
                .when().post("/analise")
                .then()
                .statusCode(200)
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertTrue(corpo.contains("passou de 8192 caracteres"),
                "a recusa veio sem a mensagem");
        org.junit.jupiter.api.Assertions.assertFalse(corpo.contains("zqzqzqzqzqzqzqzqzqzq"),
                "o valor recusado foi ecoado na página");
        org.junit.jupiter.api.Assertions.assertTrue(
                java.util.regex.Pattern.compile("name=\"ip\" class=\"[^\"]*is-invalid").matcher(corpo).find(),
                "o campo recusado não foi marcado");
        org.junit.jupiter.api.Assertions.assertTrue(
                java.util.regex.Pattern.compile("name=\"cidr\"[^>]*value=\"27\"").matcher(corpo).find(),
                "o CIDR que cabia no teto não voltou para a tela");
    }

    /**
     * A1: uma URL longa colada no modo Domínio (link de proteção de e-mail passa de 2 mil caracteres) fica dentro do
     * teto e cai na validação do próprio modo, que extrai o host. O host do teste passa de 253 de propósito: a
     * validação recusa sem consultar o DNS, e a mensagem fala do nome, não da URL.
     */
    @Test
    void campoNoTetoSegueParaAValidacaoDoModo() {
        String url = "https://" + "a".repeat(300) + ".invalid/caminho?p=" + "x".repeat(7000);
        org.junit.jupiter.api.Assertions.assertTrue(url.length() > 2048 && url.length() <= 8192);
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "dominio")
                .formParam("ip", url)
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(not(containsString("passou de 8192 caracteres")))
                .body(containsString("Nome de domínio longo demais"));
    }

    /**
     * Lote 1 (revisão adversarial): o corpo do /analise era lido como mapa, leitor sem teto de quantidade de campos — um
     * corpo de 1 MB com centenas de milhares de chaves montava três mapas inteiros na memória. Lido por @RestForm, a
     * enxurrada de campos é recusada pelo leitor do Quarkus (medido: 5000 campos → 400). A1: um envio com algumas
     * centenas de campos sobrando continua calculando.
     */
    @Test
    void enxurradaDeCamposERecusadaEEnvioNormalCalcula() {
        StringBuilder corpo = new StringBuilder("modo=cidr&ip=10.0.0.1&cidr=24");
        for (int i = 0; i < 5000; i++) {
            corpo.append("&k").append(i).append('=');
        }
        given()
                .contentType("application/x-www-form-urlencoded")
                .body(corpo.toString())
                .when().post("/analise")
                .then()
                .statusCode(400);
        given()
                .contentType("application/x-www-form-urlencoded")
                .body(corpo.substring(0, corpo.indexOf("&k300=")))
                .when().post("/analise")
                .then()
                .statusCode(200)
                .body(containsString("10.0.0.255"));
    }

    /**
     * Lote 1 (revisão adversarial): escape {@code %} inválido no corpo, com o CSRF aprovado (este perfil o desliga),
     * caía no leitor de formulário e virava 500 com stack trace no log, em toda rota de formulário. É erro de quem
     * enviou: 400, sem falha não tratada.
     */
    @Test
    void escapeInvalidoNoCorpoE400ENao500() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .body("modo=cidr&ip=10.0.0.1&cidr=24&x=%zz")
                .when().post("/analise")
                .then()
                .statusCode(400);
        given()
                .header("Accept", "*/*")
                .contentType("application/x-www-form-urlencoded")
                .body("vlans=10&x=%")
                .when().post("/calculadora/api/vlan")
                .then()
                .statusCode(400);
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
