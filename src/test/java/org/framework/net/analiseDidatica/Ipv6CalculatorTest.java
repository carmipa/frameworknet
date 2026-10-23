package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.domain.Ipv6Calculator;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o núcleo de cálculo IPv6 com valores concretos. Antes deste teste, o único toque no
 * Ipv6Calculator era um HttpTest tautológico que só verificava contains("2001") — substring da
 * própria entrada ecoada. Compressão, expansão e cálculo de rede /64 errados passavam verde.
 *
 * Gabarito independente da implementação (A3): os valores esperados são fatos conhecidos de IPv6
 * (RFC 5952 para forma canônica), não recalculados pela lógica sob teste.
 */
@QuarkusTest
class Ipv6CalculatorTest {

    @Inject
    Ipv6Calculator calc;

    @Test
    void comprimeExpandeValoresConcretos() {
        Map<String, Object> r = calc.processar("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertEquals("2001:db8::1", r.get("comprimido"));
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", r.get("expandido"));
        assertEquals("/64", r.get("prefixo_sugerido"));
    }

    @Test
    void expandeLoopback() {
        Map<String, Object> r = calc.processar("::1");
        assertEquals("::1", r.get("comprimido"));
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0001", r.get("expandido"));
        assertEquals("Loopback", r.get("tipo"));
    }

    @Test
    void rede64ZeraOsUltimos64Bits() {
        Map<String, Object> r = calc.processar("2001:db8:abcd:1234:5678:9abc:def0:1");
        String rede64 = String.valueOf(r.get("rede_64"));
        // A rede /64 preserva os 64 bits altos e zera os baixos: o "5678..." tem de sumir.
        assertTrue(rede64.startsWith("2001:db8:abcd:1234::"), "rede_64 inesperada: " + rede64);
        assertFalse(rede64.contains("5678"), "host nao foi zerado: " + rede64);
    }

    /**
     * Gabarito independente (A3): registro IANA de endereços IPv6 de propósito especial, RFC 3849
     * (2001:db8::/32 é documentação, não roteável), RFC 4291 (2000::/3 é o espaço global atribuído),
     * RFC 3879 (fec0::/10 site-local obsoleto). Python ipaddress .is_global concorda em todos.
     * Fronteira (A1): 2606:4700:4700::1111 e 2001:db8::1 dividem o 2000::/3 — só o primeiro é global.
     */
    @Test
    void classificaLinkLocalUlaGlobalEDocumentacao() {
        assertEquals("Link-local", calc.processar("fe80::1").get("tipo"));
        assertEquals("ULA/Privado", calc.processar("fc00::1").get("tipo"));

        Map<String, Object> global = calc.processar("2606:4700:4700::1111");
        assertEquals("Global unicast", global.get("tipo"));
        assertEquals("2000::/3", global.get("faixa"));
        assertEquals("Sim", global.get("roteavel"));

        Map<String, Object> doc = calc.processar("2001:db8::1");
        assertEquals("Documentação", doc.get("tipo"));
        assertEquals("2001:db8::/32", doc.get("faixa"));
        assertEquals("Não", doc.get("roteavel"));

        for (String foraDe2000 : new String[]{"4000::1", "::2", "fec0::1"}) {
            Map<String, Object> r = calc.processar(foraDe2000);
            assertEquals("Outro/Reservado", r.get("tipo"), foraDe2000);
            assertEquals("Não", r.get("roteavel"), foraDe2000);
        }
        assertEquals("Não especificado", calc.processar("::").get("tipo"));
    }

    /** ::ffff:0:0/96 é IPv6 válido (RFC 4291 §2.5.5.2); antes caía em ClassCastException. */
    @Test
    void ipv4MapeadoEhAceitoEClassificado() {
        Map<String, Object> r = calc.processar("::ffff:192.0.2.1");
        assertEquals("IPv4-mapeado", r.get("tipo"));
        assertEquals("::ffff:0:0/96", r.get("faixa"));
        assertEquals("Não", r.get("roteavel"));
    }

    /**
     * Auditoria CONT-31/CALC-31 — gabarito independente (A3): registro IANA de propósito especial do IPv6
     * (coluna "Globally Reachable"), RFC 9637 (3fff::/20), RFC 5180 (2001:2::/48), RFC 4380 (Teredo),
     * RFC 3056 (6to4) e RFC 6052 §3.1 (NAT64 só com IPv4 público). Fronteiras (A1): 3fff:1000::1 está fora
     * do /20 e 2001:3::1 (AMT) fora do benchmarking e do Teredo — os dois continuam globais.
     */
    @Test
    void faixasEspeciaisDoRegistroIana() {
        assertEquals("Documentação", calc.processar("3fff::1").get("tipo"));
        assertEquals("Não", calc.processar("3fff::1").get("roteavel"));
        assertEquals("Global unicast", calc.processar("3fff:1000::1").get("tipo"));
        assertEquals("Benchmarking", calc.processar("2001:2::1").get("tipo"));
        assertEquals("Não", calc.processar("2001:2::1").get("roteavel"));
        assertEquals("Teredo", calc.processar("2001:0:4136:e378::1").get("tipo"));
        assertEquals("Global unicast", calc.processar("2001:3::1").get("tipo"));
        assertEquals("6to4", calc.processar("2002:c000:204::1").get("tipo"));
        Map<String, Object> nat64Publico = calc.processar("64:ff9b::808:808");
        assertEquals("NAT64", nat64Publico.get("tipo"));
        assertEquals("Sim, via tradutor NAT64", nat64Publico.get("roteavel"));
        Map<String, Object> nat64Doc = calc.processar("64:ff9b::c000:201");
        assertEquals("NAT64", nat64Doc.get("tipo"));
        assertTrue(String.valueOf(nat64Doc.get("roteavel")).startsWith("Não"), String.valueOf(nat64Doc.get("roteavel")));
        assertEquals("Sim", calc.processar("2606:4700:4700::1111").get("roteavel"));
    }

    /**
     * Auditoria CALC-06/CALC-25/CALC-30: com prefixo a análise dava 500 ("/64/64" no cálculo da rede);
     * "endereço%zona/prefixo" perdia o prefixo; o IPv4-mapeado saía sem a notação mista.
     */
    @Test
    void prefixoEZonaInformadosSaoPreservadosSemDuplicar() {
        Map<String, Object> r = calc.processar("2001:db8::1/64");
        assertEquals("2001:db8::1/64", r.get("comprimido"));
        assertEquals("2001:db8::", r.get("rede_64"));
        assertEquals("2001:db8::/64", r.get("rede_informada"));
        assertEquals("0000:0000:0000:0001", r.get("ultimos_64"));
        assertFalse(r.toString().contains("/64/64"), r.toString());

        Map<String, Object> bloco = calc.processar("2001:db8:abcd::/48");
        assertEquals("2001:db8:abcd::/48", bloco.get("rede_informada"));
        assertEquals("2001:db8:abcd::", bloco.get("rede_64"));

        Map<String, Object> zona = calc.processar("fe80::1%eth0/64");
        assertEquals("eth0", zona.get("zone_index"));
        assertEquals("fe80::1%eth0/64", zona.get("comprimido"));
        assertEquals("fe80::/64", zona.get("rede_informada"));

        Map<String, Object> semPrefixo = calc.processar("2001:db8::1");
        assertEquals("", semPrefixo.get("rede_informada"));
        assertEquals("2001:db8::1", semPrefixo.get("comprimido"));

        assertEquals("::ffff:192.0.2.1", calc.processar("::ffff:c000:201").get("comprimido"));
        assertTrue(calc.processar("::/0").toString().contains("rota padrão"));
    }

    @Test
    void rejeitaEntradaInvalida() {
        // Caso-controle: entrada malformada e IPv4 puro rejeitados; a compressa legitima e aceita.
        assertThrows(EntradaInvalidaException.class, () -> calc.processar("nao-e-ipv6"));
        assertThrows(EntradaInvalidaException.class, () -> calc.processar(""));
        assertThrows(EntradaInvalidaException.class, () -> calc.processar("192.168.0.1"));
    }
}
