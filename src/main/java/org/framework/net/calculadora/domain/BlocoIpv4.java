package org.framework.net.calculadora.domain;

/**
 * Uma sub-rede IPv4 já calculada, pronta para exibição.
 *
 * <p><b>Propósito de negócio:</b> representa uma linha do plano de endereçamento —
 * o que o aluno copia para o roteador ou para a prova: rede, faixa utilizável,
 * broadcast, máscara e wildcard.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code rede} é sempre o endereço com os bits
 * de host zerados; {@code totalIps} vale {@code 2^(32-prefixo)} e usa
 * {@code long} porque /0 estoura {@code int}; em /31 a faixa utilizável são os
 * dois endereços (RFC 3021) e em /32 é o próprio endereço.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> é um record imutável sem validação
 * própria — quem monta é o {@link SubnetKernel}, que rejeita entradas inválidas
 * com {@code CalculadoraException} antes de chegar aqui.</p>
 */
public record BlocoIpv4(
        int indice,
        String rede,
        int prefixo,
        String mascara,
        String wildcard,
        String primeiroHost,
        String ultimoHost,
        String broadcast,
        long totalIps,
        long hostsUteis) {

    /** Notação CIDR pronta para copiar, ex.: {@code 192.168.0.0/24}. */
    public String cidr() {
        return rede + "/" + prefixo;
    }
}
