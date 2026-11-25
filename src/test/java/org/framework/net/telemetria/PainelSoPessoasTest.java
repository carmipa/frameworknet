package org.framework.net.telemetria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regra do filtro "só pessoas" do painel (proposta D18), sem servidor.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o painel mede carga e soma robôs e pessoas; o filtro mostra o mesmo painel só com
 * gente, para decidir produto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> sem o filtro entra tudo; com o filtro o acesso HTTP só entra marcado
 * {@code humano}; acesso sem a marca fica fora (não se sabe se foi pessoa); evento que não é acesso HTTP entra sempre.
 * O esperado vem do requisito (A3), caso a caso.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> o teste nomeia o caso que entrou ou saiu errado.</p>
 */
@DisplayName("Telemetria: regra do painel só pessoas")
class PainelSoPessoasTest {

    private static TelemetriaEvent evento(String nome, String clienteTipo) {
        Map<String, Object> campos = new HashMap<>();
        if (clienteTipo != null) {
            campos.put("clienteTipo", clienteTipo);
        }
        return new TelemetriaEvent("id", Instant.now(), "INFO", "web", nome, "ok", null, null, "GET", "/portas",
                "http_access".equals(nome) ? 200 : null, 5L, "m", campos);
    }

    @Test
    @DisplayName("sem o filtro entra tudo, inclusive robô")
    void semFiltroEntraTudo() {
        assertTrue(TelemetriaDashboardService.entraNoPainel(evento("http_access", "bot"), false));
        assertTrue(TelemetriaDashboardService.entraNoPainel(evento("http_access", null), false));
        assertTrue(TelemetriaDashboardService.entraNoPainel(evento("dns_lookup", null), false));
    }

    @Test
    @DisplayName("com o filtro: pessoa entra, robô e acesso sem marca saem, evento que não é acesso fica")
    void comFiltroSoPessoas() {
        assertTrue(TelemetriaDashboardService.entraNoPainel(evento("http_access", "humano"), true), "pessoa saiu");
        assertFalse(TelemetriaDashboardService.entraNoPainel(evento("http_access", "bot"), true), "robô entrou");
        assertFalse(TelemetriaDashboardService.entraNoPainel(evento("http_access", null), true),
                "acesso sem marca de cliente entrou como pessoa");
        assertFalse(TelemetriaDashboardService.entraNoPainel(evento("http_access", "Humano "), true),
                "marca fora do vocabulário (\"Humano \") entrou como pessoa");
        assertTrue(TelemetriaDashboardService.entraNoPainel(evento("dns_lookup", "bot"), true),
                "evento que não é acesso HTTP saiu do painel");
        assertFalse(TelemetriaDashboardService.entraNoPainel(null, true), "evento nulo entrou");
        TelemetriaEvent semCampos = new TelemetriaEvent("id", Instant.now(), "INFO", "web", "http_access", "ok",
                null, null, "GET", "/", 200, 1L, "m", null);
        assertFalse(TelemetriaDashboardService.entraNoPainel(semCampos, true), "acesso sem campos entrou");
    }
}
