package org.framework.net.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;

/**
 * Autenticação do dono do sistema pelo GitHub (OAuth 2.0).
 *
 * <p><b>Propósito de negócio:</b> o painel de Telemetria expõe eventos, rotas e
 * IPs de quem usou o site. Ele precisa de dono, e o dono é uma identidade do
 * GitHub — não uma senha compartilhada. O fluxo é o padrão de três pernas:
 * o navegador vai ao GitHub, volta com um código de uso único, o servidor troca
 * esse código por um token e pergunta ao GitHub <em>quem</em> autorizou. Só então
 * o login é comparado com a lista de permitidos.</p>
 *
 * <p><b>Invariantes do domínio:</b> quem decide a identidade é o GitHub, nunca o
 * cliente — nenhum dado vindo do navegador (login, e-mail, cookie) é aceito como
 * prova de quem é o usuário. O {@code client secret} só existe em variável de
 * ambiente e <b>nunca</b> aparece em log, em URL ou em mensagem de erro; o token
 * de acesso é usado e descartado, jamais persistido. Configuração incompleta faz
 * {@link #configurado()} devolver falso, e o botão do GitHub some da tela em vez
 * de levar o usuário a um erro do provedor.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nada lança para fora. Rede fora,
 * código inválido, resposta inesperada do GitHub ou login fora da allowlist
 * produzem um {@link Resultado} negativo com motivo curto, próprio para exibir e
 * registrar — sem detalhe que sirva de mapa para quem estiver sondando.</p>
 */
@ApplicationScoped
public class GitHubOAuthService {

    private static final String URL_AUTORIZACAO = "https://github.com/login/oauth/authorize";
    private static final String URL_TOKEN = "https://github.com/login/oauth/access_token";
    private static final String URL_USUARIO = "https://api.github.com/user";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Escopo vazio: só queremos saber quem é. Nenhum acesso a repositório é pedido. */
    private static final String ESCOPO = "read:user";

    // Optional<String>, nao defaultValue="": o SmallRye recusa valor vazio explicito
    // vindo de ${VAR:} (SRCFG00040) e a aplicacao nem sobe. Licao ja paga neste
    // projeto no token de ingestao do modulo de Trafego.
    @ConfigProperty(name = "framework.security.github-oauth.client-id")
    Optional<String> clientIdConfig;

    @ConfigProperty(name = "framework.security.github-oauth.client-secret")
    Optional<String> clientSecretConfig;

    @ConfigProperty(name = "framework.security.github-oauth.logins-permitidos")
    Optional<String> loginsPermitidosConfig;

    @ConfigProperty(name = "framework.security.github-oauth.callback-url")
    Optional<String> callbackUrlConfig;

    private String clientId() {
        return clientIdConfig.orElse("").strip();
    }

    private String clientSecret() {
        return clientSecretConfig.orElse("").strip();
    }

    private String callbackUrl() {
        return callbackUrlConfig.orElse("").strip();
    }

    @Inject
    ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    /**
     * A aplicação tem credencial para oferecer login pelo GitHub?
     *
     * <p><b>Invariantes do domínio:</b> exige as três peças — id, segredo e ao
     * menos um login autorizado. Faltando qualquer uma, o login não é oferecido:
     * oferecer botão que leva a erro do provedor é pior que não oferecer, e uma
     * allowlist vazia autorizaria ninguém (ou, num descuido de código, todos).</p>
     */
    public boolean configurado() {
        return !clientId().isBlank() && !clientSecret().isBlank() && !loginsAutorizados().isEmpty();
    }

    /** Logins do GitHub autorizados, em minúsculas. */
    public Set<String> loginsAutorizados() {
        Set<String> logins = new LinkedHashSet<>();
        for (String parte : loginsPermitidosConfig.orElse("").split(",")) {
            String limpo = parte.strip().toLowerCase(Locale.ROOT);
            if (!limpo.isEmpty()) {
                logins.add(limpo);
            }
        }
        return logins;
    }

    /** Texto pronto para a tela: quem pode entrar. Não expõe credencial alguma. */
    public String donoParaExibicao() {
        return loginsAutorizados().stream().map(l -> "@" + l).reduce((a, b) -> a + " · " + b).orElse("—");
    }

    /**
     * URL para onde mandar o navegador iniciar o login.
     *
     * <p><b>Invariantes do domínio:</b> o {@code state} é obrigatório e viaja de
     * volta no callback — é o que impede que um terceiro force o seu navegador a
     * completar um fluxo que ele iniciou (CSRF de login). Quem chama é responsável
     * por gerar o valor e conferi-lo na volta.</p>
     */
    public String urlDeAutorizacao(String state) {
        return URL_AUTORIZACAO
                + "?client_id=" + codificar(clientId())
                + "&scope=" + codificar(ESCOPO)
                + "&state=" + codificar(state)
                + (callbackUrl().isBlank() ? "" : "&redirect_uri=" + codificar(callbackUrl()))
                + "&allow_signup=false";
    }

    /**
     * Troca o código do callback pela identidade de quem autorizou.
     *
     * <p><b>Invariantes do domínio:</b> o login devolvido vem de
     * {@code GET /user} autenticado, isto é, do próprio GitHub — e é conferido
     * contra a allowlist antes de qualquer sessão nascer. Token de acesso é
     * variável local: não vai para log, cookie, banco ou telemetria.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@link Resultado} negativo
     * para código inválido, credencial errada, indisponibilidade do GitHub ou
     * login não autorizado. Nunca lança.</p>
     */
    public Resultado autenticar(String code) {
        if (!configurado()) {
            return Resultado.negado("Login pelo GitHub não está configurado neste ambiente.");
        }
        if (code == null || code.isBlank()) {
            return Resultado.negado("Código de autorização ausente.");
        }

        String token;
        try {
            token = trocarCodigoPorToken(code);
        } catch (Exception ex) {
            return Resultado.negado("Não foi possível concluir a troca com o GitHub.");
        }
        if (token == null || token.isBlank()) {
            return Resultado.negado("O GitHub recusou o código de autorização.");
        }

        String login;
        try {
            login = consultarLogin(token);
        } catch (Exception ex) {
            return Resultado.negado("Não foi possível consultar a identidade no GitHub.");
        }
        if (login == null || login.isBlank()) {
            return Resultado.negado("O GitHub não devolveu a identidade do usuário.");
        }

        if (!loginsAutorizados().contains(login.toLowerCase(Locale.ROOT))) {
            return Resultado.naoAutorizado(login);
        }
        return Resultado.autorizado(login);
    }

    private String trocarCodigoPorToken(String code) throws Exception {
        String corpo = "client_id=" + codificar(clientId())
                + "&client_secret=" + codificar(clientSecret())
                + "&code=" + codificar(code)
                + (callbackUrl().isBlank() ? "" : "&redirect_uri=" + codificar(callbackUrl()));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(URL_TOKEN))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "framework-net-java-quarkus")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resposta = cliente().send(requisicao, HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) {
            return null;
        }
        JsonNode json = objectMapper.readTree(resposta.body());
        JsonNode acesso = json.get("access_token");
        return acesso == null ? null : acesso.asText("");
    }

    private String consultarLogin(String token) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(URL_USUARIO))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "framework-net-java-quarkus")
                .GET()
                .build();

        HttpResponse<String> resposta = cliente().send(requisicao, HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) {
            return null;
        }
        JsonNode json = objectMapper.readTree(resposta.body());
        JsonNode login = json.get("login");
        return login == null ? null : login.asText("");
    }

    /** Cliente HTTP criado sob demanda, sem seguir redirecionamento para outro host. */
    private HttpClient cliente() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }

    /**
     * Desfecho de uma tentativa de login.
     *
     * <p><b>Invariantes do domínio:</b> {@code login} só vem preenchido quando o
     * GitHub confirmou a identidade — inclusive no caso não autorizado, para que a
     * telemetria registre <em>quem</em> tentou entrar, que é informação de
     * segurança legítima.</p>
     */
    public record Resultado(boolean autorizado, boolean identificado, String login, String motivo) {

        static Resultado autorizado(String login) {
            return new Resultado(true, true, login, "");
        }

        static Resultado naoAutorizado(String login) {
            return new Resultado(false, true, login,
                    "A conta @" + login + " não está autorizada a abrir a Telemetria.");
        }

        static Resultado negado(String motivo) {
            return new Resultado(false, false, "", motivo);
        }
    }

    /**
     * Os motivos de falha que este serviço escreve — a tela de login só exibe estes (BACK-09: o texto vem pela
     * query {@code ?erro=}, e ecoar qualquer texto fazia da faixa oficial de erro uma isca de phishing).
     */
    public static final java.util.Set<String> MOTIVOS_DE_FALHA = java.util.Set.of(
            "Login pelo GitHub não está configurado neste ambiente.",
            "Código de autorização ausente.",
            "Não foi possível concluir a troca com o GitHub.",
            "O GitHub recusou o código de autorização.",
            "Não foi possível consultar a identidade no GitHub.",
            "O GitHub não devolveu a identidade do usuário.");
}
