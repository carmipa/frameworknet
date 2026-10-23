package org.framework.net.analiseDidatica.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.analiseDidatica.infrastructure.historico.SessaoHistorico;

/**
 * PROPÓSITO DE NEGÓCIO: liga o cookie {@code fnet_hist} à sessão de histórico da requisição e emite o
 * cookie quando a requisição criou uma sessão nova.
 *
 * INVARIANTES DO DOMÍNIO: em HTTPS o nome é {@code __Host-fnet_hist} e o servidor só lê esse nome —
 * o navegador só aceita o prefixo com Secure, Path=/ e sem Domain, então subdomínio irmão não consegue
 * plantar um identificador conhecido para depois ler o histórico da vítima (fixação de sessão). Sem
 * HTTPS (dev) vale o nome comum. Cookie HttpOnly (JavaScript não lê), SameSite=Lax, Secure conforme
 * {@code framework.security.cookie-secure} (o mesmo dos cookies de CSRF/admin), Path=/, sem Max-Age
 * (vale enquanto o navegador estiver aberto). Nunca emitido se a requisição não usou histórico.
 *
 * COMPORTAMENTO EM CASO DE FALHA: não lança; cookie ausente ou malformado resulta em sessão nova.
 */
@Provider
public class HistoricoSessaoFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    SessaoHistorico sessao;

    @ConfigProperty(name = "framework.security.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    String nomeDoCookie() {
        return cookieSecure ? "__Host-" + SessaoHistorico.COOKIE : SessaoHistorico.COOKIE;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        Cookie c = request.getCookies().get(nomeDoCookie());
        sessao.receber(c == null ? null : c.getValue());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!sessao.isNova()) {
            return;
        }
        NewCookie cookie = new NewCookie.Builder(nomeDoCookie())
                .value(sessao.valorCookie())
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
        response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
    }
}
