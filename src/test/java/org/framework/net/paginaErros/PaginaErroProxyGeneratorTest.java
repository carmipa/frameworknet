package org.framework.net.paginaErros;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.paginaErros.application.PaginaErroService.DadosPaginaErro;
import org.framework.net.paginaErros.domain.CatalogoErros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gera as páginas de 502, 503 e 504 que o <b>proxy</b> serve quando a aplicação
 * está fora — e as mantém iguais às do site.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> quando o Nginx não alcança a aplicação, quem
 * responde é o Nginx, e a aplicação não tem como desenhar nada. Medido em
 * 08/09/2026: não havia {@code error_page} para 502/503/504, então o visitante
 * recebia a tela embutida do openresty — cinza, em inglês, sem caminho de volta.
 * Isso aparece <b>a cada deploy</b>, na janela em que o container é recriado.
 *
 * <p>A saída óbvia seria escrever três HTML à mão. O problema é que eles
 * envelheceriam: o site muda, e as três páginas soltas no disco do proxy ficam
 * com a cara de um ano atrás, sem ninguém perceber — justamente porque só
 * aparecem quando algo já deu errado. Por isso elas são <b>geradas do template
 * real</b> ({@code paginaErros/erro.html}) com o catálogo real
 * ({@link CatalogoErros}, que já descreve os três códigos no vocabulário de redes
 * do site: "ROTA SEM PRÓXIMO SALTO", "LINK WAN CAÍDO", "TIMEOUT NA RESPOSTA").
 * Mexeu no template, rodou a suíte, as páginas do proxy acompanham.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) a página <b>não pode depender da
 * aplicação</b> — ela existe para o momento em que a aplicação está fora, então
 * CSS e JS que hoje vêm de {@code /paginaErros/...} são <b>embutidos</b>, e os
 * scripts que só servem ao app (tooltips) são removidos. O script de tradução e as
 * bandeiras do seletor de idioma também vão embutidos (01/10/2026): antes o script
 * era removido e as bandeiras ficavam na tela chamando uma função inexistente —
 * botão morto justamente para quem não lê português. Um
 * {@code <link>} para o CSS do site aqui daria 502 também, e a página de erro
 * apareceria quebrada; (2) o que é externo (fontes, bandeiras) continua externo,
 * porque continua alcançável; (3) links de navegação permanecem — quando o
 * serviço voltar, eles funcionam; (4) o {@code trace_id} não é inventado: numa
 * página estática não existe trace, e escrever um identificador falso mandaria
 * alguém procurar em log um evento que nunca existiu.
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> o teste falha nomeando o código e o
 * que ficou pendente. Ele é gerador <b>e</b> guarda: depois de escrever, relê
 * cada arquivo e exige que não sobre nenhuma referência a recurso da própria
 * aplicação — que é o defeito que tornaria a página inútil no único momento em
 * que ela é usada.
 */
@QuarkusTest
@DisplayName("Páginas de erro do proxy (502/503/504), geradas do template real")
class PaginaErroProxyGeneratorTest {

    /** Onde as páginas ficam versionadas, para o script de instalação levar ao proxy. */
    private static final Path DESTINO = Path.of("scripts", "erro-proxy");

    /**
     * Onde a suíte gera para COMPARAR (auditoria OPS-21: a suíte reescrevia os HTML versionados em toda
     * execução, sem comparar). Só com {@code REGENERAR_ERRO_PROXY=1} ela escreve em {@link #DESTINO}.
     */
    private static final Path GERADO = Path.of("build", "erro-proxy-gerado");

    private static final boolean REGENERAR = "1".equals(System.getenv("REGENERAR_ERRO_PROXY"));

    private static final Path RAIZ_ESTATICOS =
            Path.of("src", "main", "resources", "META-INF", "resources");

    private static final List<Integer> CODIGOS = List.of(502, 503, 504);

    /** Recursos do próprio app que precisam ser embutidos, senão dariam 502 junto. */
    private static final Pattern CSS_DO_APP =
            Pattern.compile("<link rel=\"stylesheet\" href=\"/paginaErros/css/erro\\.css[^\"]*\">");
    /**
     * Fonte de ícones LOCAL do app (nunca CDN). A página do proxy aparece com o app
     * fora do ar, então o CSS vai embutido e a fonte vai em {@code data:} — nem CDN, nem app.
     */
    private static final Pattern CSS_ICONES =
            Pattern.compile("<link rel=\"stylesheet\" href=\"/web/css/material-symbols\\.css[^\"]*\">");
    private static final Pattern JS_MATRIX =
            Pattern.compile("<script src=\"/paginaErros/js/erro-matrix\\.js[^\"]*\"></script>");

    /**
     * Favicon do app, trocado por um SVG embutido.
     *
     * <p>O {@code /icone.png} é servido pela aplicação — daria 502 junto. Um SVG
     * em {@code data:} não custa requisição nenhuma e mantém a aba identificável
     * em vez de cair no ícone genérico do navegador.</p>
     */
    private static final Pattern FAVICON_DO_APP =
            Pattern.compile("<link rel=\"icon\"[^>]*>");

    private static final String FAVICON_EMBUTIDO =
            "<link rel=\"icon\" href=\"data:image/svg+xml,"
            + "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E"
            + "%3Crect width='32' height='32' rx='7' fill='%2305070d'/%3E"
            + "%3Ctext x='16' y='22' font-family='monospace' font-size='15' font-weight='bold'"
            + " fill='%23f0b429' text-anchor='middle'%3E!%3C/text%3E%3C/svg%3E\">";

    /** Scripts que só fazem sentido com a aplicação viva. */
    private static final List<Pattern> JS_DESCARTAVEIS = List.of(
            Pattern.compile("<script[^>]*src=\"/web/js/field-tooltips\\.js[^\"]*\"[^>]*></script>"),
            // O Bootstrap só servia às dicas do field-tooltips (descartado acima) e passou a ser servido pelo
            // próprio site (22/10/2026): com a aplicação fora, ele daria 502 junto.
            Pattern.compile("<script[^>]*src=\"/web/vendor/bootstrap-[^\"]*\"[^>]*></script>"),
            // Tooltip canônico (22/10/2026): sem o adaptador acima ele não tem o que mostrar, e os dois arquivos
            // são da aplicação — dariam 502 junto. Na página do proxy o title nativo continua valendo.
            Pattern.compile("<script[^>]*src=\"/web/js/dica\\.js[^\"]*\"[^>]*></script>"),
            Pattern.compile("<link rel=\"stylesheet\" href=\"/web/css/dica-camada\\.css[^\"]*\">"));

    /**
     * O script de tradução do próprio site, embutido: sem ele as bandeiras chamariam uma função que
     * não existe (botão morto). O element.js do Google continua externo — alcançável com o app fora.
     */
    private static final Pattern JS_TRADUCAO =
            Pattern.compile("<script[^>]*src=\"/web/js/i18n-translate\\.js[^\"]*\"[^>]*></script>");

    /** Bandeiras do seletor de idioma, servidas pelo app: viram {@code data:} na página do proxy. */
    private static final Pattern BANDEIRA =
            Pattern.compile("src=\"/web/img/bandeiras/([a-z]{2})\\.png[^\"]*\"");

    /** O valor exibido no campo trace_id do terminal de diagnóstico. */
    private static final Pattern VALOR_DO_TRACE =
            Pattern.compile("<span class=\"tv tv-id\">([^<]*)</span>");

    /** Sobra proibida: qualquer href/src apontando para arquivo servido pelo app. */
    private static final Pattern RECURSO_DO_APP =
            Pattern.compile("(?:href|src)=\"(/[^\"]*\\.(?:css|js|png|jpg|svg|webmanifest)[^\"]*)\"");

    @Inject
    @Location("paginaErros/erro.html")
    Template erroTemplate;

    @Test
    @DisplayName("gera as três páginas, sem nenhuma dependência da aplicação")
    void geraPaginasAutonomas() {
        String css = lerEstatico("paginaErros/css/erro.css");
        String matrix = lerEstatico("paginaErros/js/erro-matrix.js");
        String traducao = lerEstatico("web/js/i18n-translate.js");
        criarDiretorio();

        List<String> problemas = new ArrayList<>();

        for (int codigo : CODIGOS) {
            String html = renderizar(codigo);

            html = CSS_DO_APP.matcher(html)
                    .replaceAll(Matcher.quoteReplacement("<style>\n" + css + "\n</style>"));
            html = CSS_ICONES.matcher(html)
                    .replaceAll(Matcher.quoteReplacement("<style>\n" + cssIconesEmbutido() + "\n</style>"));
            html = JS_MATRIX.matcher(html)
                    .replaceAll(Matcher.quoteReplacement("<script>\n" + matrix + "\n</script>"));
            html = FAVICON_DO_APP.matcher(html)
                    .replaceAll(Matcher.quoteReplacement(FAVICON_EMBUTIDO));
            html = JS_TRADUCAO.matcher(html)
                    .replaceAll(Matcher.quoteReplacement("<script>\n" + traducao + "\n</script>"));
            html = BANDEIRA.matcher(html).replaceAll(m -> Matcher.quoteReplacement(
                    "src=\"data:image/png;base64," + bandeiraEmBase64(m.group(1)) + "\""));
            for (Pattern descartavel : JS_DESCARTAVEIS) {
                html = descartavel.matcher(html).replaceAll("");
            }
            html = html.replace("<head>", "<head>\n    " + avisoDeOrigem(codigo));

            Path arquivo = (REGENERAR ? DESTINO : GERADO).resolve(codigo + ".html");
            escrever(arquivo, html);
            if (!REGENERAR) {
                String versionado = ler(DESTINO.resolve(codigo + ".html")).replace("\r\n", "\n");
                if (!versionado.equals(html.replace("\r\n", "\n"))) {
                    problemas.add(codigo + ".html versionado está desatualizado em relação ao template: rode"
                            + " REGENERAR_ERRO_PROXY=1 ./gradlew test --tests '*PaginaErroProxyGeneratorTest' e comite");
                }
            }

            // Guarda: relê o que foi gravado. Gerar não prova que ficou autônomo.
            String gravado = ler(arquivo);
            Matcher sobra = RECURSO_DO_APP.matcher(gravado);
            while (sobra.find()) {
                problemas.add(codigo + ".html ainda pede " + sobra.group(1));
            }
            if (!gravado.contains("<style>")) {
                problemas.add(codigo + ".html ficou sem o CSS embutido");
            }
            if (gravado.contains("aedTranslate(") && !gravado.contains("function aedTranslate(")) {
                problemas.add(codigo + ".html tem bandeira chamando aedTranslate sem o script de tradução"
                        + " (botão morto para quem não lê português)");
            }
            if (!gravado.contains(String.valueOf(codigo))) {
                problemas.add(codigo + ".html não menciona o próprio código");
            }
        }

        assertTrue(problemas.isEmpty(),
                () -> "Página de erro do proxy dependendo da aplicação — ela aparece justamente"
                        + " quando a aplicação está fora:\n  " + String.join("\n  ", problemas));
    }

    /** A página que o service worker mostra quando o aparelho está sem rede. */
    private static final Path OFFLINE = RAIZ_ESTATICOS.resolve("offline.html");

    /**
     * Gera a página offline do mesmo template e do mesmo catálogo das páginas de erro.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a página offline era um HTML escrito à mão, com visual diferente
     * de todas as outras telas de erro do site. Gerada daqui, ela acompanha o template como as do
     * proxy.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> ao contrário das páginas do proxy, os recursos continuam como
     * {@code <link>}/{@code <script>} para o próprio site — quem a serve é o service worker, a partir da
     * casca pré-cacheada (a guarda {@code ServiceWorkerCacheGuardTest} exige cada um deles na casca), e
     * embutir a fonte de ícones faria cada visitante baixar quase meio megabyte na instalação. Os
     * scripts que só servem à aplicação viva saem, como no proxy.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> o teste falha se o arquivo versionado divergir do gerado
     * ou se a página perder o texto do catálogo ou o botão de recarregar.</p>
     */
    @Test
    @DisplayName("gera a página offline do mesmo template das páginas de erro")
    void geraPaginaOffline() {
        CatalogoErros.ErroApresentado erro = CatalogoErros.semConexao();
        DadosPaginaErro dados = new DadosPaginaErro(erro, CatalogoErros.SEM_CONEXAO,
                "o pedido não saiu do aparelho", "", "sem trace — nenhuma requisição chegou ao servidor");
        String html = erroTemplate.data("erro", erro).data("dados", dados).render();
        for (Pattern descartavel : JS_DESCARTAVEIS) {
            html = descartavel.matcher(html).replaceAll("");
        }
        html = html.replace("<head>", "<head>\n    <!-- GERADO AUTOMATICAMENTE por PaginaErroProxyGeneratorTest a partir de\n"
                + "     templates/paginaErros/erro.html + CatalogoErros.semConexao().\n"
                + "     NAO EDITE ESTE ARQUIVO A MAO. Servido pelo service worker quando o aparelho\n"
                + "     esta sem rede; cada recurso daqui precisa estar na casca PRE_CACHE do sw.js. -->");

        criarDiretorio();
        Path arquivo = REGENERAR ? OFFLINE : GERADO.resolve("offline.html");
        escrever(arquivo, html);
        if (!REGENERAR) {
            assertTrue(ler(OFFLINE).replace("\r\n", "\n").equals(html.replace("\r\n", "\n")),
                    "offline.html versionado está desatualizado em relação ao template: rode"
                            + " REGENERAR_ERRO_PROXY=1 ./gradlew test --tests '*PaginaErroProxyGeneratorTest' e comite");
        }
        String gravado = ler(arquivo);
        assertTrue(gravado.contains(erro.titulo()) && gravado.contains(erro.badge()),
                "A página offline perdeu o texto do catálogo.");
        assertTrue(gravado.contains("Sem conexão | Framework de Redes"), "A aba da página offline perdeu o título.");
        assertTrue(gravado.contains("location.reload()"), "A página offline perdeu o botão de tentar de novo.");
        assertFalse(gravado.contains("HTTP 0"), "Sem rede não existe código HTTP para mostrar.");
    }

    @Test
    @DisplayName("as três páginas trazem o texto do catálogo, não um texto paralelo")
    void usamOTextoDoCatalogo() {
        for (int codigo : CODIGOS) {
            Path arquivo = DESTINO.resolve(codigo + ".html");
            assertTrue(Files.exists(arquivo),
                    () -> "Falta " + arquivo + " — rode a suíte para regenerar.");

            String html = ler(arquivo);
            CatalogoErros.ErroApresentado esperado = CatalogoErros.porCodigo(codigo);

            assertTrue(html.contains(esperado.badge()),
                    () -> codigo + ".html não traz o badge do catálogo (" + esperado.badge()
                            + "). Texto paralelo diverge do site com o tempo.");
            assertTrue(html.contains(esperado.titulo()),
                    () -> codigo + ".html não traz o título do catálogo (" + esperado.titulo() + ").");
        }
    }

    @Test
    @DisplayName("não inventam um trace_id que ninguém vai achar no log")
    void naoInventamTraceId() {
        // A primeira versão desta guarda casava o documento INTEIRO contra
        // ".*trace_id.*[0-9a-f]{16,}.*" e reprovava por causa do CSS e do JS
        // embutidos, que têm sequências hexadecimais longas. Instrumento que
        // acusa o arquivo errado não mede o que diz medir: agora ela extrai o
        // VALOR do campo e julga só ele.
        for (int codigo : CODIGOS) {
            String html = ler(DESTINO.resolve(codigo + ".html"));
            Matcher campo = VALOR_DO_TRACE.matcher(html);
            assertTrue(campo.find(),
                    () -> codigo + ".html não tem o campo trace_id do terminal de diagnóstico;"
                            + " o template mudou e esta guarda deixou de ver o que julga.");
            String valor = campo.group(1).strip();
            assertFalse(valor.matches("[0-9a-fA-F-]{16,}"),
                    () -> codigo + ".html traz um trace_id com cara de real (" + valor + ")."
                            + " A página é estática e servida pelo proxy: o pedido nunca chegou à"
                            + " aplicação, então não há evento nenhum com esse identificador.");
        }
    }

    private String renderizar(int codigo) {
        CatalogoErros.ErroApresentado erro = CatalogoErros.porCodigo(codigo);
        DadosPaginaErro dados = new DadosPaginaErro(
                erro,
                codigo,
                "requisição não chegou à aplicação",
                "",
                "sem trace — o erro ocorreu antes da aplicação");
        return erroTemplate.data("erro", erro).data("dados", dados).render();
    }

    private static String avisoDeOrigem(int codigo) {
        return "<!-- GERADO AUTOMATICAMENTE por PaginaErroProxyGeneratorTest a partir de\n"
                + "     templates/paginaErros/erro.html + CatalogoErros(" + codigo + ").\n"
                + "     NAO EDITE ESTE ARQUIVO A MAO: a proxima execucao da suite o sobrescreve.\n"
                + "     Para mudar o visual, mude o template do site — as tres paginas acompanham.\n"
                + "     Servido pelo NGINX (error_page " + codigo + ") quando a aplicacao esta fora,\n"
                + "     por isso CSS e JS estao embutidos: nada aqui pode depender do app. -->";
    }

    private static void criarDiretorio() {
        try {
            Files.createDirectories(REGENERAR ? DESTINO : GERADO);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui criar " + DESTINO.toAbsolutePath(), e);
        }
    }

    /** material-symbols.css com a URL da fonte trocada pelo próprio WOFF2 em base64. */
    private static String cssIconesEmbutido() {
        try {
            byte[] fonte = Files.readAllBytes(RAIZ_ESTATICOS.resolve("web/fonts/material-symbols-outlined.woff2"));
            String dados = "data:font/woff2;base64," + java.util.Base64.getEncoder().encodeToString(fonte);
            return lerEstatico("web/css/material-symbols.css")
                    .replaceAll("url\\('[^']*material-symbols-outlined\\.woff2[^']*'\\)",
                            Matcher.quoteReplacement("url('" + dados + "')"));
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui embutir a fonte de ícones", e);
        }
    }

    private static String lerEstatico(String relativo) {
        return ler(RAIZ_ESTATICOS.resolve(relativo));
    }

    /** Bandeira do seletor de idioma em base64 (alguns centos de bytes cada). */
    private static String bandeiraEmBase64(String pais) {
        try {
            return java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(RAIZ_ESTATICOS.resolve("web/img/bandeiras/" + pais + ".png")));
        } catch (IOException e) {
            throw new UncheckedIOException("bandeira " + pais + " ausente — a página do proxy ficaria quebrada", e);
        }
    }

    private static String ler(Path caminho) {
        try {
            return Files.readString(caminho);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + caminho.toAbsolutePath(), e);
        }
    }

    private static void escrever(Path caminho, String conteudo) {
        try {
            Files.writeString(caminho, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui gravar " + caminho.toAbsolutePath(), e);
        }
    }
}
