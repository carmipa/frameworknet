package org.framework.net.ferramentas.exception;

/** Falha de domínio do módulo Ferramentas. Traduzida por FerramentasExceptionMapper. */
public class FerramentasException extends RuntimeException {
    public FerramentasException(String message) { super(message); }
    public FerramentasException(String message, Throwable cause) { super(message, cause); }
}
