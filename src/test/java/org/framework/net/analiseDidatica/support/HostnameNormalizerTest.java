package org.framework.net.analiseDidatica.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * O normalizador do modo "Domínio para IP" (auditoria SEC-01): os pontos das pontas saem por laço linear. A forma
 * anterior, {@code replaceAll("^\\.+|\\.+$", "")}, retrocedia a cada ponto do meio — 80 mil pontos custavam 11,9 s
 * e 250 mil, 125,6 s de CPU por pedido. Gabaritos: o que a pessoa digitaria e o nome que o DNS espera.
 */
class HostnameNormalizerTest {

    /** A1: o mesmo sinal da suspeita (pontos) em uso legítimo — ponto final de FQDN e ponto colado por engano. */
    @Test
    void pontosDasPontasSaemEOsDoMeioFicam() {
        assertEquals("google.com", HostnameNormalizer.normalizar(".google.com."));
        assertEquals("a.b", HostnameNormalizer.normalizar("...a.b..."));
        assertEquals("Example.COM", HostnameNormalizer.normalizar("  .Example.COM.  "));
        assertEquals("", HostnameNormalizer.normalizar("...."));
        assertEquals("", HostnameNormalizer.normalizar(null));
    }

    @Test
    void urlViraSoONomeDoHost() {
        assertEquals("www.example.org", HostnameNormalizer.normalizar("https://www.example.org/x?y=1"));
        assertEquals("example.org", HostnameNormalizer.normalizar("example.org/caminho"));
        assertEquals("example.org", HostnameNormalizer.normalizar("http://example.org.:8080/"));
    }

    /**
     * A2: a entrada que media 80 s na forma antiga (200 mil pontos no meio) termina bem abaixo de 2 s, e os pontos do
     * meio continuam lá — nada é removido além das pontas.
     */
    @Test
    void custoLinearComMilharesDePontosNoMeio() {
        String entrada = "a" + ".".repeat(200_000) + "a";
        String saida = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> HostnameNormalizer.normalizar(entrada));
        assertEquals(entrada, saida);
    }
}
