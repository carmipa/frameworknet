package org.framework.net.protocolos.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Critério do teto de corpo da API dos aprofundamentos (proposta C03), sem subir a aplicação.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o teto só protege se reconhecer o caminho da API em todas as grafias que chegam ao
 * mesmo recurso, e se não confundir página ou prefixo parecido com a API.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> parâmetro de matriz e caixa não escondem o prefixo {@code /protocolos/api/};
 * {@code /protocolos/apix} e as páginas não são a API (fronteira, A1); 1024 bytes passa, 1025 não, e tamanho ilegível
 * conta como acima do teto (falha fechada).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia o caminho ou o tamanho mal classificado.</p>
 */
@DisplayName("Protocolos: o teto de corpo reconhece a API e só ela")
class LimiteCorpoProtocolosApiTest {

    @ParameterizedTest(name = "{0} é a API")
    @ValueSource(strings = {"/protocolos/api/autoavaliacao", "/Protocolos/API;v=1/autoavaliacao",
            "/protocolos;x=1/api/autoavaliacao", "/protocolos/api"})
    void reconheceAApi(String caminho) {
        assertTrue(LimiteCorpoProtocolosApi.ehApiDeProtocolos(caminho), caminho);
    }

    @ParameterizedTest(name = "{0} não é a API")
    @ValueSource(strings = {"/protocolos/apix/autoavaliacao", "/protocolos/tcp", "/protocolos", "/academia/api/eventos",
            "/protocolos/tcp/api"})
    void naoConfundeComAApi(String caminho) {
        assertFalse(LimiteCorpoProtocolosApi.ehApiDeProtocolos(caminho), caminho);
    }

    @Test
    @DisplayName("caminho nulo não é a API")
    void caminhoNulo() {
        assertFalse(LimiteCorpoProtocolosApi.ehApiDeProtocolos(null));
    }

    @ParameterizedTest(name = "Content-Length \"{0}\" passa do teto ou é ilegível")
    @ValueSource(strings = {"1025", "99999999999999999999", "abc", "", "1e3"})
    void acimaOuIlegivel(String declarado) {
        assertTrue(LimiteCorpoProtocolosApi.excede(declarado), declarado);
    }

    @ParameterizedTest(name = "Content-Length \"{0}\" cabe no teto")
    @ValueSource(strings = {"1024", "0", " 512 "})
    void cabeNoTeto(String declarado) {
        assertFalse(LimiteCorpoProtocolosApi.excede(declarado), declarado);
    }
}
