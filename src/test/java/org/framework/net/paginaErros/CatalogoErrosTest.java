package org.framework.net.paginaErros;

import jakarta.ws.rs.core.Response;
import org.framework.net.paginaErros.domain.AreaDoErro;
import org.framework.net.paginaErros.domain.CatalogoErros;
import org.framework.net.paginaErros.domain.CatalogoErros.ErroApresentado;
import org.framework.net.security.AdminApiKeyService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catálogo de erros sem subir o Quarkus (INV-ERRO-001).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a página de erro precisa ser coerente com o status que o
 * navegador recebeu e com quem está do outro lado. Um 413 com a linha "400 Bad Request" manda a
 * pessoa procurar defeito de formato quando o problema é tamanho; um aluno com 401 mandado
 * "autenticar-se em Administração" procura uma tela que não é para ele.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> no fallback, a linha de status traz o código real; fora da
 * administração, 401 e 403 não citam Telemetria nem chave administrativa; a área de
 * administração acompanha as rotas que o {@code AdminApiKeyFilter} protege; todo status que o
 * código da aplicação emite tem entrada própria no catálogo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia o código, o caminho ou o arquivo
 * que emite o status sem texto. Sem a pasta de fontes (execução fora da raiz), a guarda de
 * códigos emitidos é ignorada por {@code Assumptions}, nunca aprovada.</p>
 */
@DisplayName("Catálogo de erros: coerência com o status real e com a área")
class CatalogoErrosTest {

    private static final Path FONTES = Path.of("src", "main", "java");

    @Test
    @DisplayName("fallback mostra o código real na linha de status, nunca o do representante")
    void fallbackMostraOCodigoReal() {
        for (int codigo : List.of(418, 451, 599)) {
            ErroApresentado erro = CatalogoErros.porCodigo(codigo);
            assertTrue(erro.statusTexto().startsWith(codigo + " "),
                    codigo + " exibido como \"" + erro.statusTexto() + "\"");
            assertFalse(erro.statusTexto().startsWith("400 ") || erro.statusTexto().startsWith("500 "),
                    codigo + " exibido como \"" + erro.statusTexto() + "\"");
        }
    }

    @Test
    @DisplayName("fallback mantém a cor da família e nunca devolve vazio")
    void fallbackMantemFamilia() {
        assertEquals("err-400", CatalogoErros.porCodigo(418).classeCss());
        assertTrue(CatalogoErros.porCodigo(418).cliente());
        assertEquals("err-500", CatalogoErros.porCodigo(599).classeCss());
        assertFalse(CatalogoErros.porCodigo(599).cliente());
        assertEquals("500 Internal Server Error", CatalogoErros.porCodigo(0).statusTexto(),
                "código fora de 400–599 vira o 500 inteiro");
    }

    @Test
    @DisplayName("os códigos novos têm entrada própria e linha de status do próprio código")
    void codigosNovosNoCatalogo() {
        for (int codigo : List.of(410, 413, 414, 415, 431, 507)) {
            assertTrue(CatalogoErros.codigos().contains(codigo), codigo + " fora do catálogo");
            ErroApresentado erro = CatalogoErros.porCodigo(codigo);
            assertEquals(codigo, erro.codigo());
            assertTrue(erro.statusTexto().startsWith(codigo + " "), erro.statusTexto());
        }
    }

    @Test
    @DisplayName("todo código do catálogo tem cor própria no CSS da página")
    void todoCodigoTemCor() throws IOException {
        Path css = Path.of("src", "main", "resources", "META-INF", "resources", "paginaErros", "css", "erro.css");
        Assumptions.assumeTrue(Files.isRegularFile(css), "erro.css não encontrado");
        String conteudo = Files.readString(css);
        for (int codigo : CatalogoErros.codigos()) {
            assertTrue(conteudo.contains(".err-" + codigo + " { --accent:"),
                    "err-" + codigo + " sem --accent: a página sairia sem cor de destaque");
        }
    }

    @Test
    @DisplayName("401 e 403 fora da administração não mandam ninguém para a Telemetria")
    void textosDoSiteNaoCitamAdministracao() {
        for (String caminho : List.of("/academia/fundamentos/binario", "/conta/api/eu", "/protocolos", "/", "")) {
            for (int codigo : List.of(401, 403)) {
                ErroApresentado erro = CatalogoErros.porCodigo(codigo, caminho);
                String texto = (erro.titulo() + " " + erro.descricao() + " " + erro.hint()).toLowerCase();
                assertFalse(texto.contains("telemetria") || texto.contains("administra"),
                        codigo + " em \"" + caminho + "\" cita administração: " + texto);
                assertEquals(codigo + (codigo == 401 ? " Unauthorized" : " Forbidden"), erro.statusTexto());
            }
        }
    }

    @Test
    @DisplayName("401 e 403 na administração continuam com o texto de administração")
    void textosDaAdministracaoPreservados() {
        for (String caminho : List.of("/telemetria", "/telemetria/api/exportar", "/export/json", "/ipv6/export",
                "/login", "/admin/login")) {
            assertEquals(CatalogoErros.porCodigo(401), CatalogoErros.porCodigo(401, caminho), caminho);
            assertEquals(CatalogoErros.porCodigo(403), CatalogoErros.porCodigo(403, caminho), caminho);
        }
    }

    /**
     * A1: os pares carregam o mesmo sinal (prefixo de texto) e só o limite de segmento os separa;
     * o gabarito vem do {@code AdminApiKeyService}, que é quem protege de fato (A3).
     */
    @Test
    @DisplayName("a área de administração acompanha as rotas protegidas pelo filtro de administração")
    void areaAcompanhaRotasProtegidas() {
        AdminApiKeyService protegidas = new AdminApiKeyService();
        for (String caminho : List.of("/telemetria", "/telemetria/api/a", "/export", "/export/pdf",
                "/ipv6/export", "/ipv6/export/csv", "/exportar", "/telemetriax", "/ipv6", "/academia",
                "/conta/api/eu", "/protocolos/export")) {
            boolean admin = AreaDoErro.doCaminho(caminho) == AreaDoErro.ADMINISTRACAO;
            boolean loginDaTelemetria = caminho.startsWith("/login") || caminho.startsWith("/admin");
            assertEquals(protegidas.isProtectedPath(caminho) || loginDaTelemetria, admin,
                    "área divergente do AdminApiKeyFilter em " + caminho);
        }
    }

    // ---------------------------------------------------------------- códigos emitidos × catálogo

    /**
     * Códigos que o próprio Quarkus/Vert.x devolve sem linha nossa: rota inexistente (404),
     * verbo errado (405), corpo acima do limite (413) e tipo de conteúdo não aceito (415).
     */
    private static final Set<Integer> EMITIDOS_PELO_FRAMEWORK = Set.of(404, 405, 413, 415);

    private static final Pattern STATUS_ENUM = Pattern.compile("Response\\.Status\\.([A-Z_]+)");
    private static final Pattern STATUS_LITERAL = Pattern.compile("\\.status\\(\\s*(\\d{3})\\b");
    /** Resposta escrita direto no roteador do Vert.x, antes do JAX-RS (ex.: teto de corpo). */
    private static final Pattern STATUS_VERTX = Pattern.compile("\\.setStatusCode\\(\\s*(\\d{3})\\b");
    private static final Pattern EXCECAO_JAXRS = Pattern.compile("new\\s+(?:jakarta\\.ws\\.rs\\.)?(\\w+Exception)\\(");
    private static final Map<String, Integer> EXCECOES_JAXRS = Map.of(
            "BadRequestException", 400,
            "NotAuthorizedException", 401,
            "ForbiddenException", 403,
            "NotFoundException", 404,
            "NotAllowedException", 405,
            "NotAcceptableException", 406,
            "NotSupportedException", 415,
            "InternalServerErrorException", 500,
            "ServiceUnavailableException", 503);

    @Test
    @DisplayName("todo status de erro que a aplicação emite tem texto próprio no catálogo")
    void codigosEmitidosTemTexto() {
        Assumptions.assumeTrue(Files.isDirectory(FONTES), "fontes não encontrados");
        Map<Integer, String> emitidos = new java.util.TreeMap<>();
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            arquivos.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    for (int codigo : codigosEmitidos(Files.readString(p))) {
                        emitidos.putIfAbsent(codigo, p.toString());
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        assertTrue(emitidos.keySet().containsAll(Set.of(400, 401, 403, 422, 429, 500, 503)),
                "instrumento cego: não achou os status sabidamente emitidos — achou " + emitidos.keySet());

        Set<Integer> todos = new TreeSet<>(emitidos.keySet());
        todos.addAll(EMITIDOS_PELO_FRAMEWORK);
        List<String> semTexto = todos.stream()
                .filter(c -> !CatalogoErros.codigos().contains(c))
                .map(c -> c + " (" + emitidos.getOrDefault(c, "framework") + ")")
                .toList();
        assertTrue(semTexto.isEmpty(), "status emitido sem entrada no catálogo: " + semTexto);
    }

    @Test
    @DisplayName("calibração: o leitor de status acha enum, literal e exceção, e ignora sucesso e texto")
    void calibracaoDoLeitor() {
        assertEquals(Set.of(418), codigosEmitidos("return Response.status(418).build();"));
        assertEquals(Set.of(410), codigosEmitidos("Response.status(Response.Status.GONE)"));
        assertEquals(Set.of(406), codigosEmitidos("throw new NotAcceptableException(\"x\");"));
        assertEquals(Set.of(413), codigosEmitidos("contexto.response().setStatusCode(413).end();"));
        assertEquals(Set.of(), codigosEmitidos("Response.status(Response.Status.OK); Response.status(204);"),
                "A1: status de sucesso não é erro");
        assertEquals(Set.of(), codigosEmitidos("// throw new IllegalStateException(\"falha\");"),
                "A1: exceção que não é JAX-RS não tem status");
    }

    private static Set<Integer> codigosEmitidos(String fonte) {
        Set<Integer> codigos = new TreeSet<>();
        Matcher m = STATUS_ENUM.matcher(fonte);
        while (m.find()) {
            try {
                codigos.add(Response.Status.valueOf(m.group(1)).getStatusCode());
            } catch (IllegalArgumentException naoEhStatus) {
                // Response.Status.Family e afins: não é um código.
            }
        }
        m = STATUS_LITERAL.matcher(fonte);
        while (m.find()) {
            codigos.add(Integer.parseInt(m.group(1)));
        }
        m = STATUS_VERTX.matcher(fonte);
        while (m.find()) {
            codigos.add(Integer.parseInt(m.group(1)));
        }
        m = EXCECAO_JAXRS.matcher(fonte);
        while (m.find()) {
            Integer codigo = EXCECOES_JAXRS.get(m.group(1));
            if (codigo != null) {
                codigos.add(codigo);
            }
        }
        codigos.removeIf(c -> c < 400);
        return codigos;
    }
}
