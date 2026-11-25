package org.framework.net.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carga do arquivo canônico da telemetria no arranque (Lote 1, revisão operacional). A versão anterior gravava campos de
 * até 1 MB, e o processo que encerra grava a janela em memória: ler tudo no arranque seguinte estouraria a memória em
 * laço — o mesmo modo de falha do histórico.
 */
class TelemetriaCargaTetoTest {

    @TempDir
    Path tempDir;

    private TelemetriaStore novoStore() {
        TelemetriaStore store = new TelemetriaStore(new ObjectMapper());
        store.baseDir = tempDir.toString();
        store.maxEvents = 50;
        store.jsonlMaxBytes = 10_000_000;
        store.enabled = true;
        store.appName = "teste";
        store.appVersion = "1";
        return store;
    }

    /**
     * A2: arquivo acima do teto vai para a quarentena SEM ser lido (o conteúdo é JSON válido — se fosse lido, os
     * eventos voltariam). A1: o arquivo legítimo, gravado pela própria versão, volta com cada campo no teto de tamanho.
     */
    @Test
    void arquivoGrandeDemaisNaoELidoEOLegitimoVoltaNoTeto() throws Exception {
        TelemetriaStore gravador = novoStore();
        gravador.registrar(new TelemetriaEvent("id-1", Instant.now(), "INFO", "teste", "evento_carga", "ok",
                null, null, null, null, null, null, "m".repeat(5_000), Map.of("legado", "q".repeat(100_000), "n", 7)));
        gravador.flush();
        Path canonico = tempDir.resolve("telemetria_compartilhada.json");
        assertTrue(Files.exists(canonico));

        TelemetriaStore leitor = novoStore();
        leitor.onStart(null);
        TelemetriaEvent volta = leitor.snapshotEventos().stream()
                .filter(e -> "evento_carga".equals(e.evento())).findFirst().orElseThrow();
        assertEquals(TelemetriaLogger.MAX_VALOR_CAMPO + TelemetriaContext.MARCA_TRUNCADO.length(),
                String.valueOf(volta.fields().get("legado")).length(), "campo legado voltou sem o teto de tamanho");
        assertTrue(volta.message().length() <= TelemetriaLogger.MAX_MENSAGEM + TelemetriaContext.MARCA_TRUNCADO.length());

        String json = Files.readString(canonico);
        StringBuilder gigante = new StringBuilder(json);
        while (gigante.length() <= 4 * 1024 * 1024) {
            gigante.append(' ');
        }
        Files.writeString(canonico, gigante);
        TelemetriaStore depois = novoStore();
        depois.onStart(null);
        assertTrue(depois.snapshotEventos().stream().noneMatch(e -> "evento_carga".equals(e.evento())),
                "o arquivo acima do teto foi lido");
        assertFalse(Files.exists(canonico));
        try (var ls = Files.list(tempDir)) {
            assertTrue(ls.anyMatch(p -> p.getFileName().toString().startsWith("telemetria_compartilhada.json.grande-demais-")));
        }
        // Revisão operacional (2ª passada): a quarentena não fica só no log, que gira — vira evento.
        assertTrue(depois.snapshotEventos().stream().anyMatch(e -> "telemetry_load".equals(e.evento())
                && "quarentena".equals(e.fields().get("status"))), "a quarentena não registrou evento");
    }

    /**
     * Revisão (2ª passada): se a carga não conseguiu mover o arquivo grande demais, gravar a janela por cima dele apagaria
     * a evidência. Com a gravação suspensa, o canônico não é escrito. A2: sem o guardião, o flush o criaria.
     */
    @Test
    void gravacaoCanonicaSuspensaNaoEscreveOArquivo() throws Exception {
        TelemetriaStore store = novoStore();
        store.gravacaoCanonicaSuspensa = true;
        store.registrar(new TelemetriaEvent("id-s", Instant.now(), "INFO", "teste", "evento_suspenso", "ok",
                null, null, null, null, null, null, "x", Map.of()));
        store.flush();
        assertFalse(Files.exists(tempDir.resolve("telemetria_compartilhada.json")),
                "a gravação suspensa escreveu o arquivo canônico");
    }
}
