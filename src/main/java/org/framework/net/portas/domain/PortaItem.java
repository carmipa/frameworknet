package org.framework.net.portas.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PortaItem(
        String porta,
        String protocoloTransporte,
        String servico,
        String categoria,
        String risco,
        String recomendacao,
        String badge,
        String badgeColor) {
}
