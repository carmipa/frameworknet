package org.framework.net.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O compose de desenvolvimento diz espelhar a produção (auditoria OPS-09): mesmo comando do Redis, teto de
 * memória e nenhum healthcheck próprio batendo em "/" (que gerava evento de telemetria a cada 15 s).
 */
class ComposeDevEspelhaProducaoGuardTest {

    private static String comandoRedis(List<String> linhas) {
        return linhas.stream().map(String::strip).filter(l -> l.startsWith("command: redis-server"))
                .findFirst().orElse("(ausente)");
    }

    @Test
    void devEspelhaProducao() throws IOException {
        // Dentro do build da imagem os compose não estão no contexto (o Dockerfile copia só src e
        // scripts/erro-proxy): lá a guarda não tem alvo e se ignora — estado 2, nunca aprovação.
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(Path.of("docker-compose.dev.yml"))
                && Files.exists(Path.of("docker-compose.yml")), "compose fora do contexto: guarda sem alvo");
        List<String> dev = Files.readAllLines(Path.of("docker-compose.dev.yml"), StandardCharsets.UTF_8);
        List<String> prod = Files.readAllLines(Path.of("docker-compose.yml"), StandardCharsets.UTF_8);
        assertTrue(dev.size() > 20 && prod.size() > 20, "instrumento cego: compose não lido");
        assertEquals(comandoRedis(prod), comandoRedis(dev), "Redis do dev diverge da produção");
        assertEquals(prod.stream().filter(l -> l.strip().startsWith("mem_limit:")).count(),
                dev.stream().filter(l -> l.strip().startsWith("mem_limit:")).count(), "teto de memória ausente no dev");
        assertFalse(dev.stream().anyMatch(l -> l.contains("127.0.0.1:8080/ ")), "healthcheck do dev bate em / (gera telemetria)");
        assertFalse(dev.stream().anyMatch(l -> l.strip().equals("healthcheck:")), "healthcheck próprio no dev diverge do Dockerfile");
    }
}
