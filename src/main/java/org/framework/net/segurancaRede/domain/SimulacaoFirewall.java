package org.framework.net.segurancaRede.domain;

import java.util.List;

/**
 * Resultado da comparação didática entre um firewall SEM estado (ACL que avalia
 * cada pacote isolado) e um COM estado (que mantém tabela de conexões).
 *
 * <p><b>Propósito de negócio:</b> mostrar, sobre a MESMA sequência de pacotes de
 * um fluxo, por que o firewall com estado libera o tráfego de retorno de uma
 * conexão que a própria rede iniciou — enquanto o sem estado ou bloqueia esse
 * retorno ou depende de um buraco permanente na entrada.</p>
 *
 * <p><b>Invariantes do domínio:</b> a avaliação é DETERMINÍSTICA — a mesma
 * sequência produz sempre o mesmo veredito; o estado da conexão é explícito em
 * cada passo (antes e depois); e uma flag isolada (ex.: um SYN-ACK sem SYN
 * anterior) é tratada como "fora de estado", NUNCA como prova de ataque. As
 * duas políticas são declaradas em texto, não escondidas na lógica.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário desconhecido é rejeitado
 * pela camada de aplicação; este record só carrega dados já validados e nunca é
 * nulo em suas listas (ausência é lista vazia).</p>
 */
public record SimulacaoFirewall(
        String cenario,
        String descricao,
        String politicaSemEstado,
        String politicaComEstado,
        List<AvaliacaoPacote> avaliacoes) {

    public SimulacaoFirewall {
        avaliacoes = avaliacoes == null ? List.of() : List.copyOf(avaliacoes);
    }

    /** Quantos passos os dois modelos trataram de forma diferente. */
    public long divergencias() {
        return avaliacoes.stream().filter(AvaliacaoPacote::divergem).count();
    }

    /**
     * Um pacote do fluxo, com sua direção e flag TCP.
     *
     * <p><b>Invariantes:</b> {@code direcao} é {@code SAIDA} (LAN→WAN, iniciado
     * por dentro) ou {@code ENTRADA} (WAN→LAN, vindo de fora). {@code flag} é o
     * rótulo TCP do pacote (SYN, SYN-ACK, ACK, DADOS, FIN, FIN-ACK).</p>
     */
    public record PacoteFluxo(int passo, String direcao, String flag, String resumo) {

        public boolean entrada() {
            return "ENTRADA".equals(direcao);
        }

        public boolean saida() {
            return "SAIDA".equals(direcao);
        }
    }

    /**
     * O veredito dos dois firewalls para um pacote, com o estado da conexão
     * antes e depois e a razão de cada decisão.
     *
     * <p><b>Invariantes:</b> {@code semEstado} e {@code comEstado} são sempre
     * {@code PERMITIDO} ou {@code BLOQUEADO}; {@code divergem} é verdadeiro
     * quando os dois modelos discordam — o ponto didático da tela.</p>
     */
    public record AvaliacaoPacote(
            PacoteFluxo pacote,
            String semEstado,
            String razaoSemEstado,
            String comEstado,
            String razaoComEstado,
            String estadoAntes,
            String estadoDepois) {

        public boolean divergem() {
            return !semEstado.equals(comEstado);
        }
    }
}
