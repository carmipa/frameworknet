package org.framework.net.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.framework.net.telemetria.infrastructure.TelemetriaStreamRedis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Janela de leitura do painel (auditoria OPS-05): o Stream guarda dezenas de milhares de eventos e o
 * painel lia só os 500 da memória. Stream falso devolve exatamente o que pedem, até o que tem.
 */
class TelemetriaJanelaLeituraTest {

    private static List<TelemetriaEvent> eventos(int n) {
        List<TelemetriaEvent> lista = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lista.add(new TelemetriaEvent("e" + i, Instant.now(), "INFO", "web", "http_access", "ok",
                    null, null, "GET", "/", 200, 1L, "m", Map.of()));
        }
        return lista;
    }

    @Test
    void lePeloStreamAteAJanelaConfiguradaNaoAteAMemoria() {
        int[] pedido = {0};
        List<TelemetriaEvent> guardados = eventos(1200);
        TelemetriaStore store = new TelemetriaStore(new ObjectMapper());
        store.maxEvents = 500;
        store.janelaLeitura = 5000;
        store.stream = new TelemetriaStreamRedis() {
            @Override
            public List<TelemetriaEvent> ultimos(int limite) {
                pedido[0] = limite;
                return guardados.subList(0, Math.min(limite, guardados.size()));
            }
        };
        assertEquals(1200, store.snapshotEventos().size(), "os 1200 guardados, não só os 500 da memória");
        assertEquals(5000, pedido[0]);
        assertEquals(5000, store.janelaDeLeitura());
    }
}
