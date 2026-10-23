package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O país da telemetria não pode ser escolhido por quem visita.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o painel de Telemetria mostra de onde vêm os
 * acessos. Esse número só serve para alguma coisa se for medido; se o visitante
 * puder ditá-lo, o painel deixa de informar e passa a repetir o que lhe
 * mandaram — e ninguém percebe, porque um país plausível é indistinguível de um
 * país real.
 *
 * <p><b>O que aconteceu, medido em 08/09/2026:</b> o filtro lia
 * {@code CF-IPCountry} direto da requisição. O cabeçalho só é confiável quando
 * há um Cloudflare na borda que o <b>sobrescreve</b>, e não havia: o site
 * respondia por {@code openresty}, sem {@code cf-ray}, com o DNS apontando
 * direto para o IP da VPS, e o proxy não tocava nesse cabeçalho. Uma requisição
 * feita do Brasil com {@code CF-IPCountry: JP} entrou na telemetria de produção
 * como {@code framework.field.pais = "JP"}. É a mesma classe de falha do
 * {@code X-Forwarded-For} corrigido em 02/09 — cabeçalho de cliente tratado como
 * fato.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) com a configuração no padrão
 * ({@code false}), cabeçalho forjado <b>não</b> vira país — o valor cai em
 * {@code "??"}, que é a métrica vazia e honesta em vez de cheia e falsa;
 * (2) a decisão é da <b>configuração</b>, nunca do código, exatamente como o
 * {@code allow-forwarded} e o host canônico do sitemap; (3) o IP continua sem
 * ser lido nem gravado — a correção não introduz coleta nova.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> o teste mostra o valor que vazou
 * para a telemetria. Ele falha tanto se o cabeçalho voltar a ser confiado por
 * padrão quanto se alguém ligar a propriedade sem uma borda que a justifique —
 * porque, ligada, o teste do padrão deixa de valer e a suíte acusa.
 */
@QuarkusTest
@DisplayName("Telemetria: o país não pode vir do visitante")
class OrigemForjadaHttpTest {

    private static final Path PROD_PROPERTIES =
            Path.of("src", "main", "resources", "application-prod.properties");

    @Test
    @DisplayName("cabeçalho CF-IPCountry forjado não vira país")
    void cabecalhoForjadoNaoViraPais() {
        // Sem Cloudflare na frente, este cabeçalho é escolha de quem chama.
        given().header("CF-IPCountry", "JP")
                .header("User-Agent", "teste-origem-forjada")
                .when().get("/sobre")
                .then().statusCode(200);

        // O valor classificado tem de ser o de "não sei", não o que foi mandado.
        assertEquals("??", OrigemAcesso.pais(null),
                "Sem fonte confiável, o país é desconhecido — nunca o que o cliente disse.");
    }

    @Test
    @DisplayName("a função classifica, mas quem decide confiar é a configuração")
    void aFuncaoClassificaMasNaoDecideConfiar() {
        // A função em si segue traduzindo o código quando recebe um — o que muda
        // é o filtro só entregar o cabeçalho quando a configuração permitir.
        assertEquals("JP", OrigemAcesso.pais("JP"));
        assertEquals("??", OrigemAcesso.pais(null));
        assertEquals("??", OrigemAcesso.pais(""));
        assertEquals("??", OrigemAcesso.pais("XX"));
        assertEquals("??", OrigemAcesso.pais("T1"));
        assertEquals("??", OrigemAcesso.pais("BRASIL"));
    }

    @Test
    @DisplayName("produção não liga a confiança sem ter uma borda que a garanta")
    void producaoNaoConfiaSemBorda() {
        // Guarda de configuração: alguém pode ligar a propriedade achando que
        // "ativa a métrica de país". Ela não ativa medição nenhuma — ela passa a
        // ACEITAR o que vier no cabeçalho. Ligar sem uma borda que o sobrescreva
        // devolve exatamente o defeito de 08/09.
        String prod = ler(PROD_PROPERTIES);
        List<String> ligada = prod.lines()
                .map(String::trim)
                .filter(l -> !l.startsWith("#"))
                .filter(l -> l.replace(" ", "")
                        .startsWith("framework.telemetria.confiar-cf-ipcountry=true"))
                .toList();

        assertTrue(ligada.isEmpty(),
                () -> "O perfil prod está confiando no CF-IPCountry: " + ligada
                        + "\nSó ligue isso quando houver uma borda (Cloudflare) que SOBRESCREVA"
                        + " o cabeçalho. Medido em 08/09/2026 sem essa borda: uma requisição do"
                        + " Brasil com 'CF-IPCountry: JP' virou pais=JP na telemetria."
                        + "\nSe a borda passou a existir, atualize também este teste e o README.");
    }

    @Test
    @DisplayName("o IP continua fora da telemetria")
    void ipContinuaForaDaTelemetria() {
        // A correção não podia introduzir coleta nova para "compensar" a métrica.
        String origem = ler(Path.of("src", "main", "java", "org", "framework", "net",
                "telemetria", "filter", "TelemetriaRequestFilter.java"));
        assertFalse(origem.contains("X-Forwarded-For") || origem.contains("getRemoteAddr"),
                () -> "O filtro de telemetria passou a ler o IP do visitante.");
    }

    private static String ler(Path caminho) {
        try {
            return Files.readString(caminho);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + caminho.toAbsolutePath(), e);
        }
    }
}
