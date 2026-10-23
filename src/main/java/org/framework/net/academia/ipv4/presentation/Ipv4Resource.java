package org.framework.net.academia.ipv4.presentation;

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
 * Páginas do nível IPv4: a visão do nível e as lições de máscara e de sub-redes.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entrega as lições em Ver · Mexer · Provar sobre a régua de 32
 * bits. Toda conta roda no navegador (R10) com as contas próprias do nível — nem a Calculadora do
 * site nem as contas de Fundamentos são usadas (D2/D3, duplicação consciente).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só leitura; rotas fixas (nenhum parâmetro de caminho escolhe
 * template); título, resumo, "próxima lição" e o mapa dos níveis (para o
 * aviso de nível bloqueado) vêm da trilha; o HTML é anônimo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> Academia não pronta ⇒ 503; lição ausente do catálogo ⇒
 * 404 pela página de erro padrão.</p>
 */
@Path("/academia/ipv4")
public class Ipv4Resource {

    private static final String NIVEL = "ipv4";

    @Inject
    @Location("academia/ipv4/index.html")
    Template visao;

    @Inject
    @Location("academia/ipv4/mascara.html")
    Template mascara;

    @Inject
    @Location("academia/ipv4/subredes.html")
    Template subredes;

    @Inject
    AcademiaAberta academiaAberta;

    @Inject
    TrilhaService trilha;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance nivel() {
        academiaAberta.exigir();
        return visao.data("activeMainMenu", "academia").data("nivel", nivelIpv4()).data("niveis", trilha.niveis());
    }

    @GET
    @Path("/mascara")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoMascara() {
        return licao(mascara, "ipv4.mascara");
    }

    @GET
    @Path("/subredes")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoSubredes() {
        return licao(subredes, "ipv4.subredes");
    }

    private TemplateInstance licao(Template template, String id) {
        academiaAberta.exigir();
        Licao licao = trilha.licao(id).orElseThrow(NotFoundException::new);
        return template.data("activeMainMenu", "academia")
                .data("nivel", nivelIpv4())
                .data("niveis", trilha.niveis())
                .data("licao", licao)
                .data("proxima", trilha.proxima(id).orElse(null));
    }

    private Nivel nivelIpv4() {
        return trilha.nivel(NIVEL).orElseThrow(NotFoundException::new);
    }
}
