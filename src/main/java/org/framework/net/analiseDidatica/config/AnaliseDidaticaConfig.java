package org.framework.net.analiseDidatica.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "framework.app")
public interface AnaliseDidaticaConfig {

    @WithDefault("60")
    int maxHistory();

    @WithDefault("20")
    String comparadorCidrPadraoA();

    @WithDefault("24")
    String comparadorCidrPadraoB();
}
