package org.framework.net.portas.application;

import org.framework.net.portas.domain.PortaItem;

public record PortaItemExibicao(
        String porta,
        String protocoloTransporte,
        String servico,
        String categoria,
        String risco,
        String recomendacao,
        String badge,
        String badgeColor,
        String alternativaSegura,
        String aprofundamentoRota) {

    public static PortaItemExibicao from(PortaItem item, String alternativaSegura, String aprofundamentoRota) {
        return new PortaItemExibicao(
                item.porta(),
                item.protocoloTransporte(),
                item.servico(),
                item.categoria(),
                item.risco(),
                item.recomendacao(),
                item.badge(),
                item.badgeColor(),
                alternativaSegura,
                aprofundamentoRota);
    }
}
