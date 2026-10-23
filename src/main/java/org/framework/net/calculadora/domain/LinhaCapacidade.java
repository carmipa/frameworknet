package org.framework.net.calculadora.domain;

/**
 * Uma linha da matriz "quantas sub-redes cabem".
 *
 * <p><b>Propósito de negócio:</b> responde de uma vez a pergunta clássica de
 * prova — dado um /21, quantas /24 cabem? E quantas /26? Cada linha é um
 * prefixo alvo possível com a quantidade de blocos e a capacidade de cada um.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code subredes} vale
 * {@code 2^(prefixoAlvo - prefixoBase)} e {@code ipsPorSubrede} vale
 * {@code 2^(32-prefixoAlvo)}; ambos são {@code long} para suportar prefixos
 * curtos como /0 e /8.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável sem validação
 * própria; a faixa de prefixos válidos é garantida por quem monta a matriz.</p>
 */
public record LinhaCapacidade(
        int prefixoAlvo,
        String mascara,
        long subredes,
        long ipsPorSubrede,
        long hostsUteis,
        boolean destaque) {
}
