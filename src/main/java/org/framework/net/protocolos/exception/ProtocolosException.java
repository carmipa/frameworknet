package org.framework.net.protocolos.exception;

public class ProtocolosException extends RuntimeException {

    public ProtocolosException(String message) {
        super(message);
    }

    public ProtocolosException(String message, Throwable cause) {
        super(message, cause);
    }
}
