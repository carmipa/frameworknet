package org.framework.net.analiseDidatica.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O que o histórico guarda como entrada da consulta (revisão de boa-fé do Lote 1 da auditoria). No modo Domínio a
 * pessoa pode colar uma URL inteira — com token de acesso e longa a ponto de ser cortada no teto do histórico, num
 * ponto em que a repetição já não funciona. Guarda-se o host, que é o que a consulta usou.
 */
class EntradaParaHistoricoTest {

    @Test
    void modoDominioGuardaSoOHost() {
        assertEquals("www.example.org",
                HomeAnaliseService.entradaParaHistorico("dominio", "https://www.example.org/x?token=segredo"));
        assertEquals("example.org", HomeAnaliseService.entradaParaHistorico("dominio", ".example.org."));
    }

    /**
     * Revisão adversarial: {@code modo} vai ao dataset público como vocabulário fixo, mas a aba ativa vem do formulário.
     * Só os modos da tela passam; IP e texto livre viram "invalido".
     */
    @Test
    void modoDaTelemetriaSoAceitaOsModosDaTela() {
        assertEquals("dominio", HomeAnaliseService.modoParaTelemetria(" Dominio "));
        assertEquals("invalido", HomeAnaliseService.modoParaTelemetria("8.8.8.8"));
        assertEquals("invalido", HomeAnaliseService.modoParaTelemetria(null));
    }

    /** A1: nos outros modos a entrada vai como foi digitada — a barra do CIDR, por exemplo, é parte da consulta. */
    @Test
    void demaisModosGuardamOQueFoiDigitado() {
        assertEquals("10.0.0.1/24", HomeAnaliseService.entradaParaHistorico("cidr", "10.0.0.1/24"));
        assertEquals("", HomeAnaliseService.entradaParaHistorico("autoip", null));
    }
}
