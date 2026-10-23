package org.framework.net.academia;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.framework.net.academia.core.application.PortaoAcademia;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;
import org.framework.net.telemetria.TelemetriaStore;
import org.framework.net.telemetria.infrastructure.TelemetriaAcademiaAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Academia de pé: páginas, API de eventos, teto do corpo e o isolamento de falha (D12).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> provar pela fronteira HTTP o que o aluno e o resto do site
 * recebem — as páginas abrem com menu e lição certos; o evento de lição desconhecida não entra; o
 * erro de JavaScript não leva dado pessoal nem entra na telemetria comum; e a Academia fechada não
 * derruba nenhuma outra rota.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> contagens distintas e não nulas onde há contagem (a visita
 * aparece na telemetria; o erro aparece no buffer próprio e NÃO na telemetria comum); o estado do
 * portão é restaurado ao fim do teste que o fecha.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia rota, status e trecho.</p>
 */
@QuarkusTest
@DisplayName("Academia: páginas, eventos e isolamento de falha")
class AcademiaHttpTest {

    @Inject
    PortaoAcademia portao;

    @Inject
    Instance<VerificacaoArranque> verificacoes;

    @Inject
    TelemetriaStore telemetriaStore;

    @Inject
    TelemetriaAcademiaAdapter adaptador;

    @Inject
    org.framework.net.telemetria.TelemetriaConsoleBuffer console;

    @ParameterizedTest(name = "{0} abre com o menu na Academia")
    @ValueSource(strings = {"/academia", "/academia/fundamentos", "/academia/fundamentos/binario",
            "/academia/fundamentos/hexadecimal", "/academia/fundamentos/camadas",
            "/academia/ipv4", "/academia/ipv4/mascara", "/academia/ipv4/subredes",
            "/academia/transporte", "/academia/transporte/aperto", "/academia/transporte/janela"})
    void paginasAbrem(String rota) {
        String html = given().header("Accept", "text/html").when().get(rota)
                .then().statusCode(200).contentType(containsString("text/html"))
                .body(not(containsString("Internal Server Error")))
                .extract().asString();
        assertTrue(html.contains("href=\"/academia\""), "link do menu ausente em " + rota);
        assertTrue(html.matches("(?s).*<a\\s+href=\"/academia\"[^>]*?class=\"aed-nav-link is-active\".*"),
                "Academia não aparece ativa no menu em " + rota);
        assertTrue(html.contains("/academia/core/css/academia.css"), "CSS da Academia ausente em " + rota);
    }

    @ParameterizedTest(name = "lição {0} traz o próprio id, o selo e os scripts da lição")
    @ValueSource(strings = {"fundamentos.binario", "fundamentos.hexadecimal", "fundamentos.camadas",
            "ipv4.mascara", "ipv4.subredes", "transporte.aperto", "transporte.janela"})
    void licaoTrazIdESelo(String id) {
        String nivel = id.substring(0, id.indexOf('.'));
        String slug = id.substring(id.indexOf('.') + 1);
        given().when().get("/academia/" + nivel + "/" + slug).then().statusCode(200)
                .body(containsString("data-acad-licao=\"" + id + "\""))
                .body(containsString("data-acad-selo=\"" + id + "\""))
                .body(containsString("/academia/" + nivel + "/js/" + slug + ".js"))
                .body(containsString("só neste navegador"));
    }

    /**
     * Ordem de Paulo (01/10/2026): "não é só enxergar, mas ler também". Toda lição abre com a parte
     * Entenda, com os quatro blocos (por que importa, como funciona, onde se erra, para lembrar) e
     * texto de verdade — o piso de palavras reprova lição que nascer só com animação.
     */
    @ParameterizedTest(name = "lição {0} tem a parte Entenda com texto para ler")
    @ValueSource(strings = {"fundamentos.binario", "fundamentos.hexadecimal", "fundamentos.camadas",
            "ipv4.mascara", "ipv4.subredes", "transporte.aperto", "transporte.janela"})
    void licaoTemTextoParaLer(String id) {
        var licao = org.framework.net.academia.trilha.domain.CatalogoTrilha.licao(id).orElseThrow();
        String html = given().when().get(licao.rota()).then().statusCode(200).extract().asString();
        int inicio = html.indexOf("id=\"ler\"");
        assertTrue(inicio > 0, "lição sem a parte Entenda: " + id);
        assertTrue(inicio < html.indexOf("id=\"ver\""), "Entenda tem de vir antes do Ver: " + id);
        String trecho = html.substring(inicio, html.indexOf("id=\"ver\""));
        for (String bloco : List.of("Por que importa", "Como funciona", "Onde se costuma errar", "Para lembrar")) {
            assertTrue(trecho.contains(bloco), "bloco \"" + bloco + "\" ausente em " + id);
        }
        String texto = trecho.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int palavras = texto.split(" ").length;
        assertTrue(palavras >= 200, id + ": só " + palavras + " palavras na parte Entenda (piso 200)");
    }

    @Test
    @DisplayName("a trilha da landing sai como abas (uma por nível) e o mapa dos níveis lista as lições do catálogo")
    void trilhaEmAbas() {
        String html = given().when().get("/academia").then().statusCode(200).extract().asString();
        for (var nivel : org.framework.net.academia.trilha.domain.CatalogoTrilha.niveis()) {
            assertTrue(html.contains("data-acad-aba=\"" + nivel.id() + "\""), "aba ausente: " + nivel.id());
            assertTrue(html.contains("data-acad-painel=\"" + nivel.id() + "\""), "painel ausente: " + nivel.id());
            StringBuilder licoes = new StringBuilder();
            nivel.licoes().forEach(l -> licoes.append(l.id()).append(' '));
            assertTrue(html.contains("data-nivel=\"" + nivel.id() + "\"") && html.contains("data-licoes=\"" + licoes + "\""),
                    "mapa sem o nível ou as lições de " + nivel.id());
        }
        assertTrue(html.contains("role=\"tablist\""), "lista de abas sem papel de tablist");
        assertTrue(html.contains("/academia/core/js/niveis.js") && html.contains("/academia/inicio/js/abas.js"),
                "scripts do desbloqueio ausentes");
    }

    @ParameterizedTest(name = "{0} traz o mapa dos níveis e o aviso de bloqueio, escondido de saída")
    @ValueSource(strings = {"/academia/ipv4", "/academia/ipv4/mascara", "/academia/transporte/janela"})
    void avisoDeBloqueio(String rota) {
        String html = given().when().get(rota).then().statusCode(200).extract().asString();
        String nivel = rota.split("/")[2];
        assertTrue(html.contains("data-acad-mapa"), "mapa ausente em " + rota);
        assertTrue(html.matches("(?s).*data-acad-bloqueio=\"" + nivel + "\"[^>]*\\shidden.*"),
                "aviso de bloqueio ausente ou visível de saída em " + rota);
    }

    @ParameterizedTest(name = "lição {0}: abas de todos os níveis e sub-abas das lições do nível, com a atual marcada")
    @ValueSource(strings = {"fundamentos.hexadecimal", "ipv4.subredes", "transporte.aperto"})
    void navegacaoNaLicao(String id) {
        var licao = org.framework.net.academia.trilha.domain.CatalogoTrilha.licao(id).orElseThrow();
        var nivel = org.framework.net.academia.trilha.domain.CatalogoTrilha.nivel(licao.nivelId()).orElseThrow();
        String html = given().when().get(licao.rota()).then().statusCode(200).extract().asString();
        String nav = html.substring(html.indexOf("data-acad-nav>"), html.indexOf("</nav>", html.indexOf("data-acad-nav>")));
        assertTrue(nav.contains("href=\"/academia#trilha\""), "sem caminho de volta para a Academia");
        for (var n : org.framework.net.academia.trilha.domain.CatalogoTrilha.niveis()) {
            // A1: nível aberto é link; nível "em breve" aparece, mas sem link (não tem página — seria 404)
            assertEquals(n.aberto(), nav.contains("href=\"" + n.rota() + "\""),
                    (n.aberto() ? "aba de nível aberto sem link: " : "nível em breve com link para página inexistente: ") + n.id());
            assertTrue(nav.contains(">" + n.titulo() + "<"), "nível ausente da navegação: " + n.id());
        }
        assertTrue(nav.contains("href=\"" + nivel.rota() + "\" class=\"acad-subaba\""), "sub-aba da visão do nível ausente");
        for (var l : nivel.licoes()) {
            assertTrue(nav.contains("href=\"" + l.rota() + "\""), "sub-aba de lição ausente: " + l.id());
        }
        assertTrue(nav.matches("(?s).*href=\"" + licao.rota() + "\" class=\"acad-subaba active\" aria-current=\"page\".*"),
                "a lição aberta não aparece marcada");
        // ACAD-11: a PÁGINA é a lição; o nível em que ela está é "true" (parte do caminho), não "page".
        assertEquals(1, nav.split("aria-current=\"page\"", -1).length - 1,
                "só a lição aberta é a página atual");
        assertTrue(nav.matches("(?s).*aria-current=\"true\"\\s+data-acad-nav-nivel=\"" + nivel.id() + "\".*"),
                "a marca de parte do caminho precisa estar no nível da lição");
        assertEquals(1, nav.split("aria-current=\"true\"", -1).length - 1,
                "o nível da lição aparece marcado como parte do caminho");
    }

    @Test
    @DisplayName("lição inexistente é 404 pela página padrão, nunca página pela metade")
    void licaoInexistente() {
        given().header("Accept", "text/html").when().get("/academia/fundamentos/inventada")
                .then().statusCode(404).body(containsString("err-404"));
    }

    @Test
    @DisplayName("visita de lição do catálogo é aceita e vira evento com faixas, nunca o número exato")
    void visitaAceita() {
        // A telemetria do teste é persistida entre execuções: só conta evento nascido AGORA.
        java.time.Instant antes = java.time.Instant.now();
        given().contentType("application/json")
                .body("{\"tipo\":\"visita\",\"licaoId\":\"fundamentos.hexadecimal\",\"segundos\":417,"
                        + "\"interacoes\":12,\"concluiu\":true,\"extra\":\"ignorado\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202).body("resultado", equalTo("ACEITO"));

        var evento = telemetriaStore.snapshotEventos().stream()
                .filter(e -> "academia_visita".equals(e.evento()))
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .filter(e -> e.fields() != null && "fundamentos.hexadecimal".equals(e.fields().get("licao")))
                .findFirst().orElseThrow(() -> new AssertionError("visita não chegou à telemetria"));
        assertEquals("ATE_10MIN", evento.fields().get("tempo"));
        assertEquals("VARIAS", evento.fields().get("interacoes"));
        assertFalse(evento.fields().containsValue(417L) || evento.fields().containsValue(417),
                "o tempo exato não pode sair");
        assertFalse(evento.fields().containsKey("extra"), "campo fora do esquema não pode entrar");
    }

    @Test
    @DisplayName("lição desconhecida e tipo desconhecido são recusados com 422")
    void recusas() {
        given().contentType("application/json")
                .body("{\"tipo\":\"visita\",\"licaoId\":\"../../etc/passwd\",\"segundos\":1}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("lição desconhecida"));
        given().contentType("application/json")
                .body("{\"tipo\":\"apagar\",\"licaoId\":\"fundamentos.binario\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("tipo desconhecido"));
        given().contentType("application/json").body("{}")
                .when().post("/academia/api/eventos")
                .then().statusCode(422).body("motivo", equalTo("tipo ausente"));
    }

    @Test
    @DisplayName("erro de JavaScript chega saneado ao buffer próprio e NÃO entra na telemetria comum")
    void erroJsForaDoDataset() {
        // Marca só de letras (sobrevive ao saneamento) e única por execução: a telemetria do teste é
        // persistida em disco entre execuções, então a busca tem de achar ESTE evento, não um antigo.
        String marca = "sentinela" + java.util.UUID.randomUUID().toString().replaceAll("[^a-f]", "")
                .replace('a', 'q').replace('b', 'w');
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.camadas\",\"tipoErro\":\"erro\","
                        + "\"mensagem\":\"" + marca + " 8.8.8.8 a@b.com 123.456.789-09\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202).body("resultado", equalTo("ACEITO"));

        var guardado = adaptador.errosGuardados().stream()
                .filter(e -> e.mensagem().startsWith(marca))
                .findFirst().orElseThrow(() -> new AssertionError("erro não chegou ao buffer próprio"));
        assertFalse(guardado.mensagem().contains("8.8.8.8"));
        assertFalse(guardado.mensagem().contains("a@b.com"));
        assertFalse(guardado.mensagem().contains("123.456.789-09"));

        assertTrue(console.snapshot(500).stream().anyMatch(l -> l.contains(marca)),
                "ACAD-04: o erro de JS aparece no console do painel (o buffer não tinha leitor)");

        boolean naTelemetriaComum = telemetriaStore.snapshotEventos().stream()
                .anyMatch(e -> String.valueOf(e.fields()).contains(marca));
        assertFalse(naTelemetriaComum, "erro de JS não pode entrar na telemetria que alimenta o dataset público");
    }

    @Test
    @DisplayName("corpo acima de 8 KB é recusado com 413 antes de chegar à aplicação; 8 KB passa do teto")
    void tetoDoCorpo() {
        String preenchimento = "x".repeat(8200);
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.binario\",\"mensagem\":\"" + preenchimento + "\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(413).body(containsString("8 KB"));

        // ACAD-10/01: o teto vale também com parâmetro de matriz no caminho (o RESTEasy o ignora e
        // entrega ao mesmo recurso); antes o 9 KB passava e chegava à aplicação.
        for (String rota : List.of("/academia;x/api/eventos", "/academia/api;x/eventos", "/academia/api/eventos;y")) {
            given().urlEncodingEnabled(false).contentType("application/json")
                    .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.binario\",\"mensagem\":\"" + preenchimento + "\"}")
                    .when().post(rota)
                    .then().statusCode(413);
        }

        String cabe = "x".repeat(7900);
        given().contentType("application/json")
                .body("{\"tipo\":\"erro\",\"licaoId\":\"fundamentos.binario\",\"mensagem\":\"" + cabe + "\"}")
                .when().post("/academia/api/eventos")
                .then().statusCode(202);
    }

    /**
     * D12: com a Academia fechada, as rotas dela respondem 503 e as demais seguem iguais. O estado
     * é restaurado no fim, pela mesma decisão do arranque.
     */
    @Test
    @DisplayName("Academia degradada responde 503 nas rotas dela e o resto do site continua 200")
    void falhaIsolada() {
        try {
            portao.decidir(true, List.of(new VerificacaoArranque() {
                @Override
                public String nome() {
                    return "teste";
                }

                @Override
                public void verificar() {
                    throw new IllegalStateException("falha simulada");
                }
            }));
            given().header("Accept", "text/html").when().get("/academia/fundamentos/binario")
                    .then().statusCode(503).body(containsString("err-503"));
            given().contentType("application/json")
                    .body("{\"tipo\":\"visita\",\"licaoId\":\"fundamentos.binario\"}")
                    .when().post("/academia/api/eventos").then().statusCode(503);
            for (String rota : List.of("/", "/calculadora", "/laboratorios", "/protocolos")) {
                given().header("Accept", "text/html").when().get(rota).then().statusCode(200);
            }
        } finally {
            portao.decidir(true, verificacoes);
        }
        given().when().get("/academia").then().statusCode(200);
    }
}
