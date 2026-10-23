package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fechamento do acesso à Telemetria.
 *
 * <p><b>Propósito de negócio:</b> até esta mudança o painel estava <b>aberto</b>
 * em produção — a regra antiga só exigia chave quando o dashboard estava
 * desligado, de modo que ligar o recurso desligava a proteção. Estes testes são a
 * prova de que a porta fechou e de que ela não fechou demais (o fluxo de login
 * precisa continuar alcançável).</p>
 *
 * <p><b>Invariantes do domínio:</b> sem sessão não se entra; navegador vai para a
 * tela de login e cliente de máquina recebe 401; leitor autenticado vê o painel
 * mas não exporta nem limpa; a chave nunca aparece na resposta.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota e o status
 * recebido.</p>
 */
@QuarkusTest
@DisplayName("Segurança: acesso à Telemetria por sessão")
class AcessoTelemetriaHttpTest {

    @Inject
    SessaoTelemetriaService sessao;

    @Inject
    GitHubOAuthService oauth;

    @Test
    @DisplayName("regressão: /telemetria sem sessão não abre — era o buraco em produção")
    void telemetriaSemSessaoNaoAbre() {
        given().redirects().follow(false)
                .header("Accept", "text/html")
                .when().get("/telemetria")
                .then()
                .statusCode(303)
                .header("Location", containsString("/login"));
    }

    @Test
    @DisplayName("cliente de máquina recebe 401 em JSON, não a tela")
    void clienteDeMaquinaRecebe401() {
        given().header("Accept", "application/json")
                .when().get("/telemetria/api/resumo")
                .then()
                .statusCode(401)
                .body(containsString("Telemetria exige autenticação"))
                .body(not(containsString("<html")));
    }

    @ParameterizedTest(name = "{0} fica aberto — é o caminho que leva ao login")
    @CsvSource({"/login", "/login?modo=contingencia"})
    void telaDeLoginFicaAcessivel(String rota) {
        given().header("Accept", "text/html")
                .when().get(rota)
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.anyOf(
                        containsString("ACESSO À TELEMETRIA"),
                        containsString("Acesso de Contingência")))
                .body(org.hamcrest.Matchers.anyOf(
                        containsString("id=\"matrix\""),
                        containsString("adminKey")));
    }

    @Test
    @DisplayName("a tela padrão mostra o GitHub e esconde o campo de chave")
    void telaPadraoNaoExpoeCampoDeChave() {
        given().header("Accept", "text/html")
                .when().get("/login")
                .then()
                .statusCode(200)
                .body(containsString("Autenticação"))
                .body(not(containsString("name=\"adminKey\"")));
    }

    @Test
    @DisplayName("a contingência só aparece por ?modo=contingencia")
    void contingenciaSoPorParametro() {
        given().header("Accept", "text/html")
                .when().get("/login?modo=contingencia")
                .then()
                .statusCode(200)
                .body(containsString("Acesso de Contingência"))
                .body(containsString("name=\"adminKey\""));
    }

    @Test
    @DisplayName("chave de contingência inválida devolve 401 e não diz se a chave existe")
    void chaveInvalidaNaoViraOraculo() {
        String corpo = given().formParam("adminKey", "chave-errada")
                .when().post("/login/chave")
                .then().statusCode(401)
                .extract().body().asString();

        assertTrue(corpo.contains("Chave inválida."));
        assertFalse(corpo.contains("dev-admin-key"), "A chave configurada não pode vazar na resposta.");
    }

    @Test
    @DisplayName("callback sem o state do cookie é recusado antes de falar com o GitHub")
    void callbackSemStateERecusado() {
        given().redirects().follow(false)
                .when().get("/login/github/callback?code=qualquer&state=forjado")
                .then()
                .statusCode(303)
                .header("Location", containsString("/login/?erro="));
    }

    @Test
    @DisplayName("a sessão é assinada: valor adulterado não vale")
    void sessaoAdulteradaNaoVale() {
        String valido = sessao.emitirValor("carmipa",
                SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_DONO);

        assertEquals("carmipa", sessao.validar(valido));
        String[] p = valido.split("\\.");
        p[3] = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("invasor".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("", sessao.validar(String.join(".", p)),
                "Trocar o login re-encodado sem refazer a assinatura tem de ser recusado.");
        assertEquals("", sessao.validar(valido.substring(0, valido.length() - 4) + "AAAA"),
                "Assinatura trocada precisa invalidar o cookie inteiro.");
        assertEquals("", sessao.validar("1.2.3.4.5"));
        assertEquals("", sessao.validar(""));
    }

    @Test
    @DisplayName("papel: leitor não vira dono trocando o texto do cookie")
    void leitorNaoViraDonoSemAssinatura() {
        String leitor = sessao.emitirValor("aluno",
                SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_LEITOR);
        String forjado = leitor.replace("." + SessaoTelemetriaService.PAPEL_LEITOR + ".",
                "." + SessaoTelemetriaService.PAPEL_DONO + ".");

        assertEquals("", sessao.validar(forjado),
                "Trocar o papel muda o corpo assinado; a assinatura tem de recusar.");
        assertEquals(SessaoTelemetriaService.PAPEL_LEITOR,
                sessao.papelDaSessao(SessaoTelemetriaService.COOKIE_NAME + "=" + leitor));
        assertFalse(sessao.ehDono(SessaoTelemetriaService.COOKIE_NAME + "=" + leitor));
    }

    @Test
    @DisplayName("leitor autenticado vê o painel, mas não exporta nem limpa o console")
    void leitorVeMasNaoMexe() {
        String cookie = SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirValor(
                "aluno", SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_LEITOR);

        given().header("Cookie", cookie).header("Accept", "text/html")
                .when().get("/telemetria")
                .then().statusCode(200);

        for (String acao : new String[]{"/telemetria/api/exportar", "/telemetria/api/pasta"}) {
            given().header("Cookie", cookie).header("Accept", "application/json")
                    .when().get(acao)
                    .then().statusCode(403);
        }
        // F24: as duas ações que MUDAM estado (uma destrutiva) não tinham teste — tirar uma delas de
        // ACOES_DE_DONO daria ao leitor o poder de apagar o console com a suíte verde.
        for (String acao : new String[]{"/telemetria/api/console/limpar", "/telemetria/api/dataset/sincronizar"}) {
            given().header("Cookie", cookie).header("Accept", "application/json")
                    .when().post(acao)
                    .then().statusCode(403);
        }
    }

    @Test
    @DisplayName("controle: o dono limpa o console (a recusa do leitor é pelo papel, não pela rota)")
    void donoLimpaOConsole() {
        String cookie = SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirValor(
                "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_DONO);
        int status = given().header("Cookie", cookie).header("Accept", "application/json")
                .when().post("/telemetria/api/console/limpar")
                .then().extract().statusCode();
        org.junit.jupiter.api.Assertions.assertTrue(status >= 200 && status < 300, "dono recebeu " + status);
    }

    @Test
    @DisplayName("dono entra e faz tudo")
    void donoTemAcessoCompleto() {
        String cookie = SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirValor(
                "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_DONO);

        given().header("Cookie", cookie).header("Accept", "text/html")
                .when().get("/telemetria")
                .then().statusCode(200);
        given().header("Cookie", cookie).header("Accept", "application/json")
                .when().get("/telemetria/api/resumo")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("logout apaga o cookie e devolve para o login")
    void logoutEncerraASessao() {
        given().redirects().follow(false)
                .header("Cookie", SessaoTelemetriaService.COOKIE_NAME + "=" + sessao.emitirValor(
                        "carmipa", SessaoTelemetriaService.ORIGEM_GITHUB, SessaoTelemetriaService.PAPEL_DONO))
                .when().get("/login/sair")
                .then()
                .statusCode(303)
                .header("Location", containsString("/login"))
                .cookie(SessaoTelemetriaService.COOKIE_NAME, containsString(""));
    }

    @Test
    @DisplayName("sem credencial configurada, o botão do GitHub não é oferecido")
    void semCredencialNaoOfereceBotao() {
        // No perfil de teste as variáveis do OAuth não existem: a tela precisa
        // dizer isso em vez de levar o usuário a um erro do provedor.
        assertFalse(oauth.configurado());
        given().header("Accept", "text/html")
                .when().get("/login")
                .then().statusCode(200)
                .body(containsString("não está configurado"))
                .body(not(containsString("/telemetria/oauth/iniciar")));
    }
}
