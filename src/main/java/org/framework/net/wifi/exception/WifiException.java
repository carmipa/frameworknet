package org.framework.net.wifi.exception;

/** Falha de domínio do módulo Wifi. Traduzida por WifiExceptionMapper. */
public class WifiException extends RuntimeException {
    public WifiException(String message) { super(message); }
    public WifiException(String message, Throwable cause) { super(message, cause); }
}
