package org.framework.net.protocolos;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.protocolos.application.ProtocoloItemExibicao;
import org.framework.net.protocolos.application.ProtocolosService;
import org.framework.net.protocolos.domain.ProtocoloItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProtocolosServiceTest {

    @Inject
    ProtocolosService protocolosService;

    @Test
    void catalogoDeveTer47Itens() {
        List<ProtocoloItemExibicao> itens = protocolosService.montarProtocolosCatalogoExibicao();
        assertEquals(47, itens.size());
    }

    @Test
    void filtrarPorOspf() {
        List<ProtocoloItem> filtrados = protocolosService.filtrarProtocolos("OSPF");
        assertFalse(filtrados.isEmpty());
        assertTrue(filtrados.stream().anyMatch(p -> p.nome().contains("OSPF")));
    }

    @Test
    void filtrarVazioRetornaTodos() {
        assertEquals(47, protocolosService.filtrarProtocolos("").size());
        assertEquals(47, protocolosService.filtrarProtocolos(null).size());
    }

    @Test
    void agruparPorCamada() {
        Map<String, List<ProtocoloItemExibicao>> grupos = protocolosService.agruparPorCamada();
        assertFalse(grupos.isEmpty());
        int total = grupos.values().stream().mapToInt(List::size).sum();
        assertEquals(47, total);
    }

    @Test
    void troubleshootingRoteamentoContemOspfOuEigrp() {
        List<ProtocoloItemExibicao> roteamento = protocolosService.montarTroubleshootingRoteamento();
        assertFalse(roteamento.isEmpty());
        assertTrue(roteamento.stream().anyMatch(p ->
                p.nome().contains("OSPF") || p.nome().contains("EIGRP") || p.nome().contains("BGP")));
    }
}
