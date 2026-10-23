package org.framework.net.academia.eventos.domain;

import java.util.Objects;

/**
 * O que o navegador de uma lição pode contar ao servidor — e só isto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> saber, sem saber quem é o aluno, se as lições funcionam e são
 * usadas: quanto tempo se fica numa lição, se ela foi concluída e quais erros de JavaScript
 * aparecem nos navegadores reais (F5 — o site não tinha nenhum jeito de saber).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO (INV-ACAD-005):</b> lista fechada de campos; nenhum valor digitado
 * pelo aluno; tempo e interações em faixas, nunca o número exato; a lição é sempre do catálogo
 * (conferida antes de chegar aqui); a mensagem de erro já passou pelo {@link SaneadorTexto}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> campo nulo lança {@link NullPointerException} na
 * construção — quem constrói é a aplicação, depois de validar.</p>
 */
public sealed interface EventoAcademia {

    /** Lição em que o evento aconteceu. */
    String licaoId();

    /**
     * Resumo de uma visita a uma lição, enviado uma vez quando a página fecha.
     *
     * @param licaoId    lição do catálogo
     * @param tempo      faixa de tempo na página
     * @param interacoes faixa de quantidade de interações
     * @param concluiu   se o aluno concluiu a lição nesta visita
     */
    record Visita(String licaoId, FaixaTempo tempo, FaixaInteracoes interacoes, boolean concluiu)
            implements EventoAcademia {
        public Visita {
            Objects.requireNonNull(licaoId, "licaoId");
            Objects.requireNonNull(tempo, "tempo");
            Objects.requireNonNull(interacoes, "interacoes");
        }
    }

    /**
     * Um erro de JavaScript capturado numa lição.
     *
     * @param licaoId  lição do catálogo
     * @param tipo     classe do erro
     * @param mensagem mensagem já saneada
     */
    record ErroJs(String licaoId, TipoErroJs tipo, String mensagem) implements EventoAcademia {
        public ErroJs {
            Objects.requireNonNull(licaoId, "licaoId");
            Objects.requireNonNull(tipo, "tipo");
            mensagem = SaneadorTexto.sanear(mensagem);
        }
    }

    /** Faixa de tempo na página — nunca o número exato. */
    enum FaixaTempo {
        ATE_30S, ATE_2MIN, ATE_10MIN, MAIS_10MIN;

        /** Faixa de uma duração em segundos; negativo conta como zero. */
        public static FaixaTempo de(long segundos) {
            if (segundos <= 30) {
                return ATE_30S;
            }
            if (segundos <= 120) {
                return ATE_2MIN;
            }
            return segundos <= 600 ? ATE_10MIN : MAIS_10MIN;
        }
    }

    /** Faixa de quantidade de interações — nunca o número exato. */
    enum FaixaInteracoes {
        NENHUMA, POUCAS, VARIAS, MUITAS;

        /** Faixa de uma contagem; negativo conta como zero. */
        public static FaixaInteracoes de(long quantidade) {
            if (quantidade <= 0) {
                return NENHUMA;
            }
            if (quantidade <= 5) {
                return POUCAS;
            }
            return quantidade <= 30 ? VARIAS : MUITAS;
        }
    }

    /** Classes de erro de JavaScript que a lição reporta. */
    enum TipoErroJs { ERRO, PROMESSA_REJEITADA }
}
