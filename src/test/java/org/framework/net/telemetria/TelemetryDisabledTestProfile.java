package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TelemetryDisabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "framework.telemetry.dashboard-enabled", "false"
        );
    }
}
