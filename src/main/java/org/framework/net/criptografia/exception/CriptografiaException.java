package org.framework.net.criptografia.exception;

/** Falha de domínio do módulo Criptografia. Traduzida por CriptografiaExceptionMapper. */
public class CriptografiaException extends RuntimeException {
    public CriptografiaException(String message) { super(message); }
    public CriptografiaException(String message, Throwable cause) { super(message, cause); }
}
