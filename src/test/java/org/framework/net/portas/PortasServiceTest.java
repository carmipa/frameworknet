package org.framework.net.portas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.portas.application.PortaItemExibicao;
import org.framework.net.portas.application.PortasService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PortasServiceTest {

    @Inject
    PortasService portasService;

    @Test
    void catalogoDeveTer30Itens() {
        List<PortaItemExibicao> itens = portasService.montarPortasCatalogoExibicao();
        assertEquals(30, itens.size());
    }

    @Test
    void alternativaSeguraTelnetDeveSerSsh() {
        PortaItemExibicao telnet = portasService.montarPortasCatalogoExibicao().stream()
                .filter(p -> "Telnet".equalsIgnoreCase(p.servico()))
                .findFirst()
                .orElseThrow();
        assertEquals("SSH", telnet.alternativaSegura());
    }

    @Test
    void alternativaSeguraHttps() {
        PortaItemExibicao https = portasService.montarPortasCatalogoExibicao().stream()
                .filter(p -> "HTTPS".equalsIgnoreCase(p.servico()))
                .findFirst()
                .orElseThrow();
        assertTrue(https.alternativaSegura().toLowerCase().contains("tls"));
    }

    @Test
    void todosItensDevemTerPortaEServico() {
        List<PortaItemExibicao> itens = portasService.montarPortasCatalogoExibicao();
        assertFalse(itens.isEmpty());
        assertTrue(itens.stream().allMatch(p -> p.porta() != null && !p.porta().isBlank()));
        assertTrue(itens.stream().allMatch(p -> p.servico() != null && !p.servico().isBlank()));
    }
}
