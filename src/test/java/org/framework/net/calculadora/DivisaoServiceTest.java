package org.framework.net.calculadora;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.calculadora.application.DivisaoService;
import org.framework.net.calculadora.domain.BlocoIpv4;
import org.framework.net.calculadora.domain.LinhaCapacidade;
import org.framework.net.calculadora.domain.PlanoDivisao;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DivisaoServiceTest {

    @Inject
    DivisaoService divisaoService;

    @Test
    void barra21EmBarra24DaOitoSubredes() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.0.0/21", "", "24");

        assertEquals(8L, plano.totalSubredes());
        assertEquals(2048L, plano.totalIpsBase());
        assertEquals(256L, plano.ipsPorSubrede());
        assertEquals(254L, plano.hostsUteisPorSubrede());
        assertEquals(3L, plano.bitsEmprestados());
        assertEquals(8, plano.blocos().size());
        assertFalse(plano.truncado());

        assertEquals("192.168.0.0/24", plano.blocos().get(0).cidr());
        assertEquals("192.168.7.0/24", plano.blocos().get(7).cidr());
        assertEquals("192.168.0.1", plano.blocos().get(0).primeiroHost());
        assertEquals("192.168.0.254", plano.blocos().get(0).ultimoHost());
        assertEquals("192.168.0.255", plano.blocos().get(0).broadcast());
        assertEquals("0.0.0.255", plano.blocos().get(0).wildcard());
    }

    @Test
    void matrizDeCapacidadeCobreDoPrefixoBaseAte32() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.0.0/21", "", "24");
        List<LinhaCapacidade> capacidade = plano.capacidade();

        assertEquals(12, capacidade.size());
        assertEquals(21, capacidade.get(0).prefixoAlvo());
        assertEquals(1L, capacidade.get(0).subredes());
        assertEquals(32, capacidade.get(11).prefixoAlvo());
        assertEquals(2048L, capacidade.get(11).subredes());

        LinhaCapacidade barra24 = capacidade.stream()
                .filter(l -> l.prefixoAlvo() == 24).findFirst().orElseThrow();
        assertEquals(8L, barra24.subredes());
        assertTrue(barra24.destaque());

        LinhaCapacidade barra30 = capacidade.stream()
                .filter(l -> l.prefixoAlvo() == 30).findFirst().orElseThrow();
        assertEquals(512L, barra30.subredes());
        assertEquals(2L, barra30.hostsUteis());
    }

    @Test
    void aceitaMascaraPontuadaNoLugarDoPrefixo() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.0.0 255.255.248.0", "", "24");
        assertEquals(21, plano.prefixoBase());
        assertEquals(8L, plano.totalSubredes());
    }

    @Test
    void alinhaEnderecoDeHostAoEnderecoDeRede() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.3.77/21", "", "24");
        assertEquals("192.168.0.0", plano.blocoBase());
        assertTrue(plano.explicacao().contains("é um host dentro da rede"));
    }

    @Test
    void seisSubredesViramOitoComSobraDeclarada() {
        PlanoDivisao plano = divisaoService.dividirPorQuantidade("192.168.0.0/21", "", "6");

        assertEquals(24, plano.prefixoAlvo());
        assertEquals(8L, plano.totalSubredes());
        assertTrue(plano.explicacao().contains("sobram 2 sub-redes"));
    }

    @Test
    void quinhentosHostsExigemBarra23() {
        PlanoDivisao plano = divisaoService.dividirPorHosts("10.0.0.0/16", "", "500");

        assertEquals(23, plano.prefixoAlvo());
        assertEquals(510L, plano.hostsUteisPorSubrede());
        assertEquals(128L, plano.totalSubredes());
    }

    @Test
    void duzentosECinquentaEQuatroHostsExigemBarra24() {
        PlanoDivisao plano = divisaoService.dividirPorHosts("10.0.0.0/16", "", "254");
        assertEquals(24, plano.prefixoAlvo());
        assertEquals(254L, plano.hostsUteisPorSubrede());
    }

    /**
     * Auditoria CALC-26 — gabarito (A3): RFC 3021 (/31 tem 2 hosts) e a própria tabela de hosts úteis da
     * calculadora. Antes 2 hosts iam para /30 e a base /31 era recusada "porque comporta 2 hosts".
     * Fronteira (A1): 3 hosts continuam exigindo /29 (o /30 só tem 2 úteis).
     */
    @Test
    void poucosHostsUsamBarra31EBarra32SemSeContradizer() {
        assertEquals(31, divisaoService.dividirPorHosts("10.0.0.0/24", "", "2").prefixoAlvo());
        assertEquals(32, divisaoService.dividirPorHosts("10.0.0.0/24", "", "1").prefixoAlvo());
        assertEquals(31, divisaoService.dividirPorHosts("10.0.0.0/31", "", "2").prefixoAlvo());
        assertEquals(29, divisaoService.dividirPorHosts("10.0.0.0/24", "", "3").prefixoAlvo());
    }

    /** Auditoria CALC-33: zero à esquerda (octal no inet_aton) e dígito não ASCII são recusados; "10.0.0.0" passa. */
    @Test
    void blocoComZeroAEsquerdaOuDigitoNaoAsciiERecusado() {
        assertThrows(CalculadoraException.class, () -> divisaoService.dividirPorPrefixo("010.0.0.0/8", "", "16"));
        assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorPrefixo("١٠.0.0.0/8", "", "16"));
        assertEquals(16, divisaoService.dividirPorPrefixo("10.0.0.0/8", "", "16").prefixoAlvo());
    }

    @Test
    void listagemGrandeEhTruncadaMasTotalRealPermanece() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("10.0.0.0/8", "", "30");

        assertEquals(4194304L, plano.totalSubredes());
        assertTrue(plano.truncado());
        assertEquals(512, plano.exibidos());
        assertEquals(512, plano.blocos().size());
    }

    @Test
    void barra31TemDoisEnderecosUtilizaveisSemBroadcast() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.0.0/30", "", "31");
        BlocoIpv4 primeiro = plano.blocos().get(0);

        assertEquals(2L, primeiro.hostsUteis());
        assertEquals("192.168.0.0", primeiro.primeiroHost());
        assertEquals("192.168.0.1", primeiro.ultimoHost());
        assertTrue(primeiro.broadcast().contains("N/A"));
    }

    @Test
    void barra32TemUmUnicoEndereco() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("192.168.0.0/31", "", "32");
        BlocoIpv4 primeiro = plano.blocos().get(0);

        assertEquals(1L, primeiro.hostsUteis());
        assertEquals("192.168.0.0", primeiro.primeiroHost());
        assertEquals("192.168.0.0", primeiro.ultimoHost());
    }

    @Test
    void blocoBarraZeroNaoEstouraInteiro() {
        PlanoDivisao plano = divisaoService.dividirPorPrefixo("0.0.0.0/0", "", "1");

        assertEquals(4294967296L, plano.totalIpsBase());
        assertEquals(2L, plano.totalSubredes());
        assertEquals(2147483648L, plano.ipsPorSubrede());
    }

    @Test
    void prefixoAlvoMenorQueOBaseEhRecusado() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorPrefixo("192.168.0.0/24", "", "16"));
        assertTrue(erro.getMessage().contains("maior que o bloco base"));
    }

    @Test
    void prefixoForaDaFaixaEhRecusado() {
        assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorPrefixo("192.168.0.0/21", "", "33"));
    }

    @Test
    void octetoForaDaFaixaEhRecusado() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorPrefixo("192.300.0.0/21", "", "24"));
        assertTrue(erro.getMessage().contains("0 a 255"));
    }

    @Test
    void mascaraNaoContiguaEhRecusada() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorPrefixo("192.168.0.0 255.0.255.0", "", "24"));
        assertTrue(erro.getMessage().contains("não é contígua"));
    }

    @Test
    void campoDeCriterioEmBrancoDizOQueFazer() {
        // Regressão do erro real capturado na telemetria em 2026-08-03:
        // "dividir_por_quantidade status=error ... Quantidade de sub-redes não foi informado."
        CalculadoraException quantidade = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorQuantidade("192.168.0.0/21", "", ""));
        assertTrue(quantidade.getMessage().contains("Digite quantas sub-redes"));
        assertTrue(quantidade.getMessage().contains("por prefixo alvo"));

        CalculadoraException hosts = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorHosts("192.168.0.0/21", "", ""));
        assertTrue(hosts.getMessage().contains("Digite quantos hosts"));
        assertTrue(hosts.getMessage().contains("por prefixo alvo"));
    }

    @Test
    void hostsDemaisParaOBlocoBaseSaoRecusados() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> divisaoService.dividirPorHosts("192.168.0.0/24", "", "5000"));
        assertTrue(erro.getMessage().contains("maior que o bloco base"));
    }
}
