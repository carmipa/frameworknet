package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.web.domain.PaginasPublicas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O que o {@code <head>} declara ao buscador — canonical e description.
 *
 * <p><b>Propósito de negócio:</b> duas falhas silenciosas moram aqui. A primeira:
 * este domínio responde 200 tanto em {@code http://} quanto em {@code https://},
 * com o mesmo corpo — sem {@code <link rel="canonical">} quem decide qual URL
 * indexar é o buscador, e ele pode escolher a insegura. A segunda: sem
 * {@code <meta name="description">} o resumo do resultado de busca é montado pelo
 * buscador a partir do texto da tela, que aqui começa por rótulo de menu. Nenhuma
 * das duas quebra o site — as duas apagam o projeto do índice em silêncio, que é
 * a razão de existir uma guarda em vez de uma conferida manual.
 *
 * <p><b>Invariantes do domínio:</b> (1) toda página pública tem canonical
 * absoluto e description não vazia; (2) o canonical de uma página aponta para ela
 * mesma, nunca para outra — canonical errado é pior que canonical ausente, porque
 * manda o buscador indexar o endereço errado; (3) rota fora da lista branca
 * (login, erro, inexistente) <b>não</b> recebe canonical: é o que garante que
 * nenhum caminho vindo do cliente vire atributo do {@code <head>}; (4) query
 * string não entra — {@code /portas?x=1} e {@code /portas} são a mesma página;
 * (5) nenhuma URL que recebe canonical pode estar fechada no {@code robots.txt},
 * porque oferecer ao índice o que se proibiu rastrear é o site contradizendo a si
 * mesmo.
 *
 * <p><b>Comportamento em caso de falha:</b> o teste nomeia a rota e mostra o
 * cabeçalho recebido. A varredura falha <b>fechada</b>: lista de páginas vazia
 * reprova, em vez de aprovar por não ter olhado nada — foi assim que uma guarda
 * de outro projeto passou meses verde e cega.
 */
@QuarkusTest
@DisplayName("SEO do head: canonical e description")
class SeoHttpTest {

    private static final Path ROBOTS =
            Path.of("src", "main", "resources", "META-INF", "resources", "robots.txt");

    private static final Pattern CANONICAL =
            Pattern.compile("<link rel=\"canonical\" href=\"([^\"]*)\">");

    private static final Pattern DESCRIPTION =
            Pattern.compile("<meta name=\"description\" content=\"([^\"]*)\">");

    /** Descrição curta demais não descreve nada — o buscador a descarta e inventa outra. */
    private static final int DESCRICAO_MINIMA = 50;

    /**
     * Marca do {@code shared/base.html} no corpo renderizado.
     *
     * <p>Serve de controle positivo: a ausência de canonical só significa alguma
     * coisa numa resposta que passou pelo layout. Num 404 curto ou num template
     * autônomo ela seria vacuidade, e a guarda estaria aprovando por cegueira.</p>
     */
    private static final String MARCA_DO_LAYOUT = "aed-app";

    /**
     * Uma tag de âncora inteira, com todos os atributos.
     *
     * <p>Captura a tag toda de propósito: o {@code rel} e o {@code href} precisam
     * ser lidos do MESMO elemento. Procurar os dois soltos no HTML acharia um
     * {@code rel="nofollow"} de outro link e aprovaria o errado — o mesmo defeito
     * que "contagem certa não prova conjunto certo".</p>
     */
    private static final Pattern ANCORA = Pattern.compile("<a\\s[^>]*>");

    /** Destino interno de um link, sem âncora nem query. */
    private static final Pattern HREF_INTERNO = Pattern.compile("href=\"(/[^\"#?]*)\"");

    /**
     * Valor do atributo {@code rel} de uma âncora.
     *
     * <p>Lê o VALOR e não a string {@code rel="nofollow"} literal: {@code rel}
     * aceita vários tokens separados por espaço ({@code noopener nofollow}), e um
     * elemento só pode ter um atributo {@code rel}. Procurar a string exata
     * obrigaria a escrever HTML inválido para satisfazer a guarda — guarda que
     * força o erro que deveria impedir.</p>
     */
    private static final Pattern REL = Pattern.compile("\\srel=\"([^\"]*)\"");

    @Test
    @DisplayName("toda página pública declara canonical absoluto e description")
    void paginasPublicasDeclaramCanonicalEDescription() {
        List<String> rotas = PaginasPublicas.rotas();
        // Falha FECHADA: sem rotas não há o que verificar, e "não achei nada" não
        // pode parecer "está tudo certo".
        assertFalse(rotas.isEmpty(), "A lista de páginas públicas está vazia: a guarda não verificou nada.");

        List<String> semCanonical = new ArrayList<>();
        List<String> semDescricao = new ArrayList<>();

        for (String rota : rotas) {
            String html = corpo(rota);

            Matcher canonical = CANONICAL.matcher(html);
            if (!canonical.find() || canonical.group(1).isBlank()) {
                semCanonical.add(rota);
            } else if (!canonical.group(1).startsWith("http")) {
                semCanonical.add(rota + " (canonical relativo: " + canonical.group(1) + ")");
            }

            Matcher descricao = DESCRIPTION.matcher(html);
            if (!descricao.find() || descricao.group(1).strip().length() < DESCRICAO_MINIMA) {
                semDescricao.add(rota);
            }
        }

        assertTrue(semCanonical.isEmpty(),
                () -> "Página pública sem canonical absoluto: " + semCanonical);
        assertTrue(semDescricao.isEmpty(),
                () -> "Página pública sem description de pelo menos "
                        + DESCRICAO_MINIMA + " caracteres: " + semDescricao);
    }

    @Test
    @DisplayName("o canonical de cada página aponta para ela mesma")
    void canonicalApontaParaAPropriaPagina() {
        for (String rota : List.of("/", "/analise", "/portas", "/portas/web", "/protocolos/bgp", "/sobre")) {
            String encontrado = canonicalDe(corpo(rota));
            String esperado = "/".equals(rota) ? "/" : rota;
            assertTrue(encontrado.endsWith(esperado),
                    () -> "O canonical de " + rota + " aponta para outro lugar: " + encontrado);
        }
    }

    @Test
    @DisplayName("query string não entra no canonical")
    void queryStringNaoEntraNoCanonical() {
        String comQuery = canonicalDe(corpo("/portas?utm_source=teste&pagina=3"));
        String semQuery = canonicalDe(corpo("/portas"));
        assertEquals(semQuery, comQuery,
                "A mesma página com query diferente tem de declarar o mesmo endereço canônico.");
        assertFalse(comQuery.contains("utm_source"),
                () -> "Parâmetro de campanha vazou para o canonical: " + comQuery);
    }

    @Test
    @DisplayName("rota fora da lista pública renderiza o layout e ainda assim não recebe canonical")
    void rotaFechadaNaoRecebeCanonical() {
        // A escolha destas duas rotas é deliberada, e a calibração da guarda foi
        // quem a ditou. Na primeira versão os controles eram /login e uma rota
        // inventada: nenhum dos dois renderiza o layout — /login é template
        // autônomo e a rota inventada devolve 404 de 58 bytes —, então os dois
        // passariam mesmo com a lista branca removida. Guarda que aprova por não
        // ter o que olhar é a falha que o protocolo chama de verde e cega.
        //
        // /admin/login e /informacoes respondem 200 e passam pelo shared/base.html,
        // logo TERIAM canonical se o SeoPagina não os recusasse. São controle real.
        for (String rota : List.of("/admin/login", "/informacoes")) {
            String html = corpo(rota);
            // Controle positivo: se o layout não foi renderizado, a ausência do
            // canonical não prova nada e a guarda tem de reprovar, não passar.
            assertTrue(html.contains(MARCA_DO_LAYOUT),
                    () -> "A rota " + rota + " deixou de renderizar o layout; como controle ela"
                            + " não vale mais, porque passaria sem verificar nada.\n" + cabeca(html));
            assertFalse(CANONICAL.matcher(html).find(),
                    () -> "A rota " + rota + " está fora da lista de páginas públicas e não pode declarar"
                            + " canonical.\n" + cabeca(html));
        }
    }

    @Test
    @DisplayName("a tela de login é autônoma e se protege com noindex")
    void telaDeLoginDeclaraNoindex() {
        // login/index.html não estende o shared/base.html (tem head próprio), então
        // o mecanismo do canonical não a alcança — e não deve mesmo. A proteção
        // dela é a meta robots do próprio template. Sem esta guarda, alguém que
        // reescreva a tela remove a tag sem que nada acuse.
        //
        // Vale dizer o que esta tag NÃO faz: /login está em Disallow no robots.txt,
        // e robô que obedece ao Disallow nunca busca a página, logo nunca lê o
        // noindex. As duas juntas não se somam — a meta é a rede de baixo, para o
        // dia em que a rota sair do robots.txt ou o robô ignorá-lo.
        String html = corpo("/login");
        assertTrue(html.contains("name=\"robots\" content=\"noindex\""),
                () -> "A tela de login precisa declarar noindex no próprio head.\n" + cabeca(html));
        assertFalse(CANONICAL.matcher(html).find(),
                () -> "A tela de login não pode declarar canonical.\n" + cabeca(html));
    }

    @Test
    @DisplayName("nada que recebe canonical está fechado no robots.txt")
    void canonicalNaoContradizORobots() {
        Map<String, List<String>> grupos = RobotsTxt.grupos(leRobots());
        List<String> disallowGoogle = grupos.get("googlebot");
        assertTrue(disallowGoogle != null && !disallowGoogle.isEmpty(),
                "Não li regra nenhuma para o Googlebot: a guarda não verificou nada.");

        List<String> contradicoes = PaginasPublicas.rotas().stream()
                .filter(rota -> RobotsTxt.bloqueado(disallowGoogle, rota))
                .toList();

        assertTrue(contradicoes.isEmpty(),
                () -> "Estas páginas declaram canonical mas estão fechadas no robots.txt: " + contradicoes);
    }

    @Test
    @DisplayName("cada página pública tem uma description própria, não a genérica")
    void descriptionNaoSeRepeteEntrePaginas() {
        // O <head> tem uma description padrão para que página nenhuma fique sem
        // nenhuma. O risco desse padrão é virar muleta: página nova nasce sem
        // declarar a sua, herda a genérica e o buscador passa a ver dezenas de
        // resultados descritos com a mesma frase — que ele trata como conteúdo
        // raso. Esta guarda transforma "esqueci de escrever" em build vermelho.
        Map<String, List<String>> porDescricao = new LinkedHashMap<>();
        for (String rota : PaginasPublicas.rotas()) {
            Matcher m = DESCRIPTION.matcher(corpo(rota));
            String descricao = m.find() ? m.group(1).strip() : "(ausente)";
            porDescricao.computeIfAbsent(descricao, chave -> new ArrayList<>()).add(rota);
        }
        assertFalse(porDescricao.isEmpty(), "Nenhuma descrição lida: a guarda não verificou nada.");

        List<String> repetidas = porDescricao.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getValue() + " compartilham a mesma descrição")
                .toList();

        assertTrue(repetidas.isEmpty(),
                () -> "Páginas sem descrição própria (caíram no texto padrão): " + repetidas);
    }

    @Test
    @DisplayName("link para rota fechada no robots é marcado com rel=nofollow")
    void linkParaRotaFechadaEMarcadoNofollow() {
        // Link interno é o caminho pelo qual o buscador DESCOBRE uma URL. Sem
        // nofollow ele descobre /telemetria e /informacoes pela home, tenta
        // rastrear, esbarra no Disallow e as registra como "indexada, mas
        // bloqueada": URL no índice sem conteúdo nenhum, e um aviso permanente no
        // Search Console. O nofollow não substitui o Disallow — um evita a
        // descoberta, o outro evita o rastreio —, e nenhum dos dois é controle de
        // acesso: quem fecha essas rotas é o login.
        List<String> disallow = RobotsTxt.grupos(leRobots()).get("googlebot");
        assertTrue(disallow != null && !disallow.isEmpty(),
                "Não li regra nenhuma para o Googlebot: a guarda não verificou nada.");

        List<String> semNofollow = new ArrayList<>();
        for (String rota : PaginasPublicas.rotas()) {
            Matcher tag = ANCORA.matcher(corpo(rota));
            while (tag.find()) {
                String atributos = tag.group();
                Matcher href = HREF_INTERNO.matcher(atributos);
                if (!href.find()) {
                    continue;
                }
                String destino = href.group(1);
                if (!RobotsTxt.bloqueado(disallow, destino)) {
                    continue;
                }
                Matcher rel = REL.matcher(atributos);
                boolean marcado = rel.find()
                        && List.of(rel.group(1).trim().split("\\s+")).contains("nofollow");
                if (!marcado) {
                    semNofollow.add(rota + " -> " + destino);
                }
            }
        }

        assertTrue(semNofollow.isEmpty(),
                () -> "Link para rota fechada no robots.txt sem rel=\"nofollow\": " + semNofollow);
    }

    private static String corpo(String rota) {
        return given().when().get(rota).then().extract().asString();
    }

    private static String canonicalDe(String html) {
        Matcher m = CANONICAL.matcher(html);
        assertTrue(m.find(), () -> "Esperava um canonical e não encontrei.\n" + cabeca(html));
        return m.group(1);
    }

    private static String cabeca(String html) {
        return html.length() > 1200 ? html.substring(0, 1200) : html;
    }

    private static String leRobots() {
        try {
            return Files.readString(ROBOTS);
        } catch (IOException e) {
            throw new UncheckedIOException("robots.txt não pôde ser lido em " + ROBOTS.toAbsolutePath(), e);
        }
    }
}
