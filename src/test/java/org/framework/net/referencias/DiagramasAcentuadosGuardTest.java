package org.framework.net.referencias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda dos acentos no texto dos diagramas Mermaid.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> 16 diagramas do site estavam escritos sem acento ("Conexao", "cabecalho",
 * "nao sobrepoe", "Vitima") — texto que o aluno lê na tela, ao lado de páginas acentuadas (auditoria CONT-51).
 * O Mermaid desenha UTF-8 sem problema; a falta de acento era hábito, não limitação.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum {@code diagrama.definicao} do conteúdo traz as grafias sem acento
 * mais comuns do português técnico do site. Identificadores com sublinhado (ex.: {@code chave_publica}) e
 * comandos não entram na lista.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e palavra; sem nenhum diagrama lido o
 * teste reprova (alvo vazio não é aprovação).</p>
 */
class DiagramasAcentuadosGuardTest {

    private static final Pattern SEM_ACENTO = Pattern.compile("(?<![\\w-])(?:"
            + "nao|Nao|conexao|Conexao|conexoes|cabecalho|endereco|Endereco|enderecos|aplicacao|Aplicacao|Fisica"
            + "|servico|Diagnostico|seguranca|confianca|confiavel|validacao|autenticacao|Execucao|codigo|publico"
            + "|propria|unico|unica|minimo|nivel|binario|vitima|Vitima|trafego|ninguem|Ninguem|sobrepoe"
            + "|intermediaria|ancora|invalido|invalida|especifico|dominio|alcancado|alcancada|Usuario|automatico"
            + ")(?![\\w-])");

    @Test
    void diagramasDoSiteEstaoAcentuados() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int diagramas = 0;
        ObjectMapper json = new ObjectMapper();
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json")).toList()) {
                List<String> definicoes = new ArrayList<>();
                coletarDiagramas(json.readTree(p.toFile()), definicoes);
                for (String d : definicoes) {
                    diagramas++;
                    Matcher m = SEM_ACENTO.matcher(d);
                    while (m.find()) {
                        violacoes.add(p + ": " + m.group());
                    }
                }
            }
        }
        assertTrue(diagramas > 40, "só " + diagramas + " diagramas lidos: a guarda ficou cega");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): a mesma palavra com e sem acento, e o identificador com sublinhado. */
    @Test
    void regraDiscriminaAFronteira() {
        assertTrue(SEM_ACENTO.matcher("B -->|Nao| C[Conexao recusada]").find());
        assertTrue(SEM_ACENTO.matcher("C1 -.->|\"nao sobrepoe\"| C6").find());
        assertFalse(SEM_ACENTO.matcher("B -->|Não| C[Conexão recusada]").find());
        assertFalse(SEM_ACENTO.matcher("B->>B: Verify(chave_publica, h2, s)").find());
        assertFalse(SEM_ACENTO.matcher("participant D as Domínio").find());
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
