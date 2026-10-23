package org.framework.net.telemetria.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F08: o caminho da requisição ia CRU para o dataset público. Qualquer visitante que pedisse
 * /protocolos/8.8.8.8 (ou /portas/a@b.co) travava a publicação — a auditoria final acusava "IP
 * público residual" — e o texto que não casasse com as regex iria publicado como veio. A rota
 * publicada é generalizada: segmento que não é palavra estática vira {param}.
 * Fronteira (A1): slugs legítimos do catálogo (bgp, estrela-estendida) continuam legíveis.
 */
class DatasetRotaPublicavelTest {

    @Test
    void segmentoLivreViraParametro() {
        assertEquals("/protocolos/{param}", DatasetPublicavelService.rotaPublicavel("/protocolos/8.8.8.8"));
        assertEquals("/portas/{param}", DatasetPublicavelService.rotaPublicavel("/portas/a@b.co"));
        assertEquals("/protocolos/{param}",
                DatasetPublicavelService.rotaPublicavel("/protocolos/3f2b9c1e-7d4a-4b8e-9a61-0c2d5e6f7a8b"));
        assertEquals("/{param}", DatasetPublicavelService.rotaPublicavel("/" + "x".repeat(80)));
        // UUID que começa por letra também não passa como "palavra".
        assertEquals("/protocolos/{param}",
                DatasetPublicavelService.rotaPublicavel("/protocolos/a3f2b9c1-7d4a-4b8e-9a61-0c2d5e6f7a8b"));
    }

    @Test
    void rotaEstaticaEhPreservada() {
        assertEquals("/protocolos/bgp", DatasetPublicavelService.rotaPublicavel("/protocolos/bgp"));
        assertEquals("/resolucao-problemas", DatasetPublicavelService.rotaPublicavel("/resolucao-problemas"));
        assertEquals("/ipv6/api/projetar", DatasetPublicavelService.rotaPublicavel("/ipv6/api/projetar"));
        assertEquals("/", DatasetPublicavelService.rotaPublicavel("/"));
        assertEquals("", DatasetPublicavelService.rotaPublicavel(null));
    }
}
