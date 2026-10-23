package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Disjuntor do Redis e cache local com validade (auditoria OPS-17), com relógio controlado. */
class DisjuntorECacheLocalTest {

    @Test
    void disjuntorAbreNaFalhaEVoltaDepoisDoIntervalo() {
        long[] agora = {1_000L};
        Disjuntor d = new Disjuntor(() -> agora[0]);
        assertTrue(d.permite());
        assertTrue(d.falhou(), "primeira falha abre (troca de estado: loga)");
        assertFalse(d.falhou(), "falha com o disjuntor já aberto não é troca (não loga de novo)");
        assertFalse(d.permite(), "aberto: não chama o Redis");
        agora[0] += Disjuntor.ABERTO_NANOS - 1;
        assertFalse(d.permite());
        agora[0] += 1;
        assertTrue(d.permite(), "depois do intervalo, uma tentativa");
        assertTrue(d.funcionou(), "sucesso fecha (troca de estado: loga a volta)");
        assertFalse(d.funcionou());
    }

    @Test
    void cacheLocalVenceNoTtlERespeitaOTeto() {
        long[] agora = {0L};
        CacheLocal<String> c = new CacheLocal<>(3, Duration.ofSeconds(10), () -> agora[0]);
        c.guardar("a", "1");
        assertEquals("1", c.obter("a").orElseThrow());
        agora[0] += Duration.ofSeconds(10).toNanos() - 1;
        assertTrue(c.obter("a").isPresent(), "um nanossegundo antes do TTL ainda vale");
        agora[0] += 1;
        assertTrue(c.obter("a").isEmpty(), "no TTL vence");
        assertEquals(0, c.tamanho(), "vencido sai do mapa");
        c.guardar("x", "1");
        c.guardar("y", "2");
        c.guardar("z", "3");
        c.guardar("w", "4");
        assertTrue(c.tamanho() <= 3, "teto de entradas");
        assertEquals("4", c.obter("w").orElseThrow());
    }
}
