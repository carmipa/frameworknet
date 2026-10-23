package org.framework.net.analiseDidatica.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnaliseDidaticaUiSupportTest {

    @Test
    void octetosBitsDeveGerar32Colunas() {
        String bin = "11111111111111111111111100000000";
        var octetos = new AnaliseDidaticaUiSupport().octetosBits(bin, "255.255.255.0");
        assertEquals(4, octetos.size());
        assertEquals(8, ((java.util.List<?>) octetos.get(0).get("bits")).size());
        assertFalse(octetos.isEmpty());
    }
}
