package org.framework.net.web.support;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class FrameworkDevConfigTest {

    @Inject
    FrameworkDevConfig devConfig;

    @Test
    void openBrowserDesabilitadoPorPadraoEmTestes() {
        assertFalse(devConfig.openBrowser());
    }

    @Test
    void browserPathPadraoERaiz() {
        assertEquals("/", devConfig.browserPath());
    }
}
