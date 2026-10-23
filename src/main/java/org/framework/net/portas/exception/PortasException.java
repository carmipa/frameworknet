package org.framework.net.portas.exception;

public class PortasException extends RuntimeException {

    public PortasException(String message) {
        super(message);
    }

    public PortasException(String message, Throwable cause) {
        super(message, cause);
    }
}
