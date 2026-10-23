package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Política pública para robôs de busca — o {@code /robots.txt} do site.
 *
 * <p><b>Propósito de negócio:</b> o site precisa ser encontrado pelas páginas
 * didáticas e, ao mesmo tempo, manter robô fora das rotas que custam caro ou
 * não têm valor de busca: {@code /informacoes} dispara consulta geográfica
 * externa a cada GET (ip-api, 45 req/min), {@code /export} gera PDF sob
 * demanda, {@code /history} devolve o histórico com o IP de quem consultou e as
 * rotas de API devolvem JSON. Sem o arquivo, tudo isso é rastreável.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) o arquivo responde 200 como
 * {@code text/plain}; (2) o robots.txt <b>não soma grupos</b> — um robô obedece
 * só ao bloco mais específico que casa com ele, então cada grupo permissivo
 * repete a lista inteira de {@code Disallow}; (3) toda rota de API declarada por
 * {@code @Path} no código está fechada em todos esses grupos; (4) as páginas
 * didáticas continuam abertas — bloqueio a mais tira o site do índice, que é o
 * oposto do objetivo.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste falha nomeando o grupo e a
 * rota que ficou de fora. A varredura dos fontes falha fechada: se não achar
 * nenhuma rota de API, reprova em vez de aprovar por cegueira — "não achei" e
 * "não tinha como achar" não podem parecer a mesma coisa. Fora da raiz do
 * projeto a varredura é ignorada via {@code Assumptions}, nunca aprovada.</p>
 */
@QuarkusTest
@DisplayName("robots.txt: política pública para robôs de busca")
class RobotsTxtHttpTest {

    private static final Path RAIZ_FONTES = Path.of("src", "main", "java", "org", "framework", "net");

    private static final Pattern ANOTACAO_PATH = Pattern.compile("@Path\\(\"([^\"]*)\"\\)");

    /** Grupos que liberam a navegação: cada um precisa repetir a lista inteira. */
    private static final List<String> GRUPOS_PERMISSIVOS =
            List.of("googlebot", "bingbot", "duckduckbot", "*");

    /** Rotas que nenhum robô deve rastrear, com exemplos de subcaminho real. */
    private static final List<String> ROTAS_FECHADAS = List.of(
            "/history", "/history/catalog",
            "/export/json", "/export/pdf",
            "/informacoes", "/mascara-referencia", "/health",
            "/api/informacoes/geo");

    /**
     * Área autenticada e telas de entrada: ficam FORA do arquivo.
     *
     * <p>O robots.txt é público, então listar caminho privado nele é entregar o mapa de onde
     * procurar; e rota em {@code Disallow} nunca é buscada, logo o {@code noindex} dela nunca é
     * lido. Quem tira estas rotas do índice é o cabecalho {@code X-Robots-Tag}
     * ({@code XRobotsTagHttpTest}), e quem as fecha é o login.</p>
     */
    private static final List<String> FORA_DO_ARQUIVO = RobotsTxt.AREA_AUTENTICADA;

    /** Páginas didáticas que existem para ser encontradas. */
    private static final List<String> PAGINAS_ABERTAS = List.of(
            "/", "/analise", "/calculadora", "/portas", "/protocolos",
            "/protocolos/bgp", "/protocolos/ssh", "/resolucao-problemas",
            "/localizacao", "/trafego", "/seguranca", "/diagnostico",
            "/documentacao", "/sobre");

    @Test
    @DisplayName("é servido na raiz, com 200 e text/plain")
    void servidoNaRaizComoTextoSimples() {
        // Precisa estar na raiz: robots.txt em subpasta não vale para o site.
        // O tipo importa — o manifest já foi descartado em silêncio neste projeto
        // por ser entregue sem Content-Type.
        given()
                .when().get("/robots.txt")
                .then()
                .statusCode(200)
                .contentType(containsString("text/plain"))
                .body(containsString("User-agent:"));
    }

    @Test
    @DisplayName("todo grupo permissivo repete a lista inteira — robots.txt não soma grupos")
    void cadaGrupoPermissivoFechaTodasAsRotas() {
        Map<String, List<String>> grupos = grupos();
        List<String> faltando = new ArrayList<>();

        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.get(agente);
            assertTrue(regras != null, "O robots.txt não declara grupo para \"" + agente + "\".");
            for (String rota : ROTAS_FECHADAS) {
                if (!bloqueado(regras, rota)) {
                    faltando.add(agente + " não fecha " + rota);
                }
            }
        }

        assertTrue(faltando.isEmpty(), () -> "Um robô obedece só ao bloco mais específico que casa "
                + "com o nome dele: o que faltar no grupo dele fica liberado.\n  - "
                + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("área autenticada e tela de entrada não aparecem no robots.txt, nem em comentário")
    void areaPrivadaNaoApareceNoArquivo() {
        String corpo = given().when().get("/robots.txt").then().statusCode(200).extract().asString();
        // Controle: o arquivo lido tem regras de verdade; sem isso "não encontrei" seria cegueira.
        assertTrue(corpo.contains("Disallow: /api"), "O robots.txt lido não tem as regras esperadas.");

        List<String> listadas = FORA_DO_ARQUIVO.stream()
                .filter(prefixo -> java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(prefixo) + "(?![A-Za-z0-9_-])").matcher(corpo).find())
                .toList();
        assertTrue(listadas.isEmpty(), () -> "Caminho de área autenticada listado no robots.txt público: "
                + listadas + ". Ele vira mapa para quem quer atacar, e o Disallow impede o buscador de ler o"
                + " noindex da página.");
    }

    @Test
    @DisplayName("guarda: rota de API nova no código precisa nascer fechada aqui")
    void todaRotaDeApiDeclaradaNoCodigoEstaFechada() {
        List<String> rotasApi = rotasApiDeclaradasNoCodigo();

        // Falha fechada: varredura que não acha nada não é aprovação. Guarda verde
        // e cega, varrendo diretório vazio, já custou meses neste método.
        assertFalse(rotasApi.isEmpty(),
                "A varredura dos fontes não encontrou nenhuma rota de API — instrumento cego, "
                        + "não sistema limpo. Confira o padrão de leitura de @Path.");

        Map<String, List<String>> grupos = grupos();
        List<String> faltando = new ArrayList<>();
        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.getOrDefault(agente, List.of());
            for (String rota : rotasApi) {
                if (!bloqueado(regras, rota)) {
                    faltando.add(agente + " não fecha " + rota);
                }
            }
        }

        assertTrue(faltando.isEmpty(), () -> "Rota de API declarada no código está aberta para robô. "
                + "Acrescente o prefixo em TODOS os grupos permissivos do robots.txt.\n  - "
                + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("controle: as páginas didáticas continuam abertas")
    void paginasDidaticasContinuamAbertas() {
        // Este teste é o caso-controle dos outros: prova que a leitura do arquivo
        // sabe dizer NÃO. Sem ele, um "Disallow: /" tiraria o site do índice e
        // todos os demais testes continuariam verdes.
        Map<String, List<String>> grupos = grupos();
        List<String> excessos = new ArrayList<>();

        for (String agente : GRUPOS_PERMISSIVOS) {
            List<String> regras = grupos.getOrDefault(agente, List.of());
            for (String pagina : PAGINAS_ABERTAS) {
                if (bloqueado(regras, pagina)) {
                    excessos.add(agente + " bloqueia " + pagina);
                }
            }
        }

        assertTrue(excessos.isEmpty(), () -> "Bloqueio a mais tira o site do índice, "
                + "que é o oposto do objetivo do arquivo.\n  - " + String.join("\n  - ", excessos));
    }

    @Test
    @DisplayName("coletor de treinamento de modelo e robô de SEO ficam fora do site inteiro")
    void coletoresEmMassaBloqueados() {
        Map<String, List<String>> grupos = grupos();
        List<String> abertos = new ArrayList<>();

        for (String agente : List.of("gptbot", "ccbot", "claudebot", "anthropic-ai",
                "google-extended", "perplexitybot", "bytespider", "ahrefsbot", "semrushbot")) {
            List<String> regras = grupos.get(agente);
            if (regras == null) {
                abertos.add(agente + " não está no arquivo");
            } else if (!bloqueado(regras, "/analise")) {
                abertos.add(agente + " não está fechado no site inteiro");
            }
        }

        assertTrue(abertos.isEmpty(), () -> String.join("\n  - ", abertos));
    }

    // --- leitura do arquivo -------------------------------------------------

    /** O critério de leitura mora em {@link RobotsTxt}, numa implementação só. */
    private Map<String, List<String>> grupos() {
        return RobotsTxt.grupos(
                given().when().get("/robots.txt").then().statusCode(200).extract().asString());
    }

    private static boolean bloqueado(List<String> disallows, String caminho) {
        return RobotsTxt.bloqueado(disallows, caminho);
    }

    // --- varredura dos fontes ----------------------------------------------

    /**
     * Reconstrói as rotas de API a partir dos {@code @Path} do código: o primeiro
     * do arquivo é o da classe, os seguintes são dos métodos.
     */
    private static List<String> rotasApiDeclaradasNoCodigo() {
        Assumptions.assumeTrue(Files.isDirectory(RAIZ_FONTES),
                "Diretório de fontes não encontrado a partir de " + Path.of("").toAbsolutePath());

        List<String> rotas = new ArrayList<>();
        try (Stream<Path> caminhos = Files.walk(RAIZ_FONTES)) {
            for (Path arquivo : caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                String conteudo = Files.readString(arquivo);
                Matcher matcher = ANOTACAO_PATH.matcher(conteudo);
                String base = null;
                while (matcher.find()) {
                    String valor = matcher.group(1);
                    if (base == null) {
                        base = valor;
                        rotas.add(normalizar(valor));
                    } else {
                        rotas.add(normalizar(base + valor));
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao varrer os fontes do projeto", ex);
        }

        return rotas.stream()
                // API da área autenticada não entra no arquivo público: é fechada pelo login.
                .filter(rota -> !RobotsTxt.areaAutenticada(rota))
                // F26: além de API, todo export (gera PDF/CSV sob demanda, custo sem valor de índice)
                // nasce fechado — antes só /api/ era descoberto sozinho e /<modulo>/export novo escapava.
                .filter(rota -> rota.contains("/api/") || rota.endsWith("/api")
                        || rota.contains("/export/") || rota.endsWith("/export"))
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizar(String caminho) {
        String valor = caminho.startsWith("/") ? caminho : "/" + caminho;
        return valor.replace("//", "/");
    }
}
