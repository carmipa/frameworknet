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
        assertEquals("/a%u2028b", TelemetriaContext.caminhoParaLog("/a\u2028b"));
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

    /**
     * Regra única para valor digitado (Lote 1, OPS-03 e SEC-06): teto em caracteres antes da marca, sem partir escape
     * nem emoji ao meio. A1: emoji e acento legítimos passam iguais; A2: o corte que caísse no meio do par deixaria
     * metade órfã, que sai como "?" no console e no JSONL.
     */
    @Test
    void paraLogCortaNoTetoSemPartirEscapeNemEmoji() {
        String emoji = "😀";
        assertEquals("ação " + emoji, TelemetriaContext.paraLog("ação " + emoji, 64), "texto legítimo passa igual");
        assertEquals("abc", TelemetriaContext.paraLog("abc", 3), "exatamente no teto não corta");
        assertEquals("ab" + TelemetriaContext.MARCA_TRUNCADO, TelemetriaContext.paraLog("ab" + emoji, 3),
                "o emoji que não cabe inteiro sai inteiro, nunca pela metade");
        assertEquals("a" + TelemetriaContext.MARCA_TRUNCADO, TelemetriaContext.paraLog("a\nb", 3),
                "o escape %0A não cabe nos 2 que sobram: sai inteiro, não pela metade");
        assertEquals("a%0Ab", TelemetriaContext.paraLog("a\nb", 5));
        assertEquals("", TelemetriaContext.paraLog(null, 10));

        String gigante = TelemetriaContext.paraLog("x".repeat(1_000_000), 256);
        assertEquals(256 + TelemetriaContext.MARCA_TRUNCADO.length(), gigante.length());
    }

    /**
     * Revisão adversarial: um "%0A" DIGITADO ficava igual ao escape de uma quebra de linha real, e controle
     * bidirecional reordenava o texto do log na tela. O próprio "%" vira %25; o controle bidirecional, %uXXXX.
     */
    @Test
    void paraLogDeixaOEscapeInequivocoENeutralizaBidi() {
        assertEquals("a%250Ab", TelemetriaContext.paraLog("a%0Ab", 64));
        assertEquals("a%0Ab", TelemetriaContext.paraLog("a\nb", 64));
        assertEquals("x%u202Ey%u2066z", TelemetriaContext.paraLog("x\u202Ey\u2066z", 64));
        assertEquals("100% ok", TelemetriaContext.paraLog("100% ok", 64).replace("%25", "%"),
                "o texto legítimo com % continua legível depois de desfazer o escape");
    }

    /**
     * Revisão (2ª passada): evento antigo carregado do arquivo ou do Stream pode trazer controle cru, e o desta versão já
     * vem saneado. A neutralização de legado escapa o controle e NÃO reescapa '%': é idempotente (reaplicada a cada
     * reinício, não dobra escapes). E cortar só corta, sem partir emoji.
     */
    @Test
    void neutralizarLegadoEIdempotenteECortarSoCorta() {
        String jaSaneado = TelemetriaContext.paraLog("a\nb", 64);
        assertEquals(jaSaneado, TelemetriaContext.neutralizarLegado(jaSaneado, 64));
        assertEquals("a%0Ab", TelemetriaContext.neutralizarLegado("a\nb", 64));
        assertEquals("a%0Ab", TelemetriaContext.neutralizarLegado(TelemetriaContext.neutralizarLegado("a\nb", 64), 64));
        assertEquals("ab" + TelemetriaContext.MARCA_TRUNCADO, TelemetriaContext.cortar("ab😀", 3));
        assertEquals("a\nb", TelemetriaContext.cortar("a\nb", 64), "cortar não muda caractere nenhum");
    }

    /**
     * Campos de vocabulário do dataset público (modo, topologia): só palavra minúscula curta passa; IP e texto livre
     * viram "outro". A1: o vocabulário real do código passa igual.
     */
    @Test
    void vocabularioSoAceitaPalavraCurta() {
        assertEquals("extended_star", TelemetriaContext.vocabulario("extended_star"));
        assertEquals("estrela-estendida", TelemetriaContext.vocabulario(" Estrela-Estendida "));
        assertEquals("malha", TelemetriaContext.vocabulario("malha"));
        assertEquals("outro", TelemetriaContext.vocabulario("8.8.8.8"));
        assertEquals("outro", TelemetriaContext.vocabulario("texto livre com espaço"));
        assertEquals("outro", TelemetriaContext.vocabulario("a".repeat(33)));
        assertEquals("outro", TelemetriaContext.vocabulario(null));
    }

    /** Metade de par substituto vinda do cliente vira %uXXXX: crua, sairia como "?" e esconderia o que chegou. */
    @Test
    void paraLogNeutralizaSurrogateOrfao() {
        assertEquals("a%uD83Db", TelemetriaContext.paraLog("a\uD83Db", 64));
        assertEquals("a%uDE00", TelemetriaContext.paraLog("a\uDE00", 64));
        assertEquals("%u2029", TelemetriaContext.paraLog("\u2029", 64));
    }
}
