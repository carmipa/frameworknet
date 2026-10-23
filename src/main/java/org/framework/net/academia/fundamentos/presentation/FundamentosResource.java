package org.framework.net.academia.fundamentos.presentation;

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
 * Páginas do nível Fundamentos: a visão do nível e as três lições (binário, hexadecimal, camadas).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entrega as lições em Ver · Mexer · Provar. Toda conta roda no
 * navegador (R10) — o servidor só monta a página com o que a trilha declara sobre a lição.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só leitura; cada rota é fixa (nenhum parâmetro de caminho
 * escolhe arquivo de template); título, resumo, "próxima lição" e o mapa dos níveis (navegação e
 * aviso de nível bloqueado) vêm da trilha, nunca digitados aqui; o HTML é anônimo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> Academia não pronta ⇒ 503; lição ausente do catálogo
 * (o catálogo mudou sem a página acompanhar) ⇒ 404 pela página de erro padrão, nunca página
 * pela metade.</p>
 */
@Path("/academia/fundamentos")
public class FundamentosResource {

    private static final String NIVEL = "fundamentos";

    @Inject
    @Location("academia/fundamentos/index.html")
    Template visao;

    @Inject
    @Location("academia/fundamentos/binario.html")
    Template binario;

    @Inject
    @Location("academia/fundamentos/hexadecimal.html")
    Template hexadecimal;

    @Inject
    @Location("academia/fundamentos/camadas.html")
    Template camadas;

    @Inject
    AcademiaAberta academiaAberta;

    @Inject
    TrilhaService trilha;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance nivel() {
        academiaAberta.exigir();
        return visao.data("activeMainMenu", "academia").data("nivel", nivelFundamentos()).data("niveis", trilha.niveis());
    }

    @GET
    @Path("/binario")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoBinario() {
        return licao(binario, "fundamentos.binario");
    }

    @GET
    @Path("/hexadecimal")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoHexadecimal() {
        return licao(hexadecimal, "fundamentos.hexadecimal");
    }

    @GET
    @Path("/camadas")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance licaoCamadas() {
        return licao(camadas, "fundamentos.camadas");
    }

    private TemplateInstance licao(Template template, String id) {
        academiaAberta.exigir();
        Licao licao = trilha.licao(id).orElseThrow(NotFoundException::new);
        return template.data("activeMainMenu", "academia")
                .data("nivel", nivelFundamentos())
                .data("niveis", trilha.niveis())
                .data("licao", licao)
                .data("proxima", trilha.proxima(id).orElse(null));
    }

    private Nivel nivelFundamentos() {
        return trilha.nivel(NIVEL).orElseThrow(NotFoundException::new);
    }
}
