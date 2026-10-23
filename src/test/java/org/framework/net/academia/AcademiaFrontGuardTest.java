package org.framework.net.academia;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Guardas da tela da Academia (INV-ACAD-007 e boa-fé do computador de laboratório).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a lição tem de funcionar sem terceiros, ser traduzível sem
 * destruir valor técnico e não deixar a resposta de um aluno aparecer para o próximo no mesmo
 * computador. Cada regra aqui nasceu de um modo concreto de a lição quebrar sem erro nenhum.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b></p>
 * <ul>
 *   <li>nenhum texto desenhado em canvas ({@code fillText}/{@code strokeText}) no JS da Academia —
 *       texto em canvas não se traduz nem chega ao leitor de tela;</li>
 *   <li>nenhum endereço externo ({@code http://}/{@code https://}) no JS e no CSS da Academia — a
 *       lição não depende de terceiro;</li>
 *   <li>nenhum {@code innerHTML}/{@code outerHTML}/{@code insertAdjacentHTML} no JS da Academia —
 *       o que vem do aluno nunca vira HTML;</li>
 *   <li>todo elemento {@code acad-valor} nos templates leva {@code translate="no"};</li>
 *   <li>todo {@code <input>} dos templates da Academia leva {@code data-history="off"} — senão o
 *       histórico de campos do site ofereceria, ao próximo aluno, as respostas do anterior.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando arquivo e trecho. Pasta ausente é
 * {@code Assumptions} (não verificou, nunca aprovou); pasta presente com menos arquivos que o piso
 * reprova como instrumento cego. Cada regra é calibrada contra texto sintético nos dois sentidos.</p>
 */
@DisplayName("Academia: guardas de tela (canvas, terceiros, HTML, tradução, histórico)")
class AcademiaFrontGuardTest {

    private static final Path ESTATICO = Path.of("src", "main", "resources", "META-INF", "resources", "academia");
    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates", "academia");

    private static final Pattern TEXTO_EM_CANVAS = Pattern.compile("\\b(fillText|strokeText)\\s*\\(");
    private static final Pattern EXTERNO = Pattern.compile("https?://");
    private static final Pattern HTML_CRU = Pattern.compile("\\b(innerHTML|outerHTML|insertAdjacentHTML)\\b");
    private static final Pattern TAG_COM_VALOR = Pattern.compile("<[a-z]+\\b[^>]*class=\"[^\"]*\\bacad-valor\\b[^\"]*\"[^>]*>");
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.DOTALL);

    @Test
    @DisplayName("JS e CSS da Academia: sem texto em canvas, sem terceiros, sem HTML cru")
    void estaticos() {
        List<Path> arquivos = listar(ESTATICO, ".js", ".css");
        assertTrue(arquivos.size() >= 10, "só " + arquivos.size() + " arquivos lidos: instrumento cego");
        List<String> violacoes = new ArrayList<>();
        for (Path arquivo : arquivos) {
            String conteudo = ler(arquivo);
            violacoes.addAll(violacoesEstatico(arquivo.toString(), conteudo));
        }
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    @Test
    @DisplayName("templates da Academia: valor com translate=\"no\" e campo sem histórico")
    void templates() {
        List<Path> arquivos = listar(TEMPLATES, ".html");
        assertTrue(arquivos.size() >= 8, "só " + arquivos.size() + " templates lidos: instrumento cego");
        List<String> violacoes = new ArrayList<>();
        int valores = 0;
        int campos = 0;
        for (Path arquivo : arquivos) {
            String conteudo = ler(arquivo);
            valores += contar(TAG_COM_VALOR, conteudo);
            campos += contar(INPUT, conteudo);
            violacoes.addAll(violacoesTemplate(arquivo.toString(), conteudo));
        }
        assertTrue(valores > 10, "controle positivo: só " + valores + " elementos acad-valor vistos");
        assertTrue(campos >= 6, "controle positivo: só " + campos + " campos vistos");
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    /**
     * Fatia não fala com fatia também na tela: a página de uma fatia só carrega script e estilo do
     * kernel ({@code /academia/core/}) e dela mesma. Link de NAVEGAÇÃO para outra fatia é livre.
     */
    @Test
    @DisplayName("página de fatia só carrega asset do kernel e dela mesma")
    void assetsSemCruzarFatia() {
        List<Path> arquivos = listar(TEMPLATES, ".html");
        List<String> violacoes = new ArrayList<>();
        int referencias = 0;
        for (Path arquivo : arquivos) {
            String fatia = TEMPLATES.relativize(arquivo).getName(0).toString();
            if ("core".equals(fatia)) {
                continue;
            }
            String conteudo = ler(arquivo);
            referencias += contar(ASSET_DA_ACADEMIA, conteudo);
            violacoes.addAll(violacoesAsset(arquivo.toString(), fatia, conteudo));
        }
        assertTrue(referencias >= 20, "controle positivo: só " + referencias + " assets da Academia vistos");
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    private static final Pattern ASSET_DA_ACADEMIA = Pattern.compile("(?:src|href)=\"/academia/([a-z0-9]+)/(?:js|css)/");

    private static List<String> violacoesAsset(String nome, String fatia, String conteudo) {
        List<String> violacoes = new ArrayList<>();
        Matcher m = ASSET_DA_ACADEMIA.matcher(conteudo);
        while (m.find()) {
            String dono = m.group(1);
            if (!"core".equals(dono) && !fatia.equals(dono)) {
                violacoes.add(nome + ": a fatia " + fatia + " carrega asset da fatia " + dono);
            }
        }
        return violacoes;
    }

    @Test
    @DisplayName("calibração: cada regra reprova o caso doente e aceita o legítimo parecido")
    void calibracao() {
        assertEquals(1, violacoesAsset("x.html", "ipv4", "<script src=\"/academia/fundamentos/js/contas-fundamentos.js\">").size());
        assertEquals(0, violacoesAsset("x.html", "ipv4", "<script src=\"/academia/ipv4/js/contas-ipv4.js\"><script src=\"/academia/core/js/motor.js\">").size());
        assertEquals(0, violacoesAsset("x.html", "inicio", "<a href=\"/academia/fundamentos/binario\">").size(),
                "A1: link de navegação para outra fatia é livre");
        assertEquals(1, violacoesEstatico("x.js", "ctx.fillText('128', 0, 0);").size());
        assertEquals(0, violacoesEstatico("x.js", "// o texto nunca vai para fillTextura nem canvas").size(),
                "A1: palavra parecida não é chamada");
        assertEquals(1, violacoesEstatico("x.js", "fetch('https://cdn.exemplo/lib.js')").size());
        assertEquals(0, violacoesEstatico("x.js", "fetch('/academia/api/eventos')").size());
        assertEquals(1, violacoesEstatico("x.js", "el.innerHTML = valor;").size());
        assertEquals(0, violacoesEstatico("x.js", "el.textContent = valor;").size());

        assertEquals(1, violacoesTemplate("x.html", "<span class=\"acad-valor\">128</span>").size());
        assertEquals(0, violacoesTemplate("x.html", "<span class=\"acad-valor\" translate=\"no\">128</span>").size());
        assertEquals(0, violacoesTemplate("x.html", "<span class=\"acad-valor-grande\" translate=\"no\">0</span>").size());
        assertEquals(1, violacoesTemplate("x.html", "<input id=\"a\" class=\"form-control\">").size());
        assertEquals(0, violacoesTemplate("x.html", "<input id=\"a\"\n data-history=\"off\" class=\"form-control\">").size());
    }

    private static List<String> violacoesEstatico(String nome, String conteudo) {
        List<String> violacoes = new ArrayList<>();
        Matcher m = TEXTO_EM_CANVAS.matcher(conteudo);
        while (m.find()) {
            violacoes.add(nome + ": texto em canvas (" + m.group(1) + ")");
        }
        m = EXTERNO.matcher(conteudo);
        while (m.find()) {
            violacoes.add(nome + ": endereço externo na posição " + m.start());
        }
        m = HTML_CRU.matcher(conteudo);
        while (m.find()) {
            violacoes.add(nome + ": " + m.group(1));
        }
        return violacoes;
    }

    private static List<String> violacoesTemplate(String nome, String conteudo) {
        List<String> violacoes = new ArrayList<>();
        Matcher m = TAG_COM_VALOR.matcher(conteudo);
        while (m.find()) {
            if (!m.group().contains("translate=\"no\"")) {
                violacoes.add(nome + ": valor sem translate=\"no\": " + m.group());
            }
        }
        m = INPUT.matcher(conteudo);
        while (m.find()) {
            if (!m.group().contains("data-history=\"off\"")) {
                violacoes.add(nome + ": campo sem data-history=\"off\": " + m.group().replaceAll("\\s+", " "));
            }
        }
        return violacoes;
    }

    private static int contar(Pattern padrao, String conteudo) {
        int n = 0;
        Matcher m = padrao.matcher(conteudo);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static List<Path> listar(Path raiz, String... extensoes) {
        Assumptions.assumeTrue(Files.isDirectory(raiz), "pasta não encontrada: " + raiz.toAbsolutePath());
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(p -> Stream.of(extensoes).anyMatch(e -> p.toString().endsWith(e)))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
