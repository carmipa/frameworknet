package org.framework.net.paginaErros;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nenhuma resposta do site pode ser uma tela em branco — e o único caso que
 * ainda é fica registrado aqui, com o motivo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> status de erro com corpo vazio é a pior falha
 * de interface possível: o visitante não vê mensagem, não vê caminho de volta e
 * não sabe sequer que houve erro — a janela fica branca. Esta guarda varre os
 * erros que um visitante realmente encontra e exige página em todos.
 *
 * <p><b>O LIMITE CONHECIDO, medido e contido:</b> URL com par de escape
 * percentual inválido ({@code /%%%%}) é recusada pelo <b>parser HTTP do
 * Vert.x</b>, antes de existir roteador, recurso, filtro ou
 * {@code ExceptionMapper}. Medido em 08/09/2026 direto na aplicação em produção,
 * sem proxy: {@code 400}, sem {@code Content-Type}, <b>zero byte</b>.
 *
 * <p>Foi tentada a correção pela aplicação — um
 * {@code router.errorHandler(400, …)} no roteador do Vert.x. <b>Não funcionou, e
 * a medição provou:</b> a suíte deu exatamente o mesmo resultado com e sem o
 * handler, porque a requisição é recusada antes de o roteador vê-la. O código foi
 * descartado em vez de ficar no repositório dando a impressão de proteger algo.
 *
 * <p>Também foi descartada a correção pelo Nginx. Ligar
 * {@code proxy_intercept_errors on} faria o proxy capturar <b>todos</b> os 400
 * vindos da aplicação — inclusive os 400 em JSON das rotas {@code /api/}, que
 * virariam HTML e quebrariam todo {@code fetch()} do frontend em silêncio. A cura
 * seria pior que a doença, e este teste guarda essa fronteira em
 * {@link #rotaDeApiContinuaJson()}.
 *
 * <p><b>Por que é aceitável:</b> o caso atinge robô, scanner e link corrompido,
 * não fluxo de usuário — navegador codifica {@code %} digitado como {@code %25},
 * que é caminho válido e cai no 404 normal, com página. Fica como risco residual
 * declarado, e não como lacuna silenciosa.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> mostra a resposta crua. E o teste do
 * limite é bidirecional de propósito: se um upgrade do Quarkus passar a devolver
 * corpo nesse caso, ele <b>também</b> falha — avisando que o risco residual
 * deixou de existir e que este texto precisa ser atualizado.
 */
@QuarkusTest
@DisplayName("Resposta nunca em branco (e o único limite conhecido)")
class RespostaNuncaEmBrancoHttpTest {

    private static final String NAVEGADOR =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    /** Marca do layout — prova que veio tela, não corpo solto. */
    private static final String MARCA_DA_PAGINA = "Framework de Redes";

    @Test
    @DisplayName("todo erro que um visitante encontra devolve página, nunca vazio")
    void erroDeVisitanteNuncaVemVazio() {
        record Caso(String descricao, String rota, String metodo) { }

        List<Caso> casos = List.of(
                new Caso("rota inexistente", "/rota-que-nao-existe-abc", "GET"),
                new Caso("subrota inexistente", "/portas/familia-que-nao-existe", "GET"),
                new Caso("aprofundamento inexistente", "/protocolos/protocolo-inventado", "GET"),
                new Caso("caminho profundo", "/a/b/c/d/e/f", "GET"),
                new Caso("verbo errado na home", "/", "POST"),
                new Caso("verbo errado em página", "/sobre", "PUT"),
                new Caso("query sem sentido", "/analise?ip=&mascara=", "GET"),
                new Caso("arquivo inexistente", "/nao-existe.css", "GET"));

        List<String> emBranco = new ArrayList<>();
        for (Caso caso : casos) {
            Response r = given().header("Accept", NAVEGADOR)
                    .when().request(caso.metodo(), caso.rota()).thenReturn();
            String corpo = r.getBody().asString();

            if (corpo == null || corpo.isBlank()) {
                emBranco.add(caso.descricao() + " (" + caso.metodo() + " " + caso.rota()
                        + ") -> status " + r.getStatusCode() + " com corpo VAZIO");
            } else if (r.getStatusCode() >= 400 && !corpo.contains(MARCA_DA_PAGINA)
                    && !corpo.trim().startsWith("{")) {
                emBranco.add(caso.descricao() + " -> respondeu algo que não é página nem JSON");
            }
        }

        assertTrue(emBranco.isEmpty(),
                () -> "Resposta em branco — o visitante veria uma janela vazia:\n  "
                        + String.join("\n  ", emBranco));
    }

    @Test
    @DisplayName("LIMITE CONHECIDO: escape percentual inválido devolve 400 sem corpo")
    void limiteConhecidoDoParser() {
        // Este teste afirma o que HOJE acontece, não o que se deseja. Ele existe
        // para que o risco residual seja um fato verificado a cada build, e não
        // uma frase num documento que ninguém relê.
        RespostaCrua r = pedirCru("/%%%%", NAVEGADOR);

        assertEquals(400, r.status(),
                () -> "O parser do Vert.x deixou de responder 400 aqui. Comportamento mudou:"
                        + " revise o risco residual documentado nesta classe.\n" + r.resumo());
        assertTrue(r.corpo().isBlank(),
                () -> "BOA NOTÍCIA, e mesmo assim esta guarda falha de propósito: a resposta"
                        + " deixou de ser vazia. O risco residual descrito nesta classe não"
                        + " existe mais — atualize o javadoc e transforme isto em exigência de"
                        + " página.\n" + r.resumo());
    }

    @Test
    @DisplayName("REGRESSÃO: rota de API continua JSON mesmo com Accept de navegador")
    void rotaDeApiContinuaJson() {
        // Fronteira que a correção pelo Nginx teria atravessado.
        for (String url : List.of("/api/nao-existe", "/calculadora/api/nao-existe")) {
            Response r = given().header("Accept", NAVEGADOR).when().get(url).thenReturn();
            String corpo = r.getBody().asString();
            assertFalse(corpo.contains("<html"),
                    () -> url + " devolveu HTML. Rota de API responde JSON mesmo quando o Accept"
                            + " pede página — senão todo fetch() do frontend quebra em silêncio.");
            assertFalse(corpo.isBlank(), () -> url + " devolveu corpo vazio.");
        }
    }

    @Test
    @DisplayName("REGRESSÃO: home e 404 comum seguem como antes")
    void oQueJaFuncionavaContinua() {
        assertEquals(200, given().header("Accept", NAVEGADOR).when().get("/")
                .thenReturn().getStatusCode(), "A home tem de continuar 200.");

        Response r404 = given().header("Accept", NAVEGADOR)
                .when().get("/rota-que-nao-existe-abc").thenReturn();
        assertEquals(404, r404.getStatusCode());
        assertTrue(r404.getBody().asString().contains(MARCA_DA_PAGINA),
                () -> "O 404 comum tem de continuar servindo a página do site.");
    }

    /**
     * Fala HTTP/1.1 na mão.
     *
     * <p>Necessário porque nenhum cliente Java aceita montar esta URL: o
     * RestAssured re-codifica o {@code %} e envia {@code /%25%25%25%25} — caminho
     * <b>válido</b>, que dá 404 comum e faria o teste passar sem exercitar nada; e
     * com {@code urlEncodingEnabled(false)} o {@code java.net.URI} recusa do lado
     * do cliente ({@code Malformed escape pair}), sem sequer chegar ao servidor.
     * As duas armadilhas foram encontradas medindo, nesta ordem.</p>
     */
    private static RespostaCrua pedirCru(String alvo, String accept) {
        String pedido = "GET " + alvo + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Accept: " + accept + "\r\n"
                + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket("localhost", RestAssured.port)) {
            socket.setSoTimeout(10_000);
            OutputStream saida = socket.getOutputStream();
            saida.write(pedido.getBytes(StandardCharsets.ISO_8859_1));
            saida.flush();
            try (InputStream entrada = socket.getInputStream()) {
                return RespostaCrua.de(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha falando HTTP cru com " + alvo, e);
        }
    }

    /** Resposta HTTP crua, separada em linha de status, cabeçalhos e corpo. */
    private record RespostaCrua(int status, String cabecalhos, String corpo) {

        static RespostaCrua de(String bruta) {
            int corte = bruta.indexOf("\r\n\r\n");
            String cabeca = corte < 0 ? bruta : bruta.substring(0, corte);
            String corpo = corte < 0 ? "" : bruta.substring(corte + 4);
            int codigo = 0;
            String[] partes = cabeca.split("\r\n", 2)[0].split(" ");
            if (partes.length > 1) {
                try {
                    codigo = Integer.parseInt(partes[1]);
                } catch (NumberFormatException ignorado) {
                    codigo = 0;
                }
            }
            return new RespostaCrua(codigo, cabeca, corpo);
        }

        String resumo() {
            return "--- resposta crua ---\n" + cabecalhos
                    + "\n--- corpo (" + corpo.length() + " bytes) ---\n"
                    + corpo.substring(0, Math.min(400, corpo.length()));
        }
    }
}
