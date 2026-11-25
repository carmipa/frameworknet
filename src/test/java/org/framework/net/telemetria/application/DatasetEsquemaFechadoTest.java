package org.framework.net.telemetria.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.shared.InputLimits;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dataset público com esquema fechado (auditoria OPS-02): só sai campo do esquema; texto livre que o
 * visitante digitou ou que a aplicação montou (mensagem com caminho, hostname, rede, e-mail na rota) não
 * sai, e o campo novo aparece contado em estatisticas.json.
 */
@QuarkusTest
class DatasetEsquemaFechadoTest {

    @Inject
    DatasetPublicavelService dataset;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaStore store;

    @Test
    @SuppressWarnings("unchecked")
    void soCampoDoEsquemaSaiETextoLivreFicaDeFora() throws Exception {
        String marca = "ops02" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("mensagem", "falha em /home/paulo/segredo.txt para fulano@exemplo.com");
        campos.put("hostname", "DESKTOP-QDNQHL1");
        campos.put("baseNetwork", "200.200.200.0/24");
        campos.put("rota", "/fulano@exemplo.com/" + marca);
        campos.put("campoNovo" + marca, "qualquer");
        campos.put("modo", "cidr");
        campos.put("total", 3);
        telemetriaLogger.logEvent("info", "teste", "evento_" + marca, campos);

        DatasetPublicavelService.Pacote pacote = dataset.gerar();

        String jsonl = pacote.arquivos().entrySet().stream()
                .filter(e -> e.getKey().endsWith("eventos.jsonl")).findFirst().orElseThrow().getValue();
        String linha = jsonl.lines().filter(l -> l.contains("evento_" + marca)).findFirst().orElse(null);
        assertNotNull(linha, "o evento de teste precisa estar no pacote para o teste valer");

        assertTrue(linha.contains("\"framework.field.modo\""), "campo do esquema sai: " + linha);
        assertTrue(linha.contains("\"framework.field.total\""), "contagem sai: " + linha);
        assertTrue(linha.contains("{param}"), "rota generalizada: " + linha);
        for (String proibido : new String[]{"segredo.txt", "fulano", "DESKTOP-QDNQHL1", "200.200.200", "campoNovo"}) {
            assertFalse(linha.contains(proibido), proibido + " vazou: " + linha);
        }

        String estat = pacote.arquivos().entrySet().stream()
                .filter(e -> e.getKey().endsWith("estatisticas.json")).findFirst().orElseThrow().getValue();
        Map<String, Object> estatisticas = objectMapper.readValue(estat, Map.class);
        Map<String, Object> fora = (Map<String, Object>) estatisticas.get("campos_fora_do_esquema");
        assertTrue(fora.containsKey("campoNovo" + marca), "campo novo contado por nome: " + fora);
        assertTrue(estatisticas.containsKey("identificadores_distintos"));
        assertFalse(estatisticas.containsKey("visitantes_distintos"), "não é contagem de visitantes");
    }

    /**
     * Lote 1 (revisão operacional, 2ª passada): evento gravado por versão anterior pode ter texto do formulário em
     * {@code modo}/{@code topology}. Na saída ele passa pelo guardião de vocabulário — sai "outro" — e o vocabulário
     * real do código passa igual (A1).
     */
    @Test
    void vocabularioQueVeioDoFormularioSaiComoOutro() {
        String marca = "voc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("modo", "Texto Livre Digitado 123");
        campos.put("topology", "mesh");
        telemetriaLogger.logEvent("info", "teste", "evento_" + marca, campos);

        String jsonl = dataset.gerar().arquivos().entrySet().stream()
                .filter(e -> e.getKey().endsWith("eventos.jsonl")).findFirst().orElseThrow().getValue();
        String linha = jsonl.lines().filter(l -> l.contains("evento_" + marca)).findFirst().orElse(null);
        assertNotNull(linha, "o evento de teste precisa estar no pacote para o teste valer");
        assertFalse(linha.contains("Texto Livre"), "texto livre do formulário saiu publicado: " + linha);
        assertTrue(linha.contains("outro"), linha);
        assertTrue(linha.contains("mesh"), "o vocabulário real precisa passar igual: " + linha);
    }

    /**
     * Campo {@code erro}: só passa nome de classe de falha (A1 — a fronteira é a mesma forma curta e sem espaço).
     * Nome próprio solto, frase da colagem e endereço saem "outro".
     */
    @Test
    void campoErroSoPublicaNomeDeClasseDeFalha() {
        assertEquals("EntradaInvalidaException", DatasetPublicavelService.classeDeFalhaPublicavel("EntradaInvalidaException"));
        assertEquals("StackOverflowError", DatasetPublicavelService.classeDeFalhaPublicavel("StackOverflowError"));
        assertEquals("NoStackTraceThrowable", DatasetPublicavelService.classeDeFalhaPublicavel(" NoStackTraceThrowable "));
        assertEquals("outro", DatasetPublicavelService.classeDeFalhaPublicavel("Fulano"));
        assertEquals("outro", DatasetPublicavelService.classeDeFalhaPublicavel("Linha 1 (Fulano): curta"));
        assertEquals("outro", DatasetPublicavelService.classeDeFalhaPublicavel("10.0.0.0/24"));
        assertEquals("outro", DatasetPublicavelService.classeDeFalhaPublicavel("Exception"));
        assertEquals("outro", DatasetPublicavelService.classeDeFalhaPublicavel(null));
    }

    /**
     * Evento gravado pela versão anterior (ainda no Stream ou no arquivo da janela) com a mensagem curta da colagem em
     * {@code erro}: abaixo de 64 caracteres e sem {@code @}, passava pelo guardião de tamanho e saía publicado. A2: sem o
     * guardião de classe de falha, o nome aparece na linha.
     */
    @Test
    void erroLegadoComTextoDaColagemSaiComoOutro() {
        String marca = "leg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String nome = "Aluna" + marca;
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("status", "invalid_input");
        campos.put("erro", "Linha 1 (" + nome + "): curta");
        telemetriaLogger.logEvent("warn", "teste", "evento_" + marca, campos);

        String jsonl = dataset.gerar().arquivos().entrySet().stream()
                .filter(e -> e.getKey().endsWith("eventos.jsonl")).findFirst().orElseThrow().getValue();
        String linha = jsonl.lines().filter(l -> l.contains("evento_" + marca)).findFirst().orElse(null);
        assertNotNull(linha, "o evento de teste precisa estar no pacote para o teste valer");
        assertFalse(linha.contains(nome), "texto da colagem publicado em erro: " + linha);
        assertTrue(linha.contains("\"framework.field.erro\""), "o campo continua no esquema: " + linha);
        assertTrue(linha.contains("outro"), linha);
    }

    /**
     * Caminho real: a colagem da turma com uma linha de 51 colunas de hosts gera a mensagem que repete o nome do aluno.
     * O evento guarda o nome da classe em {@code erro} e o texto em {@code mensagem} (A7: a causa continua no painel);
     * no pacote publicado o nome não aparece e a classe aparece (A6: verificado no arquivo gerado, não no mapa).
     * A2: com a origem antiga ({@code "erro", ex.getMessage()}), {@code erro} traz o nome e a primeira asserção cai.
     */
    @Test
    void falhaDaColagemDaTurmaNaoPublicaONomeDoAluno() {
        String nome = "Aluno" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String roster = nome + "\t172.51.0.0" + "\t10".repeat(InputLimits.MAX_LOCATION_ROWS + 1);
        Instant antes = Instant.now();
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "export_class_zip")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("eigrp_as", "71")
                .formParam("routing_mode", "auto")
                .formParam("remote_access", "ssh")
                .formParam("class_roster_paste", roster)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("localidades"));

        TelemetriaEvent evento = store.snapshotEventos().stream()
                .filter(e -> "problem_export_class_zip".equals(e.evento()))
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .findFirst()
                .orElseThrow(() -> new AssertionError("o evento da falha da colagem não foi gravado"));
        assertEquals("invalid_input", evento.status(), "a falha da colagem saiu com outro status: " + evento.status());
        assertEquals("EntradaInvalidaException", evento.fields().get("erro"),
                "erro deve ser o nome da classe, nunca o texto: " + evento.fields());
        assertTrue(String.valueOf(evento.fields().get("mensagem")).contains(nome),
                "a causa (com o nome) precisa continuar no evento do painel: " + evento.fields());

        String jsonl = dataset.gerar().arquivos().entrySet().stream()
                .filter(e -> e.getKey().endsWith("eventos.jsonl")).findFirst().orElseThrow().getValue();
        String linha = jsonl.lines().filter(l -> l.contains(evento.id())).findFirst().orElse(null);
        assertNotNull(linha, "o evento precisa estar no pacote para o teste valer");
        assertFalse(linha.contains(nome), "nome do aluno publicado: " + linha);
        assertTrue(linha.contains("EntradaInvalidaException"), linha);
        assertTrue(linha.contains("invalid_input"), "a falha saiu publicada com outro status: " + linha);
    }
}
