package org.framework.net.web.support;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class DevBrowserLauncherStartupTest {

    private String previousPortProperty;

    @Inject
    DevBrowserLauncher devBrowserLauncher;

    @Inject
    FrameworkDevConfig devConfig;

    @AfterEach
    void limparOverridePorta() {
        if (previousPortProperty == null) {
            System.clearProperty("quarkus.http.port");
        } else {
            System.setProperty("quarkus.http.port", previousPortProperty);
        }
    }

    @Test
    void beansDeDevBrowserEstaoDisponiveisNoContexto() {
        assertNotNull(devBrowserLauncher);
        assertNotNull(devConfig);
    }

    @Test
    void emTestesLaunchModeNaoAbreNavegadorAutomaticamente() {
        assertNotEquals(LaunchMode.DEVELOPMENT, LaunchMode.current());
        assertFalse(devConfig.openBrowser());
    }

    @Test
    void resolveHttpPortPriorizaSystemProperty() {
        previousPortProperty = System.getProperty("quarkus.http.port");
        System.setProperty("quarkus.http.port", "8099");
        assertEquals(8099, DevBrowserLauncher.resolveHttpPort(ConfigProvider.getConfig()));
    }
}
