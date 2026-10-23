package org.framework.net.analiseDidatica.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "framework.geo")
public interface GeoConfig {

    @WithDefault("300")
    int cacheTtlSeconds();

    @WithDefault("geo/GeoLite2-City.mmdb")
    String databasePath();

    @WithDefault("true")
    boolean usarFallback();
}
