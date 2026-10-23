package org.framework.net.academia.transporte.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.academia.core.presentation.AcademiaAberta;
import org.framework.net.academia.trilha.application.TrilhaService;
import org.framework.net.academia.trilha.domain.Licao;
import org.framework.net.academia.trilha.domain.Nivel;

/**
 * Páginas do nível Transporte: a visão do nível e as lições do aperto de mão e da janela.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entrega as lições em Ver · Mexer · Provar sobre o TCP — os
 * números de sequência do aperto de mão em três vias e a retransmissão com janela deslizante.
 * Toda conta roda no navegador (R10) com as contas próprias do nível; nenhuma outra fatia é
 * usada (duplicação consciente).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só leitura; rotas fixas (nenhum parâmetro de caminho escolhe
 * template); título, resumo, "próxima lição" e o mapa dos níveis (para o
 * aviso de nível bloqueado) vêm da trilha; o HTML é anônimo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> Academia não pronta ⇒ 503; lição ausente do catálogo ⇒
 * 404 pela página de erro padrão.</p>
 */
@Path("/academia/transporte")
public class TransporteResource {

    private static final String NIVEL = "transporte";

    @Inject
    @Location("academia/transporte/index.html")
    Template visao;

    @Inject
    @Location("academia/transporte/aperto.html")
    Template aperto;

    @Inject
    @Location("academia/transporte/janela.html")
    Template janela;

    @Inject
    AcademiaAberta academiaAberta;

    @Inject
    TrilhaService trilha;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance nivel() {
        academiaAberta.exigir();
        return visao.data("activeMainMenu", "academia").data("nivel", nivelTransporte()).data("niveis", trilha.niveis());
    }

    @GET
    @Path("/aperto")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoAperto() {
        return licao(aperto, "transporte.aperto");
    }

    @GET
    @Path("/janela")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoJanela() {
        return licao(janela, "transporte.janela");
    }

    private TemplateInstance licao(Template template, String id) {
        academiaAberta.exigir();
        Licao licao = trilha.licao(id).orElseThrow(NotFoundException::new);
        return template.data("activeMainMenu", "academia")
                .data("nivel", nivelTransporte())
                .data("niveis", trilha.niveis())
                .data("licao", licao)
                .data("proxima", trilha.proxima(id).orElse(null));
    }

    private Nivel nivelTransporte() {
        return trilha.nivel(NIVEL).orElseThrow(NotFoundException::new);
    }
}
