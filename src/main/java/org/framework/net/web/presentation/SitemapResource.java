package org.framework.net.web.presentation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.web.domain.PaginasPublicas;

import java.util.Optional;

/**
 * Mapa do site para buscadores — o {@code /sitemap.xml}.
 *
 * <p><b>Propósito de negócio:</b> o robots.txt diz por onde o robô <i>não</i>
 * pode passar; o sitemap diz o que existe para ser encontrado. As páginas
 * didáticas são o produto deste projeto, e várias delas (os aprofundamentos de
 * protocolo, por exemplo) só são alcançadas por botão dentro de um grid — um
 * robô que não as encontra não as indexa.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) só entram páginas HTML públicas, nunca
 * rota autenticada, de API ou fechada no robots.txt — sitemap e robots dizendo
 * coisas opostas é contradição que o buscador resolve contra o site. Quem decide
 * o que é público é {@link PaginasPublicas}, a mesma lista que o
 * {@code <link rel="canonical">} consulta: duas listas discordando apareceriam
 * como os dois lados "corretos"; (2) a URL é absoluta e no host canônico do site,
 * senão o Google descarta as entradas como submissão cruzada — o host vem de
 * {@code framework.site.base-url} e, na falta dela, da própria requisição; este
 * código <b>não lê cabeçalho de encaminhamento por conta própria</b>, pela mesma
 * razão que o {@code RequestRateLimiter} não lê: quem decide se o cabeçalho vale
 * é a configuração, não o código; (3) não há {@code lastmod} nem
 * {@code priority} — data de alteração inventada a cada requisição é dado
 * fabricado, e o Google ignora prioridade.</p>
 *
 * <p><b>Por que o host é configurado, e não deduzido:</b> um sitemap declara o
 * <i>endereço canônico</i> das páginas, não o endereço pelo qual alguém pediu o
 * arquivo — e cabeçalho de requisição é escolha de quem chama. Com
 * {@code framework.site.base-url} configurada, nenhuma entrada do cliente alcança
 * a saída; sem ela, a URL segue a requisição, que é o certo em desenvolvimento.
 * A precaução deixou de ser hipotética em 02/09/2026: o perfil {@code prod}
 * ligava {@code allow-forwarded} junto de {@code proxy-address-forwarding}, e
 * nessa combinação o Vert.x lê <b>só</b> o cabeçalho {@code Forwarded} — que o
 * Nginx do NPM não envia — de modo que o {@code UriInfo} enxergava
 * {@code http://} atrás de um proxy TLS. A configuração foi corrigida; o host
 * canônico continua, porque ele não depende de configuração de proxy nenhuma.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> não há entrada externa nem I/O; a
 * lista é estática e o método não lança. Página que deixar de existir vira
 * entrada morta no sitemap — é o {@code SitemapHttpTest} que impede, exigindo
 * 200 de cada URL listada.</p>
 */
@Path("/sitemap.xml")
public class SitemapResource {

    /**
     * Host canônico do site, com esquema e sem barra final.
     *
     * <p>{@code Optional<String>} e não {@code defaultValue=""}: o SmallRye
     * recusa valor vazio explícito (SRCFG00040). Ausente em desenvolvimento — aí
     * a URL segue a requisição.</p>
     */
    @ConfigProperty(name = "framework.site.base-url")
    Optional<String> baseUrlCanonica;

    @GET
    @Produces("application/xml; charset=UTF-8")
    public Response sitemap(@Context UriInfo uriInfo) {
        String base = baseUrlCanonica
                .filter(valor -> !valor.isBlank())
                .map(SitemapResource::semBarraFinal)
                .orElseGet(() -> semBarraFinal(uriInfo.getBaseUri().toString()));

        StringBuilder xml = new StringBuilder(1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String pagina : PaginasPublicas.rotas()) {
            xml.append("  <url><loc>")
                    .append(escapar(base + pagina))
                    .append("</loc></url>\n");
        }
        xml.append("</urlset>\n");

        return Response.ok(xml.toString()).build();
    }

    private static String semBarraFinal(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    /** O host vem do runtime; escapar é higiene de XML, não confiança na origem. */
    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
