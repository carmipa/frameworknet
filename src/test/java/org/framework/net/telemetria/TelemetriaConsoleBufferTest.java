package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaConsoleBufferTest {

    @Inject
    TelemetriaConsoleBuffer buffer;

    @Test
    void deveAcumularELimparLinhas() {
        buffer.limpar();
        buffer.append("info", "evento=teste status=ok");
        assertFalse(buffer.snapshot(10).isEmpty());
        buffer.limpar();
        assertTrue(buffer.snapshot(10).isEmpty());
    }
}
