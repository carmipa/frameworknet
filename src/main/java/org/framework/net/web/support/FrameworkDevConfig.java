package org.framework.net.web.support;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "framework.dev")
public interface FrameworkDevConfig {

    @WithDefault("false")
    boolean openBrowser();

    @WithDefault("/")
    String browserPath();
}
