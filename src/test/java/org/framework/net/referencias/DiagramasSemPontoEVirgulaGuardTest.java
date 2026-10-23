package org.framework.net.referencias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda do ";" dentro do texto dos diagramas Mermaid.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> no Mermaid o ";" separa comandos. Um ";" dentro de uma mensagem ou nota
 * ("Checa porta e estado; se negado, descarta") partia a linha em duas e o diagrama inteiro virava
 * "Syntax error in text" na tela (auditoria FRONT-04: Dispositivos e Troca de chaves).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhuma linha de {@code diagrama.definicao} tem ";", exceto as de estilo
 * ({@code class}, {@code classDef}, {@code style}, {@code linkStyle}), onde ele só termina o comando.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e linha; sem diagrama lido o teste
 * reprova (alvo vazio não é aprovação). A prova de que o diagrama desenha continua sendo o navegador; esta
 * guarda pega a causa conhecida antes dele.</p>
 */
class DiagramasSemPontoEVirgulaGuardTest {

    private static final Pattern LINHA_DE_ESTILO = Pattern.compile("^\\s*(classDef|class|style|linkStyle)\\b");

    @Test
    void textoDosDiagramasNaoTemPontoEVirgula() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int diagramas = 0;
        ObjectMapper json = new ObjectMapper();
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json")).toList()) {
                List<String> definicoes = new ArrayList<>();
                coletarDiagramas(json.readTree(p.toFile()), definicoes);
                for (String d : definicoes) {
                    diagramas++;
                    for (String linha : d.split("\\R")) {
                        if (quebraODiagrama(linha)) {
                            violacoes.add(p + ": " + linha.strip());
                        }
                    }
                }
            }
        }
        assertTrue(diagramas > 40, "só " + diagramas + " diagramas lidos: a guarda ficou cega");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): o mesmo ";" numa mensagem e no fim de um comando de estilo. */
    @Test
    void regraDiscriminaAFronteira() {
        assertTrue(quebraODiagrama("    FW-->>RT: Checa porta e estado; se negado, descarta"));
        assertTrue(quebraODiagrama("    Note over A,B: chaves de sessão = HKDF(S); dados cifrados"));
        assertFalse(quebraODiagrama("    class T,P,A seguro;"));
        assertFalse(quebraODiagrama("    classDef ok fill:#1a7f37,color:#ffffff;"));
        assertFalse(quebraODiagrama("    FW-->>RT: Checa porta e estado. Se negado, descarta"));
    }

    private static boolean quebraODiagrama(String linha) {
        return linha.contains(";") && !LINHA_DE_ESTILO.matcher(linha).find();
    }

    private static void coletarDiagramas(JsonNode no, List<String> destino) {
        if (no.isObject()) {
            JsonNode diagrama = no.get("diagrama");
            if (diagrama != null && diagrama.path("definicao").isTextual()) {
                destino.add(diagrama.path("definicao").asText());
            }
            no.forEach(filho -> coletarDiagramas(filho, destino));
        } else if (no.isArray()) {
            no.forEach(filho -> coletarDiagramas(filho, destino));
        }
    }
}
