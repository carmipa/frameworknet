package org.framework.net.telemetria;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teto por origem e minuto dos eventos de erro de cliente (auditoria OPS-01 e SEC-02). */
class TetoDeEventosTest {

    private static TetoDeEventos teto(int limite, long[] agora) {
        TetoDeEventos t = new TetoDeEventos();
        t.tetoPorMinuto = limite;
        t.relogio = () -> agora[0];
        return t;
    }

    @Test
    void gravaAteOTetoEDepoisSuprime() {
        long[] agora = {60_000L * 1000};
        TetoDeEventos t = teto(3, agora);
        assertTrue(t.avaliar("http-429").gravar());
        assertTrue(t.avaliar("http-429").gravar());
        assertTrue(t.avaliar("http-429").gravar(), "o próprio teto ainda grava");
        assertFalse(t.avaliar("http-429").gravar());
        assertFalse(t.avaliar("http-429").gravar());
        assertTrue(t.avaliar("http-404").gravar(), "outra categoria tem balde próprio");
    }

    @Test
    void suprimidosDoMinutoAnteriorVaoNoPrimeiroEventoSeguinte() {
        long[] agora = {60_000L * 1000};
        TetoDeEventos t = teto(2, agora);
        for (int i = 0; i < 7; i++) {
            t.avaliar("pagina-erro-404");
        }
        agora[0] += 60_000L;
        TetoDeEventos.Decisao primeira = t.avaliar("pagina-erro-404");
        assertTrue(primeira.gravar());
        assertEquals(5, primeira.suprimidosAntes(), "7 tentativas com teto 2: 5 ficaram de fora");
        assertEquals(0, t.avaliar("pagina-erro-404").suprimidosAntes(), "reporta uma vez só");
    }

    @Test
    void semExcessoNaoReportaNada() {
        long[] agora = {60_000L * 1000};
        TetoDeEventos t = teto(5, agora);
        t.avaliar("http-401");
        agora[0] += 60_000L;
        assertEquals(0, t.avaliar("http-401").suprimidosAntes());
    }
}
