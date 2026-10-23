package org.framework.net.analiseDidatica.exception;

/**
 * Exceção base do módulo de análise didática de redes.
 */
public class AnaliseDidaticaException extends RuntimeException {

    public AnaliseDidaticaException(String message) {
        super(message);
    }

    public AnaliseDidaticaException(String message, Throwable cause) {
        super(message, cause);
    }
}
