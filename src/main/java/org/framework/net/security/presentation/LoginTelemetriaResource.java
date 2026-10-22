package org.framework.net.security.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.framework.net.security.AdminApiKeyService;
import org.framework.net.security.GitHubOAuthService;
import org.framework.net.security.SessaoTelemetriaService;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.resteasy.reactive.RestForm;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Porta de entrada da Telemetria: login pelo GitHub e acesso de contingência.
 *
 * <p><b>Propósito de negócio:</b> o painel de Telemetria mostra eventos, rotas e
 * endereços de quem usou o site — informação que não pode ficar aberta. Esta
 * classe implementa as duas formas de entrar: a normal, pela identidade do GitHub
 * do dono, e a de contingência com a chave administrativa, para quando o GitHub
 * estiver indisponível.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) o parâmetro {@code state} é gerado no
 * servidor, guardado em cookie e conferido na volta — sem isso, um terceiro
 * poderia forçar o seu navegador a concluir um fluxo iniciado por ele; (2) quem
 * se autentica no GitHub mas não está na allowlist recebe <b>403</b>, que o mapper
 * de erro transforma na página "ACESSO BLOQUEADO" — a tentativa é registrada com o
 * login, porque saber <em>quem</em> tentou entrar é informação de segurança; (3)
 * nem a chave administrativa nem o token do GitHub aparecem em log, URL ou
 * telemetria.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> erro na conversa com o GitHub devolve
 * a tela de login com o motivo em texto curto, nunca detalhe interno. Chave de
 * contingência errada devolve a mesma tela com aviso genérico e registra a
 * tentativa — sem dizer se a chave existia, para não servir de oráculo.</p>
 */
@Path("/login")
public class LoginTelemetriaResource {

    private static final String COOKIE_STATE = "TELEMETRIA_OAUTH_STATE";
    private static final String MODULO = "seguranca";
    private static final URI DESTINO = URI.create("/telemetria");
    private static final URI TELA_LOGIN = URI.create("/login");

    private final SecureRandom random = new SecureRandom();

    @Inject
    GitHubOAuthService gitHubOAuthService;

    @Inject
    SessaoTelemetriaService sessaoTelemetriaService;

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("login/index.html")
    Template login;

    @Context
    HttpHeaders httpHeaders;

    /** Tela de login. {@code ?modo=contingencia} abre direto no cartão âmbar. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance telaDeLogin(@QueryParam("modo") String modo, @QueryParam("erro") String erro) {
        // Só a query é texto de fora: passa pelo filtro. Chamadas internas (ex.: "Chave inválida.") são do sistema.
        return montarTela(modo, mensagemDeErro(erro));
    }

    /**
     * Início do fluxo: gera o {@code state}, guarda em cookie e manda ao GitHub.
     *
     * <p><b>Comportamento em caso de falha:</b> sem credencial configurada, volta
     * para a tela de login com o aviso — não redireciona para um erro do GitHub.</p>
     */
    @GET
    @Path("/github")
    public Response iniciar() {
        if (!gitHubOAuthService.configurado()) {
            return Response.seeOther(
                    URI.create("/login/?erro=" + codificar(ERRO_NAO_CONFIGURADO))).build();
        }
        String state = novoState();
        return Response.seeOther(URI.create(gitHubOAuthService.urlDeAutorizacao(state)))
                .cookie(cookieDeState(state, 600))
                .build();
    }

    /**
     * Volta do GitHub.
     *
     * <p><b>Invariantes do domínio:</b> o {@code state} recebido tem de bater com
     * o do cookie <b>antes</b> de qualquer chamada ao GitHub — divergência é
     * tentativa de forçar o fluxo e para aqui.</p>
     */
    @GET
    @Path("/github/callback")
    public Response callback(@QueryParam("code") String code, @QueryParam("state") String state) {
        String esperado = valorDoCookie(COOKIE_STATE);
        if (esperado.isEmpty() || state == null || !constante(esperado, state)) {
            registrar("warn", "login_github", "state_invalido", "");
            return Response.seeOther(
                            URI.create("/login/?erro=" + codificar(ERRO_SESSAO_EXPIRADA)))
                    .cookie(cookieDeState("", 0))
                    .build();
        }

        GitHubOAuthService.Resultado resultado = gitHubOAuthService.autenticar(code);

        if (resultado.autorizado()) {
            registrar("info", "login_github", "ok", resultado.login());
            return Response.seeOther(DESTINO)
                    .cookie(cookieDeState("", 0),
                            sessaoTelemetriaService.emitirCookie(
                                    resultado.login(),
                                    SessaoTelemetriaService.ORIGEM_GITHUB,
                                    SessaoTelemetriaService.PAPEL_DONO))
                    .build();
        }

        if (resultado.identificado()) {
            // Autenticou no GitHub, mas não é o dono: 403, e o mapper de erro
            // devolve a página "ACESSO BLOQUEADO" no desenho do Framework.
            registrar("warn", "login_github", "nao_autorizado", resultado.login());
            throw new ForbiddenException(resultado.motivo());
        }

        registrar("warn", "login_github", "falha", "");
        return Response.seeOther(URI.create("/login/?erro=" + codificar(resultado.motivo())))
                .cookie(cookieDeState("", 0))
                .build();
    }

    /**
     * Acesso de contingência com a chave administrativa.
     *
     * <p><b>Invariantes do domínio:</b> a resposta é a mesma para chave errada e
     * para chave ausente — nada aqui pode servir de oráculo sobre a chave. A rota
     * está no conjunto de caminhos pesados do {@code RateLimitFilter}, o que limita
     * tentativa em série.</p>
     */
    @POST
    @Path("/chave")
    @Produces(MediaType.TEXT_HTML)
    public Response contingencia(@RestForm("adminKey") String adminKey) {
        // Exigir enforcement ativo: com ele desligado, isValid() aprova qualquer
        // coisa, e a porta de contingência aceitaria qualquer chave digitada. Num
        // ambiente que subisse com admin-api-key-required=false, isso seria acesso
        // livre à Telemetria por uma tela que promete o contrário.
        boolean liberado = adminApiKeyService.isEnforcementActive()
                && adminKey != null && !adminKey.isBlank()
                && adminApiKeyService.isValid(adminKey.strip());
        if (liberado) {
            registrar("warn", "login_contingencia", "ok", "");
            return Response.seeOther(DESTINO)
                    .cookie(sessaoTelemetriaService.emitirCookie(
                            "contingencia",
                            SessaoTelemetriaService.ORIGEM_CONTINGENCIA,
                            SessaoTelemetriaService.PAPEL_DONO))
                    .build();
        }
        registrar("warn", "login_contingencia", "recusado", "");
        return Response.ok(montarTela("contingencia", "Chave inválida.").render())
                .type(MediaType.TEXT_HTML)
                .status(Response.Status.UNAUTHORIZED)
                .build();
    }

    /** Encerra a sessão e volta para a tela de login. */
    @GET
    @Path("/sair")
    public Response sair() {
        registrar("info", "logout_telemetria", "ok",
                sessaoTelemetriaService.loginDaSessao(httpHeaders.getHeaderString("Cookie")));
        return Response.seeOther(TELA_LOGIN)
                .cookie(sessaoTelemetriaService.cookieDeSaida())
                .build();
    }

    // ------------------------------------------------------------------ apoio

    private static final String ERRO_NAO_CONFIGURADO = "Login pelo GitHub não configurado.";
    private static final String ERRO_SESSAO_EXPIRADA = "Sessão de login expirada. Tente de novo.";
    private static final String ERRO_GENERICO = "Não foi possível entrar. Tente de novo.";

    /**
     * Texto da faixa de erro: só mensagem que o próprio sistema escreve. Qualquer outro {@code ?erro=} vira a
     * mensagem genérica — a faixa oficial não ecoa texto de quem montou o link (BACK-09, CWE-451).
     */
    static String mensagemDeErro(String erro) {
        if (erro == null || erro.isBlank()) {
            return null;
        }
        if (erro.equals(ERRO_NAO_CONFIGURADO) || erro.equals(ERRO_SESSAO_EXPIRADA)
                || GitHubOAuthService.MOTIVOS_DE_FALHA.contains(erro)) {
            return erro;
        }
        return ERRO_GENERICO;
    }

    private TemplateInstance montarTela(String modo, String erro) {
        return login
                .data("contingencia", "contingencia".equalsIgnoreCase(modo))
                .data("erro", erro == null || erro.isBlank() ? null : erro)
                .data("oauthConfigurado", gitHubOAuthService.configurado())
                .data("dono", gitHubOAuthService.donoParaExibicao());
    }

    private String novoState() {
        byte[] bruto = new byte[24];
        random.nextBytes(bruto);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
    }

    private NewCookie cookieDeState(String valor, int maxAge) {
        return new NewCookie.Builder(COOKIE_STATE)
                .value(valor)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(sessaoTelemetriaService.cookieSeguro())
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private String valorDoCookie(String nome) {
        var cookie = httpHeaders.getCookies().get(nome);
        return cookie == null || cookie.getValue() == null ? "" : cookie.getValue();
    }

    private void registrar(String nivel, String evento, String status, String login) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("resultado", status);
        if (login != null && !login.isBlank()) {
            campos.put("login", login);
        }
        telemetriaLogger.logEvent(nivel, MODULO, evento, status, campos);
    }

    private static String codificar(String texto) {
        return java.net.URLEncoder.encode(texto == null ? "" : texto, StandardCharsets.UTF_8);
    }

    private static boolean constante(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
