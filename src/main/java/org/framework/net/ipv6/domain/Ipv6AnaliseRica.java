package org.framework.net.ipv6.domain;

import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DelegacaoInfo;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.SubredeIpv6;

import java.util.List;

/**
 * Modelo de EXIBIÇÃO rica da Análise IPv6 — o análogo IPv6 do resultado da Análise Didática IPv4.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> agrega, num único objeto, tudo o que a tela da Análise IPv6 mostra
 * (banner de tipo, grade de 128 bits célula-a-célula, aplicação do prefixo, capacidade, passo-a-passo,
 * linha do tempo, delegação, régua de /64, tabelas de referência/conversão, GRC/segurança, CLI
 * OSPFv3/EIGRP, termos, banner e resumo tipo prova), espelhando a página IPv4 na medida em que os
 * conceitos existem em IPv6.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> puro modelo de dados (records imutáveis); nada de broadcast nem
 * "hosts úteis" — conceitos que não existem em IPv6. Toda contagem é 2^(128−prefixo).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não valida entrada — é montado pelo
 * {@link Ipv6SubnetKernel#analisarRica} a partir de uma {@link AnaliseIpv6} já validada.</p>
 */
public final class Ipv6AnaliseRica {

    private Ipv6AnaliseRica() {
    }

    /** Uma célula de bit da grade de 128 bits: índice global, expoente, valor, peso local e css. */
    public record BitCelula(int num, int power, char val, long pesoLocal, String css) { }

    /** Um hexteto (16 bits) da grade: índice (1–8), hex e as 16 células. */
    public record HextetoGrade(int indice, String hex, List<BitCelula> bits) { }

    /** Uma linha da tabela "aplicação do prefixo" (análogo do AND IP×máscara do IPv4). */
    public record LinhaPrefixo(String campo, String valor, String css) { }

    /** Um passo do assistente de cálculo (ícone emoji + etapa + ação + resultado). */
    public record PassoWizard(String icone, String etapa, String acao, String resultado) { }

    /** Linha do tempo do bloco: rede → faixa atribuível → último (sem broadcast). */
    public record LinhaTempo(String posicao, String rede, String primeiro, String ultimo, String entrada) { }

    /** Uma linha da tabela de referência de prefixos IPv6. */
    public record ReferenciaPrefixo(String prefixo, String quantasLan, String enderecos,
            String fronteira, boolean atual) { }

    /** Uma linha da tabela de conversão nibble/hexteto. */
    public record ConversaoLinha(String referencia, String bits, String nibbles, String hextetos, String hex) { }

    /** Uma dica de segurança (tipo bootstrap + emoji + texto). */
    public record DicaSeguranca(String tipo, String icon, String texto) { }

    /** Um termo de rede (SLAAC/DHCPv6/NDP…) com sua explicação. */
    public record TermoRede(String termo, String valor) { }

    /** Um item do resumo tipo prova (campo/valor). */
    public record ItemProva(String campo, String valor) { }

    /** Um item do banner contextual didático (rótulo/valor). */
    public record BannerItem(String rotulo, String valor) { }

    /**
     * Agregado completo consumido pelo template {@code resultado_analise.html}.
     */
    public record Resultado(
            AnaliseIpv6 base,
            int bitsRede,
            int bitsInterface,
            List<HextetoGrade> hextetos,
            List<LinhaPrefixo> aplicacaoPrefixo,
            String totalEnderecos,
            String totalPotencia,
            String gateway,
            List<DelegacaoInfo> delegacao,
            List<SubredeIpv6> regua,
            int reguaCount,
            List<PassoWizard> wizard,
            LinhaTempo linhaTempo,
            List<ReferenciaPrefixo> referencia,
            List<ConversaoLinha> conversao,
            List<String> grcResumo,
            List<DicaSeguranca> segurancaDicas,
            List<TermoRede> termos,
            String ciscoOspfv3,
            String ciscoEigrp,
            String ciscoNota,
            String bannerTitulo,
            String bannerSubtitulo,
            List<BannerItem> bannerItens,
            String provaFrase,
            List<ItemProva> provaItens,
            String nivelTema,
            String nivelTemaDescricao,
            String corAcento,
            String corBit1,
            String enunciado,
            String textoCopia) { }
}
