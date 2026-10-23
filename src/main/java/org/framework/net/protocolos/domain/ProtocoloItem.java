package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProtocoloItem(
        String nome,
        String camada,
        String transporte,
        String portaComum,
        String funcao,
        String seguro,
        String badge,
        String badgeColor,
        String alcance,
        String algoritmo,
        String metrica,
        String distanciaAdministrativa,
        String atualizacao,
        String sintaxeBase,
        String dicaDidatica,
        String categoria,
        String convergencia,
        String ecmp,
        String problemasComuns,
        String mitigacoes,
        String casoUsoReal,
        String diagnosticoComandos) {
}
