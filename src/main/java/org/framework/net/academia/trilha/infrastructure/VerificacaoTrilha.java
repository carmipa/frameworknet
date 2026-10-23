package org.framework.net.academia.trilha.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;
import org.framework.net.academia.trilha.domain.CatalogoTrilha;

/**
 * Conferência do catálogo da trilha no arranque.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> catálogo inconsistente é pego no build pelo teste; esta
 * verificação é a segunda camada, para o caso de um build ter passado sem o teste — aí a
 * Academia fica fechada com a causa no log, em vez de servir lição com id repetido.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> sem rede, sem disco, sem efeito colateral.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> propaga a {@link IllegalStateException} do catálogo;
 * o kernel a transforma em Academia {@code DEGRADADA}.</p>
 */
@ApplicationScoped
public class VerificacaoTrilha implements VerificacaoArranque {

    @Override
    public String nome() {
        return "trilha";
    }

    @Override
    public void verificar() {
        CatalogoTrilha.validar();
    }
}
