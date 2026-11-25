package org.framework.net.paginaErros.presentation;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cabeçalho {@code Content-Type} de formulário que o leitor do Quarkus REST não consegue usar é erro de quem enviou
 * (Lote 1, revisão adversarial, 2ª passada): {@code charset} desconhecido ou de nome ilegal derrubava o leitor com
 * exceção não tratada — 500 e pilha inteira no log a cada pedido. Medido: o leitor só usa o charset ao decodificar um
 * escape {@code %}; corpo sem escape é legível e calcula (200). E corpo multipart não entra nas rotas de formulário
 * (415): tem outro leitor, com outros tetos, e nenhuma tela o envia.
 *
 * <p>A1 (fronteira): o mesmo sinal — o parâmetro {@code charset} — com valor legítimo continua calculando, com e sem
 * escape. O cliente é o {@link HttpClient} do JDK, que manda o cabeçalho exatamente como escrito (o RestAssured o
 * reescreve).</p>
 */
@QuarkusTest
class CharsetDoFormularioHttpTest {

    private static final String CORPO_ANALISE = "modo=cidr&ip=10.0.0.1&cidr=24";
    /** Mesmo cálculo, com um escape: {@code %32%34} é "24" — o leitor precisa do charset para decodificar. */
    private static final String CORPO_ANALISE_COM_ESCAPE = "modo=cidr&ip=10.0.0.1&cidr=%32%34";
    private static final String CORPO_PROJETAR = "action_type=calculate&base_network=172.42.0.0%2F16"
            + "&topology_type=mesh&loc_name=Matriz&loc_hosts=40&loc_name=Filial&loc_hosts=30";

    @TestHTTPResource("/")
    URI raiz;

    private HttpResponse<String> postar(String rota, String contentType, String corpo) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(raiz.resolve(rota))
                .header("Content-Type", contentType)
                .header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            return cliente.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest(name = "{0} com Content-Type [{1}] recebe {2}")
    @CsvSource(delimiter = '|', value = {
            "analise-escape|application/x-www-form-urlencoded; charset=foo|400",
            "analise-escape|application/x-www-form-urlencoded; charset=@@|400",
            "resolucao-problemas|application/x-www-form-urlencoded; charset=foo|400",
            "resolucao-problemas|application/x-www-form-urlencoded; charset=@@|400",
            "analise|multipart/form-data; boundary=fronteira|415",
            "analise|text/plain|415",
    })
    void contentTypeInutilizavelEErroDeQuemEnviou(String rota, String contentType, int esperado) throws Exception {
        HttpResponse<String> resposta = postar(rotaDe(rota), contentType, corpoDe(rota));
        assertEquals(esperado, resposta.statusCode(), "corpo: " + resposta.body().substring(0, Math.min(300, resposta.body().length())));
    }

    private static String rotaDe(String caso) {
        return caso.startsWith("analise") ? "analise" : caso;
    }

    private static String corpoDe(String caso) {
        return switch (caso) {
            case "analise" -> CORPO_ANALISE;
            case "analise-escape" -> CORPO_ANALISE_COM_ESCAPE;
            default -> CORPO_PROJETAR;
        };
    }

    /**
     * Cabeçalho que nem chega a ser um tipo de mídia legível: erro de sintaxe de quem enviou — 400 ou 415
     * (RFC 9110, §15.5.1 e §15.5.16), nunca 500.
     */
    @ParameterizedTest(name = "Content-Type ilegível [{0}] recebe 400 ou 415")
    @ValueSource(strings = {"a/b/c", "texto-sem-barra"})
    void contentTypeIlegivelRecebe400Ou415(String contentType) throws Exception {
        HttpResponse<String> resposta = postar("analise", contentType, CORPO_ANALISE_COM_ESCAPE);
        assertTrue(resposta.statusCode() == 400 || resposta.statusCode() == 415, "veio " + resposta.statusCode());
    }

    /**
     * Tipo legível com parâmetro lixo: medido em 25/11/2026, o leitor ignora o parâmetro e o formulário é lido (200).
     * Leniência sem dano — o requisito aqui é só não virar 500.
     */
    @ParameterizedTest(name = "Content-Type com parâmetro lixo [{0}] não vira 500")
    @ValueSource(strings = {"application/x-www-form-urlencoded; charset", "application/x-www-form-urlencoded;;=;"})
    void parametroLixoNoContentTypeNaoVira500(String contentType) throws Exception {
        HttpResponse<String> resposta = postar("analise", contentType, CORPO_ANALISE_COM_ESCAPE);
        assertTrue(resposta.statusCode() < 500, "veio " + resposta.statusCode());
    }

    /** O caso {@code charset=foo} é a fronteira medida: charset desconhecido, mas corpo sem escape — legível, calcula. */
    @ParameterizedTest(name = "{0} com charset legítimo [{1}] calcula")
    @CsvSource(delimiter = '|', value = {
            "analise|application/x-www-form-urlencoded; charset=UTF-8|10.0.0.255",
            "analise-escape|application/x-www-form-urlencoded; charset=UTF-8|10.0.0.255",
            "analise|application/x-www-form-urlencoded; charset=foo|10.0.0.255",
            "analise|application/x-www-form-urlencoded; charset=ISO-8859-1|10.0.0.255",
            "analise|application/x-www-form-urlencoded|10.0.0.255",
            "resolucao-problemas|application/x-www-form-urlencoded; charset=UTF-8|Matriz",
    })
    void charsetLegitimoContinuaCalculando(String rota, String contentType, String noResultado) throws Exception {
        HttpResponse<String> resposta = postar(rotaDe(rota), contentType, corpoDe(rota));
        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.body().contains(noResultado), "o cálculo não aparece na página");
    }
}
