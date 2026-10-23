package org.framework.net.telemetria;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O que entra na correlação vindo do cliente: X-Request-Id (SEC-03) e caminho (OPS-11). */
class TelemetriaContextSaneamentoTest {

    private final TelemetriaContext contexto = new TelemetriaContext();

    @Test
    void requestIdDoClienteSoComFormatoCurto() {
        assertEquals("abc-123_x.y", contexto.iniciarRequisicao("abc-123_x.y", "GET", "/").requestId());
        assertEquals("a".repeat(64), contexto.iniciarRequisicao("a".repeat(64), "GET", "/").requestId(),
                "64 caracteres é o limite aceito");
        String longo = contexto.iniciarRequisicao("a".repeat(65), "GET", "/").requestId();
        assertEquals(8, longo.length(), "acima do limite vira identificador gerado");
        assertNotEquals("evil\nlinha", contexto.iniciarRequisicao("evil\nlinha", "GET", "/").requestId());
        assertEquals(8, contexto.iniciarRequisicao("id com espaço", "GET", "/").requestId().length());
        contexto.limpar();
    }

    @Test
    void caminhoNaoForjaLinhaENaoInflaOEvento() {
        assertEquals("/protocolos/x%0Alinha-forjada", TelemetriaContext.caminhoParaLog("/protocolos/x\nlinha-forjada"));
        assertEquals("/a%0D%09%1B%7F", TelemetriaContext.caminhoParaLog("/a\r\t\u001B\u007F"));
        assertEquals("/a%u2028b", TelemetriaContext.caminhoParaLog("/a b"));
        assertEquals("/protocolos/configuração", TelemetriaContext.caminhoParaLog("/protocolos/configuração"),
                "texto legítimo acentuado passa igual");
        String gigante = TelemetriaContext.caminhoParaLog("/" + "x".repeat(4000));
        assertEquals(TelemetriaContext.MAX_CAMINHO + TelemetriaContext.MARCA_TRUNCADO.length(), gigante.length());
        assertTrue(gigante.endsWith(TelemetriaContext.MARCA_TRUNCADO));
        String noLimite = "/" + "x".repeat(TelemetriaContext.MAX_CAMINHO - 1);
        assertEquals(noLimite, TelemetriaContext.caminhoParaLog(noLimite), "exatamente no limite não trunca");
        assertEquals("/", TelemetriaContext.caminhoParaLog(null));
        assertFalse(TelemetriaContext.caminhoParaLog("/a\nb").contains("\n"));
    }
}
