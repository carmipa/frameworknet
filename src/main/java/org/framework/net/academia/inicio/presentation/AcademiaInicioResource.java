package org.framework.net.academia.inicio.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.academia.core.presentation.AcademiaAberta;
import org.framework.net.academia.trilha.application.TrilhaService;

/**
 * A porta de entrada da Academia ({@code /academia}).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> convida a estudar (D5): mostra a trilha inteira — o que já dá
 * para fazer e o que vem depois — e uma demonstração viva, sem pedir conta nem cadastro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só leitura; o HTML é anônimo (o progresso do aluno é lido
 * pelo navegador, nunca escrito pelo servidor na página — R5); nível ainda fechado aparece sem
 * link.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> Academia não pronta ⇒ 503 com a página de erro
 * padrão; falha de template segue o fluxo Qute/JAX-RS de qualquer página.</p>
 */
@Path("/academia")
public class AcademiaInicioResource {

    @Inject
    @Location("academia/inicio/index.html")
    Template index;

    @Inject
    AcademiaAberta academiaAberta;

    @Inject
    TrilhaService trilha;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance inicio() {
        academiaAberta.exigir();
        return index.data("activeMainMenu", "academia")
                .data("niveis", trilha.niveis())
                .data("primeira", trilha.niveis().getFirst().licoes().getFirst());
    }
}
