package org.framework.net.calculadora.exception;

/**
 * Erro de entrada da Calculadora de Sub-redes e VLANs.
 *
 * <p><b>Propósito de negócio:</b> sinalizar que o operador informou um bloco,
 * prefixo, VLAN ID ou faixa que não descreve um plano de endereçamento
 * calculável, devolvendo uma mensagem didática em vez de um stack trace.</p>
 *
 * <p><b>Invariantes do domínio:</b> a mensagem é sempre destinada ao usuário
 * final (português, sem termos de implementação) porque é renderizada
 * diretamente no fragmento de erro da tela.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> é uma {@link RuntimeException} não
 * checada, capturada pelo {@link CalculadoraExceptionMapper}, que responde 400
 * com o fragmento HTML quando a chamada vem do htmx e texto puro caso contrário.</p>
 */
public class CalculadoraException extends RuntimeException {

    public CalculadoraException(String message) {
        super(message);
    }

    public CalculadoraException(String message, Throwable cause) {
        super(message, cause);
    }
}
