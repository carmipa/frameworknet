package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fontes de texto servidas pelo próprio site (proposta C21).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o Google Fonts entrava por {@code @import} em toda página: uma origem externa a mais
 * na CSP, uma conexão a mais antes da primeira pintura e o endereço de quem visita entregue a terceiro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum template nem estático próprio pede {@code fonts.googleapis.com} ou
 * {@code fonts.gstatic.com}; a CSP não libera essas origens, e continua liberando as do tradutor (A1: origem Google
 * legítima com o mesmo sinal); cada família usada no CSS do site tem {@code @font-face} local, e cada arquivo citado
 * é servido como woff2 de verdade (assinatura {@code wOF2}: o navegador consegue usar o que baixou, A6).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova nomeando o arquivo com a origem externa, a família sem fonte
 * local ou o arquivo de fonte que não é servido.</p>
 */
@QuarkusTest
@DisplayName("Web: fontes de texto locais, sem Google Fonts")
class FontesLocaisHttpTest {

    private static final Path RECURSOS = Path.of("src/main/resources");
    private static final Pattern GOOGLE_FONTS = Pattern.compile("fonts\\.(googleapis|gstatic)\\.com");
    private static final Set<String> FAMILIAS_DO_SITE = Set.of("Inter Tight", "JetBrains Mono", "Space Grotesk");

    static List<String> quemPedeGoogleFonts(Stream<Path> arquivos) {
        List<String> achados = new ArrayList<>();
        arquivos.forEach(p -> {
            try {
                if (GOOGLE_FONTS.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()) {
                    achados.add(p.toString().replace('\\', '/'));
                }
            } catch (IOException e) {
                achados.add(p + " (ilegível: " + e.getMessage() + ")");
            }
        });
        return achados;
    }

    @Test
    @DisplayName("nenhum template nem estático próprio pede o Google Fonts")
    void semGoogleFontsNosArquivos() throws IOException {
        List<Path> alvos;
        try (Stream<Path> todos = Files.walk(RECURSOS)) {
            alvos = todos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(html|css|js|properties)$"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/vendor/"))
                    .toList();
        }
        assertTrue(alvos.size() >= 100, "só " + alvos.size() + " arquivos lidos: instrumento cego?");
        assertEquals(List.of(), quemPedeGoogleFonts(alvos.stream()));
    }

    @Test
    @DisplayName("calibração: o leitor acha o @import do Google e não confunde com o tradutor")
    void leitorCalibrado() throws IOException {
        Path dir = Files.createTempDirectory("fontes");
        Path doente = Files.writeString(dir.resolve("a.css"), "@import url(\"https://fonts.googleapis.com/css2?x\");");
        Path sao = Files.writeString(dir.resolve("b.properties"), "script-src https://translate.googleapis.com");
        assertEquals(1, quemPedeGoogleFonts(Stream.of(doente, sao)).size());
    }

    @Test
    @DisplayName("a CSP não libera mais o Google Fonts e continua liberando o tradutor")
    void cspSemGoogleFonts() {
        String csp = given().when().get("/").then().statusCode(200).extract().header("Content-Security-Policy");
        assertTrue(csp != null && csp.contains("font-src 'self'"), "a página saiu sem CSP: " + csp);
        assertFalse(GOOGLE_FONTS.matcher(csp).find(), "a CSP ainda libera o Google Fonts: " + csp);
        assertTrue(csp.contains("https://translate.googleapis.com"), "a CSP perdeu a origem do tradutor");
    }

    @Test
    @DisplayName("cada família do site tem @font-face local, e cada arquivo é servido como woff2")
    void cadaFamiliaTemFonteLocalServida() {
        String home = given().when().get("/").then().statusCode(200).extract().asString();
        Matcher link = Pattern.compile("<link rel=\"stylesheet\" href=\"(/web/css/fontes\\.css\\?v=[0-9a-z]+)\">")
                .matcher(home);
        assertTrue(link.find(), "a página não carrega a folha de fontes locais");
        String css = given().when().get(link.group(1)).then().statusCode(200)
                .contentType(containsString("text/css")).extract().asString();

        Set<String> familias = new TreeSet<>();
        Matcher familia = Pattern.compile("font-family: \"([^\"]+)\"").matcher(css);
        while (familia.find()) {
            familias.add(familia.group(1));
        }
        assertTrue(familias.containsAll(FAMILIAS_DO_SITE), "família sem fonte local: " + familias);

        Matcher url = Pattern.compile("url\\(\"([^\"]+)\"\\)").matcher(css);
        int arquivos = 0;
        while (url.find()) {
            byte[] bytes = given().when().get(url.group(1)).then().statusCode(200).extract().asByteArray();
            assertTrue(bytes.length > 4 && bytes[0] == 'w' && bytes[1] == 'O' && bytes[2] == 'F' && bytes[3] == '2',
                    url.group(1) + " não é woff2");
            arquivos++;
        }
        assertEquals(8, arquivos, "esperava 8 arquivos (três famílias, itálico da Inter Tight, latin e latin-ext)");
    }
}
