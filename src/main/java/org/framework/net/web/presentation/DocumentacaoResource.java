package org.framework.net.web.presentation;

import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.web.support.MarkdownRenderer;
import org.framework.net.web.support.ReadmeLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/documentacao")
public class DocumentacaoResource {

    @Inject
    ReadmeLoader readmeLoader;

    @Inject
    MarkdownRenderer markdownRenderer;

    @Inject
    @io.quarkus.qute.Location("documentacao/index.html")
    Template documentacao;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        ReadmeLoader.ReadmeContent readme = readmeLoader.carregar();
        String conteudoHtml = "";
        List<Map<String, Object>> secoes = List.of();
        List<Map<String, Object>> tocGrupos = List.of();
        String erro = readme.erro();
        if (readme.conteudo() != null) {
            MarkdownRenderer.RenderResult render = markdownRenderer.render(readme.conteudo());
            conteudoHtml = render.html();
            secoes = render.secoes();
            tocGrupos = agruparToc(secoes);
        }
        return documentacao
                .data("activeMainMenu", "documentacao")
                .data("nomeDocumento", "README.md")
                .data("conteudoHtml", new RawString(conteudoHtml))
                .data("secoes", secoes)
                .data("tocGrupos", tocGrupos)
                .data("erro", erro);
    }

    private static List<Map<String, Object>> agruparToc(List<Map<String, Object>> secoes) {
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> secao : secoes) {
            if (!Boolean.TRUE.equals(secao.get("tocVisivel"))) {
                continue;
            }
            String grupo = String.valueOf(secao.get("tocGrupo"));
            grupos.computeIfAbsent(grupo, ignored -> new ArrayList<>()).add(secao);
        }

        List<Map<String, Object>> saida = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grupos.entrySet()) {
            Map<String, Object> grupo = new LinkedHashMap<>();
            grupo.put("titulo", entry.getKey());
            grupo.put("itens", entry.getValue());
            saida.add(grupo);
        }
        return saida;
    }
}
