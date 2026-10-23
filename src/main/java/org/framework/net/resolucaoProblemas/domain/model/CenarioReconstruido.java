package org.framework.net.resolucaoProblemas.domain.model;

import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.LinhaNaoInterpretada;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;

import java.util.List;

/**
 * Projeto reconstruído a partir da configuração colada.
 *
 * <p><b>Propósito de negócio:</b> é o resultado da engenharia reversa — o mesmo
 * tipo de material que a aba "Projetar" entrega (LANs, enlaces, tabela por
 * roteador, desenho, scripts), só que derivado de uma configuração existente em
 * vez de calculado a partir de requisitos. Carrega junto o veredito da auditoria:
 * o que estava errado, o que foi corrigido e com base em qual evidência.</p>
 *
 * <p><b>Invariantes do domínio:</b> os roteadores aqui já estão <em>corrigidos</em>;
 * o texto original do usuário nunca é sobrescrito e continua disponível na tela ao
 * lado. Toda correção aplicada tem um {@link AchadoConfiguracao} correspondente com
 * evidência — não existe alteração silenciosa. {@code pendencias} lista o que
 * faltou para fechar o cenário, em vez de completar por conta própria.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário sem nenhum roteador
 * reconhecido é resultado legítimo, com listas vazias e
 * {@code resumo.cenarioConsistente() == false} — não é exceção.</p>
 */
public record CenarioReconstruido(
        List<RoteadorLido> roteadores,
        List<AchadoConfiguracao> achados,
        List<LinhaNaoInterpretada> linhasNaoInterpretadas,
        List<LanReconstruida> lans,
        List<EnlaceReconstruido> enlaces,
        List<TabelaRoteador> tabelas,
        List<Pendencia> pendencias,
        List<ScriptCorrigido> scripts,
        String mermaid,
        Resumo resumo) {

    public CenarioReconstruido {
        roteadores = lista(roteadores);
        achados = lista(achados);
        linhasNaoInterpretadas = lista(linhasNaoInterpretadas);
        lans = lista(lans);
        enlaces = lista(enlaces);
        tabelas = lista(tabelas);
        pendencias = lista(pendencias);
        scripts = lista(scripts);
        mermaid = mermaid == null ? "" : mermaid;
    }

    private static <T> List<T> lista(List<T> origem) {
        return origem == null ? List.of() : List.copyOf(origem);
    }

    public boolean vazio() {
        return roteadores.isEmpty();
    }

    /** Achados de uma severidade, na ordem em que a auditoria os produziu. */
    public List<AchadoConfiguracao> achadosDe(AchadoConfiguracao.Severidade severidade) {
        return achados.stream().filter(a -> a.severidade() == severidade).toList();
    }

    /** LAN reconhecida atrás de um roteador, com os números que a aba "Projetar" também mostra. */
    public record LanReconstruida(
            String roteador,
            String nomeInterface,
            String rede,
            int prefixo,
            String mascara,
            String wildcard,
            String gateway,
            String primeiroHost,
            String ultimoHost,
            String broadcast,
            long hostsUtilizaveis,
            boolean anunciadaNoRoteamento) {

        public String cidr() {
            return rede + "/" + prefixo;
        }
    }

    /**
     * Enlace ponto a ponto entre dois roteadores.
     *
     * <p><b>Invariantes do domínio:</b> {@code completo} é falso quando só uma
     * ponta foi encontrada nos scripts colados — o enlace aparece na tela como
     * pendência, nunca como se estivesse fechado.</p>
     */
    public record EnlaceReconstruido(
            String rede,
            int prefixo,
            String mascara,
            String roteadorA,
            String nomeInterfaceA,
            String ipA,
            String roteadorB,
            String nomeInterfaceB,
            String ipB,
            String ladoDce,
            boolean completo) {

        public String cidr() {
            return rede + "/" + prefixo;
        }

        public String nome() {
            return completo ? roteadorA + " ↔ " + roteadorB : roteadorA + " ↔ (ponta ausente)";
        }
    }

    /** Tabela por roteador, no mesmo formato usado no laboratório do Packet Tracer. */
    public record TabelaRoteador(String roteador, long asBgp, List<LinhaTabela> linhas) {

        public TabelaRoteador {
            linhas = lista(linhas);
        }
    }

    /** Uma interface na tabela do roteador. */
    public record LinhaTabela(
            String nomeInterface,
            String ip,
            String mascara,
            String cidr,
            String papel,
            String observacao) {
    }

    /**
     * Algo que faltou para fechar o cenário.
     *
     * <p><b>Propósito de negócio:</b> dizer ao usuário exatamente o que colar ou
     * informar a seguir, em vez de preencher a lacuna com um palpite.</p>
     */
    public record Pendencia(String descricao, String comoResolver) {
    }

    /** Script de um roteador já com as correções aplicadas e comentadas. */
    public record ScriptCorrigido(String roteador, String conteudo, int correcoes) {

        public boolean alterado() {
            return correcoes > 0;
        }
    }

    /** Contagens do cabeçalho da tela. */
    public record Resumo(
            int roteadores,
            int enlaces,
            int lans,
            int errosCorrigidos,
            int errosSemCorrecao,
            int avisos,
            int linhasNaoInterpretadas,
            boolean cenarioConsistente) {

        public int totalAchados() {
            return errosCorrigidos + errosSemCorrecao + avisos;
        }
    }
}
