package org.framework.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Leitura do token CSRF no corpo do formulário (Lote 1 da auditoria, OPS-05): o {@code URLDecoder} lançava com
 * {@code %} solto e o pedido virava 500 com stack trace no log. Token ilegível é token ausente (403); o resto do corpo
 * não atrapalha a leitura de um token legítimo.
 */
class CsrfCorpoMalformadoTest {

    @Test
    void escapeInvalidoNoValorViraTokenAusente() {
        assertEquals("", CsrfRequestFilter.parseUrlEncodedParam("csrf_token=%", "csrf_token"));
        assertEquals("", CsrfRequestFilter.parseUrlEncodedParam("csrf_token=%zz", "csrf_token"));
    }

    /**
     * Revisão adversarial: o {@code split("&")} criava uma String por par — um corpo de 1 MB como {@code a&a&…} virava
     * meio milhão delas (~25 MB passageiros por pedido). O percurso por índice lê o mesmo corpo alocando quase nada
     * (medido pela própria JVM) e em tempo linear, inclusive sem nenhum {@code =} no corpo.
     */
    @Test
    void corpoComMeioMilhaoDeParesELidoSemAlocarPorPar() {
        String corpo = "a&".repeat(500_000) + "csrf_token=t";
        String semIgual = "a&".repeat(500_000);
        com.sun.management.ThreadMXBean mx =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        // Sem medição de alocação na JVM, o teste NÃO verificou (fica como ignorado), nunca aprovado por medir zero.
        org.junit.jupiter.api.Assumptions.assumeTrue(mx.isThreadAllocatedMemorySupported() && mx.isThreadAllocatedMemoryEnabled(),
                "JVM sem medição de alocação por thread");
        // Controle positivo: o instrumento tem de enxergar 2 MB alocados de propósito.
        long antesControle = mx.getCurrentThreadAllocatedBytes();
        byte[] controle = new byte[2_000_000];
        long vistoControle = mx.getCurrentThreadAllocatedBytes() - antesControle;
        org.junit.jupiter.api.Assertions.assertTrue(vistoControle > 1_000_000 && controle.length > 0,
                "instrumento cego: 2 MB alocados de propósito, medidos " + vistoControle + " bytes");
        CsrfRequestFilter.parseUrlEncodedParam("csrf_token=aquece", "csrf_token");
        long antes = mx.getCurrentThreadAllocatedBytes();
        String achado = CsrfRequestFilter.parseUrlEncodedParam(corpo, "csrf_token");
        String vazio = CsrfRequestFilter.parseUrlEncodedParam(semIgual, "csrf_token");
        long alocado = mx.getCurrentThreadAllocatedBytes() - antes;
        assertEquals("t", achado);
        assertEquals("", vazio);
        org.junit.jupiter.api.Assertions.assertTrue(alocado < 1_000_000, "a leitura do token alocou " + alocado + " bytes");
    }

    /** A1: chave codificada continua casando, como no leitor anterior. */
    @Test
    void chaveCodificadaContinuaCasando() {
        assertEquals("t", CsrfRequestFilter.parseUrlEncodedParam("x=1&csrf%5Ftoken=t", "csrf_token"));
        assertEquals("", CsrfRequestFilter.parseUrlEncodedParam("csrf_token&x=1", "csrf_token"));
    }

    /** A1: o mesmo sinal (% no corpo) em outro campo não impede ler o token legítimo. */
    @Test
    void escapeInvalidoEmOutroCampoNaoEscondeOTokenLegitimo() {
        assertEquals("ok", CsrfRequestFilter.parseUrlEncodedParam("x=%&csrf_token=ok", "csrf_token"));
        assertEquals("t", CsrfRequestFilter.parseUrlEncodedParam("%zz=1&csrf_token=t", "csrf_token"));
        assertEquals("abc d", CsrfRequestFilter.parseUrlEncodedParam("csrf_token=abc%20d", "csrf_token"));
        assertEquals("", CsrfRequestFilter.parseUrlEncodedParam("x=1", "csrf_token"));
    }
}
