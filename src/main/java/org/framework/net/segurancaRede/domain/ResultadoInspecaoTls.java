package org.framework.net.segurancaRede.domain;

import java.util.List;

/**
 * Resultado da inspeção didática de um certificado/conexão TLS.
 *
 * <p><b>Propósito de negócio:</b> o aluno informa os atributos de uma conexão
 * (nome acessado, nome no certificado, validade, emissor, versão e cipher) e vê
 * o que um validador de verdade checaria — nome, validade, cadeia, protocolo e
 * cipher — com veredito por item e um veredito geral. Ensina que o navegador
 * recusa por qualquer falha, e que "tem cadeado" não é o mesmo que "confiável".</p>
 *
 * <p><b>Invariantes do domínio:</b> record puro de dados, sem framework. A lista
 * de checagens nunca é nula; o veredito geral é o pior estado entre elas
 * (uma falha derruba tudo, como no navegador).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — a validação de entrada e o
 * cálculo ficam no serviço de aplicação.</p>
 */
public record ResultadoInspecaoTls(
        String veredito,
        String vereditoCor,
        String resumo,
        List<Checagem> checagens) {

    /** Construtor compacto: honra "lista de checagens nunca nula" (imutável e não-nula). */
    public ResultadoInspecaoTls {
        checagens = checagens == null ? List.of() : List.copyOf(checagens);
    }

    /**
     * Uma verificação individual.
     *
     * @param estado    rótulo: {@code OK}, {@code ATENÇÃO} ou {@code FALHA}
     * @param estadoCor cor Bootstrap: {@code success}, {@code warning}, {@code danger}
     * @param icone     ícone Material Symbols coerente com o estado
     */
    public record Checagem(
            String nome,
            String estado,
            String estadoCor,
            String detalhe,
            String icone) {
    }
}
