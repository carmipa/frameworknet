package org.framework.net.portas.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.portas.application.PortasAprofundamentoService;
import org.framework.net.portas.application.PortasService;
import org.framework.net.portas.domain.PortaAprofundamento;

/**
 * Entrada HTTP do módulo de Portas: catálogo (aba Geral) e aprofundamentos.
 *
 * <p><b>Propósito de negócio:</b> {@code /portas} é o DataGrid comparativo das
 * portas. Abaixo dele ficam os aprofundamentos ({@code /portas/anatomia},
 * {@code /portas/web}…), páginas didáticas por conceito e por família de serviço —
 * o mesmo padrão do módulo de Protocolos.</p>
 *
 * <p><b>Invariantes do domínio:</b> todas as rotas do módulo vivem NESTA classe. O
 * sub-menu vem de {@link PortaAprofundamento#disponiveis()} — nunca escrito à mão.
 * A rota curinga {@code /portas/{slug}} só atende slugs registrados; slug
 * inexistente é 404, nunca a página de outra família.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> conteúdo ausente já derrubou a
 * aplicação no boot (o catálogo falha fechado); slug desconhecido lança
 * {@link NotFoundException} (404 pela página de erro).</p>
 */
@Path("/portas")
public class PortasResource {

    private static final String MENU_ATIVO = "portas";

    @Inject
    PortasService portasService;

    @Inject
    PortasAprofundamentoService portasAprofundamentoService;

    @Inject
    @Location("portas/index.html")
    Template index;

    @Inject
    @Location("portas/aprofundamento.html")
    Template aprofundamento;

    /** Aba Geral: o catálogo comparativo completo. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return index
                .data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", PortaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", "geral")
                .data("portasCatalogo", portasService.montarPortasCatalogoExibicao());
    }

    /**
     * Aprofundamento de portas (Anatomia + famílias), resolvido pelo slug da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> slug não registrado resulta em
     * {@link NotFoundException} (404), nunca conteúdo de outra página.</p>
     */
    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        PortaAprofundamento item = PortaAprofundamento.porSlug(slug)
                .orElseThrow(NotFoundException::new);
        return aprofundamento
                .data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", PortaAprofundamento.disponiveis())
                .data("aprofundamentoAtivo", item.slug())
                .data("item", item)
                .data("conteudo", portasAprofundamentoService.carregarParaExibicao(item.slug()));
    }
}
