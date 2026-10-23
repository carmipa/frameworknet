package org.framework.net.simuladores.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp.Kpi;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp.Mitigacao;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp.Passo;

import java.util.List;
import java.util.Locale;

/**
 * Gera cenários didáticos de anomalias TCP para a sub-aba "Anomalias" da página
 * Tráfego.
 *
 * <p><b>Propósito de negócio:</b> transformar dois ataques clássicos — SYN flood
 * e sequestro por previsão de número de sequência — em passo a passo observável,
 * com os indicadores que denunciam o abuso e as defesas que o fecham. Ensina a
 * pensar como analista de defesa sem exigir tráfego real.</p>
 *
 * <p><b>Invariantes do domínio:</b> computação pura e determinística — mesma
 * entrada, mesma saída; nenhum pacote sai da máquina. Os números do backlog
 * (128 meio-abertas) e do espaço de ISN (2^32) são valores didáticos fixos, não
 * medição de um servidor real.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> tipo nulo, em branco ou desconhecido
 * cai no cenário padrão (SYN flood) em vez de lançar — a sub-aba nunca fica sem
 * conteúdo por causa de um parâmetro de URL inesperado.</p>
 */
@ApplicationScoped
public class AnomaliaTcpService {

    /** Capacidade didática do backlog de conexões meio-abertas. */
    private static final int BACKLOG = 128;

    /**
     * Monta o cenário pedido.
     *
     * @param tipo {@code syn-flood} ou {@code sequence-hijack}; qualquer outro
     *             valor cai no SYN flood.
     */
    public ResultadoAnomaliaTcp simular(String tipo) {
        String alvo = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        if ("sequence-hijack".equals(alvo)) {
            return sequenceHijack();
        }
        return synFlood();
    }

    private ResultadoAnomaliaTcp synFlood() {
        List<Kpi> kpis = List.of(
                new Kpi("Capacidade do backlog", BACKLOG + " meio-abertas", "info",
                        "Fila de conexões em SYN_RECV aguardando o ACK final. Cheia, novas conexões são recusadas."),
                new Kpi("SYN forjados na rajada", "500+ /s", "danger",
                        "IP de origem falsificado: o SYN-ACK vai para um endereço que nunca responde."),
                new Kpi("Slots presos", BACKLOG + " / " + BACKLOG, "danger",
                        "Cada meio-aberta segura o slot até o timeout (dezenas de segundos)."),
                new Kpi("Conexões legítimas", "recusadas", "danger",
                        "Com o backlog cheio, o SYN de quem realmente quer conectar é descartado."));

        List<Passo> passos = List.of(
                new Passo(1, "Cliente legítimo", "SYN →",
                        "Conexão normal: o servidor guarda o estado no backlog (1/" + BACKLOG
                                + "), responde SYN-ACK e o cliente devolve ACK. Slot liberado, conexão ESTABLISHED.",
                        "SYN_RECV → ESTABLISHED", "normal"),
                new Passo(2, "Atacante", "SYN (IP forjado) →",
                        "O atacante envia SYN com endereço de origem falsificado. O servidor aloca um slot "
                                + "(SYN_RECV) e envia o SYN-ACK para o IP forjado.",
                        "SYN_RECV (meio-aberta)", "ataque"),
                new Passo(3, "Servidor", "⧖ aguarda o ACK",
                        "O ACK nunca chega — o IP forjado não existe ou ignora o SYN-ACK. O slot fica preso "
                                + "até o timeout, retransmitindo o SYN-ACK várias vezes.",
                        "SYN_RECV preso", "alerta"),
                new Passo(4, "Atacante", "SYN × 500/s →",
                        "A rajada repete o passo 2 centenas de vezes por segundo. As meio-abertas se acumulam "
                                + "mais rápido do que expiram.",
                        "backlog subindo → " + BACKLOG + "/" + BACKLOG, "ataque"),
                new Passo(5, "Servidor", "backlog cheio",
                        "Atingido o teto de " + BACKLOG + " meio-abertas, não há mais slot para alocar.",
                        BACKLOG + "/" + BACKLOG + " — saturado", "alerta"),
                new Passo(6, "Cliente legítimo", "SYN → ✗ descartado",
                        "O SYN de quem realmente quer conectar chega, mas não há slot: o servidor o descarta. "
                                + "É a negação de serviço — o servidor está de pé, mas surdo.",
                        "recusado", "ataque"),
                new Passo(7, "Servidor (com SYN cookies)", "SYN → SYN-ACK sem estado",
                        "Com SYN cookies ligados, o servidor NÃO aloca slot no SYN: codifica o estado dentro do "
                                + "próprio número de sequência do SYN-ACK. Só materializa a conexão quando um ACK "
                                + "válido volta com o cookie correto — a rajada forjada não consome backlog.",
                        "sem estado até o ACK", "ok"));

        List<Mitigacao> mitigacoes = List.of(
                new Mitigacao("SYN cookies",
                        "O servidor não guarda estado no SYN. O ISN do SYN-ACK É o estado: um hash do 4-tuple com "
                                + "um SEGREDO do servidor, mais um contador de tempo e o MSS (RFC 4987 §3.6). Sem o "
                                + "segredo ninguém fabrica um cookie válido; sem ACK válido de volta, nada é alocado."),
                new Mitigacao("Aumentar o backlog e reduzir o timeout de SYN-RECV",
                        "Mais slots e retransmissões de SYN-ACK mais curtas diminuem a janela, mas sozinho é "
                                + "corrida perdida contra a banda do atacante — paliativo, não solução."),
                new Mitigacao("Filtragem de origem (BCP 38 / uRPF) na borda",
                        "Bloquear na saída pacotes com IP de origem que não pertence à rede corta a falsificação "
                                + "na fonte. Depende do operador de rede, não do servidor."),
                new Mitigacao("Rate limiting e scrubbing anti-DDoS",
                        "Limitar SYNs por origem e passar o tráfego por um serviço de limpeza absorve o volume "
                                + "antes de chegar ao servidor."));

        return new ResultadoAnomaliaTcp(
                "syn-flood",
                "SYN Flood — esgotamento do backlog TCP",
                "O three-way handshake guarda estado no servidor entre o SYN e o ACK. O SYN flood explora "
                        + "justamente essa janela: inunda o servidor com SYNs de origem forjada, enche a fila de "
                        + "conexões meio-abertas e deixa o serviço sem espaço para quem é legítimo.",
                kpis, passos, mitigacoes,
                "Guardar estado antes de confirmar a intenção do outro lado é o que o ataque explora. SYN "
                        + "cookies invertem isso: não alocam nada até o ACK provar que a conexão é real.");
    }

    private ResultadoAnomaliaTcp sequenceHijack() {
        List<Kpi> kpis = List.of(
                new Kpi("ISN previsível (legado)", "~1 palpite", "danger",
                        "Geradores antigos incrementavam o ISN por tempo/conexão. Sabendo a regra, o próximo "
                                + "valor é estimável."),
                new Kpi("ISN aleatório (RFC 6528)", "≈ janela ÷ 2^32", "success",
                        "ISN = M + hash(segredo, 4-tuple). Basta cair na janela, então a chance por palpite é "
                                + "o tamanho da janela sobre 2^32 — pequena, mas não 1 em 2^32."),
                new Kpi("Janela de recepção", "aceita seq na faixa", "warning",
                        "O alvo aceita qualquer seq dentro da janela — não precisa ser exato, só cair no intervalo."),
                new Kpi("Origem do atacante", "off-path (cego)", "info",
                        "O atacante NÃO vê o tráfego; forja o IP de uma das pontas e aposta no número de sequência."));

        List<Passo> passos = List.of(
                new Passo(1, "Cliente A ⇄ Servidor S", "sessão ESTABLISHED",
                        "A e S trocam dados numa conexão estabelecida. Um atacante off-path quer injetar dados "
                                + "ou um RST forjando ser A — sem enxergar o tráfego entre eles.",
                        "ESTABLISHED", "normal"),
                new Passo(2, "Atacante", "precisa do seq certo",
                        "Para S aceitar o pacote, o número de sequência precisa cair dentro da janela de recepção "
                                + "atual e a porta de origem de A precisa ser conhecida.",
                        "reconhecimento", "alerta"),
                new Passo(3, "Atacante (ISN previsível)", "estima o seq atual",
                        "Se o ISN foi gerado por regra previsível (ex.: +64000 por conexão, +128000/s), o atacante "
                                + "reconstrói o valor aproximado e mira a janela.",
                        "seq estimado", "ataque"),
                new Passo(4, "Atacante", "injeta RST/dados forjando A →",
                        "Com o seq dentro da janela e o IP de A forjado, S aceita o segmento: um RST derruba a "
                                + "sessão (DoS), dados forjados a corrompem (injeção). É o TCP hijacking.",
                        "sessão sequestrada", "ataque"),
                new Passo(5, "Servidor (ISN RFC 6528)", "seq imprevisível",
                        "Com ISN = M + F(segredo, IPs/portas), cada conexão parte de um ponto imprevisível. O "
                                + "atacante off-path teria de acertar 1 valor em 2^32 — e a janela reduz pouco isso "
                                + "diante do volume necessário.",
                        "adivinhação inviável", "ok"),
                new Passo(6, "Servidor", "RST na janela, seq inexato → challenge ACK",
                        "Fora da janela, o RST é descartado em silêncio. Dentro da janela mas sem o número exato, "
                                + "o TCP moderno responde com um challenge ACK (RFC 5961 §3.2) e só derruba a sessão "
                                + "com o número exato — que o atacante cego não sabe.",
                        "reset desafiado", "ok"));

        List<Mitigacao> mitigacoes = List.of(
                new Mitigacao("ISN imprevisível (RFC 6528)",
                        "Gerar o ISN como M (relógio de 4 microssegundos) somado a um hash de um segredo com o "
                                + "4-tuple da conexão torna o valor imprevisível sem revelar o segredo. É a defesa raiz."),
                new Mitigacao("Challenge ACK contra RST/SYN forjado (RFC 5961)",
                        "Reset ou SYN dentro da janela mas com número inexato dispara um challenge ACK; a sessão só "
                                + "cai se a outra ponta responder com o número certo — o cego não consegue."),
                new Mitigacao("Timestamps TCP (PAWS)",
                        "Somam mais um campo que o atacante precisaria acertar, reduzindo ainda mais a chance do "
                                + "palpite cego passar."),
                new Mitigacao("Cifrar a camada de cima (TLS/SSH)",
                        "Sequestrar o TCP ainda derruba a conexão (DoS), mas injetar dados aceitáveis fica inútil: "
                                + "o conteúdo forjado não passa na verificação de integridade da sessão cifrada."));

        return new ResultadoAnomaliaTcp(
                "sequence-hijack",
                "Previsão de número de sequência e sequestro de sessão",
                "O TCP confia no número de sequência para ordenar e aceitar segmentos. Se o ISN inicial for "
                        + "previsível, um atacante que nem vê o tráfego (off-path) pode estimar o seq atual, forjar "
                        + "o IP de uma das pontas e injetar um RST ou dados — sequestrando a sessão. É por isso que "
                        + "gerar ISN de forma imprevisível (RFC 6528) é requisito de segurança, não detalhe.",
                kpis, passos, mitigacoes,
                "Previsibilidade é a vulnerabilidade. Um número que deveria ser aleatório e virou estimável "
                        + "transformou o TCP num protocolo sequestrável — a correção foi tornar o ISN imprevisível.");
    }
}
