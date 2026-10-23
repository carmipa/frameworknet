package org.framework.net.web.support;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(FrameworkDevConfigProfileTest.DevHabilitadoProfile.class)
class FrameworkDevConfigProfileTest {

    @Inject
    FrameworkDevConfig devConfig;

    @Test
    void perfilCustomizadoHabilitaAberturaDoNavegador() {
        assertTrue(devConfig.openBrowser());
        assertEquals("/telemetria/", devConfig.browserPath());
    }

    public static class DevHabilitadoProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.dev.open-browser", "true",
                    "framework.dev.browser-path", "/telemetria/"
            );
        }
    }
}
