package org.framework.net.web;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gabarito, em JavaScript, dos scripts globais do site (hoje: os atalhos de teclado de {@code web/js/atalhos.js}
 * e o link direto para uma aba, {@code ?aba=}, de {@code web/js/aed-tabs.js}).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a decisão de cada tecla e a escolha da aba pela URL são funções puras no
 * navegador; o gabarito escrito à mão
 * (A3) mora em {@code src/test/js/web} e roda com {@code node --test}, nativo e sem dependência — como o da
 * Academia.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todos os casos passam e pelo menos {@link #PISO_DE_TESTES} são lidos: menos que
 * isso é instrumento cego, não aprovação.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> sem {@code node} no PATH o teste é IGNORADO (o gabarito fica NÃO
 * VERIFICADO, nunca aprovado); caso reprovado falha com a saída do {@code node --test}.</p>
 */
@DisplayName("Web: gabarito dos scripts globais em JavaScript (node --test)")
class WebJsGabaritoTest {

    private static final Path TESTES_JS = Path.of("src", "test", "js", "web");
    private static final int PISO_DE_TESTES = 25;
    private static final Pattern PASS = Pattern.compile("(?m)^# pass (\\d+)");
    private static final Pattern FAIL = Pattern.compile("(?m)^# fail (\\d+)");

    @Test
    @DisplayName("todos os casos do gabarito passam, com o piso de casos lidos")
    void gabaritoPassa() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TESTES_JS), "pasta de testes JS ausente");
        Assumptions.assumeTrue(nodeDisponivel(), "node não está no PATH: gabarito JS NÃO VERIFICADO");

        List<String> comando = new ArrayList<>(List.of("node", "--test", "--test-reporter=tap"));
        try (Stream<Path> arquivos = Files.list(TESTES_JS)) {
            arquivos.filter(p -> p.toString().endsWith(".test.js")).sorted()
                    .forEach(p -> comando.add(p.toString()));
        }
        assertTrue(comando.size() > 3, "nenhum arquivo .test.js encontrado: instrumento cego");

        Process processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(processo.waitFor(120, TimeUnit.SECONDS), "node --test não terminou em 120 s");

        assertEquals(0, processo.exitValue(), "node --test reprovou:\n" + saida);
        assertEquals(0, numero(FAIL, saida), "falhas no gabarito:\n" + saida);
        int passaram = numero(PASS, saida);
        assertTrue(passaram >= PISO_DE_TESTES,
                "só " + passaram + " casos passaram (piso " + PISO_DE_TESTES + "): instrumento cego?\n" + saida);
    }

    private static boolean nodeDisponivel() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    private static int numero(Pattern padrao, String saida) {
        Matcher m = padrao.matcher(saida);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
