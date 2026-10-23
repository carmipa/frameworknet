package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class CsrfTokenService {

    private static final String COOKIE_NAME = "XSRF-TOKEN";
    /** Em HTTPS: o navegador só aceita __Host- com Secure, Path=/ e sem Domain — subdomínio irmão não planta. */
    private static final String COOKIE_NAME_HOST = "__Host-XSRF-TOKEN";
    private static final long TTL_SECONDS = 3600;

    @ConfigProperty(name = "framework.security.csrf-enabled", defaultValue = "true")
    boolean csrfEnabled;

    @ConfigProperty(name = "framework.security.csrf-secret", defaultValue = "framework-net-dev-csrf-secret")
    String csrfSecret;

    @ConfigProperty(name = "framework.security.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    private final SecureRandom random = new SecureRandom();

    public boolean isEnabled() {
        return csrfEnabled;
    }

    /**
     * PROPÓSITO: nome do cookie do token CSRF (double submit).
     * INVARIANTES: com cookie Secure (produção) é {@code __Host-XSRF-TOKEN} e o servidor só lê esse
     * nome — cookie sem prefixo, que um subdomínio irmão consegue plantar, é ignorado. Sem HTTPS (dev)
     * o navegador recusaria o prefixo, então vale o nome comum.
     * FALHA: não lança.
     */
    public String cookieName() {
        return cookieSecure ? COOKIE_NAME_HOST : COOKIE_NAME;
    }

    /**
     * Token válido que já passou da metade da validade: o filtro de resposta emite um novo. Sem isso
     * o token vencia 1 h após a emissão mesmo com a página em uso, e o próximo POST levava 403.
     */
    public boolean precisaRenovar(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            long expiry = Long.parseLong(token.strip().split("\\.", -1)[0]);
            return expiry - Instant.now().getEpochSecond() < TTL_SECONDS / 2;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String issueToken() {
        long expiry = Instant.now().getEpochSecond() + TTL_SECONDS;
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String payload = expiry + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String sig = sign(payload);
        return payload + "." + sig;
    }

    public boolean isValid(String token) {
        if (!csrfEnabled || token == null || token.isBlank()) {
            return !csrfEnabled;
        }
        String[] parts = token.strip().split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            long expiry = Long.parseLong(parts[0]);
            if (Instant.now().getEpochSecond() > expiry) {
                return false;
            }
            String payload = parts[0] + "." + parts[1];
            return constantTimeEquals(sign(payload), parts[2]);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String tokenFromCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return "";
        }
        String nome = cookieName() + "=";
        for (String chunk : cookieHeader.split(";")) {
            String trimmed = chunk.strip();
            if (trimmed.startsWith(nome)) {
                return trimmed.substring(nome.length());
            }
        }
        return "";
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(deriveKey(), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar token CSRF.", ex);
        }
    }

    private byte[] deriveKey() {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(csrfSecret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao derivar chave CSRF.", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
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
