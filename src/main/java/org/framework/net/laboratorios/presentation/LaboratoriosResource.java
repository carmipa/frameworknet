package org.framework.net.laboratorios.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Entrada HTTP da área "Laboratórios Interativos".
 *
 * <p><b>Propósito de negócio:</b> servir as páginas de uma área didática nova e
 * autocontida — uma visão geral e a experiência "Camadas em ação" — sem tocar nos
 * módulos existentes. A experiência é uma máquina de estados no cliente (JS/SVG);
 * aqui só se entregam os templates.</p>
 *
 * <p><b>Invariantes do domínio:</b> rotas somente de leitura (GET/HTML); a
 * navegação interna e a animação vivem no front-end escopado do módulo; nada aqui
 * altera o comportamento de outras telas. A visão geral lista apenas experiências
 * realmente disponíveis.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> falha de renderização de template
 * propaga ao runtime Qute/JAX-RS como em qualquer página; não há estado de
 * servidor a corromper (a simulação é client-side).</p>
 */
@Path("/laboratorios")
public class LaboratoriosResource {

    private static final String MENU_ATIVO = "laboratorios";

    @Inject
    @Location("laboratorios/index.html")
    Template index;

    @Inject
    @Location("laboratorios/camadas.html")
    Template camadas;
    @Inject @Location("laboratorios/aneis-e-rede.html") Template aneisERede;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance visaoGeral() {
        return index.data("activeMainMenu", MENU_ATIVO)
                .data("labAtivo", "geral");
    }

    @GET
    @Path("/camadas")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance camadasEmAcao() {
        return camadas.data("activeMainMenu", MENU_ATIVO)
                .data("labAtivo", "camadas");
    }

    /**
     * Serve uma simulação didática do percurso aplicação, kernel e NIC.
     *
     * <p><b>Propósito de negócio:</b> explicar privilégios e rede sem executar
     * sockets ou alterar a máquina do estudante.</p>
     * <p><b>Invariantes do domínio:</b> somente leitura; estado no JS isolado.</p>
     * <p><b>Comportamento em caso de falha:</b> erros de template seguem o fluxo
     * padrão Qute/JAX-RS; não existe estado persistente.</p>
     */
    @GET @Path("/aneis-e-rede") @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aneisERede() {
        return aneisERede.data("activeMainMenu", MENU_ATIVO).data("labAtivo", "aneis-rede");
    }
}
