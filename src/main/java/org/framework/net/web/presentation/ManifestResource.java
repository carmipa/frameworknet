package org.framework.net.web.presentation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

/**
 * Serve o web app manifest com o tipo de conteúdo correto.
 *
 * <p><b>Propósito de negócio:</b> o manifest é o que faz o navegador oferecer
 * "Instalar" na barra de endereço e permitir o atalho na área de trabalho. Ele
 * existia como arquivo estático, mas o servidor de estáticos do Quarkus não
 * conhece a extensão {@code .webmanifest} e o entregava <b>sem
 * {@code Content-Type} nenhum</b> — navegador estrito descarta o manifest nessa
 * condição, e a opção de instalar simplesmente não aparece, sem erro visível.</p>
 *
 * <p><b>Invariantes do domínio:</b> responde sempre
 * {@code application/manifest+json}, que é o tipo registrado para manifests. O
 * arquivo vive em {@code src/main/resources/pwa/} — fora de
 * {@code META-INF/resources/} de propósito, para que não haja duas rotas
 * possíveis para o mesmo caminho.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> arquivo ausente do classpath devolve
 * 404. Não lança: a ausência do manifest tira a instalação, não pode derrubar o
 * site.</p>
 */
@Path("/manifest.webmanifest")
public class ManifestResource {

    private static final String CAMINHO = "pwa/manifest.webmanifest";

    @GET
    @Produces("application/manifest+json; charset=UTF-8")
    public Response manifest() {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CAMINHO);
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream).build();
    }
}
