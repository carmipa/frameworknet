package org.framework.net.academia.eventos.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Descarte por orçamento vai ao log, no máximo uma vez por minuto (auditoria ACAD-04). */
class RegistrarEventoAcademiaLogTest {

    @Test
    void umLogPorMinuto() {
        RegistrarEventoAcademia registrar = new RegistrarEventoAcademia();
        long t0 = 5_000_000_000L;
        assertTrue(registrar.logarDescarte(t0), "o primeiro descarte é logado");
        assertFalse(registrar.logarDescarte(t0 + RegistrarEventoAcademia.INTERVALO_LOG_DESCARTE_NANOS - 1), "dentro do minuto não repete");
        assertTrue(registrar.logarDescarte(t0 + RegistrarEventoAcademia.INTERVALO_LOG_DESCARTE_NANOS), "no minuto seguinte loga de novo");
    }
}
