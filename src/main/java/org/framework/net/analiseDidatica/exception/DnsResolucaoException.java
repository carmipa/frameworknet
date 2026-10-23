package org.framework.net.analiseDidatica.exception;

/**
 * Falha ao resolver DNS ou dependência de resolução (HTTP 500).
 */
public class DnsResolucaoException extends AnaliseDidaticaException {

    public DnsResolucaoException(String message) {
        super(message);
    }

    public DnsResolucaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
