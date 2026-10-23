package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.security.SessaoTelemetriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Cobertura de fumaça de todas as rotas do menu principal.
 *
 * <p><b>Propósito de negócio:</b> o menu é a porta de entrada do sistema. Um
 * item que abre em erro 500 ou renderiza template quebrado é a falha mais cara
 * possível — o usuário nem chega ao recurso. Este teste garante que cada função
 * do menu abre, traz o menu de volta e se marca como ativa.</p>
 *
 * <p><b>Invariantes do domínio:</b> a lista aqui deve espelhar
 * {@code shared/main_menu.html}; um item novo no menu sem linha nova aqui é
 * regressão de cobertura, e o teste {@code menuNaoTemItemSemCobertura} falha
 * justamente para forçar a atualização.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota, o status
 * recebido e o marcador ausente.</p>
 */
@QuarkusTest
@DisplayName("Menu principal: todas as rotas abrem e se marcam como ativas")
class MenuRotasHttpTest {

    @Inject
    SessaoTelemetriaService sessao;

    /**
     * Cookie de dono para as rotas protegidas.
     *
     * <p>A Telemetria deixou de ser publica: o menu continua levando ate ela, mas
     * abrir exige sessao. O teste de fumaca do menu autentica para continuar
     * provando que a PAGINA abre - quem prova que ela fecha sem sessao e o
     * AcessoTelemetriaHttpTest.</p>
     */
    private String cookieDeDono() {
        return SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirCookie(
                "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB,
                SessaoTelemetriaService.PAPEL_DONO).getValue();
    }

    /** Rotas do menu principal, na ordem em que aparecem em shared/main_menu.html. */
    private static final List<String> ROTAS_DO_MENU = List.of(
            "/", "/academia", "/analise", "/calculadora", "/ipv6/analise", "/ipv6", "/ipv6/resolucao",
            "/laboratorios", "/portas", "/protocolos", "/certificados",
            "/camadas", "/criptografia", "/wifi", "/resolucao-problemas",
            "/localizacao", "/trafego", "/ferramentas", "/diagnostico", "/seguranca", "/telemetria",
            "/documentacao", "/sobre");

    @ParameterizedTest(name = "{0} abre e destaca \"{1}\" no menu")
    @CsvSource({
            "/,                       Início",
            "/analise,                Análise Didática",
            "/calculadora,            Calculadora",
            "/ipv6/analise,           Análise Didática IPv6",
            "/ipv6,                   Calculadora IPv6",
            "/ipv6/resolucao,         Resolução IPv6",
            "/laboratorios,           Laboratórios",
            "/academia,               Academia",
            "/portas,                 Portas",
            "/protocolos,             Protocolos",
            "/certificados,           Certificados",
            "/camadas,                Camadas",
            "/criptografia,           Criptografia",
            "/wifi,                   Wi-Fi",
            "/resolucao-problemas,    Resolução (VLSM+WAN)",
            "/localizacao,            Localização",
            "/trafego,                Tráfego",
            "/ferramentas,            Ferramentas",
            "/diagnostico,            Diagnóstico",
            "/seguranca,              Segurança (ACL)",
            "/telemetria,             Telemetria",
            "/documentacao,           Documentação",
            "/sobre,                  Sobre"
    })
    void rotaDoMenuAbreComOMenuERotuloCorreto(String rota, String rotulo) {
        String html = given()
                .header("Cookie", cookieDeDono())
                .when().get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString(rotulo))
                .body(not(containsString("Internal Server Error")))
                .extract().asString();
        // F23: o rótulo aparece no menu de TODA página, então conferi-lo não provava nada. O que prova
        // é o link desta rota marcado como ativo — e outro link NÃO marcado (A1).
        org.junit.jupiter.api.Assertions.assertTrue(classeDoLinkDoMenu(html, rota).contains("is-active"),
                rota + " não se marca como ativa no menu");
        String outra = "/sobre".equals(rota) ? "/portas" : "/sobre";
        org.junit.jupiter.api.Assertions.assertFalse(classeDoLinkDoMenu(html, outra).contains("is-active"),
                rota + " marcou também " + outra + " como ativa");
    }

    /** Classe do link {@code <a href="rota" class="aed-nav-link ...">} do menu (vazio se ausente). */
    private static String classeDoLinkDoMenu(String html, String rota) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<a\\s+href=\"" + java.util.regex.Pattern.quote(rota) + "\"[^>]*?\\sclass=\"(aed-nav-link[^\"]*)\"")
                .matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "link do menu para " + rota + " não encontrado: instrumento cego");
        return m.group(1);
    }

    @ParameterizedTest(name = "{0} expõe todos os demais itens do menu")
    @CsvSource({"/", "/analise", "/calculadora", "/ipv6/analise", "/ipv6", "/ipv6/resolucao",
            "/laboratorios", "/academia", "/portas", "/protocolos", "/certificados",
            "/camadas", "/criptografia", "/wifi", "/resolucao-problemas",
            "/localizacao", "/trafego", "/ferramentas", "/diagnostico", "/seguranca", "/telemetria",
            "/documentacao", "/sobre"})
    void todaPaginaDoMenuNavegaParaAsOutras(String rota) {
        var resposta = given().header("Cookie", cookieDeDono())
                .when().get(rota).then().statusCode(200);
        for (String destino : ROTAS_DO_MENU) {
            resposta.body(containsString("href=\"" + destino + "\""));
        }
    }

    /**
     * Ordem de Paulo (01/10/2026, corrigida no mesmo dia): Início é o primeiro item do menu e a
     * Academia vem logo depois. A1: os dois carregam o mesmo sinal (aed-nav-link) — só a posição separa.
     */
    @Test
    @DisplayName("Início é o primeiro item do menu e a Academia vem logo depois")
    void inicioPrimeiroEAcademiaLogoDepois() {
        String html = given().when().get("/").then().statusCode(200).extract().asString();
        java.util.regex.Matcher primeiro = java.util.regex.Pattern
                .compile("<a\\s+href=\"([^\"]+)\"[^>]*?\\sclass=\"aed-nav-link[ \"]").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(primeiro.find(), "nenhum item de menu encontrado: instrumento cego");
        org.junit.jupiter.api.Assertions.assertEquals("/", primeiro.group(1), "Início é o primeiro item");
        org.junit.jupiter.api.Assertions.assertTrue(primeiro.find(), "o menu tem mais de um item");
        org.junit.jupiter.api.Assertions.assertEquals("/academia", primeiro.group(1), "Academia vem logo depois de Início");
    }

    @Test
    @DisplayName("regressão: item novo no menu sem teste correspondente quebra o build")
    void menuNaoTemItemSemCobertura() {
        String html = given().when().get("/").then().statusCode(200).extract().asString();

        int itens = contarOcorrencias(html, "class=\"aed-nav-link ");
        org.junit.jupiter.api.Assertions.assertEquals(ROTAS_DO_MENU.size(), itens,
                "O menu principal tem " + itens + " item(ns), mas o teste cobre "
                        + ROTAS_DO_MENU.size() + ". Adicione a rota nova em ROTAS_DO_MENU e no @CsvSource.");
    }

    @Test
    void rotaInexistenteDevolve404ENaoErroDeServidor() {
        given()
                .when().get("/rota-que-nao-existe")
                .then()
                .statusCode(404);
    }

    private static int contarOcorrencias(String texto, String agulha) {
        int total = 0;
        int idx = texto.indexOf(agulha);
        while (idx >= 0) {
            total++;
            idx = texto.indexOf(agulha, idx + agulha.length());
        }
        return total;
    }
}
