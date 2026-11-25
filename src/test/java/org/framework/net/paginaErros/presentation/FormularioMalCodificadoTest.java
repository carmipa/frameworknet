package org.framework.net.paginaErros.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Só a falha do leitor de formulário do Quarkus REST vira 400 (Lote 1, revisão adversarial). A1: a mesma classe de
 * exceção lançada pelo código da aplicação continua 500 — defeito nosso não se disfarça de erro de quem chamou.
 */
class FormularioMalCodificadoTest {

    @Test
    void falhaDoLeitorDeFormularioEErroDeQuemEnviou() {
        IllegalArgumentException doLeitor = new IllegalArgumentException("Failed to decode URL %zz to ISO-8859-1");
        doLeitor.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.jboss.resteasy.reactive.common.util.URLUtils", "failedToDecodeURL", "URLUtils.java", 221)});
        assertTrue(PaginaErroMapper.formularioMalCodificado(doLeitor));
    }

    private static final StackTraceElement LEITOR = new StackTraceElement(
            "org.jboss.resteasy.reactive.server.core.multipart.FormEncodedDataDefinition$FormEncodedDataParser",
            "decodeParameterValue", "FormEncodedDataDefinition.java", 208);

    /**
     * Pilhas medidas em 25/11/2026 no {@code /resolucao-problemas}: {@code charset=foo} tem 1 quadro do JDK antes do
     * leitor; {@code charset=@@}, 4 ({@code checkName}, {@code lookup2}, {@code lookup}, {@code forName}).
     */
    @Test
    void charsetInutilizavelNoLeitorDeFormularioEErroDeQuemEnviou() {
        java.nio.charset.UnsupportedCharsetException desconhecido = new java.nio.charset.UnsupportedCharsetException("foo");
        desconhecido.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.nio.charset.Charset", "forName", "Charset.java", 534), LEITOR});
        java.nio.charset.IllegalCharsetNameException ilegal = new java.nio.charset.IllegalCharsetNameException("@@");
        ilegal.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.nio.charset.Charset", "checkName", "Charset.java", 325),
                new StackTraceElement("java.nio.charset.Charset", "lookup2", "Charset.java", 487),
                new StackTraceElement("java.nio.charset.Charset", "lookup", "Charset.java", 467),
                new StackTraceElement("java.nio.charset.Charset", "forName", "Charset.java", 531), LEITOR});
        assertTrue(PaginaErroMapper.formularioMalCodificado(desconhecido));
        assertTrue(PaginaErroMapper.formularioMalCodificado(ilegal));
    }

    /** A1: a mesma exceção de charset lançada pelo código da aplicação (fora do leitor) continua 500. */
    @Test
    void charsetInvalidoNoCodigoDaAplicacaoContinuaSendoDefeitoNosso() {
        java.nio.charset.UnsupportedCharsetException nossa = new java.nio.charset.UnsupportedCharsetException("foo");
        nossa.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.nio.charset.Charset", "forName", "Charset.java", 534),
                new StackTraceElement("org.framework.net.qualquer.Servico", "ler", "Servico.java", 10)});
        assertFalse(PaginaErroMapper.formularioMalCodificado(nossa));
        assertFalse(PaginaErroMapper.formularioMalCodificado(new java.nio.charset.UnsupportedCharsetException("foo")),
                "lançada aqui, a pilha começa no teste");
    }

    @Test
    void mesmaExcecaoDoCodigoDaAplicacaoContinuaSendoDefeitoNosso() {
        assertFalse(PaginaErroMapper.formularioMalCodificado(new IllegalArgumentException("Failed to decode URL %zz")),
                "lançada aqui, a pilha começa no teste, não no leitor do Quarkus");
        assertFalse(PaginaErroMapper.formularioMalCodificado(new IllegalStateException("x")));
        assertFalse(PaginaErroMapper.formularioMalCodificado(null));
    }
}
