package org.framework.net.analiseDidatica.exception;

/**
 * Falha ao persistir ou carregar histórico local.
 */
public class HistoricoPersistenciaException extends AnaliseDidaticaException {

    public HistoricoPersistenciaException(String message) {
        super(message);
    }

    public HistoricoPersistenciaException(String message, Throwable cause) {
        super(message, cause);
    }
}
