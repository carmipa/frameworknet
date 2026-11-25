package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.web.domain.PaginasPublicas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folha de impressão global (proposta C01): o Ctrl+P de qualquer página de conteúdo sai legível em papel branco.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o site não gera PDF; imprimir é a janela do navegador. Sem esta folha o tema escuro
 * imprimia texto claro sobre o papel branco (o navegador não imprime fundo por padrão).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> toda página pública carrega {@code /web/css/impressao.css} com
 * {@code media="print"} (não pesa na tela) e depois das folhas da própria página; o arquivo é servido como CSS e
 * esconde o menu. O resultado no papel (cores e o que some) é conferido em navegador real com a mídia de impressão
 * emulada, fora desta suíte.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova nomeando a página sem a folha, ou a folha que não é servida.</p>
 */
@QuarkusTest
@DisplayName("Web: folha de impressão em toda página pública")
class ImpressaoHttpTest {

    private static final Pattern FOLHA = Pattern.compile(
            "<link rel=\"stylesheet\" href=\"(/web/css/impressao\\.css\\?v=[0-9a-z]+)\" media=\"print\">");
    private static final Pattern OUTRA_FOLHA = Pattern.compile("<link[^>]*rel=\"stylesheet\"");

    @Test
    @DisplayName("toda página pública carrega a folha de impressão com media=print, depois das folhas da página")
    void todaPaginaCarregaAFolha() {
        List<String> semFolha = new ArrayList<>();
        String href = null;
        int conferidas = 0;
        for (String rota : PaginasPublicas.rotas()) {
            String html = given().when().get(rota).then().statusCode(200).extract().asString();
            conferidas++;
            Matcher m = FOLHA.matcher(html);
            if (!m.find()) {
                semFolha.add(rota);
                continue;
            }
            href = m.group(1);
            int fimDoHead = html.indexOf("</head>");
            Matcher depois = OUTRA_FOLHA.matcher(html.substring(m.end(), Math.max(m.end(), fimDoHead)));
            assertTrue(m.start() < fimDoHead && !depois.find(),
                    rota + ": a folha de impressão tem de vir no head, depois das folhas da página");
        }
        assertTrue(semFolha.isEmpty(), () -> "Página sem a folha de impressão: " + semFolha);
        assertTrue(conferidas >= 20, "só " + conferidas + " páginas conferidas: lista de páginas vazia?");

        String css = given().when().get(href).then().statusCode(200)
                .contentType(containsString("text/css")).extract().asString();
        assertTrue(css.contains(".aed-topnav") && css.contains("display: none !important"),
                "a folha servida não esconde o menu no papel");
        assertEquals(1, css.split("background: #fff !important", -1).length - 1,
                "a folha servida não deixa o papel branco");
    }
}
