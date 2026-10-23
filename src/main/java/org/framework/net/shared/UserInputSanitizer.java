package org.framework.net.shared;

/**
 * Sanitiza rótulos exibidos em HTML/JS (topologia, relatórios).
 */
public final class UserInputSanitizer {

    private UserInputSanitizer() {
    }

    public static String sanitizeLabel(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip()
                .replace('\0', ' ')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        StringBuilder out = new StringBuilder(cleaned.length());
        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch == '<' || ch == '>' || ch == '&' || ch == '"' || ch == '\'' || ch == '`') {
                continue;
            }
            out.append(ch);
        }
        return out.toString().strip();
    }
}
