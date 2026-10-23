package org.framework.net.calculadora.domain;

import java.util.List;

/**
 * Resultado da sumarização (supernetting) de um conjunto de redes.
 *
 * <p><b>Propósito de negócio:</b> responde o que se anuncia em
 * {@code summary-address} do OSPF ou {@code ip summary-address eigrp}: qual o
 * menor prefixo único que cobre todas as redes informadas, e quanto de espaço
 * isso arrasta junto que não foi pedido.</p>
 *
 * <p><b>Invariantes do domínio:</b> o bloco resumo sempre contém todas as redes
 * de entrada — a agregação nunca deixa rede de fora; {@code enderecosExtras}
 * expõe o custo da agregação (endereços cobertos pelo resumo que não pertencem
 * a nenhuma rede informada), e é 0 quando a agregação é exata.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável; lista vazia ou
 * rede malformada lança {@code CalculadoraException} no serviço.</p>
 */
public record ResultadoAgregacao(
        String resumo,
        int prefixoResumo,
        String mascaraResumo,
        String wildcardResumo,
        String primeiroEndereco,
        String ultimoEndereco,
        long totalEnderecosResumo,
        long enderecosCobertos,
        long enderecosExtras,
        boolean exata,
        List<BlocoIpv4> entradas,
        List<String> comandosCisco) {
}
