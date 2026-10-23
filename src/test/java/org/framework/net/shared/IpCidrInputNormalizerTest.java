package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpCidrInputNormalizerTest {

    @Test
    void splitIpAndCidrExtraiBarraDoCampoIp() {
        IpCidrInputNormalizer.SplitResult result =
                IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0/16", "");
        assertEquals("172.19.0.0", result.ip());
        assertEquals("16", result.cidrRaw());
    }

    /**
     * Auditoria CALC-35: o campo de prefixo volta preenchido com a consulta anterior; o "/16" digitado
     * junto do endereço é a intenção nova e prevalece — com aviso, para a tela dizer qual valeu.
     */
    @Test
    void barraDoCampoIpPrevaleceSobreOCampoDePrefixoComAviso() {
        IpCidrInputNormalizer.SplitResult result =
                IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0/16", "24");
        assertEquals("172.19.0.0", result.ip());
        assertEquals("16", result.cidrRaw());
        assertTrue(result.aviso().contains("/16") && result.aviso().contains("/24"), result.aviso());
    }

    @Test
    void semConflitoNaoHaAviso() {
        assertEquals(null, IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0/16", "16").aviso());
        assertEquals(null, IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0", "24").aviso());
        assertEquals("24", IpCidrInputNormalizer.splitIpAndCidr("172.19.0.0", "24").cidrRaw());
    }

    @Test
    void looksLikeIpv4OrCidrReconheceNotacaoCidr() {
        assertTrue(IpCidrInputNormalizer.looksLikeIpv4OrCidr("192.168.1.1/24"));
        assertFalse(IpCidrInputNormalizer.looksLikeIpv4OrCidr("google.com"));
    }
}
