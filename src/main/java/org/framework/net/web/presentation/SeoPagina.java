package org.framework.net.web.presentation;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.web.domain.PaginasPublicas;

import java.util.Optional;

/**
 * Endereço canônico da página que está sendo renderizada, para o {@code <head>}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o mesmo conteúdo alcançável por mais de uma URL
 * é conteúdo duplicado, e quem escolhe qual delas indexar é o buscador — não o
 * dono do site. Aqui isso não é hipótese: medido em 08/09/2026,
 * {@code http://frameworknet.carminati.dev.br/} responde <b>200 com o corpo
 * idêntico</b> ao {@code https://} (mesmo MD5), sem redirecionar; e o
 * {@code Strict-Transport-Security} que a resposta carrega é ignorado pelo
 * navegador quando chega fora de TLS (RFC 6797 §7.2), de modo que ele não desempata
 * nada. O {@code <link rel="canonical">} desempata: declara uma única URL oficial
 * — sempre a do host canônico — para todas as variantes (esquema, barra final,
 * query string de campanha). É o mesmo papel do host configurado no
 * {@code SitemapResource}, aplicado à própria página.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) <b>só página pública recebe canonical</b>
 * — a rota é conferida contra {@link PaginasPublicas}, a mesma lista do sitemap.
 * Isso é lista branca, não filtro: nenhum caminho vindo do cliente chega à saída,
 * então não há como injetar atributo no HTML por uma URL forjada, e página de erro
 * ou rota privada simplesmente não ganha a tag; (2) o host é o
 * <b>configurado</b> ({@code framework.site.base-url}), nunca o cabeçalho
 * {@code Host} da requisição — canonical apontando para o host que o cliente
 * escolheu é a forma clássica de um terceiro sequestrar a URL oficial de um site;
 * (3) sem configuração, a URL segue a requisição, que é o certo em
 * desenvolvimento e é o mesmo comportamento do sitemap — as duas peças não podem
 * divergir; (4) a query string nunca entra: {@code /informacoes?ip=1.2.3.4} e
 * {@code /informacoes} são a mesma página.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> falha fechada, devolvendo string
 * vazia — e o template não emite a tag quando o valor é vazio. Isso cobre: rota
 * fora da lista branca, requisição ausente (renderização fora do ciclo HTTP, em
 * que {@link CurrentVertxRequest#getCurrent()} devolve {@code null}) e host
 * canônico configurado em branco. Não lança em nenhum caminho: um
 * {@code <head>} não é lugar para derrubar a página, e canonical ausente é
 * degradação silenciosa aceitável — canonical <i>errado</i> não seria, porque
 * apontaria o buscador para a URL de outra pessoa.</p>
 */
@Named("seo")
@RequestScoped
public class SeoPagina {

    /**
     * Host canônico do site, com esquema e sem barra final.
     *
     * <p>{@code Optional<String>} e não {@code defaultValue=""}: o SmallRye
     * recusa valor vazio explícito (SRCFG00040). É a MESMA propriedade do
     * {@link SitemapResource} de propósito — sitemap e canonical declarando hosts
     * diferentes seria o site contradizendo a si mesmo.</p>
     */
    @ConfigProperty(name = "framework.site.base-url")
    Optional<String> baseUrlCanonica;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    /**
     * URL canônica absoluta da página atual, ou {@code ""} quando não há uma.
     *
     * <p>Vazio significa "não emita a tag", nunca "emita vazia": um
     * {@code <link rel="canonical" href="">} aponta a página para si mesma de um
     * jeito que alguns rastreadores interpretam como a raiz do site.</p>
     */
    public String getCanonical() {
        String rota = rotaAtual();
        if (!PaginasPublicas.contem(rota)) {
            return "";
        }
        String base = getUrlBase();
        if (base.isEmpty()) {
            return "";
        }
        // A raiz já é "/" — concatenar daria "//" no fim da URL canônica.
        return "/".equals(rota) ? base + "/" : base + rota;
    }

    /**
     * Host canônico com esquema e sem barra final, para montar URL absoluta de
     * imagem de compartilhamento. Vazio quando não há requisição nem configuração.
     */
    public String getUrlBase() {
        Optional<String> configurado = baseUrlCanonica.filter(valor -> !valor.isBlank());
        if (configurado.isPresent()) {
            return semBarraFinal(configurado.get().trim());
        }
        RoutingContext contexto = contextoAtual();
        if (contexto == null) {
            return "";
        }
        // Fallback de desenvolvimento: sem host canônico configurado, a origem da
        // requisição é a única verdade disponível — e não há proxy na frente.
        String origem = contexto.request().absoluteURI();
        int barraDoCaminho = origem.indexOf('/', origem.indexOf("//") + 2);
        return semBarraFinal(barraDoCaminho < 0 ? origem : origem.substring(0, barraDoCaminho));
    }

    /**
     * Caminho da requisição, normalizado na forma que {@link PaginasPublicas}
     * registra: absoluto, sem query e sem barra final — exceto a raiz.
     *
     * <p>Usa {@code normalizedPath()} do Vert.x, que já resolve {@code ..} e
     * barras repetidas antes de a comparação acontecer. Ainda assim a decisão
     * final é da lista branca: normalização é higiene, não a proteção.</p>
     */
    private String rotaAtual() {
        RoutingContext contexto = contextoAtual();
        if (contexto == null) {
            return "";
        }
        String caminho = contexto.normalizedPath();
        if (caminho == null || caminho.isBlank()) {
            return "";
        }
        return "/".equals(caminho) ? "/" : semBarraFinal(caminho);
    }

    private RoutingContext contextoAtual() {
        return currentVertxRequest == null ? null : currentVertxRequest.getCurrent();
    }

    private static String semBarraFinal(String valor) {
        return valor.length() > 1 && valor.endsWith("/")
                ? valor.substring(0, valor.length() - 1)
                : valor;
    }
}
