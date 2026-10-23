package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaLoggerTest {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TelemetriaStore store;

    /**
     * Propósito de negócio: comprova o registro e a exportação canônica de um evento funcional.
     * Invariantes do domínio: a validação não depende do crescimento da contagem quando a janela já atingiu
     * o limite; o evento recém-criado deve estar presente entre os mais recentes.
     * Comportamento em caso de falha: o teste falha se o evento não entrar na janela ou se o OTLP não existir.
     */
    @Test
    void registraEventoEPersisteArquivoCompartilhado() throws Exception {
        telemetriaLogger.logEvent("info", "teste", "evento_unitario",
                Map.of("campo", "valor", "status", "ok"));
        store.flush();

        TelemetriaResumo resumo = store.gerarResumo(500);
        assertTrue(resumo.ultimosEventos().stream()
                .anyMatch(evento -> "teste".equals(evento.modulo())
                        && "evento_unitario".equals(evento.evento())));
        assertTrue(Files.exists(store.arquivoCompartilhado()));
        assertTrue(resumo.contagemPorModulo().containsKey("teste"));
    }
}
