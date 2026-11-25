package org.framework.net.analiseDidatica.infrastructure.dns;

import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.framework.net.shared.InputLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teto do nome na única saída para o DNS (auditoria SEC-01, Lote 1). Gabarito independente da implementação: RFC 1035
 * §2.3.4 — 255 octetos no formato de rede, ou seja, 253 caracteres com os pontos. Os nomes de teste respeitam o teto
 * de 63 por rótulo, para a fronteira ser a do nome inteiro e não a do rótulo.
 */
class DnsResolverNomeTest {

    /** Nome de {@code total} caracteres com rótulos de no máximo 63. */
    static String nomeCom(int total) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < total) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            int falta = total - sb.length();
            sb.append("a".repeat(Math.min(63, falta)));
        }
        return sb.toString();
    }

    @Test
    void nomeNoTetoPassaEUmAcimaERecusado() {
        String noTeto = nomeCom(InputLimits.MAX_NOME_HOST);
        assertEquals(253, noTeto.length());
        assertEquals(noTeto, DnsResolver.normalizar(noTeto.toUpperCase()).toLowerCase());

        String acima = nomeCom(InputLimits.MAX_NOME_HOST + 1);
        DnsResolucaoException ex = assertThrows(DnsResolucaoException.class, () -> DnsResolver.normalizar(acima));
        assertTrue(ex.getMessage().contains("longo demais"), ex.getMessage());
    }

    @Test
    void normalizaCaixaEEspacosEContinuaRecusandoVazio() {
        assertEquals("example.com", DnsResolver.normalizar("  ExAmple.COM "));
        assertThrows(EntradaInvalidaException.class, () -> DnsResolver.normalizar("   "));
    }
}
