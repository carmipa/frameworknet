package org.framework.net.certificados.presentation;

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
import org.framework.net.certificados.application.CertificadosAprofundamentoService;
import org.framework.net.certificados.application.CertificadosService;
import org.framework.net.certificados.domain.CertificadoAprofundamento;

/**
 * Entrada HTTP do módulo de Certificados: catálogo (Geral) e aprofundamentos.
 *
 * <p><b>Propósito de negócio:</b> {@code /certificados} é a aba Geral — as três
 * tabelas de referência (formatos, campos X.509, tipos). Abaixo dela ficam os
 * aprofundamentos ({@code /certificados/x509}, {@code /certificados/cadeia}…),
 * organizados por grupo, no mesmo padrão de Protocolos.</p>
 *
 * <p><b>Invariantes do domínio:</b> todas as rotas vivem NESTA classe; o sub-menu
 * de dois níveis (grupo → página) vem de
 * {@link CertificadoAprofundamento#agrupadoPorGrupo()}. Slug inexistente é 404,
 * nunca a página de outro aprofundamento.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> conteúdo ausente já derrubou a app no
 * boot (catálogos falham fechados); slug desconhecido lança
 * {@link NotFoundException} (404 pela página de erro).</p>
 */
@Path("/certificados")
public class CertificadosResource {

    private static final String MENU_ATIVO = "certificados";

    @Inject
    CertificadosService certificadosService;

    @Inject
    CertificadosAprofundamentoService certificadosAprofundamentoService;

    @Inject
    @Location("certificados/index.html")
    Template index;

    @Inject
    @Location("certificados/aprofundamento.html")
    Template aprofundamento;

    /** Aba Geral: as três tabelas de referência. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return comSubMenu(index, "geral")
                .data("catalogo", certificadosService.montarCatalogoExibicao());
    }

    /**
     * Aprofundamento de certificados, resolvido pelo slug da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> slug não registrado resulta em
     * {@link NotFoundException} (404), nunca conteúdo de outra página.</p>
     */
    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundar(@PathParam("slug") String slug) {
        CertificadoAprofundamento item = CertificadoAprofundamento.porSlug(slug)
                .orElseThrow(NotFoundException::new);
        return comSubMenu(aprofundamento, item.slug())
                .data("item", item)
                .data("conteudo", certificadosAprofundamentoService.carregarParaExibicao(item.slug()));
    }

    /** Dados comuns do sub-menu de dois níveis (grupo → página). */
    private TemplateInstance comSubMenu(Template template, String slugAtivo) {
        String grupoAtivo = "geral";
        if (!"geral".equals(slugAtivo)) {
            grupoAtivo = CertificadoAprofundamento.porSlug(slugAtivo)
                    .map(CertificadoAprofundamento::grupo)
                    .orElse("geral");
        }
        return template
                .data("activeMainMenu", MENU_ATIVO)
                .data("grupos", CertificadoAprofundamento.agrupadoPorGrupo())
                .data("grupoAtivo", grupoAtivo)
                .data("aprofundamentoAtivo", slugAtivo);
    }
}
