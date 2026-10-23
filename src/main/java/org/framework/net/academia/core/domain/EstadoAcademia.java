package org.framework.net.academia.core.domain;

import java.util.Objects;

/**
 * Situação da Academia neste processo, decidida uma vez no arranque.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a Academia vive na mesma JVM que o resto do site (D12). Se uma
 * peça dela falhar, quem acessa {@code /academia} precisa receber uma resposta honesta — e o resto
 * do site precisa continuar no ar como se nada tivesse acontecido. Este valor diz qual dos dois
 * mundos vale agora e por quê.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> três situações e só três — {@code DESLIGADA} (chave de
 * configuração), {@code PRONTA} e {@code DEGRADADA} (uma verificação de arranque falhou). Toda
 * situação diferente de {@code PRONTA} carrega a causa, nunca vazia: "não está pronta" sem motivo
 * é o silêncio que esta classe existe para impedir.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> construir {@code DESLIGADA}/{@code DEGRADADA} sem causa
 * lança {@link IllegalArgumentException} — erro de programa, pego no teste.</p>
 *
 * @param situacao a situação
 * @param causa por que não está pronta; vazia só quando {@code PRONTA}
 */
public record EstadoAcademia(Situacao situacao, String causa) {

    /** As três situações possíveis. */
    public enum Situacao { DESLIGADA, PRONTA, DEGRADADA }

    public EstadoAcademia {
        Objects.requireNonNull(situacao, "situacao");
        causa = causa == null ? "" : causa.strip();
        if (situacao != Situacao.PRONTA && causa.isEmpty()) {
            throw new IllegalArgumentException("situação " + situacao + " exige causa");
        }
    }

    public static EstadoAcademia emFuncionamento() {
        return new EstadoAcademia(Situacao.PRONTA, "");
    }

    public static EstadoAcademia desligada(String causa) {
        return new EstadoAcademia(Situacao.DESLIGADA, causa);
    }

    public static EstadoAcademia degradada(String causa) {
        return new EstadoAcademia(Situacao.DEGRADADA, causa);
    }

    public boolean pronta() {
        return situacao == Situacao.PRONTA;
    }
}
