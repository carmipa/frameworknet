package org.framework.net.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetriaStoreRotationTest {

    @TempDir
    Path tempDir;

    /**
     * Propósito de negócio: comprova que a trilha incremental permanece limitada sem recriar o OTLP por evento.
     * Invariantes do domínio: existem no máximo o JSONL ativo e uma geração anterior, ambos dentro do limite.
     * Comportamento em caso de falha: o teste falha se houver crescimento sem rotação ou materialização prematura.
     */
    @Test
    void rotacionaJsonlSemRegravarOtlpPorEvento() throws Exception {
        TelemetriaStore store = new TelemetriaStore(new ObjectMapper());
        store.baseDir = tempDir.toString();
        store.maxEvents = 50;
        store.jsonlMaxBytes = 1024;
        store.enabled = true;
        store.appName = "teste";
        store.appVersion = "1";

        for (int i = 0; i < 20; i++) {
            store.registrar(new TelemetriaEvent(
                    "id-" + i, Instant.now(), "INFO", "teste", "evento", "ok",
                    null, null, null, null, null, null, "x".repeat(300), Map.of()));
        }

        assertTrue(Files.exists(tempDir.resolve("framework-net-eventos.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("framework-net-eventos.jsonl.1")));
        assertTrue(Files.size(tempDir.resolve("framework-net-eventos.jsonl")) <= 1024);
        assertTrue(Files.size(tempDir.resolve("framework-net-eventos.jsonl.1")) <= 1024);
        assertFalse(Files.exists(tempDir.resolve("telemetria_compartilhada.json")));

        store.flush();
        assertTrue(Files.exists(tempDir.resolve("telemetria_compartilhada.json")));
    }
}
