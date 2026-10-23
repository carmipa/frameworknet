package org.framework.net.simuladores.domain;

import java.util.List;

/**
 * Resultado do simulador de <b>anomalias TCP</b>: um cenário didático de abuso do
 * protocolo (SYN flood ou sequestro por previsão de número de sequência),
 * decomposto em passos, indicadores e mitigações.
 *
 * <p><b>Propósito de negócio:</b> o simulador de handshake mostra o caminho
 * correto; este mostra o que acontece quando alguém explora o mesmo mecanismo.
 * SYN flood ilustra o esgotamento do backlog da pilha TCP/IP; a previsão de
 * sequência ilustra por que geradores de ISN precisam ser imprevisíveis
 * (RFC 6528). Tudo é computação determinística — nenhum pacote real é enviado.</p>
 *
 * <p><b>Invariantes do domínio:</b> os dois cenários compartilham esta forma de
 * propósito — ambos são "uma sequência de passos com indicadores e defesas" —, o
 * que é parametrização de UM conceito (anomalia de conexão TCP), não a fusão de
 * domínios distintos. As listas nunca são nulas; a lição nunca é vazia.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — é um record de dados. Tipo
 * desconhecido é tratado por quem chama o serviço, que cai no cenário padrão.</p>
 */
public record ResultadoAnomaliaTcp(
        String tipo,
        String titulo,
        String resumo,
        List<Kpi> kpis,
        List<Passo> passos,
        List<Mitigacao> mitigacoes,
        String licao) {

    /** Construtor compacto: honra "listas nunca nulas" (imutáveis e não-nulas). */
    public ResultadoAnomaliaTcp {
        kpis = kpis == null ? List.of() : List.copyOf(kpis);
        passos = passos == null ? List.of() : List.copyOf(passos);
        mitigacoes = mitigacoes == null ? List.of() : List.copyOf(mitigacoes);
    }

    /** Um indicador numérico do cenário (capacidade do backlog, meio-abertas etc.). */
    public record Kpi(String rotulo, String valor, String cor, String dica) {
    }

    /**
     * Um evento do cenário.
     *
     * @param nivel classificação visual: {@code normal}, {@code ataque},
     *              {@code alerta} ou {@code ok}
     */
    public record Passo(
            int ordem,
            String ator,
            String acao,
            String detalhe,
            String estado,
            String nivel) {
    }

    /** Uma defesa contra a anomalia, com explicação curta. */
    public record Mitigacao(String nome, String comoFunciona) {
    }
}
