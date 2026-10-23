package org.framework.net.web.domain;

import java.util.List;
import java.util.function.Supplier;

/** Acesso de teste ao que é de pacote em {@link PaginasPublicas}. */
public final class PaginasPublicasTestes {

    private PaginasPublicasTestes() {
    }

    public static List<String> rotasDaAcademia(Supplier<List<String>> fonte) {
        return PaginasPublicas.rotasDaAcademia(fonte);
    }
}
