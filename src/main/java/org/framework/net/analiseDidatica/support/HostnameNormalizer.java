package org.framework.net.analiseDidatica.support;

import java.net.URI;

/**
 * Normaliza o que a pessoa digitou no modo "Domínio para IP" até sobrar só o nome do host.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> aceitar {@code google.com}, {@code https://google.com/x?y=1} ou
 * {@code .google.com.} e entregar ao resolvedor DNS só o nome ({@code google.com}).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> custo LINEAR no tamanho da entrada — os pontos das pontas saem por
 * laço, nunca por expressão regular com retrocesso (a forma {@code ^\.+|\.+$} era quadrática: um texto
 * com centenas de milhares de pontos no meio prendia um núcleo por minutos). Fora os espaços e os pontos
 * das pontas e o que não é host numa URL, nada é removido.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; entrada nula ou vazia devolve {@code ""} e URL que
 * não parseia cai no texto bruto aparado.</p>
 */
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
            return aparaPontos(bruto);
        }
        String alvoParse = bruto.contains("://") ? bruto : "//" + bruto;
        try {
            URI parsed = URI.create(alvoParse);
            if (parsed.getHost() != null) {
                return aparaPontos(parsed.getHost().strip());
            }
        } catch (IllegalArgumentException ignored) {
            return aparaPontos(bruto);
        }
        return aparaPontos(bruto);
    }

    /**
     * Tira os pontos do começo e do fim do texto.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> {@code .google.com.} (ponto final do FQDN, ponto colado por engano)
     * vira {@code google.com}.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> uma passada em cada ponta, sem retrocesso; os pontos do meio ficam.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; texto só de pontos vira {@code ""}.</p>
     */
    static String aparaPontos(String texto) {
        int inicio = 0;
        int fim = texto.length();
        while (inicio < fim && texto.charAt(inicio) == '.') {
            inicio++;
        }
        while (fim > inicio && texto.charAt(fim - 1) == '.') {
            fim--;
        }
        return texto.substring(inicio, fim);
    }
}
