package org.framework.net.analiseDidatica.support;

import java.net.URI;

public final class HostnameNormalizer {

    private HostnameNormalizer() {
    }

    public static String normalizar(String entrada) {
        String bruto = entrada == null ? "" : entrada.strip();
        if (bruto.isEmpty()) {
            return "";
        }
        boolean pareceUrl = bruto.contains("://") || bruto.startsWith("//")
                || bruto.contains("/") || bruto.contains("?") || bruto.contains("#") || bruto.contains(":");
        if (!pareceUrl) {
            return bruto.strip().replaceAll("^\\.+|\\.+$", "");
        }
        String alvoParse = bruto.contains("://") ? bruto : "//" + bruto;
        try {
            URI parsed = URI.create(alvoParse);
            if (parsed.getHost() != null) {
                return parsed.getHost().strip().replaceAll("^\\.+|\\.+$", "");
            }
        } catch (IllegalArgumentException ignored) {
            return bruto.strip().replaceAll("^\\.+|\\.+$", "");
        }
        return bruto.strip().replaceAll("^\\.+|\\.+$", "");
    }
}
