package org.framework.net.telemetria.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaLogger;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
}
