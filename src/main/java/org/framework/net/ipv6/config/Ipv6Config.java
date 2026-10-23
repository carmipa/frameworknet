package org.framework.net.ipv6.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Teto de renderização da Calculadora IPv6.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> um /32 dividido em /64 dá 2^32 = 4.294.967.296 sub-redes.
 * Renderizar isso trava o navegador. Este limite define quantas sub-redes a tela chega a listar,
 * sem esconder o total matemático real.</p>
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o limite afeta só a exibição — a quantidade total de sub-redes
 * continua calculada e mostrada por inteiro, com aviso de truncamento.</p>
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> ausência de configuração usa o padrão; lido uma vez no
 * boot do Quarkus.</p>
 */
@ConfigMapping(prefix = "framework.ipv6")
public interface Ipv6Config {

    /** Máximo de sub-redes IPv6 renderizadas em uma divisão. */
    @WithDefault("256")
    int maxLinhas();

    /** Quantas sub-redes contíguas do mesmo prefixo a "régua" da Análise lista (análogo ao IPv4). */
    @WithDefault("8")
    int reguaCount();
}
