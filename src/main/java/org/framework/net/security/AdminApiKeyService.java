package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class AdminApiKeyService {

    public static final String HEADER_NAME = "X-Admin-Api-Key";
    public static final String COOKIE_NAME = "ADMIN_API_KEY";

    /**
     * Prefixos que exigem chave administrativa.
     *
     * <p><b>Por que {@code /telemetria} inteiro:</b> não existe subconjunto seguro.
     * Verificado em produção em 2026-08-03 — {@code /api/resumo},
     * {@code /api/dashboard} e {@code /api/console} devolvem as mensagens de
     * evento cruas, que contêm o IP do visitante; {@code /api/exportar} entrega o
     * NDJSON completo (347 KB, com IPv4 e IPv6 reais); {@code /api/pasta} e
     * {@code /api/dashboard} revelam caminhos absolutos do servidor; e
     * {@code /api/console/limpar} é destrutivo. Proteger só parte deixaria o
     * mesmo dado saindo por outra porta.</p>
     */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/export",
            "/ipv6/export",
            "/telemetria"
    );

    @ConfigProperty(name = "framework.security.admin-api-key", defaultValue = "")
    String configuredKey;

    @ConfigProperty(name = "framework.security.admin-api-key-required", defaultValue = "false")
    boolean adminApiKeyRequired;

    public boolean isEnforcementActive() {
        return adminApiKeyRequired && configuredKey != null && !configuredKey.isBlank();
    }

    public boolean isProtectedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return PROTECTED_PREFIXES.stream().anyMatch(prefix ->
                normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
    }

    public boolean isValid(String submittedKey) {
        if (!isEnforcementActive()) {
            return true;
        }
        if (submittedKey == null || submittedKey.isBlank()) {
            return false;
        }
        return constantTimeEquals(configuredKey.strip(), submittedKey.strip());
    }



    public String configuredKeyForDisplay() {
        if (!isEnforcementActive()) {
            return "";
        }
        return configuredKey;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
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
