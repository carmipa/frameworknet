package org.framework.net.academia.core.domain.ports;

/**
 * Uma conferência que uma parte da Academia faz no arranque, antes de a Academia abrir.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o kernel da Academia decide se ela abre, mas não conhece as
 * partes (kernel não importa peer nem fatia). Cada parte que precisa conferir algo — o catálogo
 * da trilha, por exemplo — implementa esta porta, e o kernel só pergunta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a verificação é rápida, sem rede e sem efeito colateral;
 * {@link #nome()} identifica a parte no log do arranque.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #verificar()} lança qualquer exceção com a
 * causa; o kernel a captura (exceto {@link VirtualMachineError}), deixa a Academia
 * {@code DEGRADADA} com essa causa e o site segue no ar.</p>
 */
public interface VerificacaoArranque {

    /** Nome curto da parte verificada, para o log ("trilha"). */
    String nome();

    /** Lança com a causa se a parte não puder atender. */
    void verificar();
}
