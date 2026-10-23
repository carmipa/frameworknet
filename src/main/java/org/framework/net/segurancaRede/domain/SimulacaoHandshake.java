package org.framework.net.segurancaRede.domain;

import java.util.List;

/**
 * Passo a passo de um handshake TLS 1.3, com o que é claro e o que é cifrado.
 *
 * <p><b>Propósito de negócio:</b> deixar o aluno avançar o handshake mensagem a
 * mensagem — ClientHello, ServerHello, Certificate, CertificateVerify, Finished
 * e os dados da aplicação — vendo em que ponto as chaves passam a valer (o que
 * vira cifrado) e onde uma incompatibilidade derruba a conexão.</p>
 *
 * <p><b>Invariantes do domínio:</b> a sequência é DETERMINÍSTICA por cenário;
 * cada passo diz quem envia, se está cifrado e o que carrega (distinguindo cipher
 * suite, troca de chaves e assinatura). Um cenário de falha marca EXATAMENTE o
 * passo que aborta e explica o alerta TLS correspondente — nunca falha em
 * silêncio nem seguem passos depois do aborto.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário fora do catálogo é recusado
 * na aplicação; este record só transporta dados já montados e nunca tem listas
 * nulas.</p>
 */
public record SimulacaoHandshake(
        String cenario,
        String titulo,
        boolean sucesso,
        String desfecho,
        List<PassoHandshake> passos) {

    public SimulacaoHandshake {
        passos = passos == null ? List.of() : List.copyOf(passos);
    }

    /**
     * Uma mensagem do handshake.
     *
     * <p><b>Invariantes:</b> {@code remetente} é {@code CLIENTE} ou
     * {@code SERVIDOR}; {@code cifrado} indica se já viaja sob as chaves de
     * sessão; {@code falha} marca o passo que aborta a conexão (só um cenário de
     * erro tem um passo com {@code falha=true}).</p>
     */
    public record PassoHandshake(
            int ordem,
            String remetente,
            String mensagem,
            boolean cifrado,
            boolean falha,
            String descricao) {
    }
}
