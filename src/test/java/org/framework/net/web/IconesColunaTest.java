package org.framework.net.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.framework.net.web.support.IconesColuna;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapa de ícone por coluna: ícone semântico para todo cabeçalho que o projeto usa (colunas dos JSON e
 * cabeçalhos de texto fixo dos templates), o mesmo glifo para o mesmo rótulo em qualquer tabela, e um ícone
 * neutro (nunca vazio) para o desconhecido/nulo — é função total.
 */
@DisplayName("Web: ícone por coluna de tabela")
class IconesColunaTest {

    private static final Pattern TH = Pattern.compile("<th(\\s[^<>]*)?>(.*?)</th>", Pattern.DOTALL);
    private static final Pattern GLIFO = Pattern.compile(
            "^\\s*<span class=\"material-symbols-outlined[^\"]*\"[^>]*>([a-z0-9_]+)</span>\\s*([^<{}]+?)\\s*$");

    @Test
    @DisplayName("colunas conhecidas ganham ícone semântico (ignorando caixa, acento e espaços)")
    void conhecidas() {
        assertEquals("door_open", IconesColuna.iconeColuna("Porta"));
        assertEquals("door_open", IconesColuna.iconeColuna("  PORTA  "));
        assertEquals("layers", IconesColuna.iconeColuna("Camada"));
        assertEquals("lock", IconesColuna.iconeColuna("Seguro"));
        assertEquals("swap_calls", IconesColuna.iconeColuna("Transporte"));
        assertEquals("key", IconesColuna.iconeColuna("Chave"));
        assertEquals("data_object", IconesColuna.iconeColuna("Funcao"));
        assertEquals("lan", IconesColuna.iconeColuna("Rede"));
        assertEquals("schedule", IconesColuna.iconeColuna("Quando usar"));
    }

    @Test
    @DisplayName("desconhecido, nulo ou vazio caem num ícone neutro — nunca vazio")
    void fallback() {
        assertEquals(IconesColuna.NEUTRO, IconesColuna.iconeColuna("Coluna Totalmente Nova"));
        assertEquals(IconesColuna.NEUTRO, IconesColuna.iconeColuna(null));
        assertEquals(IconesColuna.NEUTRO, IconesColuna.iconeColuna("   "));
        assertFalse(IconesColuna.conhecido("Coluna Totalmente Nova"));
        assertTrue(IconesColuna.conhecido("Rede"));
    }

    /** Toda coluna declarada nos JSON de conteúdo tem glifo próprio: coluna nova sem ícone reprova aqui. */
    @Test
    @DisplayName("toda coluna dos JSON de conteúdo tem glifo próprio")
    void colunasDosJsonTemGlifo() throws IOException {
        Set<String> colunas = new TreeSet<>();
        ObjectMapper om = new ObjectMapper();
        try (Stream<Path> t = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : t.filter(f -> f.toString().endsWith(".json")).toList()) {
                coletarColunas(om.readTree(p.toFile()), colunas);
            }
        }
        assertTrue(colunas.size() > 200, "instrumento cego: " + colunas.size() + " colunas");
        List<String> sem = colunas.stream().filter(c -> !IconesColuna.conhecido(c)).toList();
        assertEquals(List.of(), sem);
    }

    /** As tabelas que o diagnóstico monta no Java também passam pelo mapa: coluna nova sem glifo reprova aqui. */
    @Test
    @DisplayName("toda coluna das tabelas do diagnóstico tem glifo próprio")
    void colunasDoDiagnosticoTemGlifo() throws IOException {
        String fonte = Files.readString(Path.of(
                "src/main/java/org/framework/net/ferramentasDiagnostico/application/DiagnosticoService.java"),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("new Tabela\\(\"[^\"]*\",\\s*List\\.of\\(([^)]*)\\)").matcher(fonte);
        Set<String> colunas = new TreeSet<>();
        while (m.find()) {
            Matcher c = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
            while (c.find()) {
                colunas.add(c.group(1));
            }
        }
        assertTrue(colunas.size() > 15, "instrumento cego: " + colunas);
        assertEquals(List.of(), colunas.stream().filter(c -> !IconesColuna.conhecido(c)).toList());
    }

    /** As tabelas do README servido pela Central de Documentação: o cabeçalho delas também passa pelo mapa. */
    @Test
    @DisplayName("toda coluna das tabelas do README tem glifo próprio")
    void colunasDoReadmeTemGlifo() throws IOException {
        List<String> linhas = Files.readAllLines(Path.of("src/main/resources/README.md"), StandardCharsets.UTF_8);
        Pattern separador = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");
        Set<String> colunas = new TreeSet<>();
        for (int i = 1; i < linhas.size(); i++) {
            if (separador.matcher(linhas.get(i).strip()).matches()) {
                for (String c : linhas.get(i - 1).strip().replaceAll("^\\||\\|$", "").split("\\|")) {
                    colunas.add(c.replace("`", "").replace("*", "").strip());
                }
            }
        }
        assertTrue(colunas.size() > 20, "instrumento cego: " + colunas);
        assertEquals(List.of(), colunas.stream().filter(c -> !IconesColuna.conhecido(c)).toList());
    }

    private static void coletarColunas(JsonNode n, Set<String> saida) {
        if (n.isArray()) {
            n.forEach(x -> coletarColunas(x, saida));
        } else if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                if (e.getKey().equals("colunas") && e.getValue().isArray()) {
                    e.getValue().forEach(c -> {
                        if (c.isTextual()) {
                            saida.add(c.asText());
                        }
                    });
                }
                coletarColunas(e.getValue(), saida);
            });
        }
    }

    static List<String> divergencias(String html) {
        List<String> achados = new ArrayList<>();
        Matcher m = TH.matcher(html);
        while (m.find()) {
            String attrs = m.group(1) == null ? "" : m.group(1);
            if (attrs.contains("data-icone-excecao")) {
                continue;
            }
            Matcher g = GLIFO.matcher(m.group(2));
            if (!g.find()) {
                continue;
            }
            String rotulo = g.group(2).strip();
            String esperado = IconesColuna.iconeColuna(rotulo);
            if (!IconesColuna.conhecido(rotulo) || !esperado.equals(g.group(1))) {
                achados.add(rotulo + " usa " + g.group(1) + ", o mapa diz " + esperado);
            }
        }
        return achados;
    }

    @Test
    @DisplayName("o instrumento da coerência discrimina glifo certo, glifo errado e rótulo fora do mapa")
    void instrumentoDiscrimina() {
        String s = "<span class=\"material-symbols-outlined\" aria-hidden=\"true\" translate=\"no\">";
        assertEquals(List.of(), divergencias("<th title=\"x\">" + s + "lan</span>Rede</th>"));
        assertEquals(List.of("Rede usa hub, o mapa diz lan"), divergencias("<th>" + s + "hub</span>Rede</th>"));
        assertEquals(1, divergencias("<th>" + s + "lan</span>Coluna Nova</th>").size());
        assertEquals(List.of(), divergencias("<th>{col}</th><th>Rede</th>"), "sem glifo literal não é desta guarda");
    }

    /** Mesmo rótulo, mesmo glifo: o cabeçalho de texto fixo usa o glifo que o mapa devolve. */
    @Test
    @DisplayName("cabeçalhos de texto fixo usam o glifo do mapa")
    void templatesCoerentesComOMapa() throws IOException {
        List<String> achados = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> t = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path p : t.filter(f -> f.toString().endsWith(".html")).toList()) {
                lidos++;
                for (String d : divergencias(Files.readString(p, StandardCharsets.UTF_8))) {
                    achados.add(p.getFileName() + ": " + d);
                }
            }
        }
        assertTrue(lidos > 50, "instrumento cego: " + lidos + " templates");
        assertEquals(List.of(), achados);
    }
}
