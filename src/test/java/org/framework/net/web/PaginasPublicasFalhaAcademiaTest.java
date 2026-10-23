package org.framework.net.web;

import org.framework.net.web.domain.PaginasPublicasTestes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D12 com falha do CATÁLOGO (auditoria ACAD-02 e ACAD-09): o teste falhaIsolada simulava a falha numa
 * verificação sintética que não toca o catálogo. Aqui a fonte das rotas da Academia falha do mesmo jeito
 * que um catálogo quebrado falha na carga da classe — e a lista do site precisa sobreviver.
 */
class PaginasPublicasFalhaAcademiaTest {

    @Test
    void catalogoQuebradoTiraSoAsRotasDaAcademia() {
        assertEquals(List.of(), PaginasPublicasTestes.rotasDaAcademia(() -> {
            throw new ExceptionInInitializerError(new IllegalArgumentException("minutos deve ser positivo"));
        }));
        assertEquals(List.of(), PaginasPublicasTestes.rotasDaAcademia(() -> {
            throw new NoClassDefFoundError("org/framework/net/academia/trilha/domain/CatalogoTrilha");
        }));
        assertEquals(List.of(), PaginasPublicasTestes.rotasDaAcademia(() -> {
            throw new IllegalStateException("nível repetido");
        }));
        assertEquals(List.of("/academia"), PaginasPublicasTestes.rotasDaAcademia(() -> List.of("/academia")),
                "fonte saudável passa intacta");
    }
}
