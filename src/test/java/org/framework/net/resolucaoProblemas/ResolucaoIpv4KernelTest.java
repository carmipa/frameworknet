package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cobre a blindagem de {@link Ipv4Kernel#parseIpv4Parts(String, String)} no kernel de VLSM.
 * O bug era gêmeo do da análise didática: octeto com mais de 3 dígitos estourava int e
 * propagava NumberFormatException crua (500) em vez de erro de domínio.
 */
@QuarkusTest
class ResolucaoIpv4KernelTest {

    @Inject
    Ipv4Kernel kernel;

    /**
     * Auditoria CALC-36: 0.x e 127.x caíam no "E" do fim. Pelo bit inicial (0) são do espaço da classe A,
     * reservados; a vizinhança (126 = A, 128 = B, 240 = E) não muda.
     */
    @Test
    void octetosZeroE127SaoEspacoDaClasseAReservado() {
        assertEquals("A", kernel.classificacaoIpv4(0).classe());
        assertEquals("A", kernel.classificacaoIpv4(127).classe());
        assertEquals(true, kernel.classificacaoIpv4(127).faixaOcteto().contains("loopback"));
        assertEquals("1-126", kernel.classificacaoIpv4(126).faixaOcteto());
        assertEquals("B", kernel.classificacaoIpv4(128).classe());
        assertEquals("E", kernel.classificacaoIpv4(240).classe());
    }

    /**
     * Auditoria CALC-20: a forma incompleta do inet_aton era reinterpretada em silêncio ("192.168.1/24" →
     * 192.168.0.0/24). Fronteira (A1): a forma completa passa, inclusive 0.0.0.0/0 e host com bits ligados.
     */
    @Test
    void redeBaseIncompletaOuComZeroAEsquerdaERecusada() {
        for (String ruim : new String[]{"192.168.1/24", "10.1/16", "192.168.010.0/24", "10/8"}) {
            assertThrows(EntradaInvalidaException.class, () -> kernel.parseNetwork(ruim, "Rede base"), ruim);
        }
        assertEquals("192.168.1.0/24", kernel.parseNetwork("192.168.1.0/24", "Rede base").toCanonicalString());
        assertEquals("10.0.0.0/8", kernel.parseNetwork("10.0.0.77/8", "Rede base").toCanonicalString());
        assertEquals("0.0.0.0/0", kernel.parseNetwork("0.0.0.0/0", "Rede base").toCanonicalString());
    }

    @Test
    void parseIpv4PartsRejeitaZeroAEsquerdaEDigitoNaoAscii() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("192.168.010.1", "IP"));
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("\u0661\u0669\u0662.168.0.1", "IP"));
        assertEquals(0, kernel.parseIpv4Parts("192.168.0.1", "IP")[2]);
    }

    @Test
    void parseIpv4PartsValido() {
        int[] parts = kernel.parseIpv4Parts("10.20.30.40", "IP");
        assertEquals(10, parts[0]);
        assertEquals(40, parts[3]);
    }

    @Test
    void parseIpv4PartsOctetoEstouraIntEhRejeitadoComoDominio() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("9999999999.1.1.1", "IP"));
        int[] noLimite = kernel.parseIpv4Parts("255.255.255.255", "IP");
        assertEquals(255, noLimite[0]);
    }

    @Test
    void parseIpv4PartsRejeitaPontoSobrando() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("192.168.0.10.", "IP"));
        int[] ok = kernel.parseIpv4Parts("192.168.0.10", "IP");
        assertEquals(10, ok[3]);
    }

    @Test
    void parseIpv4PartsOctetoForaDaFaixa() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("256.1.1.1", "IP"));
    }
}
