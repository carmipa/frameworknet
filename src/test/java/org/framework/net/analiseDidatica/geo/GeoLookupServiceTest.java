package org.framework.net.analiseDidatica.geo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.infrastructure.geo.GeoLookupService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GeoLookupServiceTest {

    @Inject
    GeoLookupService geoLookupService;

    @Test
    void ipPrivadoMarcaReservado() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("192.168.1.10");
        assertEquals("private_or_local", out.get("motivo"));
        assertTrue(Boolean.TRUE.equals(out.get("reservado")));
        assertEquals("🏠 Rede local", out.get("risco_badge"));
        assertNotNull(out.get("reservado_motivo"));
    }

    /**
     * F05: nome de host NÃO é resolvido. "localhost" resolve para 127.0.0.1 — antes respondia
     * private_or_local e servia de oráculo de nomes de container na rede Docker (existe × não existe)
     * e prendia thread em DNS lento. Fronteira (A1): o LITERAL 127.0.0.1 continua private_or_local.
     */
    @Test
    void nomeDeHostNaoEhResolvido() {
        assertEquals("invalid", geoLookupService.lookupRegiaoGeografica("localhost").get("motivo"));
        assertEquals("private_or_local", geoLookupService.lookupRegiaoGeografica("127.0.0.1").get("motivo"));
    }

    /** CGNAT (RFC 6598) não tem geolocalização pública: não vai para a API externa. */
    @Test
    void cgnatEhTratadoComoNaoPublico() {
        assertEquals("private_or_local", geoLookupService.lookupRegiaoGeografica("100.64.0.1").get("motivo"));
    }

    /**
     * BACK-08: ULA (RFC 4193) e documentação (RFC 5737, RFC 3849) não são roteáveis — não vão ao provedor e não
     * viram "tente novamente". Fronteira: 192.0.43.7 (mesmo início, fora do /24) não é marcado reservado.
     */
    @Test
    void ulaEDocumentacaoSaoReservados() {
        for (String ip : new String[]{"fd00::1", "192.0.2.1", "198.51.100.9", "2001:db8::1"}) {
            assertEquals("private_or_local", geoLookupService.lookupRegiaoGeografica(ip).get("motivo"), ip);
        }
        org.junit.jupiter.api.Assertions.assertNotEquals("private_or_local",
                geoLookupService.lookupRegiaoGeografica("192.0.43.7").get("motivo"));
    }

    @Test
    void ipInvalidoRetornaErro() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("nao-e-ip");
        assertEquals("invalid", out.get("motivo"));
        assertFalse(Boolean.TRUE.equals(out.get("ok")));
        assertNotNull(out.get("erro"));
    }

    @Test
    void ipPublicoRetornaCamposEnriquecidos() {
        Map<String, Object> out = geoLookupService.lookupRegiaoGeografica("8.8.8.8");
        // F25: sem .mmdb local o lookup vai ao ip-api (45 req/min, internet). Falha DE REDE/COTA é
        // estado "não verificado" (ignorado), não reprovação por causa ambiental (A2). Se o serviço
        // respondeu, o enriquecimento tem de estar completo — isso continua reprovando.
        String motivo = String.valueOf(out.get("motivo"));
        boolean falhaDeLogica = java.util.Set.of("private_or_local", "invalid", "empty").contains(motivo);
        org.junit.jupiter.api.Assumptions.assumeFalse(!Boolean.TRUE.equals(out.get("ok")) && !falhaDeLogica,
                "ip-api indisponível/limitado neste ambiente (motivo=" + motivo + ") — não verificado");
        assertTrue(Boolean.TRUE.equals(out.get("ok")), "resposta sem ok: " + out);
        assertNotNull(out.get("pais"));
        assertNotNull(out.get("pais_codigo"));
        assertNotNull(out.get("risco_badge"));
        assertNotNull(out.get("proxy_flag"));
    }

    @Test
    void normalizarIpRejeitaHostname() {
        Optional<String> norm = geoLookupService.normalizarIpDigitado("example.com");
        assertTrue(norm.isEmpty());
    }

    @Test
    void normalizarIpAceitaIpv4() {
        Optional<String> norm = geoLookupService.normalizarIpDigitado("8.8.8.8");
        assertTrue(norm.isPresent());
        assertEquals("8.8.8.8", norm.get());
    }
}
