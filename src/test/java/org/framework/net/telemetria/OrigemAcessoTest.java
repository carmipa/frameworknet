package org.framework.net.telemetria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Classificação de origem de acesso (país por CF-IPCountry, bot × pessoa por
 * User-Agent). Garante que só saem valores agregáveis e que o desconhecido cai
 * em "??"/"bot" — nunca lança, nunca lida com IP.
 */
@DisplayName("Telemetria: classificação de origem de acesso (métricas, sem IP)")
class OrigemAcessoTest {

    @Test
    @DisplayName("país: ISO-2 em maiúsculas, ou '??' para ausente/desconhecido/Tor/inválido")
    void paisNormaliza() {
        assertEquals("BR", OrigemAcesso.pais("br"));
        assertEquals("US", OrigemAcesso.pais("US"));
        assertEquals("??", OrigemAcesso.pais(null));
        assertEquals("??", OrigemAcesso.pais(""));
        assertEquals("??", OrigemAcesso.pais("XX"), "XX = desconhecido no Cloudflare");
        assertEquals("??", OrigemAcesso.pais("T1"), "T1 = Tor");
        assertEquals("??", OrigemAcesso.pais("BRA"), "não é ISO-2");
    }

    @Test
    @DisplayName("tipo: navegador é humano; bots/ferramentas e User-Agent vazio são bot")
    void tipoClassifica() {
        assertEquals("humano", OrigemAcesso.tipo(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"));
        assertEquals("bot", OrigemAcesso.tipo("Googlebot/2.1 (+http://www.google.com/bot.html)"));
        assertEquals("bot", OrigemAcesso.tipo("curl/8.4.0"));
        assertEquals("bot", OrigemAcesso.tipo("python-requests/2.31.0"));
        assertEquals("bot", OrigemAcesso.tipo(null), "sem User-Agent = automação");
        assertEquals("bot", OrigemAcesso.tipo("   "));
    }
}
