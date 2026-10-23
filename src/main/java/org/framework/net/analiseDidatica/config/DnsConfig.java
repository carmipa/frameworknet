package org.framework.net.analiseDidatica.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "framework.dns")
public interface DnsConfig {

    @WithDefault("180")
    int cacheTtlSeconds();

    @WithDefault("3")
    int resolveTimeoutSeconds();
}
