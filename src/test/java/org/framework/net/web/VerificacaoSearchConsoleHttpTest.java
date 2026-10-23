package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O arquivo que prova ao Google que este site é nosso.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o Search Console só aceita enviar sitemap,
 * pedir indexação e mostrar os relatórios de cobertura depois que a propriedade
 * está <i>verificada</i>. A verificação deste domínio é feita pelo método de
 * arquivo, e o próprio Google avisa: <i>"para continuar verificado, não remova o
 * arquivo, mesmo após a verificação bem-sucedida"</i>. Ele não é um artefato de
 * instalação que se joga fora — é a credencial permanente, reconferida de tempos
 * em tempos.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) o arquivo existe no código-fonte, em
 * {@code META-INF/resources}, e é servido como estático — pelo mesmo motivo do
 * {@code robots.txt}: estático não passa pelos filtros JAX-RS, então robô do
 * Google não vira evento de telemetria; (2) o <b>conteúdo é exatamente</b> o que
 * o Google gerou, {@code google-site-verification: <nome do arquivo>} — o
 * Google compara o conteúdo, não só o status 200, e uma página de erro
 * respondendo 200 no lugar dele reprovaria a verificação; (3) a rota <b>não pode
 * estar fechada no robots.txt</b>: mesmo que a verificação em si não obedeça ao
 * robots, fechá-la seria declarar que não queremos que ela seja lida — o oposto
 * do que ela existe para fazer.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> falha fechada e nomeia o arquivo. É
 * esta guarda que impede o modo de falha real: uma limpeza de estáticos daqui a
 * seis meses apaga um arquivo de nome estranho que "ninguém sabe para que
 * serve", a verificação cai em silêncio, e a perda só aparece quando o Search
 * Console parar de reportar — sem erro, sem log, sem alerta. O nome fica escrito
 * aqui de propósito, para que quem for apagá-lo encontre este teste antes.
 */
@QuarkusTest
@DisplayName("Search Console: o arquivo de verificação da propriedade")
class VerificacaoSearchConsoleHttpTest {

    /** Emitido pelo Google para {@code https://frameworknet.carminati.dev.br/} em 08/09/2026. */
    private static final String ARQUIVO = "googlea97461b897c3f9fa.html";

    /** O Google confere o conteúdo, não só o status. */
    private static final String CONTEUDO = "google-site-verification: " + ARQUIVO;

    private static final Path NO_CODIGO =
            Path.of("src", "main", "resources", "META-INF", "resources", ARQUIVO);

    private static final Path ROBOTS =
            Path.of("src", "main", "resources", "META-INF", "resources", "robots.txt");

    @Test
    @DisplayName("o arquivo existe no código-fonte, com o conteúdo exato do Google")
    void arquivoExisteNoCodigoComOConteudoCerto() {
        assertTrue(Files.exists(NO_CODIGO),
                () -> "O arquivo de verificação do Search Console sumiu de " + NO_CODIGO.toAbsolutePath()
                        + ". Sem ele a propriedade é DESVERIFICADA na próxima reconferência do Google,"
                        + " e o site perde sitemap, pedido de indexação e relatórios — em silêncio.");

        String texto = ler(NO_CODIGO).strip();
        assertEquals(CONTEUDO, texto,
                "O conteúdo tem de ser exatamente o que o Google gerou: ele compara o texto.");
    }

    @Test
    @DisplayName("é servido em 200 com o conteúdo exato")
    void servidoComOConteudoExato() {
        given()
                .when().get("/" + ARQUIVO)
                .then()
                .statusCode(200)
                .body(containsString(CONTEUDO));
    }

    @Test
    @DisplayName("a rota não está fechada no robots.txt")
    void rotaNaoEstaFechadaNoRobots() {
        List<String> disallow = RobotsTxt.grupos(ler(ROBOTS)).get("googlebot");
        assertTrue(disallow != null && !disallow.isEmpty(),
                "Não li regra nenhuma para o Googlebot: a guarda não verificou nada.");

        assertFalse(RobotsTxt.bloqueado(disallow, "/" + ARQUIVO),
                () -> "O robots.txt fechou a rota do arquivo de verificação, que existe justamente"
                        + " para ser lida pelo Google.");
    }

    private static String ler(Path caminho) {
        try {
            return Files.readString(caminho);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + caminho.toAbsolutePath(), e);
        }
    }
}
