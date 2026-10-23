package org.framework.net.telemetria;

import java.util.List;
import java.util.Locale;

/**
 * Classifica a ORIGEM de um acesso sem guardar dado pessoal.
 *
 * <p><b>Propósito de negócio:</b> alimentar as métricas de "de onde vêm os
 * acessos" — país (código ISO, agregável) e se é bot ou pessoa — a partir de
 * sinais que já chegam na requisição, para o painel de Telemetria. Nunca lida
 * com o IP em si.</p>
 *
 * <p><b>Invariantes do domínio:</b> só produz valores agregáveis e NÃO
 * identificáveis (código de país de 2 letras ou "??"; "bot"/"humano"). O IP do
 * visitante nunca é lido nem armazenado — o país vem do cabeçalho CF-IPCountry
 * (posto pelo Cloudflare na borda) e o tipo, do User-Agent.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> entrada ausente/ambígua cai em "??"
 * (país) ou "bot" (User-Agent vazio é quase sempre automação) — nunca lança.</p>
 */
public final class OrigemAcesso {

    private OrigemAcesso() {
    }

    /** Trechos que, no User-Agent, denunciam automação (minúsculo). */
    private static final List<String> SINAIS_BOT = List.of(
            "bot", "crawl", "spider", "slurp", "bingpreview", "facebookexternalhit",
            "embedly", "curl", "wget", "python-requests", "python-urllib", "go-http-client",
            "java/", "okhttp", "apache-httpclient", "headless", "phantomjs", "puppeteer",
            "playwright", "lighthouse", "pingdom", "uptimerobot", "semrush", "ahrefs",
            "mj12", "dotbot", "petalbot", "dataprovider", "scrapy", "httpx");

    /**
     * País pelo cabeçalho CF-IPCountry do Cloudflare. Código ISO de 2 letras em
     * maiúsculas, ou "??" quando ausente (dev/local), "XX" (desconhecido) ou "T1" (Tor).
     */
    public static String pais(String cfIpCountry) {
        if (cfIpCountry == null || cfIpCountry.isBlank()) {
            return "??";
        }
        String c = cfIpCountry.trim().toUpperCase(Locale.ROOT);
        if (c.equals("XX") || c.equals("T1") || !c.matches("[A-Z]{2}")) {
            return "??";
        }
        return c;
    }

    /** "bot" ou "humano" a partir do User-Agent. User-Agent vazio → "bot". */
    public static String tipo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "bot";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String sinal : SINAIS_BOT) {
            if (ua.contains(sinal)) {
                return "bot";
            }
        }
        return "humano";
    }
}
