package org.framework.net.localizacao;

import org.framework.net.localizacao.infrastructure.CepLookupService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CepLookupServiceTest {

    @Test
    void normalizaCepComMascara() {
        assertEquals("01001000", CepLookupService.normalizarCep("01001-000"));
        assertEquals("01001000", CepLookupService.normalizarCep(" 01001000 "));
        assertEquals("01001000", CepLookupService.normalizarCep("01.001-000"));
    }

    @Test
    void rejeitaCepInvalido() {
        assertNull(CepLookupService.normalizarCep(null));
        assertNull(CepLookupService.normalizarCep(""));
        assertNull(CepLookupService.normalizarCep("123"));
        assertNull(CepLookupService.normalizarCep("abcdefgh"));
        assertNull(CepLookupService.normalizarCep("010010000")); // 9 dígitos
    }
}
