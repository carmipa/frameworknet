package org.framework.net.protocolos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A nota de todo cabeçalho binário declara a soma dos bits dos campos (invariante do {@code CabecalhoBinario}).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a régua de bits ensina o tamanho do cabeçalho; se a nota diz um total e os campos
 * somam outro, a página ensina errado sem ninguém perceber. O documento do {@code CabecalhoBinario} dizia que "o
 * teste confere" essa soma, e o teste não existia.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> para cada {@code conteudo.json} com cabeçalho, a nota contém "{soma} bits", com a
 * soma calculada dos campos (relação aritmética, A3). Vale para todos os módulos, não só protocolos.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando o arquivo, a soma calculada e a nota.</p>
 */
@DisplayName("Conteúdo: a nota do cabeçalho declara a soma dos bits")
class CabecalhoDeclaraASomaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static List<String> divergencias(List<Path> arquivos) throws IOException {
        List<String> erradas = new ArrayList<>();
        for (Path arquivo : arquivos) {
            JsonNode cabecalho = JSON.readTree(arquivo.toFile()).path("cabecalho");
            if (!cabecalho.path("campos").isArray() || cabecalho.path("campos").isEmpty()) {
                continue;
            }
            int soma = 0;
            for (JsonNode campo : cabecalho.path("campos")) {
                soma += campo.path("bits").asInt();
            }
            String nota = cabecalho.path("nota").asText("");
            if (!nota.matches("(?s).*(^|\\D)" + soma + " bits.*")) {
                erradas.add(arquivo + ": campos somam " + soma + " bits; nota: " + nota);
            }
        }
        return erradas;
    }

    @Test
    @DisplayName("todo conteúdo com cabeçalho declara na nota a soma dos bits dos campos")
    void notaDeclaraASoma() throws IOException {
        List<Path> arquivos;
        try (Stream<Path> todos = Files.walk(Path.of("src/main/resources"))) {
            arquivos = todos.filter(p -> p.getFileName().toString().equals("conteudo.json")).toList();
        }
        assertTrue(arquivos.size() >= 20, "só " + arquivos.size() + " conteúdos lidos: instrumento cego?");
        assertEquals(List.of(), divergencias(arquivos));
    }

    @Test
    @DisplayName("calibração: total errado ou ausente reprova; o certo passa, mesmo com número parecido antes")
    void leitorCalibrado() throws IOException {
        Path dir = Files.createTempDirectory("cabecalho");
        String campos = "\"campos\":[{\"nome\":\"a\",\"bits\":16},{\"nome\":\"b\",\"bits\":8}]";
        Path certo = Files.writeString(dir.resolve("certo.json"),
                "{\"cabecalho\":{" + campos + ",\"nota\":\"16 + 8 = 24 bits = 3 bytes\"}}");
        Path errado = Files.writeString(dir.resolve("errado.json"),
                "{\"cabecalho\":{" + campos + ",\"nota\":\"16 + 8 = 32 bits\"}}");
        Path parecido = Files.writeString(dir.resolve("parecido.json"),
                "{\"cabecalho\":{" + campos + ",\"nota\":\"total de 124 bits\"}}");
        Path semCabecalho = Files.writeString(dir.resolve("sem.json"), "{\"resumo\":\"x\"}");
        assertEquals(List.of(), divergencias(List.of(certo, semCabecalho)), "A1: o total certo foi recusado");
        assertEquals(2, divergencias(List.of(errado, parecido)).size(), "A2: total errado ou só parecido passou");
    }
}
