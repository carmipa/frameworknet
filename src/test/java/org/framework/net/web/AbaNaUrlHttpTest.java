package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.web.domain.PaginasPublicas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Links que abrem uma página de abas direto numa aba, com {@code ?aba=<id>} (proposta D09).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o "Inspetor TLS" de /certificados abre a aba do inspetor em /seguranca, e os links
 * de encapsulamento abrem a aba de encapsulamento em /trafego, em vez de cair na primeira aba da página. Quem escolhe
 * a aba é o {@code aed-tabs.js} (gabarito em {@code src/test/js/web/aed-tabs.test.js}); aqui se confere o outro lado:
 * o link aponta para uma aba que existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todo link {@code ?aba=} de qualquer página pública aponta para uma aba que existe
 * na página de destino (o link é seguido: o destino responde 200 e tem {@code data-tab} com o id); os links pedidos
 * pelo requisito existem (A3: o esperado é o requisito, não o que está no template).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova nomeando a página de origem, o link e a aba que não existe.</p>
 */
@QuarkusTest
@DisplayName("Web: links ?aba= abrem uma aba que existe")
class AbaNaUrlHttpTest {

    private static final Pattern LINK_COM_ABA = Pattern.compile("href=\"(/[a-z0-9/-]*)\\?aba=([^\"&]+)\"");

    /**
     * Confere os links {@code ?aba=} que entram em páginas de abas do {@code aed-tabs.js}.
     *
     * <p>A1: {@code /resolucao-problemas?aba=reversa} é link legítimo com o mesmo sinal, mas aquela página tem
     * mecanismo de abas próprio (não carrega o {@code aed-tabs.js}); por isso a conferência vale só para destino que
     * carrega o script, e o outro destino entra na contagem à parte, sem reprovar.</p>
     */
    @Test
    @DisplayName("todo link ?aba= para página do aed-tabs.js aponta para uma aba que existe nela")
    void todoLinkComAbaAbreUmaAbaQueExiste() {
        Map<String, String> destinos = new HashMap<>();
        Set<String> conferidos = new TreeSet<>();
        Set<String> outroMecanismo = new TreeSet<>();
        List<String> defeitos = new ArrayList<>();
        for (String rota : PaginasPublicas.rotas()) {
            Matcher m = LINK_COM_ABA.matcher(corpo(rota));
            while (m.find()) {
                String destino = m.group(1);
                String aba = m.group(2);
                String alvo = destinos.computeIfAbsent(destino, AbaNaUrlHttpTest::corpo);
                if (!alvo.contains("/web/js/aed-tabs.js")) {
                    outroMecanismo.add(destino + "?aba=" + aba);
                    continue;
                }
                conferidos.add(rota + " -> " + destino + "?aba=" + aba);
                if (!alvo.contains("data-tab=\"" + aba + "\"")) {
                    defeitos.add(rota + ": " + destino + "?aba=" + aba + " não é aba de " + destino);
                }
            }
        }
        assertTrue(defeitos.isEmpty(), () -> "Link para aba que não existe:\n" + String.join("\n", defeitos));
        assertTrue(conferidos.size() >= 4, "o leitor conferiu só " + conferidos + ": instrumento cego?");
        assertTrue(outroMecanismo.contains("/resolucao-problemas?aba=reversa"),
                "controle A1: o link legítimo para a aba própria da Resolução sumiu do levantamento " + outroMecanismo);
    }

    @Test
    @DisplayName("os links do requisito abrem a aba certa: Inspetor TLS e encapsulamento")
    void linksDoRequisitoAbremAAbaCerta() {
        List<String> faltam = new ArrayList<>();
        if (!corpo("/certificados").contains("href=\"/seguranca?aba=tls\"")) {
            faltam.add("o Inspetor TLS de /certificados não abre a aba do inspetor");
        }
        for (String origem : List.of("/camadas", "/laboratorios", "/laboratorios/camadas")) {
            if (!corpo(origem).contains("href=\"/trafego?aba=encapsulamento\"")) {
                faltam.add(origem + " não abre a aba de encapsulamento do Tráfego");
            }
        }
        assertTrue(faltam.isEmpty(), () -> String.join("\n", faltam));
    }

    private static String corpo(String rota) {
        return given().when().get(rota).then().statusCode(200).extract().asString();
    }
}
