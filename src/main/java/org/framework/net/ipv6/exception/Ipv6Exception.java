package org.framework.net.ipv6.exception;

/**
 * Erro de domínio da calculadora IPv6 (entrada inválida ou pedido impossível → HTTP 400).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> transportar uma mensagem didática de erro do cálculo IPv6 até a
 * tela, sem virar 500.</p>
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só representa erro previsível de entrada/regra (prefixo fora de
 * faixa, alvo mais amplo que a base, endereço malformado).</p>
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> é mapeada por {@code Ipv6ExceptionMapper} para 400.</p>
 */
public class Ipv6Exception extends RuntimeException {

    public Ipv6Exception(String message) {
        super(message);
    }

    public Ipv6Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
