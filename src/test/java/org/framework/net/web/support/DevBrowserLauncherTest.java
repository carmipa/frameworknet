package org.framework.net.web.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevBrowserLauncherTest {

    private final String previousPortProperty = System.getProperty("quarkus.http.port");
    private final String previousHostProperty = System.getProperty("quarkus.http.host");

    @AfterEach
    void limparOverridesJvm() {
        restaurarPropriedade("quarkus.http.port", previousPortProperty);
        restaurarPropriedade("quarkus.http.host", previousHostProperty);
    }

    private static void restaurarPropriedade(String chave, String valor) {
        if (valor == null) {
            System.clearProperty(chave);
        } else {
            System.setProperty(chave, valor);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0, 127.0.0.1",
            "'::', 127.0.0.1",
            "localhost, localhost",
            "127.0.0.1, 127.0.0.1"
    })
    void browserHostResolveEnderecosDeEscuta(String entrada, String esperado) {
        assertEquals(esperado, DevBrowserLauncher.browserHost(entrada));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void browserHostVazioOuNuloUsaLocalhost(String entrada) {
        assertEquals("127.0.0.1", DevBrowserLauncher.browserHost(entrada));
    }

    @ParameterizedTest
    @CsvSource({
            "'', /",
            "'   ', /",
            "/, /",
            "/telemetria, /telemetria",
            "telemetria, /telemetria",
            "analise/, /analise/"
    })
    void normalizeBrowserPathGaranteBarraInicial(String entrada, String esperado) {
        assertEquals(esperado, DevBrowserLauncher.normalizeBrowserPath(entrada));
    }

    @Test
    void normalizeBrowserPathNullUsaRaiz() {
        assertEquals("/", DevBrowserLauncher.normalizeBrowserPath(null));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0, 8080, /, http://127.0.0.1:8080/",
            "localhost, 8081, telemetria, http://localhost:8081/telemetria",
            "'::', 9090, /documentacao, http://127.0.0.1:9090/documentacao"
    })
    void buildBrowserUrlMontaUrlCompleta(String host, int porta, String path, String esperado) {
        assertEquals(esperado, DevBrowserLauncher.buildBrowserUrl(host, porta, path));
    }
}
