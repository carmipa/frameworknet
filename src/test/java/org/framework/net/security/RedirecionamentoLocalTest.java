package org.framework.net.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FRONT-03: só caminho local passa; o resto vira o padrão, sem lançar. */
class RedirecionamentoLocalTest {

    @ParameterizedTest(name = "passa: {0}")
    @ValueSource(strings = {"/export/pdf", "/ipv6/export/json?endereco=2001:db8::1", "/a?b=1&c=%20d", "/%5Cnao-e-barra"})
    void caminhoLocalPassa(String destino) {
        assertEquals(destino, RedirecionamentoLocal.destino(destino, "/padrao"));
    }

    @ParameterizedTest(name = "recusa: [{0}]")
    @ValueSource(strings = {"", "  ", "//example.org", "/\\example.org", "\\\\example.org", "https://example.org",
            "example.org", "/\texample.org", "/a b", "/\u007f"})
    void destinoExternoOuInvalidoViraOPadrao(String destino) {
        assertEquals("/padrao", RedirecionamentoLocal.destino(destino, "/padrao"));
    }
}
