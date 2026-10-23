package org.framework.net.camadas.exception;

/**
 * Falha de domínio do módulo de Camadas (modelo OSI × TCP/IP).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> sinaliza catálogo/aprofundamento ausente ou
 * inválido, ou slug pedido sem correspondência.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> traduzida por
 * {@code CamadasExceptionMapper} em resposta de erro, nunca conteúdo de outra
 * página.</p>
 */
public class CamadasException extends RuntimeException {

    public CamadasException(String message) {
        super(message);
    }

    public CamadasException(String message, Throwable cause) {
        super(message, cause);
    }
}
