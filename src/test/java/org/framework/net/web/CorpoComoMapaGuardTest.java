package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de fronteira do Lote 1 da auditoria ("mecanismo para não voltar"). Recurso que recebe o corpo do formulário como
 * {@code MultivaluedMap} ou {@code Form} usa um leitor sem teto por campo e sem teto de quantidade de campos: o
 * {@code POST /analise} levava assim 1 MB por campo ao DNS, ao log, à telemetria e ao histórico, e um corpo com centenas
 * de milhares de chaves montava três mapas inteiros na memória. Formulário se lê por {@code @RestForm}, campo a campo.
 *
 * <p>Quem precisar desse caminho entra em {@link #PERMITIDOS} com o teto que aplica e o teste que o prova. Hoje, nenhum.</p>
 */
class CorpoComoMapaGuardTest {

    /** Arquivo do recurso → o teto que ele aplica e onde isso é provado. */
    private static final Set<String> PERMITIDOS = Set.of();

    private static final Pattern PARAMETRO_CORPO_COMO_MAPA =
            Pattern.compile("(MultivaluedMap<\\s*String\\s*,\\s*[\\w<>?]+\\s*>|\\bForm)\\s+\\w+\\s*[,)]");

    /** A1: o instrumento reprova o caso doente e aceita o legítimo que carrega o mesmo sinal ("MultivaluedMap", "Form"). */
    @Test
    void padraoDiscriminaCorpoComoMapaDeUsoLegitimo() {
        assertTrue(PARAMETRO_CORPO_COMO_MAPA.matcher("public X post(@QueryParam(\"t\") String t, MultivaluedMap<String, String> form) {").find());
        assertTrue(PARAMETRO_CORPO_COMO_MAPA.matcher("public X post(MultivaluedMap<String,Object> corpo, String y)").find());
        assertTrue(PARAMETRO_CORPO_COMO_MAPA.matcher("public X post(Form corpo) {").find());
        assertFalse(PARAMETRO_CORPO_COMO_MAPA.matcher("MultivaluedMap<String, String> q = uri.getQueryParameters();").find());
        assertFalse(PARAMETRO_CORPO_COMO_MAPA.matcher("@RestForm(\"ip\") String ip,").find());
        assertFalse(PARAMETRO_CORPO_COMO_MAPA.matcher("private static void colocar(Map<String, String> fields, String nome)").find());
    }

    @Test
    void nenhumRecursoLeOCorpoComoMapa() throws IOException {
        Path raiz = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(raiz), "instrumento cego: código-fonte não encontrado em " + raiz.toAbsolutePath());
        List<Path> fontes;
        try (Stream<Path> arvore = Files.walk(raiz)) {
            fontes = arvore.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(fontes.size() > 100, "instrumento cego: só " + fontes.size() + " fontes lidos");

        Set<String> achados = new TreeSet<>();
        int recursosDeFormulario = 0;
        for (Path fonte : fontes) {
            String texto = Files.readString(fonte, StandardCharsets.UTF_8);
            if (!texto.contains("@Path")) {
                continue;
            }
            if (texto.contains("@RestForm(") || texto.contains("@FormParam(")) {
                recursosDeFormulario++;
            }
            if (PARAMETRO_CORPO_COMO_MAPA.matcher(texto).find()) {
                achados.add(fonte.getFileName().toString());
            }
        }
        // Controle positivo do alcance: os recursos de formulário conhecidos foram lidos (senão o "nenhum" não vale).
        // Conta @RestForm e @FormParam: o controle não pode depender de um recurso só (nem do que a guarda vigia).
        assertTrue(recursosDeFormulario >= 4, "instrumento cego: só " + recursosDeFormulario + " recursos de formulário lidos");
        assertEquals(new TreeSet<>(PERMITIDOS), achados,
                "recurso lê o corpo do formulário como mapa (sem teto por campo nem de quantidade de campos): troque por "
                        + "@RestForm campo a campo, ou aplique teto próprio, prove com teste e registre em PERMITIDOS");
    }
}
