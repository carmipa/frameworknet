package org.framework.net.resolucaoProblemas.exception;

/**
 * Não havia vaga para montar o "Pacote da turma" dentro da espera.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> separar "servidor ocupado agora" de "colagem com erro": é a mesma família de recusa
 * para quem só sabe tratar {@link EntradaInvalidaException}, mas quem a conhece não marca a colagem como errada nem conta
 * o caso como entrada inválida na telemetria.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a mensagem pede para tentar de novo em instantes; nada foi montado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> exceção de domínio, sem causa.</p>
 */
public class PacoteTurmaOcupadoException extends EntradaInvalidaException {

    public PacoteTurmaOcupadoException(String mensagem) {
        super(mensagem);
    }
}
