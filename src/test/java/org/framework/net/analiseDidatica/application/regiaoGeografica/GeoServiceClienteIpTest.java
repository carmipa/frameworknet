package org.framework.net.analiseDidatica.application.regiaoGeografica;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IP do visitante a partir dos cabeçalhos do proxy (auditoria SEC-01): valor de cabeçalho não é
 * resolvido por DNS, a leitura do X-Forwarded-For tem teto e "público" segue a régua do
 * NetworkAddressGuard.
 */
class GeoServiceClienteIpTest {

    private final GeoService geo = new GeoService();

    @Test
    void nomeNoCabecalhoNaoViraOraculoDeDns() {
        assertEquals("", geo.clienteIpEfetivo("localhost", null, null),
                "resolver o nome devolveria 127.0.0.1 — o IP para onde o nome aponta");
        assertEquals("", geo.clienteIpEfetivo(null, "localhost", ""));
        assertEquals("198.51.100.20", geo.clienteIpEfetivo("localhost, nome-qualquer", null, "198.51.100.20"));
    }

    @Test
    void escolheOPrimeiroIpv4PublicoEPreservaOCasoComum() {
        assertEquals("8.8.8.8", geo.clienteIpEfetivo("8.8.8.8", null, "172.18.0.2"));
        assertEquals("8.8.8.8", geo.clienteIpEfetivo("10.0.0.5, 8.8.8.8", null, null));
        assertEquals("8.8.4.4", geo.clienteIpEfetivo(null, "8.8.4.4", "127.0.0.1"));
        assertEquals("203.0.113.9", geo.clienteIpEfetivo("203.0.113.9", null, null),
                "sem público, devolve o primeiro literal válido");
    }

    @Test
    void cgnatELinkLocalNaoContamComoPublico() {
        assertEquals("8.8.8.8", geo.clienteIpEfetivo("100.64.1.1, 8.8.8.8", null, null));
        assertEquals("8.8.8.8", geo.clienteIpEfetivo("169.254.83.107, 8.8.8.8", null, null));
    }

    @Test
    void forwardedForTemTetoDeEntradas() {
        String privados = String.join(", ", Collections.nCopies(GeoService.MAX_ENTRADAS_FORWARDED, "10.0.0.1"));
        assertEquals("10.0.0.1", geo.clienteIpEfetivo(privados + ", 8.8.8.8", null, null),
                "entrada além do teto não é lida");
        assertEquals("8.8.8.8", geo.clienteIpEfetivo(privados + ", 8.8.8.8", null, "8.8.8.8"),
                "a conexão continua valendo depois do teto");
    }
}
