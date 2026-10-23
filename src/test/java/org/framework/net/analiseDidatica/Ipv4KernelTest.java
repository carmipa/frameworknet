package org.framework.net.analiseDidatica;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class Ipv4KernelTest {

    @Inject
    Ipv4Kernel kernel;

    @Test
    void parseIpv4PartsValido() {
        int[] parts = kernel.parseIpv4Parts("192.168.1.10");
        assertEquals(192, parts[0]);
        assertEquals(168, parts[1]);
        assertEquals(1, parts[2]);
        assertEquals(10, parts[3]);
    }

    @Test
    void parseIpv4PartsInvalido() {
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("999.1.1.1"));
    }

    @Test
    void parseIpv4PartsOctetoEstouraIntEhRejeitadoComoDominio() {
        // Caso-controle A1: octeto gigante compartilha o sinal "só dígitos" com um octeto válido,
        // mas estouraria int no parseInt. Deve virar EntradaInvalidaException (erro didático),
        // nunca NumberFormatException crua (500). Antes da correção, este caso lançava NFE.
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("9999999999.1.1.1"));
        // Legítimo semelhante (também só dígitos, no limite superior): aceito.
        int[] noLimite = kernel.parseIpv4Parts("255.255.255.255");
        assertEquals(255, noLimite[0]);
        assertEquals(255, noLimite[3]);
    }

    @Test
    void parseIpv4PartsRejeitaZeroAEsquerdaEDigitoNaoAscii() {
        // CALC-33: "010" é 8 (octal) no inet_aton; dígitos árabe-índicos passavam por Character.isDigit.
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("010.010.010.010"));
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("\u0661\u0669\u0662.168.0.1"));
        // Fronteira (A1): o "0" sozinho e octetos sem zero à esquerda continuam valendo.
        assertEquals(0, kernel.parseIpv4Parts("10.0.0.0")[3]);
        assertEquals(100, kernel.parseIpv4Parts("10.100.0.1")[1]);
    }

    @Test
    void parseIpv4PartsRejeitaPontoSobrando() {
        // Ponto final/inicial produz octeto vazio e passa a ser rejeitado (split com limite -1).
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts("192.168.0.10."));
        assertThrows(EntradaInvalidaException.class, () -> kernel.parseIpv4Parts(".192.168.0.1"));
        // Legítimo sem ponto sobrando: aceito.
        int[] ok = kernel.parseIpv4Parts("192.168.0.10");
        assertEquals(10, ok[3]);
    }

    @Test
    void inferirCidrClassfulClasseA() {
        Ipv4Kernel.InferenciaCidr inf = kernel.inferirCidrPorIp("10.5.5.5");
        assertEquals(8, inf.cidr());
    }

    @Test
    void inferirCidrClassfulClasseC() {
        Ipv4Kernel.InferenciaCidr inf = kernel.inferirCidrPorIp("200.1.1.1");
        assertEquals(24, inf.cidr());
    }

    @Test
    void mascaraDottedParaCidr() {
        assertEquals(24, kernel.mascaraDottedParaCidr("255.255.255.0"));
        assertEquals(18, kernel.mascaraDottedParaCidr("255.255.192.0"));
        assertNull(kernel.mascaraDottedParaCidr("255.255.255.1"));
    }

    @Test
    void wildcardDottedParaCidr() {
        assertEquals(24, kernel.wildcardDottedParaCidr("0.0.0.255"));
    }

    @Test
    void coreMascaraSlash24() {
        Map<String, Object> core = kernel.coreMascara(24);
        assertNotNull(core);
        assertEquals("255.255.255.0", core.get("mask"));
        assertEquals(256L, core.get("total"));
        assertEquals(254L, core.get("uteis"));
    }

    @Test
    void processarCidr31Rfc3021() {
        Map<String, Object> res = kernel.processar("10.0.0.0", 31);
        assertNotNull(res);
        assertEquals(31, res.get("cidr"));
        assertTrue(res.get("ip_papel_alerta").toString().contains("RFC 3021")
                || res.toString().contains("RFC 3021"));
    }

    @Test
    void processarCidr32HostUnico() {
        Map<String, Object> res = kernel.processar("10.0.0.1", 32);
        assertEquals(32, res.get("cidr"));
        assertEquals("10.0.0.1", res.get("primeiro_host"));
        assertEquals("10.0.0.1", res.get("ultimo_host"));
    }

    @Test
    void broadcastSmurfAlerta() {
        Map<String, Object> res = kernel.processar("192.168.1.255", 24);
        String json = res.toString();
        assertTrue(json.contains("Smurf") || json.contains("broadcast"));
    }

    @Test
    void enunciadoProvaMascara2552552240() {
        Map<String, Object> core = kernel.coreMascara(19);
        long pulo = ((Number) core.get("pulo")).longValue();
        Ipv4Kernel.TabelaReferenciaSubredes tab = kernel.tabelaReferenciaSubredes(19);
        Map<String, Object> e = kernel.enunciadoProvaIntervalos(
                19, pulo, ((Number) core.get("total")).longValue(),
                ((Number) core.get("uteis")).longValue(), tab.octeto());
        assertEquals(8L, ((Number) e.get("qtde_intervalos")).longValue());
        assertEquals(32L, ((Number) e.get("variacao")).longValue());
        assertEquals(3, ((Number) e.get("octeto_referencia")).intValue());
        assertTrue(e.get("frase_estilo_quadro").toString().contains("8 intervalos"));
    }

    @Test
    void processarSomenteMascara() {
        Map<String, Object> res = kernel.processarSomenteMascara(18);
        assertTrue((Boolean) res.get("somente_mascara"));
        assertEquals("—", res.get("rede"));
        assertEquals(18, res.get("cidr"));
    }

    @Test
    void classeBDidaticaMascaraCorreta() {
        Ipv4Kernel.ClasseDidatica info = kernel.classeIpv4Didatica(172);
        assertEquals("B", info.classe());
        assertTrue(info.classeFaixa().contains("255.255.0.0"));
    }

    @Test
    void processarRedeSlash24ValoresConcretos() {
        Map<String, Object> res = kernel.processar("192.168.1.10", 24);
        assertEquals("192.168.1.0", res.get("rede"));
        assertEquals("192.168.1.255", res.get("broad"));
        assertEquals("192.168.1.1", res.get("primeiro_host"));
        assertEquals("192.168.1.254", res.get("ultimo_host"));
        assertEquals("255.255.255.0", res.get("mask"));
        assertEquals("0.0.0.255", res.get("wildcard"));
        assertEquals(256L, ((Number) res.get("total")).longValue());
        assertEquals(254L, ((Number) res.get("uteis")).longValue());
        assertEquals(1L, ((Number) res.get("pulo")).longValue());
    }

    @Test
    void processarRedeSlash19ValoresConcretos() {
        Map<String, Object> res = kernel.processar("172.19.0.10", 19);
        assertEquals("172.19.0.0", res.get("rede"));
        assertEquals("172.19.31.255", res.get("broad"));
        assertEquals("172.19.0.1", res.get("primeiro_host"));
        assertEquals("172.19.31.254", res.get("ultimo_host"));
        assertEquals(8192L, ((Number) res.get("total")).longValue());
        assertEquals(8190L, ((Number) res.get("uteis")).longValue());
        assertEquals(32L, ((Number) res.get("pulo")).longValue());
    }

    @Test
    void processarRedeSlash30ValoresConcretos() {
        Map<String, Object> res = kernel.processar("10.0.0.0", 30);
        assertEquals("10.0.0.0", res.get("rede"));
        assertEquals("10.0.0.3", res.get("broad"));
        assertEquals("10.0.0.1", res.get("primeiro_host"));
        assertEquals("10.0.0.2", res.get("ultimo_host"));
        assertEquals(4L, ((Number) res.get("total")).longValue());
        assertEquals(2L, ((Number) res.get("uteis")).longValue());
    }

    @Test
    void wildcardValidaSlash20() {
        assertEquals(20, kernel.wildcardDottedParaCidr("0.0.15.255"));
        Map<String, Object> res = kernel.processar("172.16.8.8", 20);
        assertEquals("172.16.0.0", res.get("rede"));
        assertEquals("172.16.15.255", res.get("broad"));
    }

    @Test
    void enunciadoProvaIncluiPotenciasDeDois() {
        Map<String, Object> res = kernel.processarSomenteMascara(19);
        @SuppressWarnings("unchecked")
        Map<String, Object> enunciado = (Map<String, Object>) res.get("enunciado_prova");
        assertNotNull(enunciado);
        assertNotNull(enunciado.get("linha_potencias_quadro"));
        assertTrue(enunciado.get("linha_potencias_quadro").toString().contains("2^"));
    }

    @Test
    void privacidadeRfc1918() {
        Ipv4Kernel.PrivacidadeResult p = kernel.privacidadeRfc1918(new int[]{10, 0, 0, 1});
        assertTrue(p.tipo().contains("Privado"));
    }

    /**
     * Gabarito independente (A3): registro IANA IPv4 Special-Purpose (RFC 6890) — 100.64.0.0/10
     * (RFC 6598, CGNAT), 192.0.2.0/24 · 198.51.100.0/24 · 203.0.113.0/24 (RFC 5737), 198.18.0.0/15
     * (RFC 2544). Python ipaddress .is_global = False para todos. Fronteira (A1): os vizinhos
     * imediatos 100.63.255.255, 100.128.0.0 e 198.20.0.0 continuam públicos.
     */
    @Test
    void faixasEspeciaisNaoSaoRotuladasComoPublicas() {
        assertEquals("CGNAT (RFC 6598)", kernel.privacidadeRfc1918(new int[]{100, 64, 0, 1}).tipo());
        assertEquals("CGNAT (RFC 6598)", kernel.privacidadeRfc1918(new int[]{100, 127, 255, 254}).tipo());
        assertEquals("Documentação (RFC 5737)", kernel.privacidadeRfc1918(new int[]{192, 0, 2, 10}).tipo());
        assertEquals("Documentação (RFC 5737)", kernel.privacidadeRfc1918(new int[]{198, 51, 100, 7}).tipo());
        assertEquals("Documentação (RFC 5737)", kernel.privacidadeRfc1918(new int[]{203, 0, 113, 9}).tipo());
        assertEquals("Benchmark (RFC 2544)", kernel.privacidadeRfc1918(new int[]{198, 18, 0, 1}).tipo());
        assertEquals("Benchmark (RFC 2544)", kernel.privacidadeRfc1918(new int[]{198, 19, 255, 255}).tipo());
        // CONT-31: 192.0.0.0/24 (RFC 6890), com 192.0.0.8 "dummy" (RFC 7600); 192.0.1.1 fica fora (fronteira).
        assertEquals("Reservado IETF (RFC 6890)", kernel.privacidadeRfc1918(new int[]{192, 0, 0, 8}).tipo());
        assertEquals("Público", kernel.privacidadeRfc1918(new int[]{192, 0, 1, 1}).tipo());

        assertEquals("Público", kernel.privacidadeRfc1918(new int[]{100, 63, 255, 255}).tipo());
        assertEquals("Público", kernel.privacidadeRfc1918(new int[]{100, 128, 0, 0}).tipo());
        assertEquals("Público", kernel.privacidadeRfc1918(new int[]{198, 20, 0, 0}).tipo());
        assertEquals("Público", kernel.privacidadeRfc1918(new int[]{8, 8, 8, 8}).tipo());
    }

    /** O "Aviso de Borda: IP Público" só aparece para IP público — nunca junto de "Especial"/"Broadcast". */
    @Test
    void avisoDeIpPublicoSoParaIpPublico() {
        for (String ip : new String[]{"0.0.0.0", "255.255.255.255", "100.64.0.1", "192.0.2.10"}) {
            assertTrue(!dicasTexto(kernel.processar(ip, 32)).contains("IP Público"), ip);
        }
        assertTrue(dicasTexto(kernel.processar("8.8.8.8", 32)).contains("IP Público"), "controle positivo");
    }

    // ---- Auditoria de 01/10/2026: CONT-02, CONT-03, CONT-30, CALC-22, CALC-36 ----
    // Gabarito: RFC 1112 §4 (D é grupo, E reservada), RFC 1122 §3.2.1.3 (rede/host/broadcast só em A/B/C),
    // RFC 3021 (/31 sem broadcast), RFC 3927 (link-local sem gateway).

    @Test
    void multicastNaoViraRedeDeHosts() {
        Map<String, Object> res = kernel.processar("224.0.0.5", 4);
        assertEquals("Endereço de grupo multicast (não é host)", res.get("ip_papel"));
        assertTrue(res.get("broad").toString().contains("não tem broadcast"), res.get("broad").toString());
        assertTrue(res.get("dhcp_info").toString().startsWith("N/A"), res.get("dhcp_info").toString());
        assertTrue(!res.get("cisco_cli").toString().contains("ip address"), res.get("cisco_cli").toString());
        assertNull(res.get("cisco_ospf_exemplo"));
        assertTrue(res.get("resumo_prova").toString().contains("Hosts úteis: N/A"));
        // fronteira: o mesmo /4 com host unicast (classe A) continua tendo rede de hosts
        Map<String, Object> controle = kernel.processar("10.0.0.5", 8);
        assertEquals("Host válido", controle.get("ip_papel"));
        assertEquals("10.255.255.255", controle.get("broad"));
    }

    @Test
    void classeELoopbackEApipaSemGatewayNemDhcp() {
        Map<String, Object> e = kernel.processar("240.0.0.1", 4);
        assertEquals("Reservado (classe E)", e.get("ip_papel"));
        Map<String, Object> lo = kernel.processar("127.0.0.1", 8);
        assertEquals("Loopback (localhost)", lo.get("ip_papel"));
        assertTrue(lo.get("gateway_sugerido").toString().startsWith("N/A"));
        assertTrue(lo.get("dhcp_info").toString().startsWith("N/A"));
        Map<String, Object> apipa = kernel.processar("169.254.10.20", 16);
        assertTrue(apipa.get("gateway_sugerido").toString().startsWith("N/A"));
        assertTrue(apipa.get("dhcp_info").toString().startsWith("N/A"));
    }

    @Test
    void dhcpNaoIncluiOGatewayEBarra31Ou32NaoTemBroadcast() {
        Map<String, Object> r24 = kernel.processar("192.168.10.50", 24);
        assertEquals("192.168.10.1", r24.get("gateway_sugerido"));
        assertTrue(r24.get("dhcp_info").toString().contains("192.168.10.2 até 192.168.10.254"),
                r24.get("dhcp_info").toString());
        Map<String, Object> r31 = kernel.processar("10.0.0.0", 31);
        assertTrue(r31.get("broad").toString().contains("RFC 3021"));
        assertEquals("10.0.0.1 (a outra ponta do /31)", r31.get("gateway_sugerido"));
        Map<String, Object> r32 = kernel.processar("10.0.0.7", 32);
        assertTrue(r32.get("broad").toString().startsWith("—"));
    }

    @Test
    void mascaraPadraoEDaClasseNaoDoPrefixo() {
        assertEquals("/24 (255.255.255.0)", kernel.processar("192.168.1.10", 26).get("mascara_padrao_classe"));
        assertTrue(kernel.processar("224.0.0.5", 4).get("mascara_padrao_classe").toString().startsWith("—"));
        // a máscara sozinha só indica classe nas três máscaras padrão
        assertEquals("B", kernel.classeReferenciaPorPrefixo(16));
        assertNull(kernel.classeReferenciaPorPrefixo(12));
        assertNull(kernel.classeReferenciaPorPrefixo(4));
        assertNull(kernel.classeReferenciaPorPrefixo(18));
    }

    @SuppressWarnings("unchecked")
    private static String dicasTexto(Map<String, Object> res) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> d : (java.util.List<Map<String, Object>>) res.get("seguranca_dicas")) {
            sb.append(d).append(' ');
        }
        return sb.toString();
    }
}
