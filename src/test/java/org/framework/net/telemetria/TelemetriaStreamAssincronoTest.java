package org.framework.net.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.framework.net.telemetria.infrastructure.TelemetriaStreamRedis;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Redis lento não segura a requisição: a publicação no Stream sai da thread de quem registra (OPS-17). */
class TelemetriaStreamAssincronoTest {

    @Test
    void registrarNaoEsperaOStream() throws Exception {
        CountDownLatch publicados = new CountDownLatch(3);
        TelemetriaStore store = new TelemetriaStore(new ObjectMapper());
        store.enabled = true;
        store.maxEvents = 50;
        store.baseDir = Files.createTempDirectory("tele-stream").toString();
        store.stream = new TelemetriaStreamRedis() {
            @Override
            public boolean publicar(TelemetriaEvent evento) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                publicados.countDown();
                return true;
            }
        };
        long inicio = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            store.registrar(new TelemetriaEvent("e" + i, Instant.now(), "INFO", "web", "http_access", "ok",
                    null, null, "GET", "/", 200, 1L, "m", Map.of()));
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(ms < 1_000, "3 registros com Stream de 2 s cada levaram " + ms + " ms na thread da requisição");
        assertTrue(publicados.await(10, TimeUnit.SECONDS), "os eventos chegam ao Stream depois, em segundo plano");
    }
}
