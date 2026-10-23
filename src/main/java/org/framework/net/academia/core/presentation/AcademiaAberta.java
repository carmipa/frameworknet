package org.framework.net.academia.core.presentation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import org.framework.net.academia.core.application.PortaoAcademia;
import org.framework.net.academia.core.domain.EstadoAcademia;
import org.jboss.logging.Logger;

/**
 * Porteiro das rotas da Academia: deixa passar só com a Academia {@code PRONTA}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> cada página e cada API da Academia chama {@link #exigir()} antes
 * de qualquer coisa. Academia desligada ou degradada responde 503 — que o site já transforma na
 * página de erro padrão para quem usa navegador — e nenhuma outra rota é afetada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a decisão lê o estado do {@link PortaoAcademia}, nunca o
 * recalcula; a causa interna vai para o log, nunca para a resposta (ela pode citar classe e
 * configuração).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link ServiceUnavailableException} com
 * {@code Retry-After} de 300 segundos quando a Academia não está pronta.</p>
 */
@ApplicationScoped
public class AcademiaAberta {

    private static final Logger LOG = Logger.getLogger(AcademiaAberta.class);
    private static final long REPETIR_APOS_SEGUNDOS = 300;

    @Inject
    PortaoAcademia portao;

    /** Lança 503 se a Academia não estiver pronta. */
    public void exigir() {
        EstadoAcademia estado = portao.estado();
        if (!estado.pronta()) {
            LOG.debugf("rota da Academia recusada estado=%s causa=%s", estado.situacao(), estado.causa());
            throw new ServiceUnavailableException(REPETIR_APOS_SEGUNDOS);
        }
    }
}
