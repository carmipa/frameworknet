package org.framework.net.academia.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.academia.core.domain.EstadoAcademia;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;

import java.util.ArrayList;
import java.util.List;

/**
 * Dono único da situação da Academia neste processo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> decide no arranque se a Academia abre, e responde a cada
 * página da Academia se ela pode atender. É o que permite a Academia falhar sozinha (D12): uma
 * parte quebrada fecha {@code /academia} com 503 e a causa no log, e nenhuma outra rota do site
 * percebe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b></p>
 * <ul>
 *   <li>antes da decisão a Academia está {@code DESLIGADA} ("arranque não concluído") — nunca
 *       aberta por omissão;</li>
 *   <li>chave desligada vence tudo: as verificações nem rodam;</li>
 *   <li>a primeira verificação que falhar define a causa; as seguintes ainda rodam e entram na
 *       mesma causa, para o log mostrar o quadro inteiro de uma vez;</li>
 *   <li>falha da verificação nunca sobe para o arranque do site: captura {@link Throwable},
 *       exceto {@link VirtualMachineError} (memória esgotada não é assunto da Academia).</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #decidir} nunca lança por causa de uma parte;
 * devolve {@code DEGRADADA} com as causas concatenadas. {@code VirtualMachineError} é
 * relançado.</p>
 */
@ApplicationScoped
public class PortaoAcademia {

    private volatile EstadoAcademia estado = EstadoAcademia.desligada("arranque ainda não concluído");

    /** Situação atual, para as páginas e para o log. */
    public EstadoAcademia estado() {
        return estado;
    }

    /**
     * Decide a situação e a guarda.
     *
     * @param ligada valor da chave {@code framework.academia.enabled}
     * @param verificacoes as conferências de arranque das partes
     */
    public EstadoAcademia decidir(boolean ligada, Iterable<VerificacaoArranque> verificacoes) {
        if (!ligada) {
            estado = EstadoAcademia.desligada("chave framework.academia.enabled=false");
            return estado;
        }
        List<String> falhas = new ArrayList<>();
        for (VerificacaoArranque verificacao : verificacoes) {
            String nome = nomeSeguro(verificacao);
            try {
                verificacao.verificar();
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable falha) {
                falhas.add(nome + ": " + falha.getClass().getSimpleName()
                        + (falha.getMessage() == null ? "" : " — " + falha.getMessage()));
            }
        }
        estado = falhas.isEmpty() ? EstadoAcademia.emFuncionamento()
                : EstadoAcademia.degradada(String.join("; ", falhas));
        return estado;
    }

    private static String nomeSeguro(VerificacaoArranque verificacao) {
        try {
            String nome = verificacao.nome();
            return nome == null || nome.isBlank() ? verificacao.getClass().getSimpleName() : nome;
        } catch (RuntimeException ex) {
            return verificacao.getClass().getSimpleName();
        }
    }
}
