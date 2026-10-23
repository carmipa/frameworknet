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
 * Guarda do contraste das cores de texto do tema (WCAG 2.1, critério 1.4.3: 4,5:1 para texto normal).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o texto "apagado" do tema ({@code --aed-text-faint} #5b6679, em 50 regras)
 * ficava em 3,1:1 sobre o cartão, e o vermelho/verde do Bootstrap em 3,4–4,2:1 sobre o fundo escuro
 * (auditoria FRONT-11). A varredura no navegador mediu 1890 textos abaixo de AA em 83 páginas; corrigido nos
 * tokens, ficou 0. Esta guarda impede a volta pelo caminho mais provável: alguém "suavizar" um token.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> cada par (cor de texto, fundo) abaixo, lido do CSS real, tem razão de
 * contraste ≥ 4,5 pela fórmula da WCAG; o fundo usado é o mais claro em que o texto aparece (o cartão,
 * não a página), que é o pior caso.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova nomeando o token, as duas cores e a razão; token não
 * encontrado no arquivo reprova também (instrumento cego não é aprovação).</p>
 */
class ContrasteTokensGuardTest {

    private static final Path ESTATICOS = Path.of("src/main/resources/META-INF/resources");

    /** Razão de contraste WCAG entre duas cores #rrggbb. */
    static double contraste(String a, String b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        double[] c = {(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
        for (int i = 0; i < 3; i++) {
            double s = c[i] / 255;
            c[i] = s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** Valor #rrggbb de uma declaração (variável ou propriedade de um seletor) no arquivo. */
    private static String valor(String arquivo, String padrao) throws IOException {
        String css = Files.readString(ESTATICOS.resolve(arquivo), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(padrao + "\\s*:?\\s*(#[0-9a-fA-F]{6})").matcher(css);
        assertTrue(m.find(), "não achei " + padrao + " em " + arquivo + " (instrumento cego)");
        return m.group(1).toLowerCase();
    }

    @Test
    void calibracaoDaFormula() {
        // Valores de referência da própria WCAG: preto/branco 21:1; o par antigo do tema reprovava.
        assertEquals(21.0, contraste("#000000", "#ffffff"), 0.01);
        assertTrue(contraste("#5b6679", "#0f1622") < 4.5, "o par doente tem de reprovar");
        assertTrue(contraste("#7d899f", "#0f1622") >= 4.5, "o par legítimo tem de passar");
    }

    @Test
    void coresDeTextoDoTemaPassamAA() throws IOException {
        String tema = "web/css/aed-command-center.css";
        String cartao = valor(tema, "--aed-card-top");
        String pagina = valor(tema, "--aed-bg");
        String[][] pares = {
                {tema, "--aed-text-faint", cartao},
                {tema, "--aed-text-faint", pagina},
                {tema, "--aed-text-secondary", cartao},
                {tema, "body\\.aed-app \\.text-danger \\{ color", cartao},
                {tema, "body\\.aed-app \\.text-success \\{ color", cartao},
                {tema, "body\\.aed-app \\.text-primary \\{ color", cartao},
                {tema, "--bs-code-color", cartao},
                {"login/css/login.css", "--tx-4", valor("login/css/login.css", "--bg")},
                {"paginaErros/css/erro.css", "--tx-4", valor("paginaErros/css/erro.css", "--bg")},
        };
        List<String> reprovados = new ArrayList<>();
        for (String[] par : pares) {
            String texto = valor(par[0], par[1]);
            double r = contraste(texto, par[2]);
            if (r < 4.5) {
                reprovados.add(String.format("%s %s %s sobre %s = %.2f:1", par[0], par[1], texto, par[2], r));
            }
        }
        assertTrue(reprovados.isEmpty(), "texto abaixo de AA (4,5:1): " + reprovados);
    }

    @Test
    void botaoIndigoTemTextoBrancoLegivelNasDuasPontas() throws IOException {
        String css = Files.readString(ESTATICOS.resolve("web/css/aed-command-center.css"), StandardCharsets.UTF_8);
        Matcher bloco = Pattern.compile("\\.aed-btn-indigo \\{[^}]*linear-gradient\\(135deg, (#[0-9a-f]{6}), (#[0-9a-f]{6})\\)")
                .matcher(css);
        assertTrue(bloco.find(), "gradiente do .aed-btn-indigo não encontrado (instrumento cego)");
        for (String ponta : List.of(bloco.group(1), bloco.group(2))) {
            assertTrue(contraste("#ffffff", ponta) >= 4.5, "branco sobre " + ponta + " abaixo de AA");
        }
    }
}
