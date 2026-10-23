package org.framework.net.ipv6;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.ipv6.application.Ipv6CidrService;
import org.framework.net.ipv6.application.Ipv6CidrService.DominioAaaaResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova end-to-end da resolução AAAA (aba "Domínio → IPv6") contra um domínio dual-stack real.
 *
 * <p><b>Propósito:</b> os demais testes IPv6 são herméticos (sem rede). Este exercita o caminho
 * completo — {@link Ipv6CidrService#resolverDominio} → {@code DnsResolver.resolverAaaaComCache} →
 * consulta AAAA via dnsjava → guarda SSRF → {@code kernel.analisar} — que
 * nenhum teste hermético cobre (A1: o legítimo é aceito e classificado, não só o defeito rejeitado).</p>
 *
 * <p><b>Sem rede não é reprovação:</b> se o ambiente não tiver DNS/saída (CI offline), o teste é
 * <em>ignorado</em> via {@link Assumptions}, nunca falha por motivo errado — mesmo padrão do
 * {@code ArquiteturaCamadasTest}.</p>
 */
@QuarkusTest
class Ipv6DominioAaaaIT {

    @Inject
    Ipv6CidrService service;

    @Test
    void resolveEAnalisaAaaaDeDominioDualStackReal() {
        Assumptions.assumeTrue(temResolucaoDeRede(),
                "Sem DNS/saída de rede no ambiente — prova de resolução AAAA ignorada.");

        DominioAaaaResult r = service.resolverDominio("cloudflare.com");

        assertEquals("cloudflare.com", r.dominio());
        assertNotNull(r.enderecoAaaa());
        assertTrue(r.enderecoAaaa().contains(":"), "AAAA deve ser IPv6: " + r.enderecoAaaa());
        assertNotNull(r.analise());
        // cloudflare.com publica AAAA em 2606:4700::/32 → global unicast (2000::/3), endereço público
        // que passa pela guarda SSRF e é classificado pela faixa IANA.
        assertEquals("Global unicast", r.analise().base().tipo(),
                "AAAA público deve ser classificado como global unicast: " + r.enderecoAaaa());
    }

    /**
     * Sonda pelo MESMO caminho da produção (dnsjava, UDP direto a um resolver público). A sonda antiga
     * usava getaddrinfo: com UDP/53 bloqueado e o resolver do SO funcionando, o teste falhava por
     * causa ambiental em vez de ser ignorado (auditoria F25).
     */
    private boolean temResolucaoDeRede() {
        try {
            org.xbill.DNS.Lookup l = new org.xbill.DNS.Lookup("cloudflare.com", org.xbill.DNS.Type.AAAA);
            org.xbill.DNS.SimpleResolver r = new org.xbill.DNS.SimpleResolver("1.1.1.1");
            r.setTimeout(java.time.Duration.ofSeconds(3));
            l.setResolver(r);
            l.run();
            return l.getResult() == org.xbill.DNS.Lookup.SUCCESSFUL;
        } catch (Exception e) {
            return false;
        }
    }
}
