package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkAddressGuardTest {

    @Test
    void rejeitaLocalhost() {
        assertThrows(Exception.class, () -> NetworkAddressGuard.rejectBlockedHostname("localhost"));
    }

    @Test
    void rejeitaIpv4Privado() throws Exception {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        assertThrows(Exception.class, () -> NetworkAddressGuard.rejectNonPublicAddress(addr, "teste"));
    }

    @Test
    void aceitaIpv4Publico() throws Exception {
        InetAddress addr = InetAddress.getByName("8.8.8.8");
        assertDoesNotThrow(() -> NetworkAddressGuard.rejectNonPublicAddress(addr, "teste"));
    }

    /**
     * BACK-02 (auditoria de 22/10/2026): as faixas de documentação e do IETF são /24 (RFC 5737, RFC 6890), mas a guarda
     * testava só dois octetos e recusava todo 192.0.x.x, 198.51.x.x e 203.0.x.x — a Análise de "icann.org"
     * (192.0.43.7) dizia "o DNS não devolveu endereço". Gabarito: is_global do ipaddress do Python, conferido à parte.
     */
    @Test
    void faixasDeDocumentacaoSaoBarra24() {
        for (String reservado : new String[]{"192.0.0.8", "192.0.2.1", "198.51.100.7", "203.0.113.200"}) {
            assertThrows(Exception.class,
                    () -> NetworkAddressGuard.rejectNonPublicAddress(InetAddress.getByName(reservado), "teste"), reservado);
        }
        // fronteira: o mesmo início de endereço, fora do /24 reservado, é público
        for (String publico : new String[]{"192.0.43.7", "192.0.1.1", "198.51.99.1", "203.0.112.1", "203.0.114.1"}) {
            assertDoesNotThrow(
                    () -> NetworkAddressGuard.rejectNonPublicAddress(InetAddress.getByName(publico), "teste"), publico);
        }
    }
}
