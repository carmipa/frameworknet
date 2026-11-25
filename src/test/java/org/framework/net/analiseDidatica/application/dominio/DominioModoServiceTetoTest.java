package org.framework.net.analiseDidatica.application.dominio;

import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver;
import org.framework.net.shared.InputLimits;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Modo "Decompor Domínio para IP" (auditoria SEC-01): nome acima do teto do DNS (253 caracteres, RFC 1035 §2.3.4) é
 * recusado ANTES de qualquer consulta, com mensagem e campo marcado. O resolvedor é um dublê que conta as chamadas:
 * nada sai para a rede neste teste.
 */
class DominioModoServiceTetoTest {

    private static final class ResolvedorContado extends DnsResolver {
        final AtomicInteger chamadas = new AtomicInteger();

        @Override
        public String resolverComCache(String hostname) {
            chamadas.incrementAndGet();
            return "93.184.216.34";
        }
    }

    private static String nomeCom(int total) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < total) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            sb.append("a".repeat(Math.min(63, total - sb.length())));
        }
        return sb.toString();
    }

    private static DominioModoService servico(ResolvedorContado dns) {
        DominioModoService s = new DominioModoService();
        s.dnsResolver = dns;
        return s;
    }

    /** A2: sem a conferência, o nome de 254 caracteres seguiria para o DNS e a contagem seria 1. */
    @Test
    void nomeAcimaDoTetoERecusadoSemConsultarODns() {
        ResolvedorContado dns = new ResolvedorContado();
        ModoResult r = servico(dns).processar(nomeCom(InputLimits.MAX_NOME_HOST + 1), "24");
        assertTrue(r.erro() != null && r.erro().contains("longo demais"), String.valueOf(r.erro()));
        assertTrue(r.invalidFields().contains("ip"));
        assertEquals(0, dns.chamadas.get(), "o nome longo demais chegou ao DNS");
    }

    /**
     * Revisão de boa-fé: URL que o normalizador não consegue ler volta inteira e passa de 253 — chamá-la de "nome longo
     * demais" levava a pessoa a encurtar o domínio, que nem era o problema. A1: URL longa que se lê (o host é curto)
     * segue para o DNS com o host só.
     */
    @Test
    void urlIlegivelTemMensagemPropriaEUrlLegivelSegueComOHost() {
        ResolvedorContado dns = new ResolvedorContado();
        ModoResult ilegivel = servico(dns).processar("https://exemplo.com.br/a b%zz" + "c".repeat(300), "24");
        assertTrue(ilegivel.erro() != null && ilegivel.erro().contains("Cole só o nome"), String.valueOf(ilegivel.erro()));
        assertEquals(0, dns.chamadas.get());

        ModoResult legivel = servico(dns).processar("https://exemplo.com.br/caminho?token=" + "c".repeat(300), "24");
        assertNull(legivel.erro());
        assertEquals(1, dns.chamadas.get(), "a URL legível não chegou ao DNS com o host");
    }

    /** A1: o maior nome legítimo (253) segue para o DNS normalmente. */
    @Test
    void nomeNoTetoSegueParaODns() {
        ResolvedorContado dns = new ResolvedorContado();
        ModoResult r = servico(dns).processar(nomeCom(InputLimits.MAX_NOME_HOST), "24");
        assertNull(r.erro());
        assertEquals(1, dns.chamadas.get());
        assertEquals("93.184.216.34", r.ipP());
        assertFalse(r.invalidFields().contains("ip"));
    }
}
