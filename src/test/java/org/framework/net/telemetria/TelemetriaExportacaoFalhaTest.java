package org.framework.net.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exportação não entrega retrato velho como atual (auditoria OPS-12): com a pasta de logs impossível de
 * criar (um ARQUIVO no lugar dela), quem entrega o arquivo recebe a falha; o flush de fundo segue
 * tolerante, como antes.
 */
class TelemetriaExportacaoFalhaTest {

    @Test
    void falhaDeGravacaoChegaAQuemEntregaOArquivo() throws IOException {
        Path ocupado = Files.createTempFile("telemetria-ocupada", ".txt");
        TelemetriaStore store = new TelemetriaStore(new ObjectMapper());
        store.enabled = true;
        store.maxEvents = 10;
        store.baseDir = ocupado.resolve("logs").toString();
        assertThrows(IOException.class, store::flushOuFalhar);
        assertDoesNotThrow(store::flush, "o flush de fundo continua sem derrubar ninguém");
    }
}
