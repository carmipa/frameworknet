package org.framework.net.calculadora;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.calculadora.application.AgregacaoService;
import org.framework.net.calculadora.domain.ResultadoAgregacao;
import org.framework.net.calculadora.domain.ResultadoFaixa;
import org.framework.net.calculadora.domain.ResultadoRelacao;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AgregacaoServiceTest {

    @Inject
    AgregacaoService agregacaoService;

    @Test
    void quatroBarra24ContiguasViramUmBarra22Exato() {
        ResultadoAgregacao resultado = agregacaoService.sumarizar(
                "192.168.0.0/24\n192.168.1.0/24\n192.168.2.0/24\n192.168.3.0/24");

        assertEquals("192.168.0.0", resultado.resumo());
        assertEquals(22, resultado.prefixoResumo());
        assertEquals("255.255.252.0", resultado.mascaraResumo());
        assertEquals("0.0.3.255", resultado.wildcardResumo());
        assertEquals(1024L, resultado.totalEnderecosResumo());
        assertEquals(1024L, resultado.enderecosCobertos());
        assertEquals(0L, resultado.enderecosExtras());
        assertTrue(resultado.exata());
    }

    @Test
    void redesEsparsasGeramResumoComEspacoExtraDeclarado() {
        ResultadoAgregacao resultado = agregacaoService.sumarizar("10.0.0.0/24, 10.0.7.0/24");

        assertEquals(21, resultado.prefixoResumo());
        assertEquals("10.0.0.0", resultado.resumo());
        assertEquals(2048L, resultado.totalEnderecosResumo());
        assertEquals(512L, resultado.enderecosCobertos());
        assertEquals(1536L, resultado.enderecosExtras());
        assertFalse(resultado.exata());
    }

    @Test
    void sobreposicaoEntreEntradasNaoEhContadaDuasVezes() {
        ResultadoAgregacao resultado = agregacaoService.sumarizar("10.0.0.0/24\n10.0.0.0/25");

        assertEquals(24, resultado.prefixoResumo());
        assertEquals(256L, resultado.enderecosCobertos());
        assertEquals(0L, resultado.enderecosExtras());
    }

    @Test
    void redeUnicaResumeNelaMesma() {
        ResultadoAgregacao resultado = agregacaoService.sumarizar("172.16.8.0/22");
        assertEquals(22, resultado.prefixoResumo());
        assertEquals("172.16.8.0", resultado.resumo());
        assertTrue(resultado.exata());
    }

    @Test
    void comandosDeResumoCobremOspfEigrpRotaEAcl() {
        String cli = String.join("\n", agregacaoService.sumarizar(
                "192.168.0.0/24\n192.168.1.0/24").comandosCisco());

        assertTrue(cli.contains("area 0 range 192.168.0.0 255.255.254.0"));
        assertTrue(cli.contains("ip summary-address eigrp 100 192.168.0.0 255.255.254.0"));
        assertTrue(cli.contains("ip route 192.168.0.0 255.255.254.0"));
        assertTrue(cli.contains("access-list 10 permit 192.168.0.0 0.0.1.255"));
    }

    @Test
    void listaVaziaEhRecusada() {
        assertThrows(CalculadoraException.class, () -> agregacaoService.sumarizar("   "));
    }

    @Test
    void blocoMaiorContendoOMenorEhDetectado() {
        ResultadoRelacao relacao = agregacaoService.comparar("10.0.0.0/16", "10.0.5.0/24");

        assertEquals("A_CONTEM_B", relacao.tipo());
        assertTrue(relacao.conflito());
        assertEquals(256L, relacao.enderecosCompartilhados());
    }

    @Test
    void blocoMenorDentroDoMaiorEhDetectadoNaOrdemInversa() {
        ResultadoRelacao relacao = agregacaoService.comparar("10.0.5.0/24", "10.0.0.0/16");
        assertEquals("B_CONTEM_A", relacao.tipo());
        assertTrue(relacao.conflito());
    }

    @Test
    void blocosIguaisSaoDetectados() {
        ResultadoRelacao relacao = agregacaoService.comparar("10.0.0.0/24", "10.0.0.0/24");
        assertEquals("IGUAIS", relacao.tipo());
        assertTrue(relacao.conflito());
    }

    @Test
    void blocosDisjuntosNaoConflitam() {
        ResultadoRelacao relacao = agregacaoService.comparar("10.0.0.0/24", "10.0.1.0/24");

        assertEquals("DISJUNTOS", relacao.tipo());
        assertFalse(relacao.conflito());
        assertEquals(0L, relacao.enderecosCompartilhados());
    }

    @Test
    void faixaViraListaMinimaDeBlocosCidr() {
        ResultadoFaixa resultado = agregacaoService.faixaParaCidr("10.0.0.5", "10.0.3.200");

        assertEquals(964L, resultado.totalEnderecos());
        assertFalse(resultado.truncado());

        long somaBlocos = resultado.blocos().stream().mapToLong(bloco -> bloco.totalIps()).sum();
        assertEquals(resultado.totalEnderecos(), somaBlocos);

        assertEquals("10.0.0.5/32", resultado.blocos().get(0).cidr());
        assertEquals("10.0.3.200/32", resultado.blocos().get(resultado.blocos().size() - 1).cidr());
    }

    @Test
    void faixaAlinhadaViraUmBlocoUnico() {
        ResultadoFaixa resultado = agregacaoService.faixaParaCidr("192.168.1.0", "192.168.1.255");

        assertEquals(1, resultado.totalBlocos());
        assertEquals("192.168.1.0/24", resultado.blocos().get(0).cidr());
        assertEquals(256L, resultado.totalEnderecos());
    }

    @Test
    void faixaDeUmEnderecoViraBarra32() {
        ResultadoFaixa resultado = agregacaoService.faixaParaCidr("8.8.8.8", "8.8.8.8");
        assertEquals(1, resultado.totalBlocos());
        assertEquals("8.8.8.8/32", resultado.blocos().get(0).cidr());
    }

    @Test
    void espacoIpv4CompletoNaoEstouraInteiro() {
        ResultadoFaixa resultado = agregacaoService.faixaParaCidr("0.0.0.0", "255.255.255.255");

        assertEquals(4294967296L, resultado.totalEnderecos());
        assertEquals(1, resultado.totalBlocos());
        assertEquals("0.0.0.0/0", resultado.blocos().get(0).cidr());
    }

    @Test
    void faixaInvertidaEhRecusada() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> agregacaoService.faixaParaCidr("10.0.3.200", "10.0.0.5"));
        assertTrue(erro.getMessage().contains("Inverta os campos"));
    }
}
