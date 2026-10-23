package org.framework.net.protocolos;

import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda do registro de aprofundamentos por protocolo.
 *
 * <p><b>Propósito de negócio:</b> o sub-menu de Protocolos é gerado a partir de
 * {@link AprofundamentoProtocolo#disponiveis()}. Se o registro apontar para um
 * template ou um CSS que não existe, o usuário clica no menu e recebe erro; se
 * uma página existir sem entrada no registro, ela fica órfã — no ar, mas
 * inalcançável pelo menu. Este teste transforma as duas situações em build
 * vermelho.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) todo item registrado tem template e CSS
 * em disco; (2) toda página de aprofundamento em disco está registrada; (3) os
 * slugs são únicos e minúsculos; (4) os nomes declarados em
 * {@code nomesNoCatalogo} existem no catálogo — é por eles que a linha do
 * DataGrid ganha o botão "Aprofundar", e um nome desatualizado sumiria com o
 * botão sem nenhum erro visível.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção nomeia o item e o arquivo
 * esperado. Fora da raiz do projeto os testes de arquivo são ignorados via
 * {@code Assumptions}, para não falharem por caminho errado.</p>
 */
@DisplayName("Protocolos: registro de aprofundamentos por protocolo")
class AprofundamentoProtocoloTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");
    private static final Path ESTATICOS = Path.of("src", "main", "resources", "META-INF", "resources");
    private static final Path CATALOGO = Path.of("src", "main", "resources", "protocolos", "catalogo.json");

    @Test
    @DisplayName("há aprofundamentos registrados e cada um traz os campos exigidos")
    void registroEstaPreenchido() {
        List<AprofundamentoProtocolo> itens = AprofundamentoProtocolo.disponiveis();
        assertFalse(itens.isEmpty(), "O sub-menu ficaria só com a aba Geral.");

        for (AprofundamentoProtocolo item : itens) {
            assertFalse(item.slug().isBlank(), "Slug em branco em " + item.titulo());
            assertEquals(item.slug().toLowerCase(Locale.ROOT), item.slug(),
                    "Slug precisa ser minúsculo: " + item.slug());
            assertFalse(item.titulo().isBlank(), "Título em branco em " + item.slug());
            assertFalse(item.rotuloMenu().isBlank(), "Rótulo de menu em branco em " + item.slug());
            assertFalse(item.icone().isBlank(), "Ícone em branco em " + item.slug());
            assertFalse(item.chamada().isBlank(), "Chamada em branco em " + item.slug());
            assertEquals("/protocolos/" + item.slug(), item.rota());
            assertTrue(item.cssVersionado().startsWith(item.css() + "?v="),
                    "O CSS precisa sair versionado para furar cache: " + item.slug());
        }
    }

    @Test
    @DisplayName("slugs são únicos")
    void slugsSaoUnicos() {
        Set<String> vistos = new HashSet<>();
        for (AprofundamentoProtocolo item : AprofundamentoProtocolo.disponiveis()) {
            assertTrue(vistos.add(item.slug()), "Slug repetido no registro: " + item.slug());
        }
    }

    @Test
    @DisplayName("todo item registrado tem template e CSS em disco")
    void registroApontaParaArquivosQueExistem() {
        Assumptions.assumeTrue(Files.isDirectory(TEMPLATES), "Rodando fora da raiz do projeto");

        List<String> faltando = new ArrayList<>();
        for (AprofundamentoProtocolo item : AprofundamentoProtocolo.disponiveis()) {
            Path template = TEMPLATES.resolve(item.template());
            if (!Files.isRegularFile(template)) {
                faltando.add(item.slug() + " → template ausente: " + template);
            }
            Path css = ESTATICOS.resolve(item.css().substring(1));
            if (!Files.isRegularFile(css)) {
                faltando.add(item.slug() + " → CSS ausente: " + css);
            }
        }

        assertTrue(faltando.isEmpty(),
                () -> "Item de menu apontando para arquivo inexistente:\n  - " + String.join("\n  - ", faltando));
    }

    @Test
    @DisplayName("regressão: página de aprofundamento em disco sem entrada no registro fica órfã")
    void naoExistePaginaForaDoRegistro() {
        Path pasta = TEMPLATES.resolve("protocolos");
        Assumptions.assumeTrue(Files.isDirectory(pasta), "Rodando fora da raiz do projeto");

        Set<String> registrados = new HashSet<>();
        for (AprofundamentoProtocolo item : AprofundamentoProtocolo.disponiveis()) {
            registrados.add(item.template().replace('\\', '/'));
        }

        List<String> orfas = new ArrayList<>();
        try (Stream<Path> subpastas = Files.list(pasta)) {
            subpastas.filter(Files::isDirectory)
                    .filter(dir -> !"partials".equals(dir.getFileName().toString()))
                    .filter(dir -> Files.isRegularFile(dir.resolve("index.html")))
                    .forEach(dir -> {
                        String esperado = "protocolos/" + dir.getFileName() + "/index.html";
                        if (!registrados.contains(esperado)) {
                            orfas.add(esperado);
                        }
                    });
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao varrer templates/protocolos", ex);
        }

        assertTrue(orfas.isEmpty(),
                () -> "Página no ar e fora do sub-menu — registre em AprofundamentoProtocolo:\n  - "
                        + String.join("\n  - ", orfas));
    }

    @Test
    @DisplayName("os nomes declarados existem no catálogo (senão o botão \"Aprofundar\" some calado)")
    void nomesDoCatalogoExistem() {
        Assumptions.assumeTrue(Files.isRegularFile(CATALOGO), "Rodando fora da raiz do projeto");

        String catalogo = ler(CATALOGO);
        List<String> ausentes = new ArrayList<>();
        for (AprofundamentoProtocolo item : AprofundamentoProtocolo.disponiveis()) {
            // nomesNoCatalogo pode ser VAZIO para páginas conceituais (ex.: "handshake"),
            // alcançáveis pelo sub-menu de camada e não por uma linha do DataGrid.
            for (String nome : item.nomesNoCatalogo()) {
                if (!catalogo.contains("\"nome\": \"" + nome + "\"")) {
                    ausentes.add(item.slug() + " → \"" + nome + "\" não existe em protocolos/catalogo.json");
                }
            }
        }

        assertTrue(ausentes.isEmpty(),
                () -> "Nome divergente entre registro e catálogo:\n  - " + String.join("\n  - ", ausentes));
    }

    @Test
    @DisplayName("resolução por slug: conhecido resolve, desconhecido devolve vazio")
    void resolucaoPorSlug() {
        assertTrue(AprofundamentoProtocolo.porSlug("bgp").isPresent());
        assertTrue(AprofundamentoProtocolo.porSlug("BGP").isPresent(), "A URL pode chegar em maiúsculas.");
        assertTrue(AprofundamentoProtocolo.porSlug(" ssh ").isPresent(), "Espaço em volta não pode virar 404.");
        assertTrue(AprofundamentoProtocolo.porSlug("http").isPresent(), "Genérico registrado precisa resolver.");
        assertTrue(AprofundamentoProtocolo.porSlug("snmp").isEmpty(), "Slug sem página não pode resolver.");
        assertTrue(AprofundamentoProtocolo.porSlug(null).isEmpty());
        assertTrue(AprofundamentoProtocolo.porSlug("   ").isEmpty());
    }

    @Test
    @DisplayName("resolução por nome do catálogo: só quem tem página é encontrado")
    void resolucaoPorNomeDoCatalogo() {
        assertEquals("bgp", AprofundamentoProtocolo.porNomeDoCatalogo("BGP-4 / eBGP").orElseThrow().slug());
        assertEquals("bgp", AprofundamentoProtocolo.porNomeDoCatalogo("iBGP").orElseThrow().slug());
        assertEquals("ssh", AprofundamentoProtocolo.porNomeDoCatalogo("SSH").orElseThrow().slug());
        assertEquals("http", AprofundamentoProtocolo.porNomeDoCatalogo("HTTP").orElseThrow().slug());
        assertEquals("telnet", AprofundamentoProtocolo.porNomeDoCatalogo("TELNET").orElseThrow().slug());
        assertTrue(AprofundamentoProtocolo.porNomeDoCatalogo("SNMP").isEmpty(),
                "Protocolo sem página não pode oferecer o botão Aprofundar.");
        assertTrue(AprofundamentoProtocolo.porNomeDoCatalogo(null).isEmpty());
        assertTrue(AprofundamentoProtocolo.porNomeDoCatalogo("").isEmpty());
    }

    private static String ler(Path caminho) {
        try {
            return Files.readString(caminho);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + caminho, ex);
        }
    }
}
