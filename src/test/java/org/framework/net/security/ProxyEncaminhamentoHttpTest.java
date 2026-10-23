package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O que a aplicação acredita sobre quem chamou, atrás do proxy reverso.
 *
 * <p><b>Propósito de negócio:</b> em produção o Nginx fala HTTPS com o mundo e
 * HTTP com o container, e conta o resto por cabeçalho. Se a aplicação não os
 * ouvir, duas coisas quebram <b>sem erro nenhum</b>: toda URL absoluta sai em
 * {@code http://} (foi o que aconteceu com o redirect de login) e o rate limit
 * passa a ver o IP do proxy em todo visitante — os 120 req/min viram do site
 * inteiro, e um robô consome a cota de todos.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) {@code X-Forwarded-Proto} decide o
 * esquema; (2) {@code X-Forwarded-For} decide a chave do balde do rate limit, e
 * clientes distintos não compartilham balde. A configuração que sustenta as duas
 * é {@code proxy-address-forwarding=true} com {@code allow-forwarded=false} — com
 * este último ligado, o Vert.x lê só o cabeçalho {@code Forwarded}, que o Nginx
 * do NPM não envia, e ignora todos os {@code X-Forwarded-*}.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste mostra o que a aplicação
 * enxergou. Reprovar aqui significa que a configuração de proxy voltou a
 * silenciar os cabeçalhos.</p>
 *
 * <p><b>Cuidado ao ler este teste:</b> ele prova que o cabeçalho é <b>ouvido</b>,
 * não que ele é <b>confiável</b>. Quem garante a confiança é o par
 * {@code trusted-proxies} (aqui, a rede do Nginx) <b>mais</b> o Nginx mandar
 * {@code X-Forwarded-For $remote_addr}, que substitui em vez de anexar — o Vert.x
 * usa a <b>primeira</b> entrada da lista, então um Nginx que anexe devolve ao
 * visitante o poder de escolher o próprio balde.</p>
 */
@QuarkusTest
@TestProfile(ProxyEncaminhamentoTestProfile.class)
@DisplayName("Proxy reverso: esquema e identidade do cliente encaminhados")
class ProxyEncaminhamentoHttpTest {

    @Test
    @DisplayName("X-Forwarded-Proto decide o esquema das URLs absolutas")
    void esquemaVemDoCabecalhoDeEncaminhamento() {
        String xml = given()
                .header("Host", "frameworknet.carminati.dev.br")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "198.51.100.200")
                .when().get("/sitemap.xml")
                .then().statusCode(200)
                .extract().asString();

        // O sitemap serve de sonda por ser a única rota que monta URL absoluta a
        // partir da requisição quando não há host canônico configurado.
        assertTrue(xml.contains("<loc>https://frameworknet.carminati.dev.br/</loc>"),
                () -> "A aplicação não enxergou o esquema encaminhado pelo proxy.\n" + xml);
    }

    @Test
    @DisplayName("X-Forwarded-Host do cliente não muda o host das URLs absolutas (OPS-07)")
    void hostNaoVemDoCabecalhoDoCliente() {
        String xml = given()
                .header("Host", "frameworknet.carminati.dev.br")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "auditoria-ops.invalid")
                .header("X-Forwarded-Prefix", "/prefixo-forjado")
                .header("X-Forwarded-For", "198.51.100.201")
                .when().get("/sitemap.xml")
                .then().statusCode(200)
                .extract().asString();
        assertTrue(xml.contains("<loc>https://frameworknet.carminati.dev.br/</loc>")
                        && !xml.contains("auditoria-ops.invalid") && !xml.contains("prefixo-forjado"),
                () -> "Host ou prefixo do cliente entrou na URL absoluta.\n" + xml);
    }

    @Test
    @DisplayName("clientes distintos não compartilham o balde do rate limit")
    void cadaClienteTemSeuBalde() {
        // Com o limite em 2/min, três clientes distintos só passam se cada um
        // tiver o próprio balde. Se os X-Forwarded-For forem ignorados, os três
        // viram o mesmo endereço e o terceiro leva 429.
        List<Integer> status = new ArrayList<>();
        for (String ip : new String[]{"203.0.113.11", "203.0.113.22", "203.0.113.33"}) {
            status.add(given()
                    .header("X-Forwarded-For", ip)
                    .when().get("/health")
                    .then().extract().statusCode());
        }

        assertEquals(List.of(200, 200, 200), status,
                "Os três clientes caíram no mesmo balde: o X-Forwarded-For não chegou "
                        + "ao Quarkus. Status observados = " + status);
    }

    @Test
    @DisplayName("o mesmo cliente continua limitado — o balde existe de verdade")
    void oMesmoClienteEsbarraNoLimite() {
        // Caso-controle do teste acima: sem ele, um rate limit desligado faria os
        // três 200 passarem e o teste anterior aprovaria por engano.
        List<Integer> status = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            status.add(given()
                    .header("X-Forwarded-For", "198.51.100.99")
                    .when().get("/health")
                    .then().extract().statusCode());
        }

        assertEquals(List.of(200, 200, 429), status,
                "O rate limit precisa continuar limitando o mesmo cliente. "
                        + "Status observados = " + status);
    }
}
