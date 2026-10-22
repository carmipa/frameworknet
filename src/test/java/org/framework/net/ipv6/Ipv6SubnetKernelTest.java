package org.framework.net.ipv6;

import org.framework.net.ipv6.domain.Ipv6SubnetKernel;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.ComparacaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DecomposicaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DelegacaoPlano;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.EngenhariaReversaIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.FaixaCidrIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.NibblesIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.ProjetoRede;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.SumarizacaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DivisaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.Eui64Result;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.UlaResult;
import org.framework.net.ipv6.exception.Ipv6Exception;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o motor IPv6 com valores concretos. Gabaritos independentes da implementação (A3): fatos
 * de IPv6 (RFC 5952 para a forma canônica, 2^(128−prefixo) para a contagem, faixas especiais IANA).
 */
class Ipv6SubnetKernelTest {

    private final Ipv6SubnetKernel kernel = new Ipv6SubnetKernel();

    @Test
    void comprimeExpandeEContaEnderecos() {
        AnaliseIpv6 r = kernel.analisar("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertEquals("2001:db8::1", r.comprimido());
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", r.expandido());
        assertEquals(128, r.prefixo());
        assertFalse(r.temPrefixo());
        assertEquals("2^0", r.totalPotencia());
        assertEquals("1", r.totalEnderecos());
    }

    /**
     * BACK-04: o zone index sai, o prefixo depois dele fica (RFC 4007 §11.7). Gabarito do ipaddress do Python:
     * ip_interface("fe80::1%eth0/64").network == fe80::/64. Sem prefixo, a zona continua não mudando nada.
     */
    @Test
    void zoneIndexNaoApagaOPrefixo() {
        AnaliseIpv6 r = kernel.analisar("fe80::1%eth0/64");
        assertEquals(64, r.prefixo());
        assertTrue(r.temPrefixo());
        assertEquals("2^64", r.totalPotencia());
        AnaliseIpv6 semPrefixo = kernel.analisar("fe80::1%eth0");
        assertEquals(128, semPrefixo.prefixo());
        assertEquals("fe80::1", semPrefixo.comprimido());
    }

    /** BACK-12: IPv4-mapped na forma mista da RFC 5952 §5 (gabarito: o texto da RFC, não a biblioteca). */
    @Test
    void ipv4MappedSaiNaFormaMista() {
        assertEquals("::ffff:192.0.2.1", kernel.analisar("::ffff:192.0.2.1").comprimido());
        assertEquals("::ffff:192.0.2.1", kernel.analisar("0:0:0:0:0:ffff:c000:201").comprimido());
        // fronteira: o mesmo "::ffff" fora do bloco mapeado continua em hexadecimal
        assertEquals("::fffe:c000:201", kernel.analisar("::fffe:c000:201").comprimido());
    }

    @Test
    void prefixoInformadoContaBlocoInteiro() {
        AnaliseIpv6 r = kernel.analisar("2001:db8::/48");
        assertTrue(r.temPrefixo());
        assertEquals(48, r.prefixo());
        assertEquals("2^80", r.totalPotencia());   // 128 − 48
        assertEquals("2001:db8::", r.rede());
    }

    /**
     * Prefixo com host zerado (o valor PADRÃO da tela) é o endereço de rede, não uma faixa com "*".
     * Gabarito: Python ipaddress ip_network('2001:db8::/48').network_address / .exploded / reverse_pointer.
     * Fronteira (A1): host com bits ligados sob o mesmo prefixo continua sendo aquele host.
     */
    @Test
    void prefixoComHostZeradoMostraEnderecoDeRedeSemCuringa() {
        AnaliseIpv6 r = kernel.analisar("2001:db8::/48");
        assertEquals("2001:db8::", r.comprimido());
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0000", r.expandido());
        assertEquals("0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa", r.reversePtr());
        assertEquals("::", kernel.analisar("::/0").comprimido());
        assertEquals("fe80::", kernel.analisar("fe80::/10").comprimido());

        AnaliseIpv6 host = kernel.analisar("2001:db8::5/48");
        assertEquals("2001:db8::5", host.comprimido());
        assertEquals("2001:db8::", host.rede());
    }

    /**
     * RFC 5952 §4.2.2: "::" NÃO substitui um único hexteto zero; §4.2.3: empate vai para o mais à
     * esquerda. Gabarito: Python ipaddress .compressed (mesmos valores).
     */
    @Test
    void formaCanonicaRfc5952NaoComprimeHextetoZeroIsolado() {
        assertEquals("2001:db8:0:1:1:1:1:1", kernel.analisar("2001:db8:0:1:1:1:1:1").comprimido());
        assertEquals("2001:db8:0:1:ffff:ffff:ffff:ffff",
                kernel.analisar("2001:db8:0:1:ffff:ffff:ffff:ffff").comprimido());
        // Fronteira (A1): dois ou mais zeros continuam comprimidos, e o empate é à esquerda.
        assertEquals("2001:db8::1", kernel.analisar("2001:db8:0:0:0:0:0:1").comprimido());
        assertEquals("2001:db8::1:0:0:1", kernel.analisar("2001:db8:0:0:1:0:0:1").comprimido());
        assertEquals("1::", kernel.analisar("1:0:0:0:0:0:0:0").comprimido());
    }

    @Test
    void nibblesAceitaEntradaComPrefixo() {
        NibblesIpv6 n = kernel.nibbles("2001:db8::/64");
        assertEquals(32, n.nibbles().size());
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0000", n.expandido());
    }

    @Test
    void classificaPorFaixaEspecialIana() {
        assertEquals("Loopback", kernel.analisar("::1").tipo());
        assertEquals("Link-local", kernel.analisar("fe80::1").tipo());
        assertEquals("ULA / Privado", kernel.analisar("fd00::1").tipo());
        assertEquals("Documentação", kernel.analisar("2001:db8::1").tipo());
        assertEquals("Global unicast", kernel.analisar("2606:4700:4700::1111").tipo());
    }

    @Test
    void solicitedNodeParaTodoUnicast() {
        // Unicast global: ff02::1:ff + 24 bits baixos (RFC 4291 §2.7.1).
        // Gabarito: Python ipaddress — ff02::1:ff00:0 | (endereço & 0xFFFFFF).
        assertEquals("ff02::1:ff00:1111", kernel.analisar("2606:4700:4700::1111").solicitedNode());
        // Link-local É unicast e TEM solicited-node (usado no DAD/NDP) — gabarito RFC 4291.
        assertEquals("ff02::1:ff00:1", kernel.analisar("fe80::1").solicitedNode());
        // ULA também.
        assertEquals("ff02::1:ff00:abcd", kernel.analisar("fd00::abcd").solicitedNode());
        // Multicast, não-especificado e loopback NÃO têm solicited-node.
        assertEquals("—", kernel.analisar("ff02::1").solicitedNode());
        assertEquals("—", kernel.analisar("::").solicitedNode());
        assertEquals("—", kernel.analisar("::1").solicitedNode());
    }

    @Test
    void binarioTem8HextetosDe16Bits() {
        AnaliseIpv6 r = kernel.analisar("2001:db8::1");
        assertEquals(8, r.binarioHextetos().size());
        assertEquals(16, r.binarioHextetos().get(0).length());
        assertEquals("0010000000000001", r.binarioHextetos().get(0)); // 0x2001
    }

    @Test
    void dividirContaEEnumeraSubredes() {
        DivisaoIpv6 d = kernel.dividir("2001:db8::/32", 48, 256);
        assertEquals(32, d.prefixoBase());
        assertEquals(48, d.prefixoAlvo());
        assertEquals("65536", d.quantidadeSubredes());   // 2^(48−32)
        assertEquals(256, d.exibidas());                 // teto de renderização
        assertTrue(d.truncado());
        assertEquals("2001:db8::", d.subredes().get(0).rede());
        assertEquals("/48", d.subredes().get(0).prefixoStr());
    }

    @Test
    void dividirPequenoNaoTrunca() {
        DivisaoIpv6 d = kernel.dividir("2001:db8::/48", 50, 256);
        assertEquals("4", d.quantidadeSubredes());       // 2^(50−48)
        assertEquals(4, d.subredes().size());
        assertFalse(d.truncado());
        assertEquals("2001:db8::", d.subredes().get(0).rede());
    }

    @Test
    void alvoMaisAmploQueBaseEhRejeitado() {
        Ipv6Exception ex = assertThrows(Ipv6Exception.class,
                () -> kernel.dividir("2001:db8::/48", 32, 256));
        assertTrue(ex.getMessage().toLowerCase().contains("amplo"));
    }

    @Test
    void dividirSemPrefixoNaBaseEhRejeitado() {
        assertThrows(Ipv6Exception.class, () -> kernel.dividir("2001:db8::", 64, 256));
    }

    @Test
    void decomporTem128BitsCorteRedeInterfaceEDelegacao() {
        DecomposicaoIpv6 d = kernel.decompor("2001:db8::/48");
        assertEquals(8, d.hextetos().size());
        assertEquals(48, d.bitsRede());
        assertEquals(80, d.bitsInterface());
        // Hexteto 3 (bits 32-47) está inteiro no prefixo /48; o hexteto 4 (bits 48-63), fora.
        assertEquals(16, d.hextetos().get(2).bitsRede());
        assertEquals(0, d.hextetos().get(3).bitsRede());
        // Delegação: um /48 comporta 65536 sub-redes /64 (gabarito 2^(64-48)).
        assertTrue(d.delegacao().stream()
                .anyMatch(l -> l.prefixo().equals("/64") && l.quantidade().equals("65536")));
    }

    @Test
    void eui64DerivaInterfaceIdComFlipUL() {
        // Gabarito independente (RFC 4291): flip do bit U/L (00 XOR 02 = 02) + inserção de FFFE.
        Eui64Result e = kernel.eui64("2001:db8:0:1::/64", "00:1a:2b:3c:4d:5e");
        assertEquals("021a:2bff:fe3c:4d5e", e.interfaceId());
        // Forma canônica da lib (seancfoley) — comprime o grupo-zero único como "::".
        assertEquals("2001:db8:0:1:21a:2bff:fe3c:4d5e", e.enderecoSlaac());   // RFC 5952 §4.2.2
        assertEquals("2001:db8:0:1::/64", e.prefixoRede());
    }

    @Test
    void eui64RejeitaMacInvalido() {
        assertThrows(Ipv6Exception.class, () -> kernel.eui64("2001:db8::/64", "xyz"));
    }

    @Test
    void ulaComecaComFdEGeraGlobalIdDe40Bits() {
        UlaResult u = kernel.gerarUla("1");
        assertTrue(u.ula48().startsWith("fd"), "ULA deve começar com fd: " + u.ula48());
        assertTrue(u.ula48().endsWith("/48"));
        assertTrue(u.ula64().endsWith("/64"));
        assertEquals(10, u.globalId().length()); // 40 bits = 10 dígitos hex
        // RFC 4193: /48 = fd + Global ID; /64 = /48 + Subnet ID. Conferido com a biblioteca, bit a bit.
        java.math.BigInteger v48 = new java.math.BigInteger(1, new inet.ipaddr.IPAddressString(u.ula48())
                .getAddress().getLower().getBytes());
        java.math.BigInteger v64 = new java.math.BigInteger(1, new inet.ipaddr.IPAddressString(u.ula64())
                .getAddress().getLower().getBytes());
        assertEquals(new java.math.BigInteger(u.globalId(), 16), v48.shiftRight(80).and(java.math.BigInteger.ONE.shiftLeft(40).subtract(java.math.BigInteger.ONE)));
        assertEquals(v48.or(java.math.BigInteger.ONE.shiftLeft(64)), v64, "ula64 = ula48 com Subnet ID 1");
    }

    @Test
    void ulaRejeitaSubnetIdForaDaFaixa() {
        assertThrows(Ipv6Exception.class, () -> kernel.gerarUla("70000"));
    }

    @Test
    void compararMesmoEnderecoTemDistanciaZero() {
        ComparacaoIpv6 c = kernel.comparar("2001:db8::1", "2001:db8::1");
        assertTrue(c.mesmoEndereco());
        assertEquals("0", c.distancia());
        assertEquals(128, c.bitsComuns());
        assertTrue(c.mesmaLan());
    }

    @Test
    void compararVizinhosContam126BitsComuns() {
        // Gabarito independente: ...0001 vs ...0010 diferem nos 2 últimos bits → 126 bits em comum.
        ComparacaoIpv6 c = kernel.comparar("2001:db8::1", "2001:db8::2");
        assertFalse(c.mesmoEndereco());
        assertEquals(126, c.bitsComuns());
        assertEquals("1", c.distancia());
        assertTrue(c.mesmaLan());
    }

    @Test
    void compararPrefixoContemEnderecoNaMesmaLan() {
        ComparacaoIpv6 c = kernel.comparar("2001:db8:0:1::/64", "2001:db8:0:1:abcd::1");
        assertTrue(c.mesmaLan());
        assertEquals(64, c.bitsComuns());
        assertTrue(c.contencao().contains("contém"));
    }

    @Test
    void compararLansDiferentesNaoSaoMesmaLan() {
        // 2001:db8:0:1 vs 2001:db8:0:2: divergem no hexteto 4 (bit 63) → 62 bits comuns, < 64.
        ComparacaoIpv6 c = kernel.comparar("2001:db8:0:1::1", "2001:db8:0:2::1");
        assertFalse(c.mesmaLan());
        assertEquals(62, c.bitsComuns());
    }

    @Test
    void planejarDelegacaoAlocaUmBlocoPorNome() {
        DelegacaoPlano p = kernel.planejarDelegacao("2001:db8::/48", 64,
                java.util.List.of("Matriz", "Filial", "DMZ"), 256);
        assertEquals(48, p.prefixoBase());
        assertEquals(64, p.prefixoAlvo());
        assertEquals("65536", p.capacidade());   // 2^(64-48)
        assertEquals(3, p.usados());
        assertEquals(3, p.alocacoes().size());
        assertEquals("Matriz", p.alocacoes().get(0).nome());
        assertEquals("2001:db8::", p.alocacoes().get(0).rede());
        assertEquals("2001:db8:0:1::", p.alocacoes().get(1).rede());
        assertEquals("2001:db8::1", p.alocacoes().get(0).gateway()); // ::1 do bloco
    }

    @Test
    void planejarDelegacaoRejeitaAlvoNaoMaisEspecifico() {
        assertThrows(Ipv6Exception.class,
                () -> kernel.planejarDelegacao("2001:db8::/48", 48, java.util.List.of("A"), 256));
    }

    @Test
    void planejarDelegacaoRejeitaMaisNomesQueCapacidade() {
        // Um /48 em /50 comporta 4 blocos; pedir 5 estoura (gabarito 2^(50-48)=4).
        assertThrows(Ipv6Exception.class, () -> kernel.planejarDelegacao("2001:db8::/48", 50,
                java.util.List.of("a", "b", "c", "d", "e"), 256));
    }

    @Test
    void planejarDelegacaoRejeitaSemNomes() {
        assertThrows(Ipv6Exception.class,
                () -> kernel.planejarDelegacao("2001:db8::/48", 64, java.util.List.of(), 256));
    }

    @Test
    void sumarizarQuatroContiguasViramUmSlash62() {
        // Gabarito independente: quatro /64 contíguos (0..3) alinham em um /62.
        SumarizacaoIpv6 s = kernel.sumarizar(java.util.List.of(
                "2001:db8:0:0::/64", "2001:db8:0:1::/64", "2001:db8:0:2::/64", "2001:db8:0:3::/64"));
        assertEquals("2001:db8::/62", s.supernet());
        assertEquals(1, s.blocosMesclados().size());
        assertEquals("2001:db8::/62", s.blocosMesclados().get(0).cidr());
    }

    @Test
    void sumarizarDetectaContencao() {
        SumarizacaoIpv6 s = kernel.sumarizar(java.util.List.of("2001:db8::/32", "2001:db8:0:1::/64"));
        assertTrue(s.umContemOutro());
        assertTrue(s.relacao().toLowerCase().contains("cont"));
        assertEquals("2001:db8::/32", s.supernet());
    }

    /**
     * F46: como no IPv4, a sumarização avisa quando o supernet arrasta espaço que não pertence a
     * nenhuma entrada. Gabarito: Python — ip_network('2001:db8::/47').num_addresses = 2^81, e as
     * duas /64 somam 2^65; extra = 2^81 − 2^65. Quatro /64 contíguas = /62 exato (extra 0).
     */
    @Test
    void sumarizarInformaEspacoExtraArrastado() {
        SumarizacaoIpv6 s = kernel.sumarizar(java.util.List.of("2001:db8:0::/64", "2001:db8:1::/64"));
        assertEquals("2001:db8::/47", s.supernet());
        assertFalse(s.exata());
        assertEquals(java.math.BigInteger.TWO.pow(65).toString(), s.enderecosCobertos());
        assertEquals(java.math.BigInteger.TWO.pow(81).subtract(java.math.BigInteger.TWO.pow(65)).toString(),
                s.enderecosExtras());

        SumarizacaoIpv6 exato = kernel.sumarizar(java.util.List.of(
                "2001:db8:0:0::/64", "2001:db8:0:1::/64", "2001:db8:0:2::/64", "2001:db8:0:3::/64"));
        assertTrue(exato.exata());
        assertEquals("0", exato.enderecosExtras());
    }

    /**
     * F46: a linha "bits de interface" é endereço AND complemento do PREFIXO digitado (não os 64 bits
     * baixos fixos); no /128 não há fronteira. Gabarito: Python
     * ip_address(int(ip_address('2001:db8:1:2::')) & ((1 << 80) - 1)) → '0:0:0:2::'.
     */
    @Test
    void aplicacaoDoPrefixoUsaOPrefixoDigitado() {
        var r48 = kernel.analisarRica("2001:db8:1:2::/48", 4);
        String iface = r48.aplicacaoPrefixo().stream()
                .filter(l -> l.campo().startsWith("Bits de interface")).findFirst().orElseThrow().valor();
        assertEquals("0:0:0:2::", iface);

        var r128 = kernel.analisarRica("2001:db8::1", 4);
        String fronteira = r128.aplicacaoPrefixo().stream()
                .filter(l -> l.campo().startsWith("Fronteira")).findFirst().orElseThrow().valor();
        assertTrue(fronteira.contains("/128"), fronteira);
        assertFalse(fronteira.contains("após o bit 128"), fronteira);
    }

    @Test
    void sumarizarRejeitaListaVazia() {
        assertThrows(Ipv6Exception.class, () -> kernel.sumarizar(java.util.List.of()));
    }

    @Test
    void faixaParaCidrCobreExatamenteUmSlash112() {
        // 2001:db8::0000 .. 2001:db8::ffff = 2^16 endereços alinhados = um /112.
        FaixaCidrIpv6 f = kernel.faixaParaCidr("2001:db8::", "2001:db8::ffff", 256);
        assertEquals(1, f.quantidadeBlocos());
        assertEquals("2001:db8::/112", f.blocos().get(0).cidr());
    }

    @Test
    void faixaParaCidrRejeitaFaixaInvertida() {
        Ipv6Exception ex = assertThrows(Ipv6Exception.class,
                () -> kernel.faixaParaCidr("2001:db8::ffff", "2001:db8::", 256));
        assertTrue(ex.getMessage().toLowerCase().contains("invertida"));
    }

    @Test
    void faixaParaCidrRejeitaEnderecoComPrefixo() {
        assertThrows(Ipv6Exception.class, () -> kernel.faixaParaCidr("2001:db8::/64", "2001:db8::ffff", 256));
    }

    @Test
    void projetarEstrelaAlocaLansEEnlaces() {
        ProjetoRede p = kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of("Matriz", "Filial", "DataCenter"), 100, 1);
        assertEquals(3, p.totalLocais());
        assertEquals(2, p.totalLinks());          // árvore: n-1
        assertEquals("65536", p.capacidadeLan()); // 2^(64-48)
        assertEquals("2001:db8::", p.lans().get(0).rede());
        assertEquals("2001:db8::1", p.lans().get(0).gateway());
        assertEquals("2001:db8:0:1::", p.lans().get(1).rede());
        assertEquals(3, p.roteadores().size());
        assertEquals(2, p.wans().size());
        assertTrue(p.roteadores().get(0).cli().contains("ipv6 unicast-routing"));
        assertTrue(p.roteadores().get(0).cli().contains("ipv6 router ospf 1"));
    }

    @Test
    void projetarMalhaTemNvezesNmenos1sobre2Enlaces() {
        ProjetoRede p = kernel.projetarRede("2001:db8::/48", 64, 127, "malha",
                java.util.List.of("A", "B", "C", "D"), 100, 1);
        assertEquals(6, p.totalLinks());          // 4*3/2
    }

    @Test
    void projetarRejeitaLanNaoMaisEspecifica() {
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 48, 127, "estrela",
                java.util.List.of("A"), 100, 1));
    }

    /**
     * F40: as WANs precisam caber na base. /62 = 4 blocos /64 (gabarito: 2^(64−62)). Com 4 LANs não
     * sobra espaço para enlace; com 3, o /127 cabe no 4º /64. Fronteira (A1) + contenção medida com a
     * biblioteca (não com a lógica sob teste).
     */
    @Test
    void projetarExigeQueAsWansCaibamNaBase() {
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/62", 64, 127, "estrela",
                java.util.List.of("A", "B", "C", "D"), 100, 1));

        ProjetoRede ok = kernel.projetarRede("2001:db8::/62", 64, 127, "estrela",
                java.util.List.of("A", "B", "C"), 100, 1);
        inet.ipaddr.IPAddress base = new inet.ipaddr.IPAddressString("2001:db8::/62").getAddress();
        for (var w : ok.wans()) {
            assertTrue(base.contains(new inet.ipaddr.IPAddressString(w.rede() + w.prefixoStr()).getAddress()),
                    "WAN fora da base: " + w.rede() + w.prefixoStr());
        }
        assertEquals(2, ok.wans().size());
    }

    /** Um /128 tem um endereço só: não serve para enlace de dois roteadores (RFC 6164 usa /127). */
    @Test
    void projetarRejeitaWan128() {
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 64, 128, "estrela",
                java.util.List.of("A", "B", "C"), 100, 1));
        assertEquals(2, kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of("A", "B", "C"), 100, 1).wans().size());
    }

    /**
     * F01: sem teto, 990 localidades em malha estouravam a memória (medido). O teto é o mesmo do
     * IPv4 (InputLimits.MAX_LOCATION_ROWS = 50). Fronteira: 50 passa (malha = 1225 enlaces), 51 não.
     */
    @Test
    void projetarLimitaQuantidadeDeLocalidades() {
        java.util.List<String> cinquenta = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            cinquenta.add("L" + i);
        }
        assertEquals(1225, kernel.projetarRede("2001:db8::/48", 64, 127, "malha",
                cinquenta, 100, 1).wans().size());
        java.util.List<String> cinquentaEUma = new java.util.ArrayList<>(cinquenta);
        cinquentaEUma.add("L51");
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 64, 127, "malha",
                cinquentaEUma, 100, 1));
    }

    @Test
    void projetarRejeitaSemLocalidades() {
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of(), 100, 1));
    }

    @Test
    void engenhariaReversaReconstroiInterfacesERotas() {
        EngenhariaReversaIpv6 e = kernel.engenhariaReversa(
                "hostname R1\nipv6 unicast-routing\ninterface GigabitEthernet0/0\n ipv6 address 2001:db8:0:1::1/64\n"
                        + "ipv6 route 2001:db8:0:2::/64 2001:db8:0:ffff::1\nipv6 router ospf 1");
        assertEquals("R1", e.hostname());
        assertTrue(e.unicastRouting());
        assertEquals(1, e.interfaces().size());
        assertEquals("2001:db8:0:1::1/64", e.enderecos().get(0).endereco());
        assertEquals("Documentação", e.enderecos().get(0).tipo()); // 2001:db8::/32
        assertEquals(1, e.rotas().size());
        assertTrue(e.protocolos().stream().anyMatch(s -> s.contains("OSPFv3")));
    }

    @Test
    void engenhariaReversaSemUnicastRoutingAcusaAchado() {
        EngenhariaReversaIpv6 e = kernel.engenhariaReversa(
                "interface Gig0/0\n ipv6 address 2001:db8:0:1::1/64\ninterface Gig0/1\n ipv6 address 2001:db8:0:2::1/64\nipv6 router ospf 1");
        assertFalse(e.unicastRouting());
        assertTrue(e.achados().stream().anyMatch(a -> a.contains("unicast-routing")));
    }

    @Test
    void engenhariaReversaVaziaRejeitada() {
        assertThrows(Ipv6Exception.class, () -> kernel.engenhariaReversa("   "));
    }

    @Test
    void nibblesExpandeEComprimeCom32Nibbles() {
        NibblesIpv6 n = kernel.nibbles("2001:db8::1");
        assertEquals("2001:db8::1", n.comprimido());
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", n.expandido());
        assertEquals(32, n.nibbles().size());
        assertEquals('2', n.nibbles().get(0).hex());
        assertEquals("0001", n.nibbles().get(3).bin());   // 4º nibble = '1'
        assertEquals('1', n.nibbles().get(31).hex());      // último nibble
        assertEquals(8, n.nibbles().get(31).hexteto());
    }

    @Test
    void nibblesRejeitaEntradaInvalida() {
        assertThrows(Ipv6Exception.class, () -> kernel.nibbles("nao-e-ipv6"));
    }

    @Test
    void planejarVlansAlocaUmSlash64PorVlanComGatewayESvi() {
        Ipv6SubnetKernel.VlanPlano v = kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(10, 20, 30), java.util.List.of("Servidores", "Wi-Fi", "Voz"),
                false, 100);
        assertEquals(3, v.total());
        assertEquals(48, v.prefixoBase());
        assertEquals(64, v.prefixoLan());
        assertEquals("65536", v.capacidade());              // 2^(64-48)
        assertEquals("2001:db8::/48", v.baseCidr());
        assertEquals(3, v.vlans().size());
        // LANs contíguas: ::/64, 0:1::/64, 0:2::/64 (gabarito independente: passo 2^(128-64))
        assertEquals("2001:db8::", v.vlans().get(0).rede());
        assertEquals("2001:db8::1", v.vlans().get(0).gateway());
        assertEquals("2001:db8:0:1::", v.vlans().get(1).rede());
        assertEquals("2001:db8:0:1::1", v.vlans().get(1).gateway());
        assertEquals("2001:db8:0:2::", v.vlans().get(2).rede());
        assertEquals(10, v.vlans().get(0).vlanId());
        assertEquals("/64", v.vlans().get(0).prefixoStr());
        // SVI: interface VlanN + endereço do gateway. SLAAC puro = RA com o prefixo e M=0/O=0
        // (RFC 4861 §4.2): other-config-flag (O=1) mandaria o host buscar DHCPv6 stateless.
        assertTrue(v.vlans().get(0).cisco().contains("interface Vlan10"));
        assertTrue(v.vlans().get(0).cisco().contains("ipv6 address 2001:db8::1/64"));
        assertFalse(v.vlans().get(0).cisco().contains("other-config-flag"));
        assertFalse(v.vlans().get(0).cisco().contains("managed-config-flag"));
        // trunk 802.1Q com as VLANs permitidas
        assertTrue(v.trunkCli().contains("switchport trunk allowed vlan 10,20,30"));
    }

    @Test
    void planejarVlansDhcpv6MarcaManagedConfigFlag() {
        Ipv6SubnetKernel.VlanPlano v = kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(10), java.util.List.of("Servidores"), true, 100);
        assertTrue(v.vlans().get(0).cisco().contains("ipv6 nd managed-config-flag"));
        assertTrue(v.dhcpNota().toLowerCase().contains("dhcpv6"));
    }

    @Test
    void planejarVlansRejeitaIdForaDeFaixaEListaVazia() {
        // A1: ID válido (10) aceito; ID fora de 1..4094 e lista vazia rejeitados.
        assertEquals(1, kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(10), java.util.List.of("ok"), false, 100).total());
        assertThrows(Ipv6Exception.class, () -> kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(5000), java.util.List.of("estoura"), false, 100));
        assertThrows(Ipv6Exception.class, () -> kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(), java.util.List.of(), false, 100));
    }

    /**
     * F44: o IOS recusa dois "interface Vlan10" com endereços distintos no mesmo plano e não permite
     * criar/renomear as VLANs 1002–1005 (Cisco, FDDI/Token Ring) nem renomear a VLAN 1 (default).
     * Mesmo contrato do gêmeo IPv4 (calculadora.VlanService). Fronteira (A1): 1001 e 1006 passam.
     */
    @Test
    void planejarVlansRejeitaIdRepetidoEReservadoCisco() {
        assertThrows(Ipv6Exception.class, () -> kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(10, 10), java.util.List.of("A", "B"), false, 100));
        for (int reservado : new int[]{1002, 1005}) {
            assertThrows(Ipv6Exception.class, () -> kernel.planejarVlans("2001:db8::/48", 64,
                    java.util.List.of(reservado), java.util.List.of("X"), false, 100), "VLAN " + reservado);
        }
        assertEquals(2, kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(1001, 1006), java.util.List.of("A", "B"), false, 100).total());

        // VLAN 1 existe sempre: o plano configura o SVI, mas não tenta "vlan 1 / name".
        String cli1 = kernel.planejarVlans("2001:db8::/48", 64,
                java.util.List.of(1), java.util.List.of("gerencia"), false, 100).vlans().get(0).cisco();
        assertTrue(cli1.contains("interface Vlan1"));
        assertFalse(cli1.contains("vlan 1\n"), cli1);
    }

    /**
     * F45: o "prefixo de rede" exibido é sempre o /64 do SLAAC (64 bits altos, resto zero), qualquer
     * que seja a forma digitada. Gabarito: Python ip_network('2001:db8:0:1::5/64', strict=False).
     */
    @Test
    void eui64MostraSempreOSlash64DoPrefixo() {
        String mac = "00:1a:2b:3c:4d:5e";
        assertEquals("2001:db8:0:1::/64", kernel.eui64("2001:db8:0:1::/64", mac).prefixoRede());
        assertEquals("2001:db8:0:1::/64", kernel.eui64("2001:db8:0:1::5", mac).prefixoRede());
        assertEquals("2001:db8:0:1::/64", kernel.eui64("2001:db8:0:1:ffff::/80", mac).prefixoRede());
        assertEquals("2001:db8:0:1:21a:2bff:fe3c:4d5e", kernel.eui64("2001:db8:0:1::5", mac).enderecoSlaac());
    }

    /** BACK-07: a redução ao /64 continua (F45), mas deixa de ser silenciosa — /64 não avisa, /96 e /48 avisam. */
    @Test
    void eui64AvisaQuandoOPrefixoNaoEraSlash64() {
        String mac = "00:1a:2b:3c:4d:5e";
        assertEquals("", kernel.eui64("2001:db8:0:1::/64", mac).aviso());
        assertTrue(kernel.eui64("2001:db8:0:1::/96", mac).aviso().contains("/96 informado"));
        assertTrue(kernel.eui64("2001:db8::/48", mac).aviso().contains("2001:db8::/64"));
        assertTrue(kernel.eui64("2001:db8:0:1::5", mac).aviso().contains("Sem prefixo informado"));
    }

    @Test
    void planejarVlansRejeitaPrefixoLanNaoMaisEspecificoQueBase() {
        assertThrows(Ipv6Exception.class, () -> kernel.planejarVlans("2001:db8::/64", 64,
                java.util.List.of(10), java.util.List.of("x"), false, 100));
    }

    /**
     * Auditoria CALC-29/CONT-32 — gabarito (A3): RFC 4291 §2.6.1 (identificador todo zero é o anycast
     * Subnet-Router, não endereço de interface), IOS (OSPFv3 sem IPv4 precisa de router-id). Fronteira
     * (A1): o mesmo prefixo sai com ::1; /128 vai numa Loopback; multicast e ::/0 não viram "ipv6 address".
     */
    @Test
    void dicaCiscoNaoConfiguraAnycastNemEnderecoQueNaoEDeInterface() {
        String lan = kernel.decompor("2001:db8:1::/64").ciscoCli();
        assertTrue(lan.contains(" ipv6 address 2001:db8:1::1/64"), lan);
        assertFalse(lan.contains(" ipv6 address 2001:db8:1::/64"), lan);
        assertTrue(lan.contains(" router-id "), lan);

        String loop = kernel.decompor("2001:db8::5/128").ciscoCli();
        assertTrue(loop.contains("interface Loopback0") && loop.contains(" ipv6 address 2001:db8::5/128"), loop);
        assertTrue(kernel.decompor("2001:db8::5/128").gatewayLinkLocal().startsWith("—"),
                "/128 não tem gateway dentro do bloco");

        for (String naoInterface : new String[]{"ff02::1/128", "::1/128"}) {
            String cli = kernel.decompor(naoInterface).ciscoCli();
            assertTrue(cli.startsWith("!") && !cli.contains("ipv6 address"), naoInterface + " -> " + cli);
        }
        String padrao = kernel.decompor("::/0").ciscoCli();
        assertTrue(padrao.contains("ipv6 route ::/0") && !padrao.contains("ipv6 address"), padrao);
    }

    /**
     * Auditoria CALC-09: cada roteador de borda aponta a default para a outra ponta do PRÓPRIO enlace;
     * EIGRP só como alternativa comentada; AS e processo fora de 1–65535 são recusados (fronteira nos dois
     * extremos aceita).
     */
    @Test
    void projetoRotaDefaultPorEnlaceEEigrpComoAlternativa() {
        ProjetoRede p = kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of("Matriz", "Filial", "DataCenter"), 100, 1);
        java.util.List<String> defaults = p.rotasEstaticas().stream().filter(r -> r.startsWith("ipv6 route ::/0")).toList();
        assertEquals(2, defaults.size(), p.rotasEstaticas().toString());
        assertEquals("ipv6 route ::/0 " + p.wans().get(0).ipA(), defaults.get(0));
        assertEquals("ipv6 route ::/0 " + p.wans().get(1).ipA(), defaults.get(1));
        String cli = p.roteadores().get(1).cli();
        assertFalse(cli.contains("\nipv6 router eigrp"), "EIGRP ativo sem interface não anuncia nada:\n" + cli);
        assertTrue(cli.contains("! ipv6 router eigrp 100"), cli);

        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of("A", "B"), 0, 1));
        assertThrows(Ipv6Exception.class, () -> kernel.projetarRede("2001:db8::/48", 64, 127, "estrela",
                java.util.List.of("A", "B"), 100, -1));
        kernel.projetarRede("2001:db8::/48", 64, 127, "estrela", java.util.List.of("A", "B"), 1, 65535);
    }

    /** Auditoria CALC-08: o plano de VLANs liga o roteamento IPv6 uma vez e cria o pool do DHCPv6. */
    @Test
    void vlansIpv6RoteiamECriamOPoolDoDhcpv6() {
        var comDhcp = kernel.planejarVlans("2001:db8::/48", 64, java.util.List.of(10, 20),
                java.util.List.of("ADM", "TI"), true, 100);
        String primeira = comDhcp.vlans().get(0).cisco();
        String segunda = comDhcp.vlans().get(1).cisco();
        assertTrue(primeira.contains("ipv6 unicast-routing"), primeira);
        assertFalse(segunda.contains("ipv6 unicast-routing"), "uma vez só:\n" + segunda);
        assertTrue(primeira.contains("ipv6 dhcp pool VLAN10\n address prefix 2001:db8::/64"), primeira);
        assertTrue(segunda.contains("ipv6 dhcp pool VLAN20\n address prefix 2001:db8:0:1::/64"), segunda);

        var slaac = kernel.planejarVlans("2001:db8::/48", 64, java.util.List.of(10),
                java.util.List.of("ADM"), false, 100);
        assertFalse(slaac.vlans().get(0).cisco().contains("ipv6 dhcp pool"));
        assertTrue(slaac.vlans().get(0).cisco().contains("ipv6 unicast-routing"));
    }

    /**
     * Auditoria CALC-15: "link-local" e "eui-64" são sintaxe válida; porta de switch (switchport) não é
     * "interface sem endereço"; redes conectadas se alcançam. Fronteira (A1): endereço realmente inválido
     * continua acusado, e interface L3 sem endereço também.
     */
    @Test
    void engenhariaReversaAceitaModificadoresEPortaDeSwitch() {
        EngenhariaReversaIpv6 e = kernel.engenhariaReversa(String.join("\n",
                "hostname SW1",
                "ipv6 unicast-routing",
                "interface GigabitEthernet0/1",
                " switchport mode trunk",
                "interface Vlan10",
                " ipv6 address fe80::1 link-local",
                " ipv6 address 2001:db8:a::/64 eui-64",
                "interface Vlan20",
                " ipv6 address 2001:db8:b::1/64"));
        String achados = String.join(" | ", e.achados());
        assertFalse(achados.contains("inválido"), achados);
        assertFalse(achados.contains("sem endereço"), achados);
        assertFalse(achados.contains("as LANs não se alcançam"), achados);
        assertTrue(achados.contains("as redes ligadas a este roteador se alcançam"), achados);

        EngenhariaReversaIpv6 ruim = kernel.engenhariaReversa(String.join("\n",
                "ipv6 unicast-routing",
                "interface GigabitEthernet0/0",
                " ipv6 address 2001:zz::1/64",
                "interface GigabitEthernet0/1"));
        String achadosRuins = String.join(" | ", ruim.achados());
        assertTrue(achadosRuins.contains("inválido"), achadosRuins);
        assertTrue(achadosRuins.contains("1 interface(s) sem endereço"), achadosRuins);
    }

    /** Auditoria CONT-31/CALC-31: as duas telas IPv6 classificam 3fff::/20 e 2001:2::/48 igual. */
    @Test
    void faixasDocumentacaoEBenchmarkNoKernel() {
        assertEquals("Documentação", kernel.analisar("3fff::1").tipo());
        assertEquals("Benchmarking", kernel.analisar("2001:2::1").tipo());
        assertEquals("Global unicast", kernel.analisar("3fff:1000::1").tipo());
    }

    @Test
    void casoControleEntradaInvalidaXlegitima() {
        // A1: malformado e IPv4 puro rejeitados; o legítimo semelhante é aceito.
        assertThrows(Ipv6Exception.class, () -> kernel.analisar("nao-e-ipv6"));
        assertThrows(Ipv6Exception.class, () -> kernel.analisar(""));
        assertThrows(Ipv6Exception.class, () -> kernel.analisar("192.168.0.1"));
        assertEquals("2001:db8::", kernel.analisar("2001:db8::").comprimido());
    }
}
