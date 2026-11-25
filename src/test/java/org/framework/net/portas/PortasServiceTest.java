package org.framework.net.portas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.portas.application.PortaItemExibicao;
import org.framework.net.portas.application.PortasService;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catálogo de portas exibido em /portas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a grade é consulta de estudo; porta comum que falta, faixa errada ou valor fora do
 * vocabulário dos filtros esconde a linha de quem procura.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> as 30 portas de antes e as 18 da proposta C26 estão na grade; a faixa de cada
 * linha é a da primeira porta pelo registro IANA (0-1023, 1024-49151, 49152-65535); o transporte está no vocabulário
 * do filtro; o selo e a cor combinam; os serviços novos têm alternativa própria, nunca a repetição da recomendação.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> cada teste nomeia a porta ou o serviço que divergiu.</p>
 */
@QuarkusTest
class PortasServiceTest {

    /** As 18 portas comuns que faltavam (relatório projeto-laboratorios, conferido por script em 25/11/2026). */
    private static final Set<String> PORTAS_C26 = Set.of("88", "161", "162", "179", "500", "4500", "514", "636", "853",
            "1194", "1812", "1883", "8883", "2049", "3478", "5060", "5353", "51820");

    private static final Map<String, String> COR_DO_SELO = Map.of(
            "🔴 Crítico", "danger", "🟡 Atenção", "warning", "🟢 Seguro", "success", "🔵 Didático", "info");

    @Inject
    PortasService portasService;

    @Test
    void catalogoTemAsPortasDeAntesEAsDaPropostaC26() {
        List<PortaItemExibicao> itens = portasService.montarPortasCatalogoExibicao();
        assertEquals(48, itens.size(), "30 de antes + 18 da C26");
        Set<String> portas = itens.stream().map(PortaItemExibicao::porta).collect(Collectors.toSet());
        assertEquals(48, portas.size(), "porta repetida na grade");
        assertTrue(portas.containsAll(PORTAS_C26), "faltam: " + PORTAS_C26.stream().filter(p -> !portas.contains(p))
                .toList());
    }

    @Test
    void faixaDaPortaBateComACategoria() {
        List<String> erradas = new ArrayList<>();
        for (PortaItemExibicao p : portasService.montarPortasCatalogoExibicao()) {
            int numero = Integer.parseInt(p.porta().split("/")[0]);
            String esperada = numero <= 1023 ? "Well-known (0-1023)"
                    : numero <= 49151 ? "Registered (1024-49151)" : "Dynamic/Private (49152-65535)";
            if (!esperada.equals(p.categoria())) {
                erradas.add(p.porta() + " em " + p.categoria() + ", devia ser " + esperada);
            }
        }
        assertTrue(erradas.isEmpty(), () -> String.join("\n", erradas));
    }

    @Test
    void transporteNoVocabularioDoFiltroESeloComACor() {
        List<String> erradas = new ArrayList<>();
        for (PortaItemExibicao p : portasService.montarPortasCatalogoExibicao()) {
            if (!Set.of("TCP", "UDP", "TCP/UDP").contains(p.protocoloTransporte())) {
                erradas.add(p.porta() + ": transporte " + p.protocoloTransporte() + " fora do filtro (TCP, UDP, TCP/UDP)");
            }
            if (!p.badgeColor().equals(COR_DO_SELO.get(p.badge()))) {
                erradas.add(p.porta() + ": selo " + p.badge() + " com cor " + p.badgeColor());
            }
        }
        assertTrue(erradas.isEmpty(), () -> String.join("\n", erradas));
    }

    @Test
    void portasNovasTemAlternativaPropriaEBgpLevaAoAprofundamento() {
        List<String> repetidas = new ArrayList<>();
        PortaItemExibicao bgp = null;
        for (PortaItemExibicao p : portasService.montarPortasCatalogoExibicao()) {
            if (PORTAS_C26.contains(p.porta()) && p.alternativaSegura().equals(p.recomendacao())) {
                repetidas.add(p.porta() + " " + p.servico());
            }
            if ("179".equals(p.porta())) {
                bgp = p;
            }
        }
        List<String> defeitos = new ArrayList<>();
        if (!repetidas.isEmpty()) {
            defeitos.add("alternativa repetindo a recomendação: " + repetidas);
        }
        if (bgp == null || !"/protocolos/bgp".equals(bgp.aprofundamentoRota())) {
            defeitos.add("a linha do BGP não leva ao aprofundamento de BGP");
        }
        assertTrue(defeitos.isEmpty(), () -> String.join("\n", defeitos));
    }

    /** Porta de serviço que tem aprofundamento registrado mostra o botão Aprofundar (o README promete o cruzamento). */
    @Test
    void portaDeServicoComAprofundamentoTemOLink() {
        List<String> semLink = new ArrayList<>();
        int comAprofundamento = 0;
        for (PortaItemExibicao p : portasService.montarPortasCatalogoExibicao()) {
            if (AprofundamentoProtocolo.porNomeDoCatalogo(p.servico()).isPresent()) {
                comAprofundamento++;
                if (p.aprofundamentoRota() == null) {
                    semLink.add(p.porta() + " " + p.servico());
                }
            }
        }
        assertTrue(comAprofundamento >= 8, "só " + comAprofundamento + " serviços com aprofundamento: leitor cego?");
        assertTrue(semLink.isEmpty(), "serviço com aprofundamento e sem o botão Aprofundar: " + semLink);
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
