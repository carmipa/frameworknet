package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fronteiras do leitor numérico compartilhado (auditoria CALC-34). */
class NumeroAsciiTest {

    @Test
    void aceitaAteNoveDigitosAsciiERecusaOResto() {
        assertEquals(OptionalInt.of(999_999_999), NumeroAscii.inteiro("999999999", NumeroAscii.MAX_DIGITOS_INT));
        assertEquals(OptionalInt.of(0), NumeroAscii.inteiro("0", 2));
        assertTrue(NumeroAscii.inteiro("1234567890", 20).isEmpty(), "10 dígitos estouraria o int");
        assertTrue(NumeroAscii.inteiro("123", 2).isEmpty(), "acima do máximo pedido");
        assertTrue(NumeroAscii.inteiro("١٢", 9).isEmpty(), "dígito árabe-índico");
        assertTrue(NumeroAscii.inteiro("-1", 9).isEmpty());
        assertTrue(NumeroAscii.inteiro(" 1", 9).isEmpty());
        assertTrue(NumeroAscii.inteiro("", 9).isEmpty());
        assertTrue(NumeroAscii.inteiro(null, 9).isEmpty());
        assertFalse(NumeroAscii.digitosAscii(""));
        assertTrue(NumeroAscii.digitosAscii("007"));
    }

    /** BACK-10: o parseInt do Java aceita "+24" e "٢٤"; o estrito recusa os dois e aceita o legítimo "24". */
    @Test
    void parseIntEstritoRecusaSinalEOutroAlfabeto() {
        assertEquals(24, NumeroAscii.parseIntEstrito("24"));
        assertEquals(24, Integer.parseInt("+24"), "controle: o parseInt do Java aceita o sinal");
        for (String ruim : new String[]{"+24", "-1", "٢٤", " 24", "", "1234567890"}) {
            org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
                    () -> NumeroAscii.parseIntEstrito(ruim), ruim);
        }
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> NumeroAscii.parseIntEstrito(null));
    }
}
