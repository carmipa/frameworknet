package org.framework.net.calculadora.domain;

import java.util.List;

/**
 * Plano de VLANs derivado de um bloco base.
 *
 * <p><b>Propósito de negócio:</b> entrega, de uma vez, o mapa VLAN ↔ sub-rede
 * que o laboratório exige: quais VLANs existem, que rede cada uma usa, qual o
 * gateway e o script CLI Cisco pronto para colar no switch e no roteador.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code vlans} nunca excede a capacidade do
 * bloco base no prefixo escolhido; {@code comandosCisco} descreve exatamente as
 * VLANs listadas, sem inventar IDs; {@code trunkAllowed} lista os mesmos IDs
 * separados por vírgula, no formato aceito por
 * {@code switchport trunk allowed vlan}; {@code vlanIdInicial}, {@code passoVlanId}
 * e {@code nomesCsv} guardam os parâmetros exatos da requisição para que a
 * exportação CSV recalcule este mesmo plano, e não um parecido.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável; pedidos que não
 * cabem no bloco base ou que atingem VLAN ID reservado lançam
 * {@code CalculadoraException} no serviço, antes de existir instância.</p>
 */
public record PlanoVlan(
        String blocoBase,
        int prefixoBase,
        int prefixoPorVlan,
        int totalVlans,
        long capacidadeMaxima,
        long hostsPorVlan,
        String estrategia,
        String trunkAllowed,
        int vlanIdInicial,
        int passoVlanId,
        String nomesCsv,
        List<VlanEntry> vlans,
        List<String> avisos,
        List<String> comandosCisco) {
}
