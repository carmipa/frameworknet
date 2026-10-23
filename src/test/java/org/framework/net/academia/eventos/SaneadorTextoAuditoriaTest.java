package org.framework.net.academia.eventos;

import org.framework.net.academia.eventos.domain.SaneadorTexto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Disfarces que passavam pelo saneador (auditoria ACAD-26), com o legítimo de mesmo sinal ao lado (A1). */
class SaneadorTextoAuditoriaTest {

    @Test
    void disfarcesSaem() {
        assertFalse(SaneadorTexto.sanear("falhou para fulano\uFF20exemplo.com").contains("exemplo"), "arroba de largura cheia");
        assertFalse(SaneadorTexto.sanear("contato fulano @ exemplo.com aqui").contains("exemplo"), "arroba com espaço");
        assertFalse(SaneadorTexto.sanear("CPF \u00B9\u00B2\u00B3").contains("\u00B9"), "algarismo sobrescrito");
        assertFalse(SaneadorTexto.sanear("rota fe80::abcd:ef01 caiu").contains("abcd"), "pedaço de IPv6");
    }

    @Test
    void mensagemDeErroLegitimaContinuaLegivel() {
        assertEquals("TypeError: campo is undefined", SaneadorTexto.sanear("TypeError: campo is undefined"));
        assertEquals("Erro na linha #", SaneadorTexto.sanear("Erro na linha 7"));
    }
}
