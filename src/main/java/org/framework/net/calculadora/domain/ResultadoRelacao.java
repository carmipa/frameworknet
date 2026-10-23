package org.framework.net.calculadora.domain;

/**
 * Relação entre dois blocos IPv4: contêm, sobrepõem ou são disjuntos.
 *
 * <p><b>Propósito de negócio:</b> é a checagem que evita o erro mais caro de um
 * plano de endereçamento — duas redes colidindo — e a que explica por que uma
 * ACL "não pega" o tráfego esperado (a rede da regra não contém o host).</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code tipo} assume exatamente um de
 * {@code IGUAIS}, {@code A_CONTEM_B}, {@code B_CONTEM_A}, {@code SOBREPOSTOS} ou
 * {@code DISJUNTOS}; em CIDR dois blocos ou são disjuntos ou um contém o outro,
 * então {@code SOBREPOSTOS} só aparece em comparação de faixas livres, nunca
 * entre dois prefixos válidos.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável; blocos malformados
 * lançam {@code CalculadoraException} antes da comparação.</p>
 */
public record ResultadoRelacao(
        String tipo,
        String rotulo,
        String explicacao,
        boolean conflito,
        BlocoIpv4 blocoA,
        BlocoIpv4 blocoB,
        long enderecosCompartilhados) {
}
