package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class CsrfResponseFilter implements ContainerResponseFilter {

    @Inject
    CsrfTokenService csrfTokenService;

    @ConfigProperty(name = "framework.security.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (!csrfTokenService.isEnabled()) {
            return;
        }
        String existing = csrfTokenService.tokenFromCookie(requestContext.getHeaderString("Cookie"));
        String token = csrfTokenService.isValid(existing) && !csrfTokenService.precisaRenovar(existing)
                ? existing
                : csrfTokenService.issueToken();
        NewCookie cookie = new NewCookie.Builder(csrfTokenService.cookieName())
                .value(token)
                .path("/")
                .maxAge(3600)
                .secure(cookieSecure)
                .httpOnly(false)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
        // Passa o NewCookie (serializado via RuntimeDelegate) em vez de NewCookie.toString() (deprecado).
        responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
    }
}
