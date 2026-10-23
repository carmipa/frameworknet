package org.framework.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenServiceTest {

    @Test
    void tokenValidoAposEmissao() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, true, "test-secret");
        String token = service.issueToken();
        assertTrue(service.isValid(token));
    }

    @Test
    void tokenAlteradoEhInvalido() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, true, "test-secret");
        String token = service.issueToken() + "x";
        assertFalse(service.isValid(token));
    }

    @Test
    void desabilitadoAceitaVazio() {
        CsrfTokenService service = new CsrfTokenService();
        inject(service, false, "test-secret");
        assertTrue(service.isValid(""));
    }

    /**
     * F34: em produção (cookie Secure) o nome é __Host-XSRF-TOKEN — o navegador só aceita esse
     * prefixo com Secure, Path=/ e sem Domain, então um subdomínio irmão (*.carminati.dev.br) não
     * consegue plantar o cookie. E o servidor só confia no nome com prefixo: o cookie plantado sem
     * prefixo é ignorado. Fronteira (A1): em dev (sem HTTPS) o nome comum continua valendo.
     */
    @Test
    void emProducaoSoConfiaNoCookieComPrefixoHost() throws Exception {
        CsrfTokenService prod = new CsrfTokenService();
        inject(prod, true, "test-secret");
        setSecure(prod, true);
        assertEquals("__Host-XSRF-TOKEN", prod.cookieName());
        assertEquals("abc.def.ghi", prod.tokenFromCookie("__Host-XSRF-TOKEN=abc.def.ghi; outro=1"));
        assertEquals("", prod.tokenFromCookie("XSRF-TOKEN=plantado.por.irmao"));

        CsrfTokenService dev = new CsrfTokenService();
        inject(dev, true, "test-secret");
        setSecure(dev, false);
        assertEquals("XSRF-TOKEN", dev.cookieName());
        assertEquals("abc.def.ghi", dev.tokenFromCookie("XSRF-TOKEN=abc.def.ghi"));
    }

    /**
     * F14 (causa-raiz): o token vencia 1 h após emitido mesmo com o aluno usando a página, e o
     * próximo POST levava 403 em silêncio. Token passado da metade da validade é renovado; recém-
     * emitido não (A1).
     */
    @Test
    void tokenPassadoDaMetadeDaValidadeEhRenovado() throws Exception {
        CsrfTokenService s = new CsrfTokenService();
        inject(s, true, "test-secret");
        assertFalse(s.precisaRenovar(s.issueToken()));
        long expiraLogo = java.time.Instant.now().getEpochSecond() + 600;   // 10 min restantes
        var sign = CsrfTokenService.class.getDeclaredMethod("sign", String.class);
        sign.setAccessible(true);
        String payload = expiraLogo + ".bm9uY2U";
        String velho = payload + "." + sign.invoke(s, payload);
        assertTrue(s.isValid(velho), "o token de teste precisa ser válido para o caso medir a renovação");
        assertTrue(s.precisaRenovar(velho));
    }

    private static void setSecure(CsrfTokenService service, boolean secure) throws Exception {
        var f = CsrfTokenService.class.getDeclaredField("cookieSecure");
        f.setAccessible(true);
        f.set(service, secure);
    }

    private static void inject(CsrfTokenService service, boolean enabled, String secret) {
        try {
            var enabledField = CsrfTokenService.class.getDeclaredField("csrfEnabled");
            enabledField.setAccessible(true);
            enabledField.set(service, enabled);
            var secretField = CsrfTokenService.class.getDeclaredField("csrfSecret");
            secretField.setAccessible(true);
            secretField.set(service, secret);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
