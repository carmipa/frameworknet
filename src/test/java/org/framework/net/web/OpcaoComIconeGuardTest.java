package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As opções do montador de comandos (Ferramentas) são rótulos montados pelo JavaScript: cada uma declara o próprio
 * ícone, e opção nova sem {@code icone} reprova aqui — o rótulo nunca nasce sem ícone em silêncio.
 */
class OpcaoComIconeGuardTest {

    private static final Path FERRAMENTAS = Path.of("src/main/resources/META-INF/resources/ferramentas/js/ferramentas.js");
    private static final Pattern OPCAO = Pattern.compile("\\{\\s*flag:\\s*\"([^\"]*)\"([^}]*)\\}");

    static List<String> semIcone(String js) {
        List<String> achados = new ArrayList<>();
        Matcher m = OPCAO.matcher(js);
        while (m.find()) {
            if (!m.group(2).matches("(?s).*\\bicone:\\s*\"[a-z0-9_]+\".*")) {
                achados.add(m.group(1));
            }
        }
        return achados;
    }

    @Test
    void instrumentoDiscrimina() {
        assertEquals(List.of("-v"), semIcone("{ flag: \"-v\", label: \"Verboso\", exp: \"x\" }"));
        assertEquals(List.of(), semIcone("{ flag: \"-v\", icone: \"subject\", label: \"Verboso\", exp: \"x\" }"));
        assertEquals(List.of("-k"), semIcone("{ flag: \"-k\", icone: \"\", label: \"Ignorar\", exp: \"x\" }"));
    }

    @Test
    void todaOpcaoDeclaraIcone() throws IOException {
        String js = Files.readString(FERRAMENTAS, StandardCharsets.UTF_8);
        long opcoes = OPCAO.matcher(js).results().count();
        assertTrue(opcoes > 10, "instrumento cego: " + opcoes + " opções");
        assertEquals(List.of(), semIcone(js));
    }
}
