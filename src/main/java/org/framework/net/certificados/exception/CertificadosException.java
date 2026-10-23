package org.framework.net.certificados.exception;

/**
 * Falha de domínio do módulo de Certificados (X.509/PKI).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> sinaliza conteúdo ausente/ inválido do catálogo
 * ou dos aprofundamentos, ou slug pedido sem correspondência.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> traduzida por
 * {@code CertificadosExceptionMapper} em resposta de erro, nunca conteúdo de outra
 * página.</p>
 */
public class CertificadosException extends RuntimeException {

    public CertificadosException(String message) {
        super(message);
    }

    public CertificadosException(String message, Throwable cause) {
        super(message, cause);
    }
}
