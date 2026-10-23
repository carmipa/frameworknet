package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A imagem do cartão de compartilhamento — a que o WhatsApp precisa baixar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> quando alguém cola o link do site num grupo de
 * WhatsApp, no LinkedIn ou no Discord, o que aparece é o cartão do Open Graph. Se
 * a imagem for pesada demais, o aplicativo <b>desiste do preview</b> e o link
 * aparece cru — sem que nada quebre e sem nenhum erro em log. Medido em
 * 08/09/2026: o {@code og:image} apontava para o {@code /icone.png} do site, que
 * tem <b>1,42 MB</b>. O WhatsApp costuma desistir acima de algumas centenas de
 * KB, e é por onde estes links mais circulam.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) a imagem responde 200 e é uma imagem de
 * verdade — {@code og:image} apontando para 404 produz cartão vazio;
 * (2) fica <b>abaixo do teto</b> desta guarda, com folga sobre o limite prático
 * do WhatsApp; (3) a URL é <b>absoluta</b> — Open Graph com caminho relativo é
 * ignorado por praticamente todo rastreador; (4) o {@code icone.png} original
 * continua existindo e servindo o site (favicon, PWA, hero): esta guarda protege
 * a imagem do cartão, não substitui a outra.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> mostra o tamanho medido e o teto. A
 * falha mais provável no futuro é alguém regerar a arte em alta resolução e
 * publicá-la por cima — o cartão pararia de aparecer em silêncio, e é
 * exatamente esse silêncio que esta guarda quebra.
 */
@QuarkusTest
@DisplayName("Open Graph: a imagem do cartão de compartilhamento")
class OpenGraphImagemHttpTest {

    /**
     * Teto em bytes.
     *
     * <p>O WhatsApp é o mais restritivo dos destinos que importam aqui e não
     * publica um número oficial; a prática gira em torno de algumas centenas de
     * KB. 300 KB deixa folga confortável e ainda cabe uma arte nítida em
     * 1200 px — a versão atual tem ~168 KB.</p>
     */
    private static final long TETO_BYTES = 300 * 1024;

    private static final Path ARQUIVO =
            Path.of("src", "main", "resources", "META-INF", "resources", "og-imagem.jpg");

    private static final Pattern OG_IMAGE =
            Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\">");

    @Test
    @DisplayName("a imagem existe no código e cabe no teto")
    void imagemCabeNoTeto() {
        assertTrue(Files.exists(ARQUIVO),
                () -> "Sumiu " + ARQUIVO.toAbsolutePath() + " — o cartão de compartilhamento"
                        + " ficaria sem imagem.");
        long bytes = tamanho(ARQUIVO);
        assertTrue(bytes <= TETO_BYTES,
                () -> String.format("A imagem do Open Graph está com %.1f KB, acima do teto de"
                        + " %d KB. Acima disso o WhatsApp desiste do preview e o link passa a"
                        + " aparecer cru — sem erro nenhum, o que torna a falha invisível."
                        + " Regere com largura 1200 e JPEG de qualidade ~88.",
                        bytes / 1024.0, TETO_BYTES / 1024));
    }

    @Test
    @DisplayName("a home declara og:image absoluto e ele responde como imagem")
    void ogImageAbsolutoEServido() {
        String html = given().when().get("/").then().extract().asString();
        Matcher m = OG_IMAGE.matcher(html);
        assertTrue(m.find(), () -> "A home não declara og:image.");

        String url = m.group(1);
        assertTrue(url.startsWith("http"),
                () -> "og:image tem de ser URL absoluta; veio: " + url
                        + ". Caminho relativo é ignorado pelos rastreadores.");

        // Baixa pelo caminho, do próprio servidor de teste: prova que o estático
        // é servido, e não só que o arquivo existe no disco do desenvolvedor.
        String caminho = url.substring(url.indexOf('/', url.indexOf("//") + 2));
        Response r = given().when().get(caminho).thenReturn();
        assertTrue(r.getStatusCode() == 200,
                () -> "og:image respondeu " + r.getStatusCode() + " em " + caminho
                        + " — cartão de compartilhamento sairia sem imagem.");
        assertTrue(r.getContentType() != null && r.getContentType().startsWith("image/"),
                () -> "og:image não é imagem: " + r.getContentType());
        assertFalse(r.getBody().asByteArray().length == 0,
                () -> "og:image veio vazia.");
    }

    @Test
    @DisplayName("as dimensões declaradas batem com o arquivo servido")
    void dimensoesDeclaradasSaoVerdadeiras() {
        // Dimensão declarada errada faz o cartão "pular" enquanto carrega e, em
        // alguns clientes, o preview ser descartado. Se a arte for regerada com
        // outro tamanho e as metas ficarem para trás, esta guarda acusa.
        String html = given().when().get("/").then().extract().asString();
        int largura = inteiroDeclarado(html, "og:image:width");
        int altura = inteiroDeclarado(html, "og:image:height");

        java.awt.Dimension real = dimensaoReal();
        assertTrue(largura == real.width && altura == real.height,
                () -> "og:image declara " + largura + "x" + altura + " mas o arquivo tem "
                        + real.width + "x" + real.height + ".");
    }

    private static int inteiroDeclarado(String html, String propriedade) {
        Matcher m = Pattern.compile("<meta property=\"" + propriedade + "\" content=\"(\\d+)\">")
                .matcher(html);
        assertTrue(m.find(), () -> "A home não declara " + propriedade + ".");
        return Integer.parseInt(m.group(1));
    }

    private static java.awt.Dimension dimensaoReal() {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(ARQUIVO.toFile());
            assertTrue(img != null, () -> "Não consegui ler " + ARQUIVO + " como imagem.");
            return new java.awt.Dimension(img.getWidth(), img.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha lendo " + ARQUIVO.toAbsolutePath(), e);
        }
    }

    private static long tamanho(Path caminho) {
        try {
            return Files.size(caminho);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha medindo " + caminho.toAbsolutePath(), e);
        }
    }
}
