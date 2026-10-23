package org.framework.net.calculadora.domain;

import java.util.List;

/**
 * Conversão de uma faixa arbitrária de IPs na lista mínima de blocos CIDR.
 *
 * <p><b>Propósito de negócio:</b> traduz o pedido do mundo real ("libere de
 * 10.0.0.5 até 10.0.3.200") para o que o equipamento entende: um conjunto de
 * prefixos CIDR. É o passo que falta entre a planilha do cliente e a ACL.</p>
 *
 * <p><b>Invariantes do domínio:</b> a união dos blocos cobre exatamente a faixa
 * pedida — nem um endereço a mais, nem a menos; a lista é a menor possível
 * (algoritmo guloso alinhado pelo maior bloco que cabe a cada passo).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável; início maior que
 * fim, ou endereço malformado, lança {@code CalculadoraException} no serviço.</p>
 */
public record ResultadoFaixa(
        String inicio,
        String fim,
        long totalEnderecos,
        int totalBlocos,
        boolean truncado,
        List<BlocoIpv4> blocos,
        List<String> comandosCisco) {
}
