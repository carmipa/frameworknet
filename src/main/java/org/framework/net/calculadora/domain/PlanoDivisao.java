package org.framework.net.calculadora.domain;

import java.util.List;

/**
 * Resultado completo de uma divisão de bloco em sub-redes de tamanho fixo (FLSM).
 *
 * <p><b>Propósito de negócio:</b> é a resposta ao caso central da calculadora —
 * "peguei um /21, quero /24, quantas redes cabem e quais são elas?". Carrega
 * tanto o total matemático quanto os blocos efetivamente renderizados e a matriz
 * de capacidade com todos os outros prefixos possíveis.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code totalSubredes} é o valor matemático
 * real e nunca é reduzido pelo teto de exibição; {@code blocos} pode ser menor
 * que ele, e nesse caso {@code truncado} é {@code true} e {@code exibidos}
 * informa quantos vieram — o total exibido jamais pode ser lido como o total
 * existente.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável; divisões
 * impossíveis (prefixo alvo menor que o base, fora de 0–32, hosts demais)
 * lançam {@code CalculadoraException} no serviço e nunca produzem instância.</p>
 */
public record PlanoDivisao(
        String blocoBase,
        int prefixoBase,
        String mascaraBase,
        long totalIpsBase,
        int prefixoAlvo,
        String mascaraAlvo,
        long totalSubredes,
        long ipsPorSubrede,
        long hostsUteisPorSubrede,
        long bitsEmprestados,
        int exibidos,
        boolean truncado,
        String explicacao,
        List<BlocoIpv4> blocos,
        List<LinhaCapacidade> capacidade) {
}
