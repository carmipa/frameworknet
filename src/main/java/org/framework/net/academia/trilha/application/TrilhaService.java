package org.framework.net.academia.trilha.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.academia.trilha.domain.CatalogoTrilha;
import org.framework.net.academia.trilha.domain.Licao;
import org.framework.net.academia.trilha.domain.Nivel;

import java.util.List;
import java.util.Optional;

/**
 * Superfície pública do peer {@code trilha} para as fatias da Academia.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> as fatias (landing, níveis, eventos) perguntam à trilha o que
 * existe; nenhuma delas mantém lista própria de lições.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só leitura; devolve o catálogo tal como declarado, na ordem de
 * estudo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> id desconhecido ou nulo ⇒ {@link Optional#empty()}.</p>
 */
@ApplicationScoped
public class TrilhaService {

    public List<Nivel> niveis() {
        return CatalogoTrilha.niveis();
    }

    public Optional<Nivel> nivel(String id) {
        return CatalogoTrilha.nivel(id);
    }

    public Optional<Licao> licao(String id) {
        return CatalogoTrilha.licao(id);
    }

    public Optional<Licao> proxima(String id) {
        return CatalogoTrilha.proxima(id);
    }

    /** {@code true} se o id é de uma lição do catálogo. */
    public boolean existe(String licaoId) {
        return CatalogoTrilha.licao(licaoId).isPresent();
    }
}
