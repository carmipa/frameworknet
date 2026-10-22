package org.framework.net.ipv6.domain;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv6.IPv6Address;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.ipv6.exception.Ipv6Exception;
import org.framework.net.shared.InputLimits;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kernel de cálculo de sub-redes IPv6.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> é o equivalente IPv6 do {@code SubnetKernel} do IPv4 — analisa um
 * endereço/prefixo (normalização RFC 5952, expansão, tipo, rede, faixa, quantidade, Interface ID,
 * multicast solicited-node, binário por hexteto) e divide um prefixo em sub-redes menores. Preenche
 * o buraco central do projeto, que só tinha analisador de endereço único de IPv6.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> IPv6 <b>não tem broadcast</b> e a aritmética de "hosts úteis =
 * total − 2" do IPv4 <b>não se aplica</b> — este kernel nunca subtrai rede/broadcast. Contagens de
 * endereços usam {@link BigInteger} (um /0 tem 2^128 endereços, estoura {@code long}). O prefixo
 * alvo de uma divisão é sempre maior (mais específico) que o prefixo base, entre 0 e 128.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada malformada, prefixo fora de 0–128 ou alvo menor
 * que a base lançam {@link Ipv6Exception} com mensagem didática. Nunca propaga exceção crua da
 * biblioteca nem devolve resultado parcial.</p>
 */
@ApplicationScoped
public class Ipv6SubnetKernel {

    private static final String SEM_GATEWAY_128 = "— (/128 é um endereço único: não há gateway no bloco)";

    /** Teto de interfaces por análise na Engenharia reversa IPv6 (um roteador real tem poucas dezenas). */
    public static final int MAX_INTERFACES_ENGENHARIA = 200;

    /** Faixas especiais IANA, avaliadas em ordem (a primeira que contém o endereço vence). */
    private static final List<FaixaEspecial> FAIXAS = List.of(
            new FaixaEspecial("::1/128", "Loopback", "Host local (equivale ao 127.0.0.1 do IPv4)"),
            new FaixaEspecial("::/128", "Não especificado", "Ausência de endereço (equivale ao 0.0.0.0)"),
            new FaixaEspecial("::ffff:0:0/96", "IPv4-mapped", "Carrega um IPv4 dentro do IPv6 (::ffff:a.b.c.d)"),
            new FaixaEspecial("64:ff9b::/96", "NAT64", "Tradução IPv6↔IPv4 (well-known prefix)"),
            new FaixaEspecial("2001:db8::/32", "Documentação", "Reservado para exemplos e material didático (RFC 3849)"),
            new FaixaEspecial("3fff::/20", "Documentação", "Reservado para exemplos com prefixos maiores (RFC 9637)"),
            new FaixaEspecial("2001:2::/48", "Benchmarking", "Testes de desempenho em laboratório; não roteável (RFC 5180)"),
            new FaixaEspecial("2001::/32", "Teredo", "Túnel IPv6 sobre UDP/IPv4"),
            new FaixaEspecial("2002::/16", "6to4", "Túnel IPv6 sobre IPv4"),
            new FaixaEspecial("fe80::/10", "Link-local", "Válido só no enlace; não roteável (autoconfiguração/NDP)"),
            new FaixaEspecial("fc00::/7", "ULA / Privado", "Uso interno, similar ao RFC 1918 do IPv4 (RFC 4193)"),
            new FaixaEspecial("ff00::/8", "Multicast", "Transmissão para um grupo (IPv6 não tem broadcast)"),
            new FaixaEspecial("2000::/3", "Global unicast", "Roteável na Internet")
    );

    /**
     * Analisa um endereço ou prefixo IPv6 e devolve todos os campos didáticos.
     *
     * @param entrada ex.: {@code 2001:db8::1}, {@code 2001:db8::/48}, {@code fe80::1%eth0}
     */
    public AnaliseIpv6 analisar(String entrada) {
        String bruto = entrada == null ? "" : entrada.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe um endereço ou prefixo IPv6 (ex.: 2001:db8::/48).");
        }
        // O zone index (%eth0) é informação de escopo, não entra no cálculo. Sai só ele: o prefixo que vem DEPOIS
        // da zona continua valendo (RFC 4007 §11.7, "fe80::1%eth0/64"). Cortar tudo a partir do % apagava o /64 e
        // a análise saía como /128 com 1 endereço (auditoria de 22/10/2026, BACK-04).
        String semZona = bruto.replaceFirst("%[^/]*", "").strip();

        IPAddressString parser = new IPAddressString(semZona);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("IPv6 inválido: formato não reconhecido (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        if (addr == null) {
            throw new Ipv6Exception("IPv6 inválido: " + bruto + ".");
        }

        Integer prefixoInformado = addr.getNetworkPrefixLength();
        boolean temPrefixo = prefixoInformado != null;
        int prefixo = temPrefixo ? prefixoInformado : 128;

        // Prefixo com host zerado ("2001:db8::/48") a biblioteca trata como BLOCO; withoutPrefixLength()
        // manteria a faixa inteira e a tela mostraria "2001:db8::*:*:*:*:*". getLower() devolve o endereço
        // de rede nesse caso e o próprio host quando há bits de host ("2001:db8::5/48").
        IPv6Address host = addr.getLower().withoutPrefixLength();
        IPv6Address bloco = prefixoBloco(host, prefixo);

        // IPv4-mapped (::ffff:0:0/96): a RFC 5952 §5 recomenda a forma mista, com o IPv4 em decimal pontuado
        // ("::ffff:192.0.2.1"), e não "::ffff:c000:201" (auditoria de 22/10/2026, BACK-12).
        String comprimido = host.isIPv4Mapped() ? host.toMixedString() : host.toCanonicalString();
        String expandido = host.toFullString();
        // getLower()/getUpper() de um bloco prefixado carregam o /n; withoutPrefixLength() tira o
        // sufixo para exibir só o endereço (senão "rede" sairia como "2001:db8::/48").
        String rede = bloco.getLower().withoutPrefixLength().toCanonicalString();
        String primeiro = bloco.getLower().withoutPrefixLength().toCanonicalString();
        String ultimo = bloco.getUpper().withoutPrefixLength().toCanonicalString();
        BigInteger total = bloco.getCount();

        FaixaEspecial faixa = classificar(host);
        // Solicited-node existe para todo endereço UNICAST (RFC 4291 §2.7.1) — global, ULA, link-local,
        // documentação etc. Só NÃO se aplica a multicast, ao não-especificado (::) e ao loopback (::1,
        // que não vive num enlace). Antes só global/ULA tinham — link-local (usado no DAD/NDP) ficava sem.
        boolean temSolicitedNode = !faixa.tipo().equals("Multicast")
                && !faixa.tipo().equals("Não especificado")
                && !faixa.tipo().equals("Loopback");

        return new AnaliseIpv6(
                bruto,
                comprimido,
                expandido,
                prefixo,
                temPrefixo,
                rede,
                primeiro,
                ultimo,
                total.toString(),
                "2^" + (128 - prefixo),
                interfaceId(host),
                faixa.tipo(),
                faixa.descricao(),
                temSolicitedNode ? solicitedNode(host) : "—",
                host.toReverseDNSLookupString(),
                binarioHextetos(host));
    }

    /**
     * Divide um prefixo base em sub-redes de {@code prefixoAlvo}.
     *
     * @param maxLinhas teto de renderização; o total matemático é sempre informado por inteiro.
     */
    public DivisaoIpv6 dividir(String cidr, int prefixoAlvo, int maxLinhas) {
        String bruto = cidr == null ? "" : cidr.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe a rede base em CIDR (ex.: 2001:db8::/32).");
        }
        if (prefixoAlvo < 0 || prefixoAlvo > 128) {
            throw new Ipv6Exception("Prefixo alvo inválido: use um valor entre /0 e /128.");
        }
        IPAddressString parser = new IPAddressString(bruto);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("Rede base IPv6 inválida (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        Integer prefixoBaseInformado = addr.getNetworkPrefixLength();
        if (prefixoBaseInformado == null) {
            throw new Ipv6Exception("Informe o prefixo da rede base (ex.: 2001:db8::/32), não só o endereço.");
        }
        int prefixoBase = prefixoBaseInformado;
        if (prefixoAlvo < prefixoBase) {
            throw new Ipv6Exception("O prefixo alvo /" + prefixoAlvo + " é mais amplo que a base /" + prefixoBase
                    + ". Sub-rede é sempre um pedaço menor: use um prefixo de /" + prefixoBase + " a /128.");
        }

        IPv6Address baseBloco = addr.toPrefixBlock();
        BigInteger inicio = new BigInteger(1, baseBloco.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixoAlvo);
        BigInteger quantidade = BigInteger.TWO.pow(prefixoAlvo - prefixoBase);
        BigInteger enderecosPorSubrede = passo;

        int limite = quantidade.min(BigInteger.valueOf(Math.max(0, maxLinhas))).intValueExact();
        List<SubredeIpv6> subredes = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            BigInteger valor = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            IPv6Address rede = new IPv6Address(paraBytes16(valor));
            subredes.add(new SubredeIpv6(
                    i + 1,
                    rede.toCanonicalString(),
                    "/" + prefixoAlvo,
                    rede.toCanonicalString(),
                    ultimoDoBloco(valor, passo)));
        }
        boolean truncado = quantidade.compareTo(BigInteger.valueOf(limite)) > 0;

        return new DivisaoIpv6(
                baseBloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixoBase,
                prefixoBase,
                prefixoAlvo,
                quantidade.toString(),
                enderecosPorSubrede.toString(),
                truncado,
                subredes.size(),
                subredes);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: decomposição didática profunda de um endereço/prefixo IPv6 — o análogo
     * IPv6 do "array de 32 bits" e da tabela AND do IPv4, para a aba de Análise. Expõe os 128 bits
     * por hexteto marcando o corte entre bits de REDE (prefixo) e de INTERFACE, a tabela de
     * delegação (/48·/56·/64), o gateway sugerido e a dica Cisco/OSPFv3.
     *
     * INVARIANTES DO DOMÍNIO: reusa {@link #analisar} (fonte única); os bits de rede de cada hexteto
     * derivam só do prefixo; sem broadcast e sem "hosts úteis" — conceitos que não existem em IPv6.
     *
     * COMPORTAMENTO EM CASO DE FALHA: propaga a {@link Ipv6Exception} de {@code analisar}.
     */
    public DecomposicaoIpv6 decompor(String entrada) {
        AnaliseIpv6 base = analisar(entrada);
        int prefixo = base.prefixo();

        List<HextetInfo> hextetos = new ArrayList<>(8);
        List<String> bins = base.binarioHextetos();
        for (int i = 0; i < 8; i++) {
            int bitsRedeNoHextet = Math.max(0, Math.min(16, prefixo - i * 16));
            String bin = bins.get(i);
            hextetos.add(new HextetInfo(i + 1, hexDoBin(bin), bin, bitsRedeNoHextet,
                    bin.substring(0, bitsRedeNoHextet), bin.substring(bitsRedeNoHextet)));
        }

        List<DelegacaoInfo> delegacao = new ArrayList<>();
        delegacao.add(delegacaoLinha(prefixo, 48, "Site — alocação típica de um cliente por um RIR/ISP"));
        delegacao.add(delegacaoLinha(prefixo, 56, "Residência / filial — delegação comum do provedor"));
        delegacao.add(delegacaoLinha(prefixo, 64, "LAN — fronteira do SLAAC (uma sub-rede por enlace)"));

        String cisco = dicaCisco(base, prefixo);

        return new DecomposicaoIpv6(base, hextetos, prefixo, 128 - prefixo,
                gatewaySugerido(base.rede(), prefixo), delegacao, cisco, enunciadoProva(prefixo));
    }

    /**
     * Exemplo de configuração Cisco para o endereço decomposto.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> mostrar como o prefixo estudado entra num roteador real.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a interface recebe o gateway do bloco (::1), nunca o endereço de
     * rede — o identificador de interface todo zero é o anycast Subnet-Router (RFC 4291 §2.6.1); /128 vai
     * numa Loopback; multicast, não especificado, loopback e IPv4-mapeado não se configuram em interface;
     * /0 é rota padrão; OSPFv3 sem IPv4 no roteador exige router-id (auditoria CALC-29, CONT-32).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; o caso que não vira configuração devolve um
     * comentário IOS ("!") explicando por quê.</p>
     */
    private String dicaCisco(AnaliseIpv6 base, int prefixo) {
        String tipo = base.tipo();
        if (prefixo == 0) {
            return "! ::/0 não é endereço de interface: é a rota padrão.\n"
                    + "ipv6 unicast-routing\nipv6 route ::/0 <próximo-salto>";
        }
        if (Set.of("Multicast", "Não especificado", "Loopback", "IPv4-mapped").contains(tipo)) {
            return "! " + tipo + ": este endereço não se configura em interface de roteador.";
        }
        if ("Link-local".equals(tipo)) {
            return "interface GigabitEthernet0/0\n ipv6 address " + base.comprimido() + " link-local";
        }
        String ospf = "ipv6 router ospf 1\n router-id 1.1.1.1\n";
        if (prefixo == 128) {
            return "ipv6 unicast-routing\n" + ospf + "interface Loopback0\n ipv6 address "
                    + base.comprimido() + "/128\n ipv6 ospf 1 area 0";
        }
        return "ipv6 unicast-routing\n" + ospf + "interface GigabitEthernet0/0\n ipv6 address "
                + gatewaySugerido(base.rede(), prefixo) + "/" + prefixo + "\n ipv6 ospf 1 area 0\n no shutdown";
    }

    /**
     * PROPÓSITO DE NEGÓCIO: deriva o Interface ID EUI-64 e o endereço SLAAC a partir de um prefixo
     * /64 e um MAC — mostra, passo a passo, como um host "ganha" o endereço IPv6 sozinho.
     *
     * INVARIANTES DO DOMÍNIO: EUI-64 = OUI(3 bytes) + FFFE + NIC(3 bytes), com o 7º bit do 1º byte
     * (U/L) invertido (XOR 0x02). O prefixo é reduzido ao seu /64 (a fronteira do SLAAC), e o resultado traz um
     * aviso para a tela quando o prefixo informado não era /64.
     *
     * COMPORTAMENTO EM CASO DE FALHA: MAC ou prefixo inválidos lançam {@link Ipv6Exception}.
     */
    public Eui64Result eui64(String prefixo, String mac) {
        String p = prefixo == null ? "" : prefixo.strip();
        if (p.isEmpty()) {
            throw new Ipv6Exception("Informe o prefixo /64 (ex.: 2001:db8:0:1::/64).");
        }
        IPAddressString ps = new IPAddressString(p);
        if (!ps.isValid() || !ps.isIPv6()) {
            throw new Ipv6Exception("Prefixo IPv6 inválido (" + p + ").");
        }
        byte[] rede = ps.getAddress().toIPv6().toPrefixBlock().getLower().getBytes();
        byte[] m = parseMac(mac);

        byte[] full = new byte[16];
        System.arraycopy(rede, 0, full, 0, 8);
        full[8] = (byte) (m[0] ^ 0x02);
        full[9] = m[1];
        full[10] = m[2];
        full[11] = (byte) 0xFF;
        full[12] = (byte) 0xFE;
        full[13] = m[3];
        full[14] = m[4];
        full[15] = m[5];

        IPv6Address addr = new IPv6Address(full);
        String macNorm = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                m[0] & 0xFF, m[1] & 0xFF, m[2] & 0xFF, m[3] & 0xFF, m[4] & 0xFF, m[5] & 0xFF);
        String iid = String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x",
                full[8] & 0xFF, full[9] & 0xFF, full[10] & 0xFF, full[11] & 0xFF,
                full[12] & 0xFF, full[13] & 0xFF, full[14] & 0xFF, full[15] & 0xFF);
        String passos = "1) MAC " + macNorm
                + " · 2) inverte o bit U/L do 1º octeto (" + String.format("%02x", m[0] & 0xFF)
                + " XOR 02 = " + String.format("%02x", full[8] & 0xFF)
                + ") · 3) insere FF:FE no meio · 4) Interface ID = " + iid;

        // Grade dos 64 bits do Interface ID, destacando o bit U/L invertido (pos 6) e o FF:FE (pos 24–39).
        List<Ipv6AnaliseRica.HextetoGrade> iidGrade = new ArrayList<>(4);
        for (int h = 0; h < 4; h++) {
            int hextet = ((full[8 + h * 2] & 0xFF) << 8) | (full[8 + h * 2 + 1] & 0xFF);
            List<Ipv6AnaliseRica.BitCelula> cel = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int pos = h * 16 + j;
                char val = ((hextet >> (15 - j)) & 1) == 1 ? '1' : '0';
                String marca = pos == 6 ? "flip" : (pos >= 24 && pos < 40 ? "fffe" : "iid");
                cel.add(new Ipv6AnaliseRica.BitCelula(pos + 1, 63 - pos, val, 1L << (15 - j),
                        marca + (val == '1' ? " on" : " off")));
            }
            iidGrade.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, String.format("%04x", hextet), cel));
        }

        // O prefixo exibido é o /64 do SLAAC: só os 64 bits altos (os mesmos copiados para o endereço).
        byte[] prefixo64 = new byte[16];
        System.arraycopy(rede, 0, prefixo64, 0, 8);
        // Prefixo diferente de /64 é reduzido ao /64 do SLAAC (decisão do F45), mas agora a tela DIZ isso: em
        // silêncio, quem digitou /48 ou /96 não aprendia que o SLAAC exige /64 (RFC 4862 §5.5.3 d; BACK-07).
        Integer informado = ps.getNetworkPrefixLength();
        String rede64 = new IPv6Address(prefixo64).toCanonicalString() + "/64";
        String aviso = informado != null && informado == 64 ? ""
                : informado == null
                ? "Sem prefixo informado: o cálculo usou os primeiros 64 bits do endereço, " + rede64
                        + " — o SLAAC sempre forma o endereço num /64 (RFC 4862 §5.5.3)."
                : "O SLAAC só forma endereço num /64 (RFC 4862 §5.5.3): o /" + informado + " informado virou " + rede64
                        + ", os primeiros 64 bits do que foi digitado. Para outra sub-rede, informe o /64 dela.";
        return new Eui64Result(macNorm, iid, addr.toCanonicalString(),
                new IPv6Address(prefixo64).toCanonicalString() + "/64", passos, iidGrade, aviso);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: gera um prefixo ULA (fd00::/8) com Global ID pseudo-aleatório de 40 bits
     * (RFC 4193) — o "IP privado do IPv6", para uso interno sem coordenação com um RIR.
     *
     * INVARIANTES DO DOMÍNIO: prefixo sempre começa com fd (L=1, local); Global ID de 40 bits;
     * Subnet ID de 16 bits (0–65535). O resultado é um /48 (site) e um /64 (LAN).
     *
     * COMPORTAMENTO EM CASO DE FALHA: Subnet ID fora de 0–65535 lança {@link Ipv6Exception}.
     */
    public UlaResult gerarUla(String subnetId) {
        int sid = parseSubnetId(subnetId);
        byte[] gid = new byte[5];
        new java.security.SecureRandom().nextBytes(gid);

        byte[] p48 = new byte[16];
        p48[0] = (byte) 0xfd;
        System.arraycopy(gid, 0, p48, 1, 5);
        byte[] p64 = p48.clone();
        p64[6] = (byte) ((sid >> 8) & 0xFF);
        p64[7] = (byte) (sid & 0xFF);

        StringBuilder gidHex = new StringBuilder();
        for (byte b : gid) {
            gidHex.append(String.format("%02x", b & 0xFF));
        }
        String explic = "fd (prefixo ULA, L=1) + Global ID de 40 bits pseudo-aleatório (" + gidHex
                + ") + Subnet ID de 16 bits (" + sid + "). Gerado por SecureRandom, conforme RFC 4193.";
        return new UlaResult(new IPv6Address(p48).toCanonicalString() + "/48",
                new IPv6Address(p64).toCanonicalString() + "/64", gidHex.toString(), explic);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: compara dois endereços/prefixos IPv6 lado a lado — quantos bits
     * compartilham, se caem na mesma /64 (mesma LAN), se um prefixo contém o outro endereço e a
     * distância numérica entre eles. É o análogo IPv6 do Comparador da Análise IPv4.
     *
     * INVARIANTES DO DOMÍNIO: a comparação é sobre os 128 bits reais (sem broadcast, sem "hosts
     * úteis"); "mesma LAN" é definida pela fronteira do SLAAC (/64), não por classe. A contenção usa
     * o prefixo declarado de cada lado; sem prefixo, o endereço vale como /128.
     *
     * COMPORTAMENTO EM CASO DE FALHA: qualquer lado malformado propaga {@link Ipv6Exception} (via
     * {@link #analisar}); nunca devolve comparação parcial.
     */
    public ComparacaoIpv6 comparar(String a, String b) {
        AnaliseIpv6 ra = analisar(a);
        AnaliseIpv6 rb = analisar(b);
        IPv6Address ha = hostDe(a);
        IPv6Address hb = hostDe(b);
        byte[] ba = ha.getBytes();
        byte[] bb = hb.getBytes();
        BigInteger va = new BigInteger(1, ba);
        BigInteger vb = new BigInteger(1, bb);

        boolean mesmoEndereco = va.equals(vb);
        int bitsComuns = bitsComuns(ba, bb);
        boolean mesmaLan = bitsComuns >= 64;
        BigInteger distancia = va.subtract(vb).abs();

        String explic = mesmoEndereco
                ? "São o mesmo endereço (distância zero). Prefixo comum: /128."
                : "Compartilham os primeiros " + bitsComuns + " bits (prefixo comum /" + bitsComuns
                        + "). " + (mesmaLan ? "Estão na mesma /64 — mesma LAN." : "Estão em /64 diferentes.")
                        + " Distância: " + distancia + " endereço(s) entre A e B.";

        // Grade dos 128 bits de A, marcando o prefixo comum (verde) x a partir da divergência (âmbar).
        List<Ipv6AnaliseRica.HextetoGrade> gradeComum = new ArrayList<>(8);
        for (int h = 0; h < 8; h++) {
            int hextet = ((ba[h * 2] & 0xFF) << 8) | (ba[h * 2 + 1] & 0xFF);
            List<Ipv6AnaliseRica.BitCelula> cel = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int pos = h * 16 + j;
                char val = ((hextet >> (15 - j)) & 1) == 1 ? '1' : '0';
                String marca = pos < bitsComuns ? "comum" : "diverge";
                cel.add(new Ipv6AnaliseRica.BitCelula(pos + 1, 127 - pos, val, 1L << (15 - j),
                        marca + (val == '1' ? " on" : " off")));
            }
            gradeComum.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, hexDoBin(
                    "0".repeat(16 - Integer.toBinaryString(hextet).length()) + Integer.toBinaryString(hextet)), cel));
        }

        return new ComparacaoIpv6(
                ra.entrada(), ra.comprimido(), ra.prefixo(), ra.temPrefixo(), ra.tipo(),
                rb.entrada(), rb.comprimido(), rb.prefixo(), rb.temPrefixo(), rb.tipo(),
                mesmoEndereco, mesmaLan, bitsComuns, "/" + bitsComuns,
                contencao(ra, rb, ha, hb), distancia.toString(), explic, gradeComum);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: planejador de delegação de prefixo — o análogo IPv6 do VLSM/Resolução do
     * IPv4. Dado um bloco base (ex.: /48) e um prefixo alvo (ex.: /64), aloca uma sub-rede contígua
     * para cada nome informado (site/departamento/VLAN), com faixa e gateway sugerido.
     *
     * INVARIANTES DO DOMÍNIO: em IPv6 não se dimensiona por "número de hosts" (a LAN é /64 com 2^64
     * endereços); dimensiona-se por QUANTIDADE de sub-redes. O alvo é sempre mais específico que a
     * base; a quantidade de nomes não pode exceder a capacidade 2^(alvo−base).
     *
     * COMPORTAMENTO EM CASO DE FALHA: base sem prefixo, alvo fora de faixa, nenhum nome ou nomes além
     * da capacidade lançam {@link Ipv6Exception}.
     */
    public DelegacaoPlano planejarDelegacao(String baseCidr, int prefixoAlvo, List<String> nomes, int maxLinhas) {
        String bruto = baseCidr == null ? "" : baseCidr.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe o bloco base em CIDR (ex.: 2001:db8::/48).");
        }
        if (prefixoAlvo < 0 || prefixoAlvo > 128) {
            throw new Ipv6Exception("Prefixo alvo inválido: use um valor entre /0 e /128.");
        }
        IPAddressString parser = new IPAddressString(bruto);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("Bloco base IPv6 inválido (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        Integer prefixoBaseInformado = addr.getNetworkPrefixLength();
        if (prefixoBaseInformado == null) {
            throw new Ipv6Exception("Informe o prefixo do bloco base (ex.: 2001:db8::/48), não só o endereço.");
        }
        int prefixoBase = prefixoBaseInformado;
        if (prefixoAlvo <= prefixoBase) {
            throw new Ipv6Exception("O prefixo alvo /" + prefixoAlvo + " precisa ser mais específico que a base /"
                    + prefixoBase + " (ex.: base /48 → alvo /64).");
        }
        List<String> limpos = new ArrayList<>();
        if (nomes != null) {
            for (String n : nomes) {
                if (n != null && !n.strip().isEmpty()) {
                    limpos.add(n.strip());
                }
            }
        }
        if (limpos.isEmpty()) {
            throw new Ipv6Exception("Informe ao menos um nome de sub-rede (uma por linha).");
        }
        BigInteger capacidade = BigInteger.TWO.pow(prefixoAlvo - prefixoBase);
        if (BigInteger.valueOf(limpos.size()).compareTo(capacidade) > 0) {
            throw new Ipv6Exception("Você pediu " + limpos.size() + " sub-redes, mas um /" + prefixoBase
                    + " dividido em /" + prefixoAlvo + " comporta só " + capacidade + ".");
        }

        IPv6Address baseBloco = addr.toPrefixBlock();
        BigInteger inicio = new BigInteger(1, baseBloco.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixoAlvo);

        int limite = Math.min(limpos.size(), Math.max(1, maxLinhas));
        List<AlocacaoLan> alocacoes = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            BigInteger valor = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            IPv6Address rede = new IPv6Address(paraBytes16(valor));
            String gw = prefixoAlvo >= 128 ? SEM_GATEWAY_128
                    : new IPv6Address(paraBytes16(valor.add(BigInteger.ONE))).toCanonicalString();
            alocacoes.add(new AlocacaoLan(i + 1, limpos.get(i), rede.toCanonicalString(),
                    "/" + prefixoAlvo, rede.toCanonicalString(), ultimoDoBloco(valor, passo), gw));
        }
        boolean truncado = limpos.size() > limite;
        String enunciado = "Um /" + prefixoBase + " comporta " + capacidade + " sub-redes /" + prefixoAlvo
                + "; este plano usa " + limpos.size() + ". Em IPv6 cada /64 já tem 2^64 endereços via SLAAC,"
                + " então dimensiona-se por quantidade de redes, não por hosts.";
        return new DelegacaoPlano(baseBloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixoBase,
                prefixoBase, prefixoAlvo, capacidade.toString(), limpos.size(), truncado, alocacoes, enunciado);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: sumariza N prefixos/endereços IPv6 — o análogo IPv6 do "Sumarizar e
     * comparar" do IPv4. Calcula o supernet (menor prefixo que cobre todos), a lista mínima de
     * blocos mesclados e, quando são exatamente dois, a relação de contenção.
     *
     * INVARIANTES DO DOMÍNIO: a sumarização é por fronteira de bit (menor prefixo comum); nunca
     * inventa "máscara". Um /0 cobre tudo. Reusa a biblioteca ipaddress (merge/cover por prefixo).
     *
     * COMPORTAMENTO EM CASO DE FALHA: lista vazia ou entrada malformada lança {@link Ipv6Exception}.
     */
    public SumarizacaoIpv6 sumarizar(List<String> entradas) {
        List<IPv6Address> blocos = new ArrayList<>();
        if (entradas != null) {
            for (String e : entradas) {
                if (e == null || e.strip().isEmpty()) {
                    continue;
                }
                String s = e.strip().replace("\"", "").replace("'", "");
                IPAddressString ps = new IPAddressString(s);
                if (!ps.isValid() || !ps.isIPv6()) {
                    throw new Ipv6Exception("Prefixo/endereço IPv6 inválido na lista: " + s);
                }
                IPv6Address a = ps.getAddress().toIPv6();
                blocos.add(a.isPrefixed() ? a.toPrefixBlock() : a.withoutPrefixLength());
            }
        }
        if (blocos.isEmpty()) {
            throw new Ipv6Exception("Informe ao menos dois prefixos IPv6 para sumarizar (um por linha).");
        }

        IPv6Address supernet = blocos.get(0);
        for (int i = 1; i < blocos.size(); i++) {
            supernet = supernet.coverWithPrefixBlock(blocos.get(i));
        }
        IPv6Address[] merged = blocos.get(0).mergeToPrefixBlocks(blocos.toArray(new IPv6Address[0]));

        List<BlocoSumario> mesclados = new ArrayList<>(merged.length);
        for (IPv6Address b : merged) {
            mesclados.add(blocoSumario(b));
        }

        boolean umContemOutro = false;
        String relacao;
        if (blocos.size() == 2) {
            IPv6Address a = blocos.get(0);
            IPv6Address b = blocos.get(1);
            if (a.contains(b) && b.contains(a)) {
                relacao = "Os dois descrevem o mesmo bloco.";
                umContemOutro = true;
            } else if (a.contains(b)) {
                relacao = "O 1º prefixo contém o 2º.";
                umContemOutro = true;
            } else if (b.contains(a)) {
                relacao = "O 2º prefixo contém o 1º.";
                umContemOutro = true;
            } else {
                relacao = "Nenhum contém o outro; o supernet acima é o menor bloco que cobre ambos.";
            }
        } else {
            relacao = "Supernet = menor prefixo que cobre todos; a lista mesclada é o conjunto mínimo de blocos.";
        }

        Integer pfx = supernet.getNetworkPrefixLength();
        int prefixoSupernet = pfx == null ? 128 : pfx;
        // Espaço coberto = soma dos blocos mesclados (sem contar sobreposição duas vezes); o que o
        // supernet tem a mais é espaço arrastado que não pertence a nenhuma entrada (como no IPv4).
        BigInteger cobertos = BigInteger.ZERO;
        for (IPv6Address b : merged) {
            cobertos = cobertos.add(b.getCount());
        }
        BigInteger extras = supernet.getCount().subtract(cobertos);
        boolean exata = extras.signum() == 0;
        String explic = "Alinhamento de bits: o supernet /" + prefixoSupernet + " é o maior prefixo comum a"
                + " todas as entradas. Diferente do IPv4, não há máscara decimal — só a fronteira de bit."
                + (exata ? " As entradas preenchem o supernet por completo: anunciá-lo equivale a anunciar todas."
                        : " Atenção: o supernet cobre " + extras + " endereço(s) que não pertencem a nenhuma entrada;"
                                + " anunciá-lo atrairia tráfego para redes que você não tem.");
        return new SumarizacaoIpv6(
                supernet.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixoSupernet,
                supernet.getLower().withoutPrefixLength().toCanonicalString(),
                supernet.getUpper().withoutPrefixLength().toCanonicalString(),
                supernet.getCount().toString(),
                blocos.size(), mesclados, explic, umContemOutro, relacao,
                exata, cobertos.toString(), extras.toString());
    }

    /**
     * PROPÓSITO DE NEGÓCIO: converte uma faixa [início, fim] de endereços IPv6 na lista MÍNIMA de
     * blocos CIDR que a cobre exatamente — o análogo IPv6 do "Faixa para CIDR" do IPv4.
     *
     * INVARIANTES DO DOMÍNIO: início ≤ fim (senão faixa invertida); a decomposição em prefixos é
     * exata (nem sobra nem falta endereço). Reusa {@code spanWithPrefixBlocks} da biblioteca.
     *
     * COMPORTAMENTO EM CASO DE FALHA: endereço inválido, entrada com prefixo (deve ser endereço puro)
     * ou faixa invertida lançam {@link Ipv6Exception}.
     */
    public FaixaCidrIpv6 faixaParaCidr(String inicio, String fim, int maxLinhas) {
        IPv6Address ini = enderecoPuro(inicio, "início");
        IPv6Address fimA = enderecoPuro(fim, "fim");
        BigInteger vi = new BigInteger(1, ini.getBytes());
        BigInteger vf = new BigInteger(1, fimA.getBytes());
        if (vi.compareTo(vf) > 0) {
            throw new Ipv6Exception("Faixa invertida: o início (" + ini.toCanonicalString()
                    + ") é maior que o fim (" + fimA.toCanonicalString() + "). Troque a ordem.");
        }
        IPv6Address[] blocos = ini.spanWithPrefixBlocks(fimA);
        int limite = Math.min(blocos.length, Math.max(1, maxLinhas));
        List<BlocoSumario> lista = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            lista.add(blocoSumario(blocos[i]));
        }
        String explic = "A faixa foi decomposta em " + blocos.length + " bloco(s) CIDR alinhado(s) a bit."
                + " Cada bloco é o maior prefixo que cabe a partir do ponto atual sem ultrapassar o fim.";
        return new FaixaCidrIpv6(ini.toCanonicalString(), fimA.toCanonicalString(),
                blocos.length, lista, explic);
    }

    private IPv6Address enderecoPuro(String entrada, String rotulo) {
        String s = entrada == null ? "" : entrada.strip().replace("\"", "").replace("'", "");
        if (s.isEmpty()) {
            throw new Ipv6Exception("Informe o endereço de " + rotulo + " da faixa (ex.: 2001:db8::).");
        }
        int idx = s.indexOf('%');
        if (idx >= 0) {
            s = s.substring(0, idx).strip();
        }
        IPAddressString ps = new IPAddressString(s);
        if (!ps.isValid() || !ps.isIPv6()) {
            throw new Ipv6Exception("Endereço IPv6 de " + rotulo + " inválido (" + s + ").");
        }
        IPv6Address a = ps.getAddress().toIPv6();
        if (a.isPrefixed()) {
            throw new Ipv6Exception("Em " + rotulo + ", informe um endereço puro (sem /prefixo).");
        }
        return a;
    }

    private BlocoSumario blocoSumario(IPv6Address bloco) {
        Integer pfx = bloco.getNetworkPrefixLength();
        int p = pfx == null ? 128 : pfx;
        return new BlocoSumario(
                bloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + p,
                bloco.getLower().withoutPrefixLength().toCanonicalString(),
                bloco.getUpper().withoutPrefixLength().toCanonicalString(),
                bloco.getCount().toString());
    }

    /**
     * PROPÓSITO DE NEGÓCIO: Laboratório de Resolução IPv6 (aba Projetar) — dos requisitos ao plano de
     * endereçamento. Aloca uma LAN /prefixoLan por localidade, enlaces WAN /prefixoWan conforme a
     * topologia, e gera gateways, rotas estáticas e CLI Cisco por roteador (OSPFv3 ativo; EIGRP IPv6
     * como alternativa comentada). O análogo IPv6 do "Projetar" do IPv4 — mas por PREFIXO e QUANTIDADE
     * de sub-redes, não por hosts.
     *
     * INVARIANTES DO DOMÍNIO: LAN comum é /64 (SLAAC); enlace ponto-a-ponto é /127 (RFC 6164) ou o
     * prefixo informado; sem broadcast e sem "hosts úteis". Alvos sempre mais específicos que a base;
     * LANs e WANs não se sobrepõem (alocação sequencial). Topologia (árvore) tem n−1 enlaces; malha
     * tem n(n−1)/2. A rota default de cada roteador de borda aponta para a outra ponta do PRÓPRIO
     * enlace; AS do EIGRP e processo OSPFv3 vão de 1 a 65535.
     *
     * COMPORTAMENTO EM CASO DE FALHA: base sem prefixo, prefixos incoerentes, AS/processo fora de
     * 1–65535, nenhuma localidade ou estouro de capacidade lançam {@link Ipv6Exception}.
     */
    public ProjetoRede projetarRede(String baseCidr, int prefixoLan, int prefixoWan,
            String topologia, List<String> locais, int eigrpAs, int ospfProc) {
        String bruto = baseCidr == null ? "" : baseCidr.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe o bloco base em CIDR (ex.: 2001:db8::/48).");
        }
        if (prefixoLan < 1 || prefixoLan > 128 || prefixoWan < 1 || prefixoWan > 128) {
            throw new Ipv6Exception("Prefixos de LAN e WAN devem estar entre /1 e /128.");
        }
        // O IOS recusa "ipv6 router eigrp 0" e "ipv6 router ospf -1" (auditoria CALC-09): 1 a 65535.
        if (eigrpAs < 1 || eigrpAs > 65535) {
            throw new Ipv6Exception("O AS do EIGRP vai de 1 a 65535 (informado: " + eigrpAs + ").");
        }
        if (ospfProc < 1 || ospfProc > 65535) {
            throw new Ipv6Exception("O processo OSPFv3 vai de 1 a 65535 (informado: " + ospfProc + ").");
        }
        IPAddressString parser = new IPAddressString(bruto);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("Bloco base IPv6 inválido (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        Integer pbase = addr.getNetworkPrefixLength();
        if (pbase == null) {
            throw new Ipv6Exception("Informe o prefixo do bloco base (ex.: 2001:db8::/48), não só o endereço.");
        }
        int prefixoBase = pbase;
        if (prefixoLan <= prefixoBase) {
            throw new Ipv6Exception("O prefixo de LAN /" + prefixoLan + " precisa ser mais específico que a base /"
                    + prefixoBase + " (ex.: base /48 → LAN /64).");
        }
        if (prefixoWan < prefixoLan) {
            throw new Ipv6Exception("O prefixo de WAN /" + prefixoWan + " deve ser igual ou mais específico que a LAN /"
                    + prefixoLan + " (enlace ponto-a-ponto usa /127).");
        }
        if (prefixoWan > 127) {
            throw new Ipv6Exception("WAN /128 tem um único endereço e não comporta os dois roteadores do enlace;"
                    + " use /127 (RFC 6164) ou /64.");
        }
        List<String> nomes = new ArrayList<>();
        if (locais != null) {
            for (String n : locais) {
                if (n != null && !n.strip().isEmpty()) {
                    nomes.add(n.strip());
                }
            }
        }
        if (nomes.isEmpty()) {
            throw new Ipv6Exception("Informe ao menos uma localidade (uma por linha).");
        }
        // Teto de entrada (mesmo do Projetar IPv4): sem ele, ~1000 localidades em malha geram ~500 mil
        // enlaces e o laço do CLI é O(n³) — uma requisição anônima de ~8 KB esgotava a memória da JVM.
        if (nomes.size() > InputLimits.MAX_LOCATION_ROWS) {
            throw new Ipv6Exception("Máximo de " + InputLimits.MAX_LOCATION_ROWS + " localidades por plano; você informou "
                    + nomes.size() + ".");
        }
        String topo = topologia == null ? "estrela" : topologia.strip().toLowerCase();
        int n = nomes.size();
        int totalLinks = switch (topo) {
            case "malha" -> n * (n - 1) / 2;
            default -> Math.max(0, n - 1); // estrela e estrela-estendida são árvores: n-1 enlaces
        };

        // Capacidade: LANs consomem n blocos /prefixoLan; WANs consomem totalLinks blocos /prefixoWan.
        BigInteger capLan = BigInteger.TWO.pow(prefixoLan - prefixoBase);
        if (BigInteger.valueOf(n).compareTo(capLan) > 0) {
            throw new Ipv6Exception("Um /" + prefixoBase + " em /" + prefixoLan + " comporta " + capLan
                    + " LANs; você pediu " + n + ".");
        }

        IPv6Address baseBloco = addr.toPrefixBlock();
        BigInteger inicio = new BigInteger(1, baseBloco.getLower().getBytes());
        BigInteger passoLan = BigInteger.TWO.pow(128 - prefixoLan);
        BigInteger passoWan = BigInteger.TWO.pow(128 - prefixoWan);

        List<ProjetoLan> lans = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigInteger v = inicio.add(passoLan.multiply(BigInteger.valueOf(i)));
            IPv6Address rede = new IPv6Address(paraBytes16(v));
            String gw = new IPv6Address(paraBytes16(v.add(BigInteger.ONE))).toCanonicalString();
            lans.add(new ProjetoLan(i + 1, nomes.get(i), rede.toCanonicalString(), "/" + prefixoLan,
                    gw, new IPv6Address(paraBytes16(v.add(BigInteger.ONE))).toCanonicalString(),
                    ultimoDoBloco(v, passoLan), BigInteger.TWO.pow(128 - prefixoLan).toString()));
        }

        // WANs começam após as LANs, alinhados ao passo da WAN.
        BigInteger apos = inicio.add(passoLan.multiply(BigInteger.valueOf(n)));
        BigInteger resto = apos.mod(passoWan);
        BigInteger wanBase = resto.signum() == 0 ? apos : apos.add(passoWan.subtract(resto));
        // As WANs também precisam caber na base: antes só as LANs eram checadas, e o enlace saía em
        // espaço que o aluno não possui (ou dava a volta em ::).
        BigInteger fimBase = inicio.add(BigInteger.TWO.pow(128 - prefixoBase));
        BigInteger fimWans = wanBase.add(passoWan.multiply(BigInteger.valueOf(totalLinks)));
        if (fimWans.compareTo(fimBase) > 0) {
            throw new Ipv6Exception("A base /" + prefixoBase + " não comporta " + n + " LAN(s) /" + prefixoLan
                    + " e mais " + totalLinks + " enlace(s) /" + prefixoWan + ". Use uma base maior (prefixo menor)"
                    + " ou menos localidades.");
        }

        // Pares de enlace (índices de localidade) conforme a topologia.
        List<int[]> pares = new ArrayList<>();
        if ("malha".equals(topo)) {
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    pares.add(new int[] {a, b});
                }
            }
        } else if ("estrela-estendida".equals(topo) && n >= 3) {
            pares.add(new int[] {0, 1});                 // núcleo — distribuição
            for (int a = 2; a < n; a++) {
                pares.add(new int[] {1, a});             // distribuição — acesso
            }
        } else { // estrela (hub = 1ª localidade)
            for (int a = 1; a < n; a++) {
                pares.add(new int[] {0, a});
            }
        }

        int offset = prefixoWan >= 127 ? 0 : 1; // /127 usa ::0 e ::1 (RFC 6164)
        List<ProjetoWan> wans = new ArrayList<>(pares.size());
        for (int k = 0; k < pares.size(); k++) {
            BigInteger v = wanBase.add(passoWan.multiply(BigInteger.valueOf(k)));
            IPv6Address rede = new IPv6Address(paraBytes16(v));
            String ipA = new IPv6Address(paraBytes16(v.add(BigInteger.valueOf(offset)))).toCanonicalString();
            String ipB = new IPv6Address(paraBytes16(v.add(BigInteger.valueOf(offset + 1L)))).toCanonicalString();
            wans.add(new ProjetoWan(k + 1, nomes.get(pares.get(k)[0]) + " ↔ " + nomes.get(pares.get(k)[1]),
                    rede.toCanonicalString(), "/" + prefixoWan, ipA, ipB,
                    nomes.get(pares.get(k)[0]), nomes.get(pares.get(k)[1])));
        }

        // CLI por roteador (um por localidade).
        List<ProjetoRoteador> roteadores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            StringBuilder cli = new StringBuilder();
            cli.append("hostname R").append(i + 1).append("-").append(nomes.get(i).replaceAll("\\s+", "_")).append('\n');
            cli.append("ipv6 unicast-routing\n");
            cli.append("interface GigabitEthernet0/0\n description LAN ").append(nomes.get(i)).append('\n');
            cli.append(" ipv6 address ").append(lans.get(i).gateway()).append("/").append(prefixoLan).append('\n');
            cli.append(" ipv6 ospf ").append(ospfProc).append(" area 0\n no shutdown\n");
            int serial = 0;
            for (int k = 0; k < pares.size(); k++) {
                int a = pares.get(k)[0];
                int b = pares.get(k)[1];
                if (a == i || b == i) {
                    String meu = a == i ? wans.get(k).ipA() : wans.get(k).ipB();
                    cli.append("interface Serial0/0/").append(serial++).append('\n');
                    cli.append(" description WAN ").append(wans.get(k).nome()).append('\n');
                    cli.append(" ipv6 address ").append(meu).append("/").append(prefixoWan).append('\n');
                    cli.append(" ipv6 ospf ").append(ospfProc).append(" area 0\n no shutdown\n");
                }
            }
            cli.append("ipv6 router ospf ").append(ospfProc).append('\n');
            cli.append(" router-id ").append(i + 1).append(".").append(i + 1).append(".").append(i + 1).append(".").append(i + 1).append('\n');
            // EIGRP é ALTERNATIVA ao OSPFv3, em comentário: ativo junto, sem "ipv6 eigrp" nas interfaces, não
            // anunciava nada; ativo nas interfaces, ganharia do OSPF pela distância administrativa (CALC-09).
            String rid = (i + 1) + "." + (i + 1) + "." + (i + 1) + "." + (i + 1);
            cli.append("! Alternativa ao OSPFv3 (use um OU outro): EIGRP IPv6\n");
            cli.append("! ipv6 router eigrp ").append(eigrpAs).append("\n!  eigrp router-id ").append(rid)
                    .append("\n!  no shutdown\n! e em cada interface acima: ipv6 eigrp ").append(eigrpAs);
            roteadores.add(new ProjetoRoteador("R" + (i + 1), nomes.get(i), cli.toString()));
        }

        List<String> rotas = new ArrayList<>();
        rotas.add("! Rotas estáticas de exemplo (alternativa ao OSPFv3/EIGRP):");
        if ("estrela".equals(topo) || "estrela-estendida".equals(topo)) {
            // Cada roteador de baixo aponta a default para a ponta de CIMA do SEU enlace — antes todos
            // apontavam para o IP do hub no primeiro enlace, que nenhum outro spoke alcança (CALC-09).
            rotas.add("! Em cada roteador de borda, rota default para a outra ponta do próprio enlace:");
            for (int k = 0; k < pares.size(); k++) {
                int abaixo = pares.get(k)[1];
                rotas.add("! R" + (abaixo + 1) + " (" + nomes.get(abaixo) + ")");
                rotas.add("ipv6 route ::/0 " + wans.get(k).ipA());
            }
            rotas.add("! No hub, uma rota para cada LAN remota via o enlace correspondente.");
        } else {
            rotas.add("! Em malha, prefira protocolo dinâmico (OSPFv3/EIGRP) — rotas estáticas não escalam.");
        }

        String ospfv3 = "ipv6 unicast-routing\nipv6 router ospf " + ospfProc
                + "\n! habilite 'ipv6 ospf " + ospfProc + " area 0' em cada interface";
        String eigrp = "ipv6 unicast-routing\nipv6 router eigrp " + eigrpAs + "\n eigrp router-id <x.x.x.x>"
                + "\n no shutdown\n! habilite 'ipv6 eigrp " + eigrpAs + "' em cada interface";

        List<String> passos = List.of(
                "1) Base /" + prefixoBase + " → " + n + " LAN(s) /" + prefixoLan + " contíguas (uma por localidade).",
                "2) Gateway de cada LAN = ::1 do bloco (convenção).",
                "3) Topologia " + topo + " → " + totalLinks + " enlace(s) WAN /" + prefixoWan + " (RFC 6164 para /127).",
                "4) OSPFv3 (processo " + ospfProc + ") ou EIGRP IPv6 (AS " + eigrpAs + ") habilitado por interface.",
                "5) Sem broadcast e sem 'hosts úteis': cada /64 tem 2^64 endereços via SLAAC.");

        String enunciado = "Plano IPv6 para " + n + " localidade(s) em " + topo + ": " + n + " LAN(s) /"
                + prefixoLan + " e " + totalLinks + " enlace(s) /" + prefixoWan + ", dentro de " + bruto + ".";

        // Diagrama de arquitetura (Mermaid): um roteador por localidade + enlaces WAN da topologia.
        StringBuilder mer = new StringBuilder("graph TD\n");
        for (int i = 0; i < n; i++) {
            mer.append("  R").append(i + 1).append("[\"").append(mermaidTexto(nomes.get(i)))
                    .append("<br/>").append(lans.get(i).rede()).append("/").append(prefixoLan).append("\"]\n");
        }
        for (int k = 0; k < pares.size(); k++) {
            mer.append("  R").append(pares.get(k)[0] + 1).append(" ---|\"").append(wans.get(k).rede())
                    .append("/").append(prefixoWan).append("\"| R").append(pares.get(k)[1] + 1).append("\n");
        }
        String mermaid = mer.toString();

        // Relatório do plano em texto puro (para baixar .txt / copiar), como o Lab do IPv4.
        StringBuilder txt = new StringBuilder();
        txt.append("PLANO DE REDE IPv6 — ").append(bruto).append(" · topologia ").append(topo).append('\n');
        txt.append(enunciado).append("\n\n== LANs (/").append(prefixoLan).append(") ==\n");
        for (ProjetoLan l : lans) {
            txt.append(String.format("%-16s %s%s  gw %s%n", l.nome(), l.rede(), l.prefixoStr(), l.gateway()));
        }
        if (!wans.isEmpty()) {
            txt.append("\n== Enlaces WAN (/").append(prefixoWan).append(") ==\n");
            for (ProjetoWan w : wans) {
                txt.append(String.format("%-24s %s%s  A=%s  B=%s%n", w.nome(), w.rede(), w.prefixoStr(), w.ipA(), w.ipB()));
            }
        }
        txt.append("\n== Rotas estáticas ==\n");
        for (String r : rotas) {
            txt.append(r).append('\n');
        }
        txt.append("\n== CLI Cisco por roteador (OSPFv3 + EIGRP IPv6) ==\n");
        for (ProjetoRoteador rt : roteadores) {
            txt.append("\n! ").append(rt.nome()).append(" · ").append(rt.local()).append('\n').append(rt.cli()).append('\n');
        }
        txt.append("\n== Passo a passo ==\n");
        for (String p : passos) {
            txt.append(p).append('\n');
        }
        String planoTexto = txt.toString();

        return new ProjetoRede(baseBloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixoBase,
                prefixoBase, prefixoLan, prefixoWan, topo, n, totalLinks, capLan.toString(),
                lans, wans, roteadores, rotas, ospfv3, eigrp, passos, enunciado, mermaid, planoTexto);
    }

    /** Sanitiza um rótulo para o Mermaid (aspas, HTML e caracteres que quebram o diagrama). */
    private static String mermaidTexto(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("|", "/").replace("[", "(").replace("]", ")")
                .replace("<", "(").replace(">", ")");
    }

    /**
     * PROPÓSITO DE NEGÓCIO: Engenharia Reversa IPv6 — lê uma configuração Cisco IPv6 colada e
     * reconstrói interfaces, endereços, rotas e o processo de roteamento, apontando achados. O
     * análogo IPv6 da aba "Engenharia reversa" do IPv4.
     *
     * INVARIANTES DO DOMÍNIO: parsing puramente textual, sem executar nada; classifica cada endereço
     * pelo tipo IANA (reusa {@link #analisar}); nunca inventa dado ausente.
     *
     * COMPORTAMENTO EM CASO DE FALHA: configuração vazia lança {@link Ipv6Exception}; mais de
     * {@link #MAX_INTERFACES_ENGENHARIA} interfaces também (o campo aceita 256 KB, o que dá milhares
     * de interfaces e uma resposta de megabytes); linha malformada vira achado, não exceção.
     */
    public EngenhariaReversaIpv6 engenhariaReversa(String config) {
        String txt = config == null ? "" : config.strip();
        if (txt.isEmpty()) {
            throw new Ipv6Exception("Cole uma configuração Cisco IPv6 para analisar.");
        }
        long nInterfaces = txt.lines().filter(l -> l.strip().toLowerCase().startsWith("interface ")).count();
        if (nInterfaces > MAX_INTERFACES_ENGENHARIA) {
            throw new Ipv6Exception("A engenharia reversa IPv6 lê até " + MAX_INTERFACES_ENGENHARIA
                    + " interfaces por vez; o texto colado tem " + nInterfaces + ". Divida em partes.");
        }
        String hostname = "";
        boolean unicastRouting = false;
        List<InterfaceLida> interfaces = new ArrayList<>();
        List<RotaLida> rotas = new ArrayList<>();
        List<String> protocolos = new ArrayList<>();
        List<String> achados = new ArrayList<>();

        String ifAtual = null;
        String descAtual = null;
        List<String> endsAtual = new ArrayList<>();
        // Porta de switch (switchport) é L2: não leva IPv6 e não conta como "interface sem endereço"
        // (auditoria CALC-15: o trunk de um switch L3 era acusado).
        java.util.Set<String> interfacesL2 = new java.util.HashSet<>();
        for (String linhaBruta : txt.split("\\r?\\n")) {
            String linha = linhaBruta.strip();
            String low = linha.toLowerCase();
            if (low.startsWith("hostname ")) {
                hostname = linha.substring(9).strip();
            } else if (low.equals("ipv6 unicast-routing")) {
                unicastRouting = true;
            } else if (low.startsWith("interface ")) {
                if (ifAtual != null) {
                    interfaces.add(new InterfaceLida(ifAtual, descAtual == null ? "" : descAtual, List.copyOf(endsAtual)));
                }
                ifAtual = linha.substring(10).strip();
                descAtual = null;
                endsAtual = new ArrayList<>();
            } else if (low.startsWith("switchport") && ifAtual != null) {
                interfacesL2.add(ifAtual);
            } else if (low.startsWith("description ") && ifAtual != null) {
                descAtual = linha.substring(12).strip();
            } else if (low.startsWith("ipv6 address ") && ifAtual != null) {
                String v = linha.substring(13).strip();
                if (!v.toLowerCase().contains("autoconfig") && !v.toLowerCase().startsWith("dhcp")) {
                    endsAtual.add(v);
                }
            } else if (low.startsWith("ipv6 route ")) {
                String[] p = linha.substring(11).strip().split("\\s+");
                if (p.length >= 2) {
                    rotas.add(new RotaLida(p[0], p[p.length - 1]));
                } else {
                    achados.add("Rota IPv6 malformada: '" + linha + "'");
                }
            } else if (low.startsWith("ipv6 router ospf")) {
                protocolos.add("OSPFv3 (" + linha.replaceFirst("(?i)ipv6 router ", "") + ")");
            } else if (low.startsWith("ipv6 router eigrp")) {
                protocolos.add("EIGRP IPv6 (" + linha.replaceFirst("(?i)ipv6 router ", "") + ")");
            }
        }
        if (ifAtual != null) {
            interfaces.add(new InterfaceLida(ifAtual, descAtual == null ? "" : descAtual, List.copyOf(endsAtual)));
        }

        // Enriquecer cada endereço com tipo e prefixo; coletar prefixos de rede.
        List<EnderecoLido> enderecos = new ArrayList<>();
        for (InterfaceLida it : interfaces) {
            for (String e : it.enderecos()) {
                // "ipv6 address fe80::1 link-local", "... 2001:db8::/64 eui-64" e "... anycast" são sintaxe
                // válida do IOS; o modificador saía junto do endereço e tudo virava "inválido" (CALC-15).
                String[] partes = e.split("\\s+");
                String so = partes[0];
                String modificador = partes.length > 1 ? partes[1].toLowerCase() : "";
                String tipo;
                String prefixo = so.contains("/") ? so.substring(so.indexOf('/'))
                        : "link-local".equals(modificador) ? "/64" : "/128";
                try {
                    tipo = analisar(so).tipo();
                    if ("eui-64".equals(modificador)) {
                        tipo = tipo + " (host EUI-64)";
                    }
                } catch (Ipv6Exception ex) {
                    tipo = "inválido";
                    achados.add("Endereço IPv6 inválido em " + it.nome() + ": '" + e + "'");
                }
                enderecos.add(new EnderecoLido(it.nome(), e, prefixo, tipo));
            }
        }

        // Achados de configuração.
        long interfacesL3 = interfaces.stream().filter(i -> !interfacesL2.contains(i.nome())).count();
        if (!unicastRouting && (!protocolos.isEmpty() || interfacesL3 > 1)) {
            achados.add("Falta 'ipv6 unicast-routing': sem isso o roteador não encaminha IPv6 entre interfaces.");
        }
        long semEndereco = interfaces.stream()
                .filter(i -> i.enderecos().isEmpty() && !interfacesL2.contains(i.nome())).count();
        if (semEndereco > 0) {
            achados.add(semEndereco + " interface(s) sem endereço IPv6 configurado.");
        }
        if (interfaces.isEmpty()) {
            achados.add("Nenhuma interface encontrada na configuração.");
        }
        if (protocolos.isEmpty() && rotas.isEmpty() && interfacesL3 > 1) {
            // As redes conectadas ao próprio roteador se alcançam (com unicast-routing); só as de outros
            // roteadores ficam sem caminho (CALC-15).
            achados.add("Sem protocolo de roteamento nem rota estática: as redes ligadas a este roteador se "
                    + "alcançam, mas as de outros roteadores ficam inalcançáveis.");
        }
        // Detecta enlaces /64 entre roteadores (recomendação /127).
        for (EnderecoLido e : enderecos) {
            if (e.prefixo().equals("/64") && e.interfaceNome().toLowerCase().startsWith("serial")) {
                achados.add("Enlace ponto-a-ponto " + e.interfaceNome() + " em /64 — considere /127 (RFC 6164).");
            }
        }
        if (achados.isEmpty()) {
            achados.add("Nenhum problema estrutural evidente encontrado.");
        }

        return new EngenhariaReversaIpv6(hostname, unicastRouting, interfaces, enderecos, rotas, protocolos, achados);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: decomposição por nibble (hexadecimal) e expansão/compressão — a aba
     * didática que mostra como um IPv6 se escreve por extenso (RFC 4291) e comprimido (RFC 5952),
     * e como cada um dos 32 nibbles vira um dígito hex de 4 bits (base do reverso ip6.arpa).
     *
     * INVARIANTES DO DOMÍNIO: reusa {@link #analisar} (fonte única); 32 nibbles = 128 bits; a forma
     * comprimida segue a canônica da biblioteca (RFC 5952).
     *
     * COMPORTAMENTO EM CASO DE FALHA: propaga {@link Ipv6Exception} de {@code analisar}.
     */
    public NibblesIpv6 nibbles(String entrada) {
        AnaliseIpv6 base = analisar(entrada);
        String hex = base.expandido().replace(":", "");
        List<NibbleInfo> lista = new ArrayList<>(32);
        for (int i = 0; i < hex.length(); i++) {
            int val = Integer.parseInt(String.valueOf(hex.charAt(i)), 16);
            String bin = "0".repeat(4 - Integer.toBinaryString(val).length()) + Integer.toBinaryString(val);
            lista.add(new NibbleInfo(i + 1, hex.charAt(i), bin, (i / 4) + 1));
        }
        String explic = "Expandido tem 8 hextetos × 4 dígitos = 32 nibbles (128 bits). A compressão RFC 5952"
                + " remove zeros à esquerda de cada hexteto e substitui a MAIOR sequência de DOIS ou mais"
                + " hextetos zero por '::' (uma única vez; no empate, a mais à esquerda). Um hexteto zero"
                + " isolado fica como '0'. O reverso ip6.arpa é a lista dos 32 nibbles, do último ao primeiro.";
        return new NibblesIpv6(base.comprimido(), base.expandido(), base.reversePtr(), lista, explic);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: plano de VLANs IPv6 (aba VLANs da Calculadora) — o análogo IPv6 do plano
     * de VLANs do IPv4. Cada VLAN recebe uma LAN /prefixoLan (padrão /64, SLAAC), gateway (::1) e a
     * configuração Cisco de SVI; gera também o trunk 802.1Q com as VLANs permitidas.
     *
     * INVARIANTES DO DOMÍNIO: VLAN ID entre 1 e 4094; LAN comum é /64; sem broadcast/hosts úteis. As
     * LANs são alocadas contíguas a partir da base; não excedem a capacidade 2^(prefixoLan−base). O
     * script liga "ipv6 unicast-routing" uma vez e, com DHCPv6, cria o pool que cada
     * "ipv6 dhcp server" referencia.
     *
     * COMPORTAMENTO EM CASO DE FALHA: base sem prefixo, prefixo incoerente, VLAN ID fora de faixa,
     * lista vazia ou estouro de capacidade lançam {@link Ipv6Exception}.
     */
    public VlanPlano planejarVlans(String baseCidr, int prefixoLan, List<Integer> ids,
            List<String> nomes, boolean dhcpv6, int maxLinhas) {
        String bruto = baseCidr == null ? "" : baseCidr.strip().replace("\"", "").replace("'", "");
        if (bruto.isEmpty()) {
            throw new Ipv6Exception("Informe o bloco base em CIDR (ex.: 2001:db8::/48).");
        }
        if (prefixoLan < 1 || prefixoLan > 128) {
            throw new Ipv6Exception("Prefixo de LAN inválido: use /1 a /128 (/64 é o padrão).");
        }
        IPAddressString parser = new IPAddressString(bruto);
        if (!parser.isValid() || !parser.isIPv6()) {
            throw new Ipv6Exception("Bloco base IPv6 inválido (" + bruto + ").");
        }
        IPv6Address addr = parser.getAddress().toIPv6();
        Integer pbase = addr.getNetworkPrefixLength();
        if (pbase == null) {
            throw new Ipv6Exception("Informe o prefixo do bloco base (ex.: 2001:db8::/48).");
        }
        int prefixoBase = pbase;
        if (prefixoLan <= prefixoBase) {
            throw new Ipv6Exception("O prefixo de LAN /" + prefixoLan + " precisa ser mais específico que a base /"
                    + prefixoBase + ".");
        }
        // Pareia id+nome, ignorando linhas vazias.
        List<Integer> idLimpos = new ArrayList<>();
        List<String> nomeLimpos = new ArrayList<>();
        int qtd = Math.max(ids == null ? 0 : ids.size(), nomes == null ? 0 : nomes.size());
        for (int i = 0; i < qtd; i++) {
            Integer id = (ids != null && i < ids.size()) ? ids.get(i) : null;
            String nome = (nomes != null && i < nomes.size()) ? nomes.get(i) : null;
            boolean vazio = id == null && (nome == null || nome.strip().isEmpty());
            if (vazio) {
                continue;
            }
            if (id == null || id < 1 || id > 4094) {
                throw new Ipv6Exception("VLAN ID inválido na linha " + (i + 1) + ": use um número de 1 a 4094.");
            }
            if (id >= 1002 && id <= 1005) {
                throw new Ipv6Exception("VLAN " + id + " (linha " + (i + 1) + ") é reservada pela Cisco para FDDI/Token Ring"
                        + " (1002–1005): o switch não deixa criá-la nem renomeá-la.");
            }
            if (idLimpos.contains(id)) {
                throw new Ipv6Exception("VLAN " + id + " repetida na linha " + (i + 1) + ": cada VLAN tem um único SVI"
                        + " e uma única LAN no plano.");
            }
            idLimpos.add(id);
            nomeLimpos.add(nome == null || nome.strip().isEmpty() ? ("VLAN" + id) : nome.strip());
        }
        if (idLimpos.isEmpty()) {
            throw new Ipv6Exception("Informe ao menos uma VLAN (VLAN ID + nome).");
        }
        int n = idLimpos.size();
        BigInteger cap = BigInteger.TWO.pow(prefixoLan - prefixoBase);
        if (BigInteger.valueOf(n).compareTo(cap) > 0) {
            throw new Ipv6Exception("Um /" + prefixoBase + " em /" + prefixoLan + " comporta " + cap
                    + " LANs; você pediu " + n + " VLAN(s).");
        }

        IPv6Address baseBloco = addr.toPrefixBlock();
        BigInteger inicio = new BigInteger(1, baseBloco.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixoLan);
        int limite = Math.min(n, Math.max(1, maxLinhas));
        List<VlanLinha> linhas = new ArrayList<>(limite);
        StringBuilder ids82 = new StringBuilder();
        for (int i = 0; i < limite; i++) {
            BigInteger v = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            IPv6Address rede = new IPv6Address(paraBytes16(v));
            String gw = new IPv6Address(paraBytes16(v.add(BigInteger.ONE))).toCanonicalString();
            // VLAN 1 é a default do switch: já existe e não pode ser renomeada — só o SVI é configurado.
            String declaracao = idLimpos.get(i) == 1
                    ? "! VLAN 1 é a default do switch (já existe; o IOS não permite renomeá-la)\n"
                    : "vlan " + idLimpos.get(i) + "\n name " + nomeLimpos.get(i) + "\n";
            // SLAAC puro: o RA anuncia o prefixo com M=0 e O=0 (RFC 4861 §4.2) — nenhum flag extra.
            // other-config-flag (O=1) seria DHCPv6 stateless, não SLAAC puro.
            // Sem "ipv6 unicast-routing" o switch L3 não roteia entre as SVIs; "ipv6 dhcp server VLANn"
            // apontava para um pool que nunca era criado (auditoria CALC-08). O pool nasce aqui, com o
            // prefixo da VLAN; o roteamento global vai uma vez, antes da primeira VLAN.
            String roteamento = i == 0
                    ? "! Uma vez no switch L3: liga o roteamento IPv6 entre as SVIs\nipv6 unicast-routing\n"
                    : "";
            String pool = dhcpv6
                    ? "ipv6 dhcp pool VLAN" + idLimpos.get(i) + "\n address prefix " + rede.toCanonicalString()
                            + "/" + prefixoLan + "\n"
                    : "";
            String cli = roteamento + declaracao + pool
                    + "interface Vlan" + idLimpos.get(i)
                    + "\n ipv6 address " + gw + "/" + prefixoLan
                    + "\n ipv6 enable"
                    + (dhcpv6
                        ? "\n ipv6 nd managed-config-flag\n ipv6 dhcp server VLAN" + idLimpos.get(i)
                        : "")
                    + "\n no shutdown";
            linhas.add(new VlanLinha(idLimpos.get(i), nomeLimpos.get(i), rede.toCanonicalString(),
                    "/" + prefixoLan, gw, cli));
            if (ids82.length() > 0) {
                ids82.append(',');
            }
            ids82.append(idLimpos.get(i));
        }
        String trunk = "interface GigabitEthernet0/1\n switchport trunk encapsulation dot1q\n"
                + " switchport mode trunk\n switchport trunk allowed vlan " + ids82;
        String dhcpNota = dhcpv6
                ? "DHCPv6 stateful: o RA marca M=1 (managed) e o servidor DHCPv6 entrega os endereços."
                : "SLAAC (padrão): o RA anuncia o prefixo /64 e os hosts se autoconfiguram (sem servidor).";
        String enunciado = n + " VLAN(s), cada uma com uma LAN /" + prefixoLan + " contígua em " + bruto
                + ". Em IPv6 a VLAN é L2; o /64 e o SVI (interface VlanN) fazem o roteamento inter-VLAN.";
        return new VlanPlano(baseBloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixoBase,
                prefixoBase, prefixoLan, n, cap.toString(), linhas, trunk, dhcpNota, enunciado);
    }

    /** Host (sem prefixo, sem zone index) já validado por {@link #analisar}. */
    private IPv6Address hostDe(String entrada) {
        String bruto = entrada == null ? "" : entrada.strip().replace("\"", "").replace("'", "");
        int idx = bruto.indexOf('%');
        if (idx >= 0) {
            bruto = bruto.substring(0, idx).strip();
        }
        return new IPAddressString(bruto).getAddress().toIPv6().getLower().withoutPrefixLength();
    }

    /** Quantidade de bits iniciais idênticos entre dois endereços de 128 bits (0–128). */
    private static int bitsComuns(byte[] a, byte[] b) {
        int bits = 0;
        for (int i = 0; i < 16; i++) {
            int x = (a[i] ^ b[i]) & 0xFF;
            if (x == 0) {
                bits += 8;
                continue;
            }
            bits += Integer.numberOfLeadingZeros(x) - 24;
            break;
        }
        return bits;
    }

    private String contencao(AnaliseIpv6 ra, AnaliseIpv6 rb, IPv6Address ha, IPv6Address hb) {
        boolean aContemB = ra.temPrefixo() && prefixoBloco(ha, ra.prefixo()).contains(hb);
        boolean bContemA = rb.temPrefixo() && prefixoBloco(hb, rb.prefixo()).contains(ha);
        if (aContemB && bContemA) {
            return "Os dois prefixos coincidem: descrevem o mesmo bloco.";
        }
        if (aContemB) {
            return "A rede A (/" + ra.prefixo() + ") contém o endereço B.";
        }
        if (bContemA) {
            return "A rede B (/" + rb.prefixo() + ") contém o endereço A.";
        }
        if (!ra.temPrefixo() && !rb.temPrefixo()) {
            return "Nenhum lado tem prefixo declarado: comparados como endereços /128.";
        }
        return "Nenhum dos prefixos declarados contém o outro endereço.";
    }

    /**
     * PROPÓSITO DE NEGÓCIO: monta o modelo de EXIBIÇÃO rica da Análise IPv6 — o gêmeo IPv6 do
     * resultado da Análise Didática IPv4 (grade de 128 bits, aplicação do prefixo, capacidade,
     * passo-a-passo, linha do tempo, delegação, régua de /64, referência, conversão, GRC, CLI,
     * termos, banner e resumo tipo prova). Reusa {@link #analisar} e {@link #decompor} como fonte.
     *
     * INVARIANTES DO DOMÍNIO: sem broadcast e sem "hosts úteis"; toda contagem é 2^(128−prefixo).
     *
     * COMPORTAMENTO EM CASO DE FALHA: propaga {@link Ipv6Exception} de {@code analisar}.
     */
    public Ipv6AnaliseRica.Resultado analisarRica(String entrada, int reguaCount) {
        DecomposicaoIpv6 dec = decompor(entrada);
        AnaliseIpv6 base = dec.base();
        int prefixo = base.prefixo();

        List<Ipv6AnaliseRica.HextetoGrade> grade = new ArrayList<>(8);
        List<String> bins = base.binarioHextetos();
        for (int h = 0; h < 8; h++) {
            String bin = bins.get(h);
            List<Ipv6AnaliseRica.BitCelula> celulas = new ArrayList<>(16);
            for (int j = 0; j < 16; j++) {
                int global = h * 16 + j;
                char val = bin.charAt(j);
                boolean rede = global < prefixo;
                String css = (rede ? "rede" : "iface") + (val == '1' ? " on" : " off");
                celulas.add(new Ipv6AnaliseRica.BitCelula(
                        global + 1, 127 - global, val, 1L << (15 - j), css));
            }
            grade.add(new Ipv6AnaliseRica.HextetoGrade(h + 1, hexDoBin(bin), celulas));
        }

        List<Ipv6AnaliseRica.LinhaPrefixo> aplicacao = List.of(
                new Ipv6AnaliseRica.LinhaPrefixo("Máscara de prefixo",
                        "/" + prefixo + " — " + prefixo + " bits 1 seguidos de " + (128 - prefixo) + " bits 0",
                        "and-row-mask"),
                new Ipv6AnaliseRica.LinhaPrefixo("Rede (endereço & prefixo)", base.rede(), "and-row-result"),
                new Ipv6AnaliseRica.LinhaPrefixo("Bits de interface (endereço & ~prefixo)",
                        parteDeHost(entrada, prefixo), "and-row-ip"),
                new Ipv6AnaliseRica.LinhaPrefixo("Fronteira rede/interface", fronteira(prefixo), "and-row-wild"));

        List<Ipv6AnaliseRica.PassoWizard> wizard = List.of(
                new Ipv6AnaliseRica.PassoWizard("🧭", "Tipo e escopo", "Classificar pela faixa IANA",
                        base.tipo() + " — " + base.descricaoTipo()),
                new Ipv6AnaliseRica.PassoWizard("📏", "Prefixo", "Ler o /" + prefixo + " (fronteira rede/interface)",
                        "/" + prefixo + " · " + prefixo + " bits de rede, " + (128 - prefixo) + " de interface"),
                new Ipv6AnaliseRica.PassoWizard("🧠", "Rede (bloco)", "Zerar os bits de interface", base.rede()),
                new Ipv6AnaliseRica.PassoWizard("📣", "Interface / SLAAC",
                        "Os bits baixos identificam a interface", base.interfaceId()));

        Ipv6AnaliseRica.LinhaTempo linhaTempo = new Ipv6AnaliseRica.LinhaTempo(
                "faixa", base.rede(), base.primeiro(), base.ultimo(), base.entrada());

        List<SubredeIpv6> regua = reguaSubredes(entrada, prefixo, reguaCount);

        List<Ipv6AnaliseRica.ReferenciaPrefixo> referencia = referenciaPrefixos(prefixo);
        List<Ipv6AnaliseRica.ConversaoLinha> conversao = List.of(
                new Ipv6AnaliseRica.ConversaoLinha("1 bit", "1", "—", "—", "—"),
                new Ipv6AnaliseRica.ConversaoLinha("1 nibble", "4", "1", "—", "1 dígito hex"),
                new Ipv6AnaliseRica.ConversaoLinha("1 hexteto", "16", "4", "1", "4 hex (0000–ffff)"),
                new Ipv6AnaliseRica.ConversaoLinha("1 endereço IPv6", "128", "32", "8", "8 hextetos"));

        List<String> grc = List.of(
                "Escopo: " + base.tipo() + " — " + base.descricaoTipo() + ".",
                "Superfície: 2^" + (128 - prefixo) + " endereços no bloco; varredura completa é inviável"
                        + " (vantagem de segurança do IPv6 sobre o IPv4).",
                grcRecomendacao(base.tipo()));

        List<Ipv6AnaliseRica.DicaSeguranca> dicas = segurancaDicas(base.tipo());

        List<Ipv6AnaliseRica.TermoRede> termos = List.of(
                new Ipv6AnaliseRica.TermoRede("SLAAC", "Autoconfiguração do host a partir do prefixo /64 + Interface ID (RA)."),
                new Ipv6AnaliseRica.TermoRede("DHCPv6", "Atribuição stateful/stateless coordenada por servidor (opcional)."),
                new Ipv6AnaliseRica.TermoRede("NDP / RA", "Neighbor Discovery substitui o ARP; Router Advertisement anuncia o prefixo."),
                new Ipv6AnaliseRica.TermoRede("Broadcast", "Não existe em IPv6 — substituído por multicast."),
                new Ipv6AnaliseRica.TermoRede("Solicited-node", base.solicitedNode() + " (multicast usado pelo NDP)."));

        String ospfv3 = dec.ciscoCli();
        // Sem IPv4 no roteador, o EIGRP IPv6 só sobe com router-id explícito.
        String eigrp = "ipv6 unicast-routing\nipv6 router eigrp 100\n eigrp router-id 1.1.1.1\n no shutdown\n"
                + "interface GigabitEthernet0/0\n ipv6 eigrp 100";
        String ciscoNota = "Em IPv6 não há wildcard: o roteamento é habilitado por interface, não por 'network' com máscara curinga.";

        List<Ipv6AnaliseRica.BannerItem> bannerItens = List.of(
                new Ipv6AnaliseRica.BannerItem("Tipo", base.tipo() + " — " + base.descricaoTipo()),
                new Ipv6AnaliseRica.BannerItem("Prefixo", "/" + prefixo + " (LAN padrão é /64 via SLAAC)"),
                new Ipv6AnaliseRica.BannerItem("Endereços", "2^" + (128 - prefixo) + " = " + base.totalEnderecos()),
                new Ipv6AnaliseRica.BannerItem("Sem broadcast", "nenhum endereço de broadcast; fora do uso em host "
                        + "ficam só o anycast Subnet-Router (identificador todo zero, RFC 4291) e os anycast "
                        + "reservados do topo do /64 (RFC 2526)"));

        List<Ipv6AnaliseRica.ItemProva> provaItens = List.of(
                new Ipv6AnaliseRica.ItemProva("🏷️ Tipo", base.tipo()),
                new Ipv6AnaliseRica.ItemProva("📏 Prefixo", "/" + prefixo),
                new Ipv6AnaliseRica.ItemProva("🔢 Endereços", "2^" + (128 - prefixo) + " = " + base.totalEnderecos()),
                new Ipv6AnaliseRica.ItemProva("🌐 Rede", base.rede()),
                new Ipv6AnaliseRica.ItemProva("🔌 Interface ID", base.interfaceId()));
        String provaFrase = base.comprimido() + "/" + prefixo + " é " + base.tipo()
                + ": " + prefixo + " bits de rede, " + (128 - prefixo) + " de interface, 2^" + (128 - prefixo)
                + " endereços, sem broadcast.";

        String[] tema = temaPorPrefixo(prefixo);

        String textoCopia = montarTextoCopia(base, prefixo);

        return new Ipv6AnaliseRica.Resultado(
                base, dec.bitsRede(), dec.bitsInterface(), grade, aplicacao,
                base.totalEnderecos(), base.totalPotencia(), dec.gatewayLinkLocal(),
                dec.delegacao(), regua, reguaCount, wizard, linhaTempo, referencia, conversao,
                grc, dicas, termos, ospfv3, eigrp, ciscoNota,
                "Contexto do bloco IPv6", "Como este prefixo se encaixa no plano de endereçamento",
                bannerItens, provaFrase, provaItens,
                tema[0], tema[1], tema[2], tema[3], dec.enunciado(), textoCopia);
    }

    /** Próximas {@code n} sub-redes contíguas do mesmo prefixo (régua), a partir do bloco base. */
    private List<SubredeIpv6> reguaSubredes(String entrada, int prefixo, int n) {
        IPv6Address addr = new IPAddressString(hostDe(entrada).toCanonicalString() + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock();
        BigInteger inicio = new BigInteger(1, addr.getLower().getBytes());
        BigInteger passo = BigInteger.TWO.pow(128 - prefixo);
        BigInteger teto = BigInteger.TWO.pow(128);
        List<SubredeIpv6> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, n); i++) {
            BigInteger valor = inicio.add(passo.multiply(BigInteger.valueOf(i)));
            if (valor.compareTo(teto) >= 0) {
                break;
            }
            IPv6Address rede = new IPv6Address(paraBytes16(valor));
            out.add(new SubredeIpv6(i + 1, rede.toCanonicalString(), "/" + prefixo,
                    rede.toCanonicalString(), ultimoDoBloco(valor, passo)));
        }
        return out;
    }

    private List<Ipv6AnaliseRica.ReferenciaPrefixo> referenciaPrefixos(int prefixoAtual) {
        int[] prefixos = {32, 40, 48, 52, 56, 60, 64};
        List<Ipv6AnaliseRica.ReferenciaPrefixo> out = new ArrayList<>(prefixos.length);
        for (int p : prefixos) {
            String lans = p <= 64 ? BigInteger.TWO.pow(64 - p).toString() : "—";
            String ends = "2^" + (128 - p);
            String fronteira = (p % 4 == 0) ? "alinhado a nibble" : "no meio de um nibble";
            out.add(new Ipv6AnaliseRica.ReferenciaPrefixo("/" + p, lans, ends, fronteira, p == prefixoAtual));
        }
        return out;
    }

    private String grcRecomendacao(String tipo) {
        return switch (tipo) {
            case "Global unicast" -> "Recomendação: firewall stateful (sem NAT para proteger), RA Guard e DHCPv6 snooping no enlace.";
            case "ULA / Privado" -> "Recomendação: uso interno; não roteie o fc00::/7 para a Internet.";
            case "Link-local" -> "Recomendação: válido apenas no enlace; nunca roteável — use para NDP e vizinhança.";
            case "Documentação" -> "Recomendação: 2001:db8::/32 é só para exemplos/aula — nunca em produção.";
            case "Multicast" -> "Recomendação: controle de grupos (MLD) e filtragem de escopo.";
            default -> "Recomendação: valide o escopo antes de rotear e aplique filtragem por prefixo.";
        };
    }

    private List<Ipv6AnaliseRica.DicaSeguranca> segurancaDicas(String tipo) {
        List<Ipv6AnaliseRica.DicaSeguranca> out = new ArrayList<>();
        out.add(new Ipv6AnaliseRica.DicaSeguranca("info", "🕵️",
                "Privacidade SLAAC (RFC 4941): use endereços temporários para não expor o MAC no Interface ID."));
        out.add(new Ipv6AnaliseRica.DicaSeguranca("warning", "🛡️",
                "RA Guard e ND inspection no switch evitam Router Advertisement forjado (ataque clássico de IPv6)."));
        out.add(new Ipv6AnaliseRica.DicaSeguranca("danger", "🔥",
                "IPv6 não tem NAT como fronteira — o firewall stateful é obrigatório, não opcional."));
        if ("ULA / Privado".equals(tipo)) {
            out.add(new Ipv6AnaliseRica.DicaSeguranca("success", "🔒",
                    "ULA (fd00::/8) fica contido na organização — bom para serviços internos."));
        }
        return out;
    }

    private String[] temaPorPrefixo(int prefixo) {
        if (prefixo >= 64) {
            return new String[] {"Verde", "prefixo /64+ (uma LAN ou mais específico)", "#3fb950", "#238636"};
        }
        if (prefixo >= 48) {
            return new String[] {"Azul", "prefixo /48–/63 (site / delegação)", "#58a6ff", "#1f6feb"};
        }
        if (prefixo >= 32) {
            return new String[] {"Âmbar", "prefixo /32–/47 (alocação de provedor)", "#d29922", "#9e6a03"};
        }
        return new String[] {"Vermelho", "prefixo /0–/31 (bloco enorme, raro)", "#f85149", "#da3633"};
    }

    private String montarTextoCopia(AnaliseIpv6 base, int prefixo) {
        return "Análise IPv6\n"
                + "Entrada: " + base.entrada() + "\n"
                + "Comprimido: " + base.comprimido() + "\n"
                + "Expandido: " + base.expandido() + "\n"
                + "Tipo: " + base.tipo() + " (" + base.descricaoTipo() + ")\n"
                + "Prefixo: /" + prefixo + "\n"
                + "Rede: " + base.rede() + "\n"
                + "Faixa: " + base.primeiro() + " — " + base.ultimo() + "\n"
                + "Endereços: 2^" + (128 - prefixo) + " = " + base.totalEnderecos() + "\n"
                + "Interface ID: " + base.interfaceId() + "\n"
                + "Solicited-node: " + base.solicitedNode() + "\n"
                + "Reverso: " + base.reversePtr();
    }

    private static byte[] parseMac(String mac) {
        String limpo = (mac == null ? "" : mac).replaceAll("[.:\\-\\s]", "");
        if (!limpo.matches("[0-9a-fA-F]{12}")) {
            throw new Ipv6Exception("MAC inválido. Use 12 dígitos hex (ex.: 00:1a:2b:3c:4d:5e).");
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(limpo.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static int parseSubnetId(String subnetId) {
        String txt = subnetId == null ? "" : subnetId.strip();
        if (txt.isEmpty()) {
            return 0;
        }
        int base = txt.toLowerCase().startsWith("0x") ? 16 : 10;
        try {
            int v = Integer.parseInt(base == 16 ? txt.substring(2) : txt, base);
            if (v < 0 || v > 65535) {
                throw new Ipv6Exception("Subnet ID fora da faixa: use 0 a 65535.");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new Ipv6Exception("Subnet ID inválido: use um número de 0 a 65535 (ou 0x____).");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String hexDoBin(String bin16) {
        return String.format("%04x", Integer.parseInt(bin16, 2));
    }

    /**
     * Gateway convencional (::1) do bloco.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> sugerir o endereço do roteador na LAN.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o gateway fica DENTRO do bloco; um /128 é um endereço só e não tem
     * gateway (antes saía rede+1, fora do bloco — auditoria CALC-28).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; /128 devolve um texto explicativo.</p>
     */
    private String gatewaySugerido(String rede, int prefixo) {
        if (prefixo >= 128) {
            return SEM_GATEWAY_128;
        }
        BigInteger inicio = new BigInteger(1, new IPAddressString(rede + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock().getLower().getBytes());
        return new IPv6Address(paraBytes16(inicio.add(BigInteger.ONE))).toCanonicalString();
    }

    private DelegacaoInfo delegacaoLinha(int prefixoBase, int alvo, String uso) {
        if (alvo < prefixoBase) {
            return new DelegacaoInfo("/" + alvo, "— (mais amplo que /" + prefixoBase + ")", uso);
        }
        return new DelegacaoInfo("/" + alvo, BigInteger.TWO.pow(alvo - prefixoBase).toString(), uso);
    }

    private String enunciadoProva(int prefixo) {
        if (prefixo <= 64) {
            return "Um /" + prefixo + " comporta " + BigInteger.TWO.pow(64 - prefixo)
                    + " sub-redes /64 — cada /64 é uma LAN com 2^64 endereços via SLAAC.";
        }
        return "Prefixo /" + prefixo + " é mais específico que /64: " + BigInteger.TWO.pow(128 - prefixo)
                + " endereços, abaixo da fronteira do SLAAC (enlaces ponto a ponto usam /127, loopbacks /128).";
    }

    private IPv6Address prefixoBloco(IPv6Address host, int prefixo) {
        return new IPAddressString(host.toCanonicalString() + "/" + prefixo)
                .getAddress().toIPv6().toPrefixBlock();
    }

    private FaixaEspecial classificar(IPv6Address host) {
        IPv6Address alvo = host.withoutPrefixLength();
        for (FaixaEspecial f : FAIXAS) {
            IPAddress bloco = new IPAddressString(f.cidr()).getAddress();
            if (bloco != null && bloco.contains(alvo)) {
                return f;
            }
        }
        return new FaixaEspecial("", "Reservado/Outro", "Fora das faixas especiais mapeadas");
    }

    /**
     * PROPÓSITO: a parte de host que o PREFIXO digitado deixa livre (endereço AND complemento da
     * máscara), para a linha "aplicação do prefixo" — num /48 são 80 bits, não os 64 baixos fixos.
     * INVARIANTES: forma canônica RFC 5952; /128 devolve "::" (não há bits de host).
     * FALHA: não lança; a entrada já foi validada por {@link #analisar}.
     */
    private String parteDeHost(String entrada, int prefixo) {
        BigInteger v = new BigInteger(1, hostDe(entrada).getBytes());
        BigInteger mascaraHost = BigInteger.ONE.shiftLeft(128 - prefixo).subtract(BigInteger.ONE);
        return new IPv6Address(paraBytes16(v.and(mascaraHost))).toCanonicalString();
    }

    /** Texto da fronteira rede/interface; nos extremos /0 e /128 não há fronteira dentro do endereço. */
    private static String fronteira(int prefixo) {
        if (prefixo >= 128) {
            return "sem fronteira: /128 é um único endereço (todos os 128 bits são de rede)";
        }
        if (prefixo <= 0) {
            return "sem fronteira: /0 é o espaço inteiro (todos os 128 bits são de interface)";
        }
        return "após o bit " + prefixo + " (hexteto " + (prefixo / 16 + 1) + ", nibble " + (((prefixo % 16) / 4) + 1) + ")";
    }

    private String interfaceId(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        StringBuilder sb = new StringBuilder();
        for (int i = 8; i < 16; i += 2) {
            if (i > 8) {
                sb.append(':');
            }
            sb.append(String.format("%02x%02x", b[i] & 0xFF, b[i + 1] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Multicast solicited-node de um unicast: ff02::1:ff + 24 bits baixos do endereço (RFC 4291).
     * Monta o endereço de 16 bytes e usa a forma canônica RFC 5952 da biblioteca (sem zeros à
     * esquerda no último hexteto), consistente com o resto do app — em vez de formatar à mão.
     */
    private String solicitedNode(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        byte[] sn = new byte[16];
        sn[0] = (byte) 0xff;
        sn[1] = 0x02;
        sn[11] = 0x01;
        sn[12] = (byte) 0xff;
        sn[13] = b[13];
        sn[14] = b[14];
        sn[15] = b[15];
        return new IPv6Address(sn).toCanonicalString();
    }

    private List<String> binarioHextetos(IPv6Address host) {
        byte[] b = host.getLower().getBytes();
        List<String> out = new ArrayList<>(8);
        for (int i = 0; i < 16; i += 2) {
            int hextet = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            String bin = Integer.toBinaryString(hextet);
            out.add("0".repeat(16 - bin.length()) + bin);
        }
        return out;
    }

    private String ultimoDoBloco(BigInteger inicio, BigInteger passo) {
        BigInteger fim = inicio.add(passo).subtract(BigInteger.ONE);
        return new IPv6Address(paraBytes16(fim)).toCanonicalString();
    }

    /** Converte um valor de até 128 bits para o array de 16 bytes (big-endian) que a lib aceita. */
    private static byte[] paraBytes16(BigInteger valor) {
        byte[] full = valor.toByteArray();
        byte[] out = new byte[16];
        int copiar = Math.min(full.length, 16);
        for (int i = 0; i < copiar; i++) {
            out[15 - i] = full[full.length - 1 - i];
        }
        return out;
    }

    private record FaixaEspecial(String cidr, String tipo, String descricao) { }

    public record AnaliseIpv6(
            String entrada, String comprimido, String expandido, int prefixo, boolean temPrefixo,
            String rede, String primeiro, String ultimo, String totalEnderecos, String totalPotencia,
            String interfaceId, String tipo, String descricaoTipo, String solicitedNode,
            String reversePtr, List<String> binarioHextetos) { }

    public record DivisaoIpv6(
            String baseCidr, int prefixoBase, int prefixoAlvo, String quantidadeSubredes,
            String enderecosPorSubrede, boolean truncado, int exibidas, List<SubredeIpv6> subredes) { }

    public record SubredeIpv6(int indice, String rede, String prefixoStr, String primeiro, String ultimo) { }

    /** Uma alocação do planejador de delegação: nome da sub-rede, bloco, faixa e gateway. */
    public record AlocacaoLan(int indice, String nome, String rede, String prefixoStr,
            String primeiro, String ultimo, String gateway) { }

    /** Plano de delegação de prefixo (análogo IPv6 do VLSM): base, alvo, capacidade e alocações. */
    public record DelegacaoPlano(String baseCidr, int prefixoBase, int prefixoAlvo, String capacidade,
            int usados, boolean truncado, List<AlocacaoLan> alocacoes, String enunciado) { }

    /** Um bloco CIDR IPv6 com sua faixa e contagem (usado por sumarização e faixa→CIDR). */
    public record BlocoSumario(String cidr, String primeiro, String ultimo, String enderecos) { }

    /** Resultado da sumarização: supernet, blocos mesclados mínimos e relação de contenção. */
    public record SumarizacaoIpv6(String supernet, String supernetPrimeiro, String supernetUltimo,
            String supernetEnderecos, int entradas, List<BlocoSumario> blocosMesclados,
            String explicacao, boolean umContemOutro, String relacao,
            boolean exata, String enderecosCobertos, String enderecosExtras) { }

    /** Resultado de faixa→CIDR: início, fim e a lista mínima de blocos CIDR que a cobre. */
    public record FaixaCidrIpv6(String inicio, String fim, int quantidadeBlocos,
            List<BlocoSumario> blocos, String explicacao) { }

    /** Um nibble (4 bits) do endereço: índice global (1–32), dígito hex, binário e o hexteto (1–8). */
    public record NibbleInfo(int indice, char hex, String bin, int hexteto) { }

    /** Decomposição por nibble + expansão/compressão (RFC 4291/5952) e reverso ip6.arpa. */
    public record NibblesIpv6(String comprimido, String expandido, String reversePtr,
            List<NibbleInfo> nibbles, String explicacao) { }

    /** Uma VLAN do plano: id 802.1Q, nome, LAN /prefixoLan, gateway e CLI do SVI. */
    public record VlanLinha(int vlanId, String nome, String rede, String prefixoStr, String gateway, String cisco) { }

    /** Plano de VLANs IPv6: base, alvo, capacidade, VLANs, trunk 802.1Q e nota de DHCPv6/SLAAC. */
    public record VlanPlano(String baseCidr, int prefixoBase, int prefixoLan, int total, String capacidade,
            List<VlanLinha> vlans, String trunkCli, String dhcpNota, String enunciado) { }

    // ---------- Laboratório de Resolução IPv6 (Projetar) ----------

    /** Uma LAN do plano: localidade, bloco /prefixoLan, gateway e faixa. */
    public record ProjetoLan(int indice, String nome, String rede, String prefixoStr, String gateway,
            String primeiro, String ultimo, String enderecos) { }

    /** Um enlace WAN do plano: bloco /prefixoWan e os dois endereços de roteador. */
    public record ProjetoWan(int indice, String nome, String rede, String prefixoStr,
            String ipA, String ipB, String roteadorA, String roteadorB) { }

    /** Um roteador do plano com seu CLI Cisco IPv6 gerado. */
    public record ProjetoRoteador(String nome, String local, String cli) { }

    /** Plano completo de rede IPv6 (aba Projetar). */
    public record ProjetoRede(String baseCidr, int prefixoBase, int prefixoLan, int prefixoWan,
            String topologia, int totalLocais, int totalLinks, String capacidadeLan,
            List<ProjetoLan> lans, List<ProjetoWan> wans, List<ProjetoRoteador> roteadores,
            List<String> rotasEstaticas, String ciscoOspfv3, String ciscoEigrp,
            List<String> passos, String enunciado, String topologyMermaid, String planoTexto) { }

    // ---------- Laboratório de Resolução IPv6 (Engenharia Reversa) ----------

    /** Uma interface lida da config Cisco: nome, descrição e endereços IPv6. */
    public record InterfaceLida(String nome, String descricao, List<String> enderecos) { }

    /** Um endereço IPv6 lido, com interface, prefixo e tipo IANA. */
    public record EnderecoLido(String interfaceNome, String endereco, String prefixo, String tipo) { }

    /** Uma rota estática IPv6 lida: destino e próximo salto. */
    public record RotaLida(String destino, String proximoSalto) { }

    /** Resultado da engenharia reversa: o que foi reconstruído da config + achados. */
    public record EngenhariaReversaIpv6(String hostname, boolean unicastRouting,
            List<InterfaceLida> interfaces, List<EnderecoLido> enderecos, List<RotaLida> rotas,
            List<String> protocolos, List<String> achados) { }

    /**
     * Um hexteto (16 bits): hex, binário completo, quantos bits são de rede (prefixo) e o binário
     * já partido em {@code binRede} (bits de prefixo) e {@code binInterface} (bits de interface).
     */
    public record HextetInfo(int indice, String hex, String bin, int bitsRede,
            String binRede, String binInterface) { }

    /** Uma linha da tabela de delegação de prefixo (/48, /56, /64). */
    public record DelegacaoInfo(String prefixo, String quantidade, String uso) { }

    /** Decomposição profunda para a Análise Didática IPv6. */
    public record DecomposicaoIpv6(AnaliseIpv6 base, List<HextetInfo> hextetos, int bitsRede,
            int bitsInterface, String gatewayLinkLocal, List<DelegacaoInfo> delegacao,
            String ciscoCli, String enunciado) { }

    /** Resultado do EUI-64: MAC normalizado, Interface ID, endereço SLAAC, passos e grade de bits do IID. */
    public record Eui64Result(String mac, String interfaceId, String enderecoSlaac,
            String prefixoRede, String passos, List<Ipv6AnaliseRica.HextetoGrade> iidGrade, String aviso) { }

    /** Resultado da geração de ULA (RFC 4193): prefixo /48, /64, o Global ID e a explicação. */
    public record UlaResult(String ula48, String ula64, String globalId, String explicacao) { }

    /**
     * Comparação de dois endereços/prefixos IPv6: forma canônica e tipo de cada lado, se são o mesmo
     * endereço, se caem na mesma /64 (LAN), quantos bits compartilham, a relação de contenção e a
     * distância numérica entre eles.
     */
    public record ComparacaoIpv6(
            String aEntrada, String aComprimido, int aPrefixo, boolean aTemPrefixo, String aTipo,
            String bEntrada, String bComprimido, int bPrefixo, boolean bTemPrefixo, String bTipo,
            boolean mesmoEndereco, boolean mesmaLan, int bitsComuns, String prefixoComum,
            String contencao, String distancia, String explicacao,
            List<Ipv6AnaliseRica.HextetoGrade> gradeComum) { }
}
