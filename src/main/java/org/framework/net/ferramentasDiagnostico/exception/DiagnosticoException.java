package org.framework.net.ferramentasDiagnostico.exception;

public class DiagnosticoException extends RuntimeException {
    public DiagnosticoException(String message) {
        super(message);
    }
    public DiagnosticoException(String message, Throwable cause) {
        super(message, cause);
    }
}
