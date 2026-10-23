package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SensitiveApisService {

    @ConfigProperty(name = "framework.security.sensitive-apis-enabled", defaultValue = "true")
    boolean sensitiveApisEnabled;

    public boolean isEnabled() {
        return sensitiveApisEnabled;
    }
}
