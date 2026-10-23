package org.framework.net.academia.core.infrastructure;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.academia.core.application.PortaoAcademia;
import org.framework.net.academia.core.domain.EstadoAcademia;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;
import org.jboss.logging.Logger;

/**
 * Liga a Academia no arranque e diz no log em que situação ela ficou.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> motor novo nasce com o estado escrito no início — sem isso
 * ninguém sabe se a Academia está aberta, desligada pela chave ou fechada por uma falha, e a
 * primeira pista vira um aluno reclamando de 503.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> uma linha de log por arranque com a situação e a causa;
 * {@code PRONTA} em INFO, as outras em WARN. Nada aqui lança para o arranque do site.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> qualquer erro inesperado ao decidir deixa a Academia
 * {@code DEGRADADA} com a causa e o site continua subindo; só {@link VirtualMachineError}
 * propaga.</p>
 */
@ApplicationScoped
public class AcademiaArranque {

    private static final Logger LOG = Logger.getLogger(AcademiaArranque.class);

    @Inject
    PortaoAcademia portao;

    @Inject
    Instance<VerificacaoArranque> verificacoes;

    @ConfigProperty(name = "framework.academia.enabled", defaultValue = "true")
    boolean ligada;

    void aoArrancar(@Observes StartupEvent evento) {
        EstadoAcademia estado;
        try {
            estado = portao.decidir(ligada, verificacoes);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable inesperado) {
            estado = portao.decidir(true, java.util.List.of(new VerificacaoArranque() {
                @Override
                public String nome() {
                    return "arranque";
                }

                @Override
                public void verificar() {
                    throw new IllegalStateException(inesperado.getClass().getSimpleName());
                }
            }));
        }
        if (estado.pronta()) {
            LOG.infof("Academia estado=%s", estado.situacao());
        } else {
            LOG.warnf("Academia estado=%s causa=%s", estado.situacao(), estado.causa());
        }
    }
}
