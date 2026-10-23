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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda da grade que cabe em qualquer tela (regra de CSS do Paulo: {@code minmax(min(100%, X), 1fr)}).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> uma coluna de grade com mínimo fixo maior que a tela empurra a página
 * para o lado no celular. Medido em 01/10/2026: em 320–360 px o {@code minmax(16rem, 1fr)} dos gráficos do
 * Tráfego passava do cartão, e havia 19 grades no site com mínimo de 12rem ou mais sem a trava
 * {@code min(100%, …)}. Corrigidas todas de uma vez; esta guarda impede a próxima.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todo {@code minmax(} cujo primeiro argumento é um comprimento fixo de
 * 12rem (192 px) ou mais vem envolvido em {@code min(100%, …)}; mínimo pequeno (até 12rem exclusive) cabe
 * em qualquer tela e passa; o instrumento é o mesmo nos arquivos reais e nos casos de calibração.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando arquivo e trecho de cada grade sem a trava;
 * pasta de estáticos sem nenhum CSS reprova também (alvo vazio não é aprovação).</p>
 */
class CssGradeResponsivaGuardTest {

    private static final Path ESTATICOS = Path.of("src/main/resources/META-INF/resources");

    /** Primeiro argumento do minmax como comprimento fixo (o caso com min(...) não casa: começa com "min("). */
    private static final Pattern MINMAX_FIXO = Pattern.compile("minmax\\(\\s*([0-9.]+)(rem|px|em)\\s*,");

    private static final double LIMITE_PX = 192;

    /** Violações num texto CSS; usada nos arquivos reais E nos casos de calibração. */
    static List<String> violacoes(String css) {
        List<String> out = new ArrayList<>();
        Matcher m = MINMAX_FIXO.matcher(css);
        while (m.find()) {
            double n = Double.parseDouble(m.group(1));
            double px = "px".equals(m.group(2)) ? n : n * 16;
            if (px >= LIMITE_PX) {
                out.add(m.group());
            }
        }
        return out;
    }

    @Test
    void instrumentoDiscriminaDoenteDeLegitimo() {
        // A1: o mesmo sinal (minmax com 18rem) reprovado sem a trava e aceito com ela.
        assertEquals(1, violacoes("grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));").size());
        assertEquals(0, violacoes("grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr));").size());
        assertEquals(1, violacoes(".x{grid-template-columns:repeat(auto-fit,minmax(300px,1fr))}").size());
        // fronteira: 12rem reprova, 11.5rem passa (cabe em 320 px com folga)
        assertEquals(1, violacoes("minmax(12rem, 1fr)").size());
        assertEquals(0, violacoes("minmax(11.5rem, 1fr)").size());
        assertEquals(0, violacoes("minmax(0, 1fr)").size());
    }

    @Test
    void todaGradeLargaCabeEmTelaPequena() throws IOException {
        List<String> problemas = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> s = Files.walk(ESTATICOS)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".css") && !x.toString().endsWith(".min.css")).toList()) {
                lidos++;
                for (String v : violacoes(Files.readString(p, StandardCharsets.UTF_8))) {
                    problemas.add(ESTATICOS.relativize(p) + ": " + v);
                }
            }
        }
        assertTrue(lidos > 10, "a guarda leu " + lidos + " CSS — alvo vazio não é aprovação");
        assertTrue(problemas.isEmpty(), "grade com mínimo fixo maior que um celular, sem min(100%, …):\n  "
                + String.join("\n  ", problemas));
    }

    /**
     * Regra de CSS do Paulo: {@code auto-fit}, nunca {@code auto-fill} — o auto-fill cria colunas vazias e cola
     * os itens à esquerda quando sobram poucos (abas de um modo só, a home numa tela larga).
     */
    @Test
    void gradeRepetidaUsaAutoFit() throws IOException {
        Pattern autoFill = Pattern.compile("repeat\\(\\s*auto-fill\\b");
        assertTrue(autoFill.matcher("repeat(auto-fill, minmax(7rem, 1fr))").find(), "calibração: tem de achar o doente");
        assertTrue(!autoFill.matcher("repeat(auto-fit, minmax(7rem, 1fr))").find(), "calibração: o legítimo passa");
        List<String> problemas = new ArrayList<>();
        int lidos = 0;
        try (Stream<Path> s = Files.walk(ESTATICOS)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".css") && !x.toString().endsWith(".min.css")).toList()) {
                lidos++;
                if (autoFill.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()) {
                    problemas.add(ESTATICOS.relativize(p).toString());
                }
            }
        }
        assertTrue(lidos > 10, "a guarda leu " + lidos + " CSS — alvo vazio não é aprovação");
        assertTrue(problemas.isEmpty(), "grade com auto-fill (use auto-fit): " + problemas);
    }
}
