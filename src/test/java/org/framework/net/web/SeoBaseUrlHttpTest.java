package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O canonical com host canônico — o que produção vai servir.
 *
 * <p><b>Propósito de negócio:</b> canonical ausente custa indexação; canonical
 * <i>errado</i> custa mais, porque manda o buscador tratar o endereço declarado
 * como o oficial. Duas formas de errar são reais aqui: sair em {@code http://}
 * num site que responde nos dois esquemas — e aí a versão indexada seria a
 * insegura —, ou seguir o cabeçalho {@code Host} da requisição, que é escolha de
 * quem chama e permitiria a um terceiro apontar a URL canônica deste site para o
 * domínio dele. O host vem da configuração justamente para que nenhuma das duas
 * dependa do proxy estar certo.</p>
 *
 * <p><b>Invariantes do domínio:</b> configurado {@code framework.site.base-url},
 * <b>todo</b> canonical sai naquele host e esquema, mesmo quando a requisição
 * chega em {@code http://localhost} — que é exatamente como o container a recebe
 * atrás do Nginx — e mesmo quando o cliente manda um {@code Host} diferente.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste mostra o canonical gerado. É
 * o mesmo desenho do {@code SitemapBaseUrlHttpTest}, e de propósito: sitemap e
 * canonical declarando hosts diferentes seria o site contradizendo a si mesmo, e
 * as duas peças leem a mesma propriedade.</p>
 */
@QuarkusTest
@TestProfile(SitemapBaseUrlTestProfile.class)
@DisplayName("canonical: host configurado vence o da requisição")
class SeoBaseUrlHttpTest {

    private static final String CANONICO = "https://frameworknet.carminati.dev.br";

    private static final Pattern CANONICAL =
            Pattern.compile("<link rel=\"canonical\" href=\"([^\"]*)\">");

    @Test
    @DisplayName("mesmo chegando por http://localhost, o canonical sai no host público")
    void canonicalSaiNoHostCanonico() {
        assertEquals(CANONICO + "/", canonicalDe("/"),
                "A raiz precisa declarar o host público com barra final.");
        assertEquals(CANONICO + "/analise", canonicalDe("/analise"),
                "As demais páginas seguem o mesmo host.");

        String html = corpo("/portas");
        assertFalse(html.contains("<link rel=\"canonical\" href=\"http://"),
                () -> "Canonical em http:// num site https:// entrega a versão insegura ao índice.");
        assertFalse(html.contains("<link rel=\"canonical\" href=\"http://localhost"),
                () -> "O host da requisição não pode vazar para o canonical de produção.");
    }

    @Test
    @DisplayName("cabeçalho Host forjado não muda o canonical")
    void hostForjadoNaoMudaOCanonical() {
        // O canonical declara o endereço OFICIAL da página. Se ele seguisse o
        // cabeçalho Host, bastaria um pedido com Host de terceiro para que o site
        // dissesse ao buscador que a versão canônica mora no domínio do atacante.
        String html = given()
                .header("Host", "dominio-de-terceiro.example")
                .header("X-Forwarded-Host", "dominio-de-terceiro.example")
                .when().get("/analise")
                .then().extract().asString();

        Matcher m = CANONICAL.matcher(html);
        assertTrue(m.find(), () -> "Esperava canonical em /analise.");
        assertEquals(CANONICO + "/analise", m.group(1),
                "O canonical tem de ignorar o Host da requisição e usar o host configurado.");
        assertFalse(html.contains("dominio-de-terceiro.example"),
                () -> "O host enviado pelo cliente não pode aparecer no head.");
    }

    private static String corpo(String rota) {
        return given().when().get(rota).then().extract().asString();
    }

    private static String canonicalDe(String rota) {
        String html = corpo(rota);
        Matcher m = CANONICAL.matcher(html);
        assertTrue(m.find(), () -> "Esperava um canonical em " + rota);
        return m.group(1);
    }
}
