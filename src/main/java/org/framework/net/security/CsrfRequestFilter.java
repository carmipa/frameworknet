package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.paginaErros.presentation.PaginaErroResposta;
import org.framework.net.telemetria.TelemetriaLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Conferência do token CSRF (cookie × cabeçalho/formulário) em toda requisição que altera estado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> impedir que outro site faça o navegador do aluno enviar formulário para
 * este, usando a sessão dele.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> POST/PUT/DELETE/PATCH sem par válido é recusado com 403, comparado em
 * tempo constante. Quem navega (formulário comum) recebe a página de erro do site, que explica o token
 * vencido; fetch, htmx e API recebem o JSON de sempre — a regra é a mesma do mapper de exceções, em
 * {@link PaginaErroResposta} (auditoria FRONT-01).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> token ausente, malformado ou divergente aborta com 403 (página ou
 * JSON, conforme o cliente); corpo de formulário ilegível conta como token ausente. Pedido sem token válido no
 * cabeçalho reserva uma vaga num balde próprio da origem ANTES de o corpo ser lido
 * ({@link RequestRateLimiter#reservarLeituraSemToken}); se o token do corpo for válido, a vaga volta — só a recusa
 * gasta o teto, e acima dele o pedido leva 429 sem o corpo ser lido — salvo quem tem cookie de token válido e corpo
 * pequeno, que ainda é lido (o colega honesto da mesma rede não paga pelas recusas dos outros). O limite da ROTA
 * continua depois deste filtro — recusa não o gasta, para uma turma atrás do mesmo NAT, com tokens vencidos, não se
 * bloquear sozinha.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class CsrfRequestFilter implements ContainerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Inject
    CsrfTokenService csrfTokenService;

    @Inject
    PaginaErroResposta paginaErroResposta;

    @Inject
    RequestRateLimiter rateLimiter;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!csrfTokenService.isEnabled() || !MUTATING.contains(requestContext.getMethod())) {
            return;
        }
        String path = requestContext.getUriInfo().getPath();
        if (path != null && (path.startsWith("/q/") || path.startsWith("/dev-ui"))) {
            return;
        }

        String cookieToken = csrfTokenService.tokenFromCookie(requestContext.getHeaderString("Cookie"));
        String cabecalho = requestContext.getHeaderString("X-CSRF-Token");
        if (cabecalho != null && !cabecalho.isBlank() && isValidPair(cookieToken, cabecalho.strip())) {
            return;   // htmx e fetch mandam o token no cabeçalho: válido, o corpo nem precisa ser lido
        }
        RequestRateLimiter.ReservaCsrf reserva = rateLimiter.reservarLeituraSemToken(requestContext);
        if (!reserva.dentroDoTeto() && !podeLerAcimaDoTeto(requestContext, cookieToken)) {
            if (reserva.acabouDeEstourar()) {
                // Um evento por origem e minuto: o 429 deste freio tem de ser distinguível do limite da rota.
                telemetriaLogger.logEvent("warn", "seguranca", "csrf_recusas_teto", Map.of("status", "bloqueado"));
            }
            requestContext.abortWith(paginaErroResposta.recusa(requestContext,
                    Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                    "{\"erro\":\"Muitos envios sem token válido desta rede. Recarregue a página e aguarde um minuto.\"}"));
            return;
        }
        String submitted = firstNonBlank(cabecalho, extractFormToken(requestContext));

        if (isValidPair(cookieToken, submitted)) {
            rateLimiter.devolverReserva(requestContext, reserva);   // formulário comum com token válido não gasta o teto
            return;
        }
        requestContext.abortWith(paginaErroResposta.recusa(requestContext, Response.Status.FORBIDDEN.getStatusCode(),
                "{\"erro\":\"Token CSRF inválido ou ausente. Recarregue a página e tente novamente.\"}"));
    }

    /** Maior corpo lido mesmo com o balde de recusas da rede cheio (o teto de um campo de formulário). */
    static final int MAX_CORPO_ACIMA_DO_TETO = 256 * 1024;

    /**
     * Com o balde de recusas da rede cheio, este pedido ainda merece ter o corpo lido?
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> numa turma atrás do mesmo NAT, as recusas de uns (token vencido, cookie
     * bloqueado) enchem o balde; o colega com formulário comum e token VÁLIDO no corpo não pode ser barrado por isso.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só com cookie de token válido (sem ele o pedido nunca passaria) e corpo
     * declarado de até {@value #MAX_CORPO_ACIMA_DO_TETO} bytes — a enxurrada de corpos de 1 MB continua cortada sem
     * leitura.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; tamanho não declarado conta como "não".</p>
     */
    private boolean podeLerAcimaDoTeto(ContainerRequestContext ctx, String cookieToken) {
        int tamanho = ctx.getLength();
        return csrfTokenService.isValid(cookieToken) && tamanho >= 0 && tamanho <= MAX_CORPO_ACIMA_DO_TETO;
    }

    private String extractFormToken(ContainerRequestContext ctx) throws IOException {
        MediaType mediaType = ctx.getMediaType();
        if (mediaType == null || !MediaType.APPLICATION_FORM_URLENCODED_TYPE.isCompatible(mediaType)) {
            return "";
        }
        InputStream entityStream = ctx.getEntityStream();
        if (entityStream == null) {
            return "";
        }
        byte[] body = entityStream.readAllBytes();
        ctx.setEntityStream(new ByteArrayInputStream(body));
        return parseUrlEncodedParam(new String(body, StandardCharsets.UTF_8), "csrf_token");
    }

    private boolean isValidPair(String cookieToken, String submitted) {
        return csrfTokenService.isValid(cookieToken)
                && csrfTokenService.isValid(submitted)
                && constantTimeEquals(cookieToken, submitted);
    }

    /**
     * Valor de um campo num corpo {@code application/x-www-form-urlencoded}.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler o token CSRF que o formulário mandou, sem depender de o recurso já ter
     * lido o corpo.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nunca lança por entrada do cliente: par com escape {@code %} inválido no
     * nome é ignorado, e no valor vale como vazio — token malformado é token ausente, e a recusa é o 403 de sempre.
     * Antes, o {@code URLDecoder} lançava e o corpo com {@code %} solto virava 500 com stack trace no log, uma
     * linha por pedido. Custo linear e SEM alocar por par: o corpo é percorrido por índice e só a chave codificada
     * de tamanho plausível (até 3 × o nome) é decodificada — o {@code split("&")} anterior criava uma String por par,
     * e um corpo de 1 MB como {@code a&a&a…} virava meio milhão delas.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> devolve {@code ""} quando o corpo é vazio, o campo não existe ou o
     * valor é ilegível.</p>
     */
    static String parseUrlEncodedParam(String body, String name) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int tamanho = body.length();
        int inicio = 0;
        while (inicio < tamanho) {
            int fim = body.indexOf('&', inicio);
            if (fim < 0) {
                fim = tamanho;
            }
            int igual = -1;
            for (int i = inicio; i < fim; i++) {
                if (body.charAt(i) == '=') {
                    igual = i;
                    break;
                }
            }
            if (chaveCasa(body, inicio, igual >= 0 ? igual : fim, name, igual >= 0)) {
                if (igual < 0) {
                    return "";
                }
                String valor = decodificarOuNulo(body.substring(igual + 1, fim));
                return valor == null ? "" : valor;
            }
            inicio = fim + 1;
        }
        return "";
    }

    /**
     * A chave do par em {@code [inicio, fim)} é {@code nome}? Chave crua compara sem alocar; chave com {@code %} ou
     * {@code +} e tamanho plausível é decodificada (como antes); par sem {@code =} vale cru, como antes.
     */
    private static boolean chaveCasa(String body, int inicio, int fim, String nome, boolean temIgual) {
        int comprimento = fim - inicio;
        if (comprimento == nome.length() && body.regionMatches(inicio, nome, 0, comprimento)) {
            return true;
        }
        if (!temIgual || comprimento > nome.length() * 3) {
            return false;
        }
        for (int i = inicio; i < fim; i++) {
            char c = body.charAt(i);
            if (c == '%' || c == '+') {
                return nome.equals(decodificarOuNulo(body.substring(inicio, fim)));
            }
        }
        return false;
    }

    /** Decodifica um trecho de formulário; escape inválido devolve {@code null} em vez de lançar. */
    private static String decodificarOuNulo(String trecho) {
        try {
            return URLDecoder.decode(trecho, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException escapeInvalido) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        return "";
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }
}
