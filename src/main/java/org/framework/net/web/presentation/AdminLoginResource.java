package org.framework.net.web.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.security.AdminApiKeyService;
import org.framework.net.security.RedirecionamentoLocal;

import java.net.URI;

@Path("/admin")
public class AdminLoginResource {

    @Inject
    AdminApiKeyService adminApiKeyService;

    @Inject
    org.framework.net.security.SessaoTelemetriaService sessaoTelemetria;

    @ConfigProperty(name = "framework.security.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    @Inject
    @io.quarkus.qute.Location("admin/login.html")
    Template loginTemplate;

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance loginPage(
            @QueryParam("redirect") String redirect,
            @QueryParam("erro") String erro) {
        return loginTemplate
                .data("redirect", safeRedirect(redirect))
                .data("erro", erro == null ? "" : erro)
                .data("enforcementActive", adminApiKeyService.isEnforcementActive());
    }

    @POST
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Response authenticate(
            @FormParam("api_key") String apiKey,
            @FormParam("redirect") String redirect) {
        if (!adminApiKeyService.isEnforcementActive()) {
            return Response.seeOther(URI.create(safeRedirect(redirect))).build();
        }
        if (!adminApiKeyService.isValid(apiKey)) {
            URI back = URI.create("/admin/login?erro=chave-invalida&redirect=" + urlEncode(safeRedirect(redirect)));
            return Response.seeOther(back).build();
        }
        // SEC-04: o cookie levava a CHAVE administrativa crua por 8 h (Path=/). Agora sai a sessão
        // assinada (HMAC, sem a chave), a mesma do login de contingência da Telemetria, papel dono; e o
        // cookie antigo é apagado.
        return Response.seeOther(URI.create(safeRedirect(redirect)))
                .cookie(sessaoTelemetria.emitirCookie("admin", org.framework.net.security.SessaoTelemetriaService.ORIGEM_CONTINGENCIA,
                        org.framework.net.security.SessaoTelemetriaService.PAPEL_DONO), cookieAntigoApagado())
                .build();
    }

    @GET
    @Path("/logout")
    public Response logout(@QueryParam("redirect") String redirect) {
        return Response.seeOther(URI.create(safeRedirect(redirect)))
                .cookie(sessaoTelemetria.cookieDeSaida(), cookieAntigoApagado())
                .build();
    }

    /** Apaga o cookie ADMIN_API_KEY de versões anteriores, que guardava a chave crua. */
    private NewCookie cookieAntigoApagado() {
        return new NewCookie.Builder(AdminApiKeyService.COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }

    /**
     * Destino local do retorno depois do login/logout.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> devolver o administrador à página de onde veio, com a consulta.</p>
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só caminho local, pela regra única de {@link RedirecionamentoLocal}.</p>
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> destino externo ou inválido vira {@code /telemetria}; nunca lança.</p>
     */
    private static String safeRedirect(String redirect) {
        return RedirecionamentoLocal.destino(redirect, "/telemetria");
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
