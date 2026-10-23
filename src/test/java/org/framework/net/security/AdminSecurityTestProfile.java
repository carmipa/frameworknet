package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class AdminSecurityTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "framework.security.admin-api-key", "test-admin-secret",
                "framework.security.admin-api-key-required", "true",
                "framework.security.csrf-enabled", "false",
                "framework.security.rate-limit-enabled", "false"
        );
    }
}
