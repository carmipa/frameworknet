package org.framework.net.protocolos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste rápido dos aprofundamentos de protocolo: a tela do piloto e a API que confere e conta a resposta (C03).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> provar, pela fronteira HTTP, que o teste aparece onde há perguntas e só lá, que o
 * servidor confere a escolha pelo gabarito do conteúdo e conta um evento por resposta válida, e que entrada fora do
 * contrato é recusada sem contar nada e sem erro 500.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a alternativa é achada pelo SENTIDO (o trecho que a RFC dá como certo ou errado),
 * nunca pela marca {@code correta} do arquivo (A3); o evento leva só protocolo, pergunta, alternativa e acerto; recusa
 * é 422 {@code RECUSADO}, corpo acima de 1 KB é 413 e o limite exato passa (A1). Os eventos são filtrados pelo
 * instante do pedido: a leitura da telemetria junta memória e arquivo, e evento de execução antiga não vale (A5). Com
 * CSRF desligado no perfil de teste, o cabeçalho que o navegador manda é provado no navegador, não aqui.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia o pedido, o status e o evento ausente ou indevido.</p>
 */
@QuarkusTest
@DisplayName("Protocolos: teste rápido no piloto e API que confere e conta a resposta")
class AutoavaliacaoHttpTest {

    private static final String API = "/protocolos/api/autoavaliacao";
    private static final String EVENTO = "autoavaliacao_resposta";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    TelemetriaStore telemetriaStore;

    /** Posição da alternativa cujo texto contém o trecho, lida do conteúdo publicado. */
    static int alternativaCom(String slug, int pergunta, String trecho) throws IOException {
        JsonNode alternativas = JSON.readTree(Path.of("src/main/resources/protocolos", slug, "conteudo.json").toFile())
                .path("autoavaliacao").path(pergunta).path("alternativas");
        for (int i = 0; i < alternativas.size(); i++) {
            if (alternativas.get(i).path("texto").asText("").contains(trecho)) {
                return i;
            }
        }
        throw new AssertionError("nenhuma alternativa com \"" + trecho + "\" em " + slug + " pergunta " + pergunta);
    }

    private List<TelemetriaEvent> eventosDesde(Instant antes) {
        return telemetriaStore.snapshotEventos().stream()
                .filter(e -> EVENTO.equals(e.evento()))
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .toList();
    }

    private static String corpo(String protocolo, int pergunta, int alternativa) {
        return "{\"protocolo\":\"" + protocolo + "\",\"pergunta\":" + pergunta + ",\"alternativa\":" + alternativa + "}";
    }

    private static int contar(String texto, String trecho) {
        Matcher m = Pattern.compile(Pattern.quote(trecho)).matcher(texto);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @ParameterizedTest(name = "/protocolos/{0} mostra o teste rápido com 4 perguntas e explicações escondidas")
    @ValueSource(strings = {"tcp", "ipv4", "http"})
    void pilotoMostraOTeste(String slug) {
        String html = given().when().get("/protocolos/" + slug).then().statusCode(200).extract().asString();
        assertTrue(html.contains("data-autoavaliacao=\"" + slug + "\""), "sem a seção do teste em " + slug);
        assertEquals(1, contar(html, "href=\"#autoavaliacao\""), "o índice da página leva ao teste rápido em " + slug);
        assertEquals(4, contar(html, "class=\"aval-pergunta\""), "perguntas renderizadas em " + slug);
        assertEquals(16, contar(html, "class=\"aval-alternativa\""), "alternativas renderizadas em " + slug);
        assertEquals(16, contar(html, "class=\"aval-porque\" data-porque=\""), "explicações em " + slug);
        assertEquals(16, contar(html, "hidden><strong>"), "explicação aparecendo antes do clique em " + slug);
        Set<String> ids = new java.util.HashSet<>();
        Matcher id = Pattern.compile("id=\"(aval-" + slug + "-[0-3]-[0-3])\"").matcher(html);
        while (id.find()) {
            ids.add(id.group(1));
        }
        assertEquals(16, ids.size(), "cada porquê precisa de id único (o botão aponta para ele): " + ids);
        assertTrue(html.contains("/protocolos/aprofundamento/js/autoavaliacao.js?v="), "sem o script em " + slug);
        assertEquals(4, contar(html, "role=\"status\" aria-live=\"polite\"><span class=\"material-symbols-outlined\""),
                "o resultado de cada pergunta é um selo de estado: precisa do lugar do ícone junto do texto em " + slug);
        int inicio = html.indexOf("<section id=\"autoavaliacao\"");
        String secao = html.substring(inicio, html.indexOf("</section>", inicio));
        assertTrue(!secao.contains("aria-disabled") && !secao.contains(" disabled"),
                "nenhuma alternativa nasce desabilitada em " + slug);
    }

    @ParameterizedTest(name = "/protocolos/{0} (sem perguntas) não mostra o teste rápido")
    @ValueSource(strings = {"udp", "icmp", "arp"})
    void foraDoPilotoNaoMostra(String slug) {
        given().when().get("/protocolos/" + slug).then().statusCode(200)
                .body(containsString("aprof-indice"))
                .body(not(containsString("data-autoavaliacao")))
                .body(not(containsString("href=\"#autoavaliacao\"")))
                .body(not(containsString("autoavaliacao.js")));
    }

    @Test
    @DisplayName("resposta certa: 202, acertou=true e um evento só com protocolo, pergunta, alternativa e acerto")
    void respostaCertaContaAcerto() throws IOException {
        int synAck = alternativaCom("tcp", 0, "SYN-ACK");
        Instant antes = Instant.now();
        given().contentType(ContentType.JSON).body(corpo("tcp", 0, synAck))
                .when().post(API)
                .then().statusCode(202).body("acertou", equalTo(true)).body("size()", equalTo(1));

        List<TelemetriaEvent> eventos = eventosDesde(antes).stream()
                .filter(e -> "tcp".equals(e.fields().get("protocolo")))
                .filter(e -> Integer.valueOf(0).equals(e.fields().get("pergunta")))
                .filter(e -> Integer.valueOf(synAck).equals(e.fields().get("alternativa")))
                .toList();
        assertEquals(1, eventos.size(), "um pedido tem de gerar exatamente um evento: " + eventos);
        TelemetriaEvent evento = eventos.get(0);
        assertEquals("protocolos", evento.modulo());
        assertEquals(Boolean.TRUE, evento.fields().get("acertou"));
        assertEquals(Set.of("protocolo", "pergunta", "alternativa", "acertou"), evento.fields().keySet(),
                "o evento não pode levar nada de quem respondeu");
    }

    @Test
    @DisplayName("resposta errada: 202, acertou=false, e o evento conta o erro")
    void respostaErradaContaErro() throws IOException {
        int fin = alternativaCom("tcp", 0, "FIN");
        Instant antes = Instant.now();
        given().contentType(ContentType.JSON).body(corpo("tcp", 0, fin))
                .when().post(API)
                .then().statusCode(202).body("acertou", equalTo(false));

        assertTrue(eventosDesde(antes).stream().anyMatch(e -> "tcp".equals(e.fields().get("protocolo"))
                        && Integer.valueOf(fin).equals(e.fields().get("alternativa"))
                        && Boolean.FALSE.equals(e.fields().get("acertou"))),
                "a resposta errada não foi contada");
    }

    @Test
    @DisplayName("fronteira legítima: slug com maiúsculas e espaços vale, e a última pergunta e alternativa existem")
    void slugNormalizadoEUltimoIndiceValem() throws IOException {
        int privado = alternativaCom("ipv4", 1, "172.20.5.10");
        Instant antes = Instant.now();
        given().contentType(ContentType.JSON).body(corpo(" IPv4 ", 1, privado))
                .when().post(API).then().statusCode(202).body("acertou", equalTo(true));
        assertTrue(eventosDesde(antes).stream().anyMatch(e -> "ipv4".equals(e.fields().get("protocolo"))
                && Integer.valueOf(1).equals(e.fields().get("pergunta"))), "slug normalizado não foi contado");

        int host = alternativaCom("http", 3, "qual site");
        given().contentType(ContentType.JSON).body(corpo("http", 3, 3))
                .when().post(API).then().statusCode(202).body("acertou", equalTo(host == 3));
    }

    @Test
    @DisplayName("fora do contrato: 422 RECUSADO e nenhum evento contado")
    void recusasNaoContam() {
        List<String> recusados = List.of(
                corpo("udp", 0, 0),
                corpo("naoexiste", 0, 0),
                corpo("tcp", 4, 0),
                corpo("tcp", -1, 0),
                corpo("tcp", 0, 4),
                corpo("tcp", 0, -1),
                "{\"protocolo\":\"tcp\",\"pergunta\":0}",
                "{\"protocolo\":null,\"pergunta\":0,\"alternativa\":0}",
                "{}",
                "null");
        Instant antes = Instant.now();
        List<String> falhas = new ArrayList<>();
        for (String corpo : recusados) {
            int status = given().contentType(ContentType.JSON).body(corpo).when().post(API).then().extract().statusCode();
            if (status != 422) {
                falhas.add(status + " para " + corpo);
            }
        }
        assertEquals(List.of(), falhas, "esperado 422 em todos");
        given().contentType(ContentType.JSON).body(corpo("udp", 0, 0)).when().post(API)
                .then().statusCode(422).body("resultado", equalTo("RECUSADO"));
        assertEquals(List.of(), eventosDesde(antes).stream().map(TelemetriaEvent::fields).toList(),
                "pedido recusado não pode virar evento");
    }

    @ParameterizedTest(name = "corpo malformado ({0}) é 4xx, nunca 500")
    @ValueSource(strings = {"{", "[1,2]", "{\"protocolo\":\"tcp\",\"pergunta\":\"x\",\"alternativa\":0}"})
    void malformadoNaoViraErroDoServidor(String corpo) {
        given().contentType(ContentType.JSON).body(corpo).when().post(API)
                .then().statusCode(allOf(greaterThanOrEqualTo(400), lessThan(500)));
    }

    @Test
    @DisplayName("tipo errado é 415 e GET na API é 405")
    void tipoEMetodoErrados() {
        given().contentType(ContentType.TEXT).body(corpo("tcp", 0, 0)).when().post(API).then().statusCode(415);
        given().when().get(API).then().statusCode(405);
    }

    @Test
    @DisplayName("corpo de 1024 bytes passa; 1025 é 413 antes da aplicação, também com parâmetro de matriz")
    void tetoDoCorpo() throws IOException {
        String base = corpo("http", 0, alternativaCom("http", 0, "404 Not Found"));
        String noLimite = base + " ".repeat(1024 - base.length());
        assertEquals(1024, noLimite.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        given().contentType(ContentType.JSON).body(noLimite).when().post(API).then().statusCode(202);
        given().contentType(ContentType.JSON).body(noLimite + " ").when().post(API)
                .then().statusCode(413).body("resultado", equalTo("RECUSADO"));
        given().urlEncodingEnabled(false).contentType(ContentType.JSON).body(noLimite + " ")
                .when().post("/protocolos/api;x=1/autoavaliacao").then().statusCode(413);
    }
}
