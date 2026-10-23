package org.framework.net.calculadora.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Tetos de renderização da Calculadora.
 *
 * <p><b>Propósito de negócio:</b> um /8 dividido em /30 dá 4.194.304 sub-redes.
 * Renderizar isso derruba o navegador e ocupa o servidor à toa. Estes limites
 * definem quantas linhas a tela chega a montar, sem esconder do usuário o total
 * matemático real.</p>
 *
 * <p><b>Invariantes do domínio:</b> os limites afetam apenas a exibição — o
 * total de sub-redes, de endereços e de VLANs possíveis continua sendo
 * calculado e mostrado integralmente, com aviso explícito de truncamento.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> ausência de configuração usa os
 * padrões declarados; valores são lidos uma vez na inicialização do Quarkus.</p>
 */
@ConfigMapping(prefix = "framework.calculadora")
public interface CalculadoraConfig {

    /** Máximo de sub-redes renderizadas em uma divisão. */
    @WithDefault("512")
    int maxLinhas();

    /** Máximo de VLANs geradas em um plano. */
    @WithDefault("256")
    int maxVlans();

    /** Máximo de redes aceitas na sumarização. */
    @WithDefault("64")
    int maxRedesAgregacao();
}
