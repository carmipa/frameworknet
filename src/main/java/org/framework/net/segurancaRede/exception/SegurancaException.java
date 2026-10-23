package org.framework.net.segurancaRede.exception;

public class SegurancaException extends RuntimeException {
    public SegurancaException(String message) {
        super(message);
    }
    public SegurancaException(String message, Throwable cause) {
        super(message, cause);
    }
}
