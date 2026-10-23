package org.framework.net.analiseDidatica.exception;

/**
 * Erro de validação de entrada informado pelo usuário (HTTP 400).
 */
public class EntradaInvalidaException extends AnaliseDidaticaException {

    public EntradaInvalidaException(String message) {
        super(message);
    }

    public EntradaInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
