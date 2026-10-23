package org.framework.net.analiseDidatica.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AnaliseDidaticaConfigTest {

    @Inject
    AnaliseDidaticaConfig config;

    @Test
    void maxHistoryUsaPadrao() {
        assertEquals(60, config.maxHistory());
    }

    @Test
    void comparadorCidrUsaPadroes() {
        assertEquals("20", config.comparadorCidrPadraoA());
        assertEquals("24", config.comparadorCidrPadraoB());
    }
}
