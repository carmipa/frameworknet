package org.framework.net.resolucaoProblemas.domain.kernel;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kernel IPv4 para VLSM/planejamento (biblioteca inet.ipaddr).
 * Complementa {@link org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel},
 * focado em didática bitwise na análise didática.
 */
@ApplicationScoped
public class Ipv4Kernel {

    /**
     * Lê uma rede IPv4 em CIDR ("192.168.1.0/24") e devolve o bloco.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a rede base de todo o planejamento VLSM.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só a forma pontuada com QUATRO octetos decimais, sem zero à
     * esquerda. A biblioteca aceita a forma do inet_aton e reinterpretava em silêncio: "192.168.1/24" virava
     * 192.168.0.0/24 (o último número ocupa 16 bits) e "10.1/16" virava 10.0.0.0/16; "010" seria octal
     * (auditoria CALC-20). Bits de host no endereço são zerados (192.168.1.77/24 → 192.168.1.0/24).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> texto vazio, forma incompleta, octeto com zero à esquerda ou
     * endereço que não é IPv4 lançam {@link EntradaInvalidaException} com a mensagem para a tela.</p>
     */
    public IPv4Address parseNetwork(String input, String fieldLabel) {
        String txt = input == null ? "" : input.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException("Campo obrigatório: " + fieldLabel + ".");
        }
        String semPrefixo = txt.contains("/") ? txt.substring(0, txt.indexOf('/')) : txt;
        if (!semPrefixo.matches("(0|[1-9]\\d{0,2})(\\.(0|[1-9]\\d{0,2})){3}")) {
            throw new EntradaInvalidaException(fieldLabel + " precisa dos quatro octetos, sem zero à esquerda "
                    + "(ex.: 192.168.1.0/24): \"" + txt + "\" seria lido de outro jeito.");
        }
        IPAddressString addrString = new IPAddressString(txt);
        if (!addrString.isValid()) {
            throw new EntradaInvalidaException("Rede base invalida: " + txt);
        }
        IPAddress address = addrString.getAddress();
        if (address == null || !address.isIPv4()) {
            throw new EntradaInvalidaException("A rede base deve ser IPv4.");
        }
        return address.toIPv4().toPrefixBlock();
    }

    public InferenciaCidr inferirCidrPorIp(String ipS) {
        int[] parts = parseIpv4Parts(ipS, "IP");
        int o1 = parts[0];
        if (o1 == 0) {
            return new InferenciaCidr(8, "Inferido (classful): 0.x.x.x => /8");
        }
        if (o1 >= 1 && o1 <= 126) {
            return new InferenciaCidr(8, "Inferido (classful): classe A => /8");
        }
        if (o1 == 127) {
            return new InferenciaCidr(8, "Inferido (classful): loopback 127.x.x.x => /8");
        }
        if (o1 >= 128 && o1 <= 191) {
            return new InferenciaCidr(16, "Inferido (classful): classe B => /16");
        }
        if (o1 >= 192 && o1 <= 223) {
            return new InferenciaCidr(24, "Inferido (classful): classe C => /24");
        }
        if (o1 >= 224 && o1 <= 239) {
            return new InferenciaCidr(4, "Inferido (classful): classe D (multicast) => /4");
        }
        return new InferenciaCidr(4, "Inferido (classful): classe E (reservada) => /4");
    }

    public ClassificacaoIpv4 classificacaoIpv4(int primeiroOcteto) {
        if (primeiroOcteto >= 1 && primeiroOcteto <= 126) {
            return new ClassificacaoIpv4("A", "1-126", "255.0.0.0");
        }
        // 0 e 127 caíam no "E" do fim (auditoria CALC-36): pelo bit inicial são do espaço da classe A,
        // reservados (0.0.0.0/8 "este host"; 127.0.0.0/8 loopback).
        if (primeiroOcteto == 0) {
            return new ClassificacaoIpv4("A", "0 · Reservada (0.0.0.0/8, \"este host\")", "255.0.0.0");
        }
        if (primeiroOcteto == 127) {
            return new ClassificacaoIpv4("A", "127 · Reservada (loopback)", "255.0.0.0");
        }
        if (primeiroOcteto >= 128 && primeiroOcteto <= 191) {
            return new ClassificacaoIpv4("B", "128-191", "255.255.0.0");
        }
        if (primeiroOcteto >= 192 && primeiroOcteto <= 223) {
            return new ClassificacaoIpv4("C", "192-223", "255.255.255.0");
        }
        if (primeiroOcteto >= 224 && primeiroOcteto <= 239) {
            return new ClassificacaoIpv4("D", "224-239", "Multicast (sem máscara padrão)");
        }
        return new ClassificacaoIpv4("E", "240-255", "Reservada/Experimental");
    }

    public int requiredPrefixForHosts(int hostCount) {
        int needed = hostCount + 2;
        int hostBits = 32 - Integer.numberOfLeadingZeros(needed - 1);
        return 32 - hostBits;
    }

    public int prefixLength(IPv4Address network) {
        Integer prefix = network.getNetworkPrefixLength();
        return prefix == null ? 32 : prefix;
    }

    public HostRange hostsRange(IPv4Address network) {
        if (network.getCount().compareTo(BigInteger.valueOf(2)) <= 0) {
            return new HostRange(network.getLower().toCanonicalString(), network.getUpper().toCanonicalString());
        }
        IPv4Address firstHost = network.getLower().increment(1).toIPv4().withoutPrefixLength();
        IPv4Address lastHost = network.getUpper().increment(-1).toIPv4().withoutPrefixLength();
        return new HostRange(firstHost.toCanonicalString(), lastHost.toCanonicalString());
    }

    public int hostsSupported(IPv4Address network) {
        BigInteger count = network.getCount();
        if (count.compareTo(BigInteger.valueOf(2)) <= 0) {
            return Math.max(count.intValue() - 2, 0);
        }
        return Math.max(count.intValue() - 2, 0);
    }

    /**
     * PROPÓSITO: máscara pontuada que o aluno cola no IOS/Packet Tracer ({@code ip address A M}).
     * INVARIANTES: sempre quatro octetos puros; nunca carrega o sufixo {@code /NN} que a biblioteca
     * anexa à máscara de um bloco com prefixo (o IOS recusa {@code 255.255.255.128/25}).
     * FALHA: não lança; {@code network} sem prefixo devolve a máscara /32.
     */
    public String netmask(IPv4Address network) {
        return network.getNetworkMask().withoutPrefixLength().toCanonicalString();
    }

    /**
     * PROPÓSITO: wildcard (máscara invertida) usada em ACL e {@code network} do OSPF/EIGRP.
     * INVARIANTES: quatro octetos puros, sem sufixo {@code /NN}.
     * FALHA: não lança.
     */
    public String wildcard(IPv4Address network) {
        return network.getHostMask().withoutPrefixLength().toCanonicalString();
    }

    public String gateway(IPv4Address network) {
        return network.getLower().increment(1).withoutPrefixLength().toCanonicalString();
    }

    /** Teto da reserva de IP fixo depois do gateway (impressora, servidor, AP de laboratório). */
    public static final int RESERVA_DHCP_MAXIMA = 9;

    /**
     * Quantos endereços depois do gateway ficam FORA do DHCP numa LAN.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o script do Packet Tracer reservava sempre gateway + 9; numa LAN
     * dimensionada sem folga (5 hosts num /29, que tem 6) a reserva engolia todos os endereços e
     * nenhum PC recebia lease (auditoria CALC-16). A reserva agora sai da folga real.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o gateway é sempre excluído (fora desta conta); a reserva nunca
     * passa de {@link #RESERVA_DHCP_MAXIMA} nem da folga {@code suportados − 1 − pedidos}, então sobram
     * sempre pelo menos {@code pedidos} endereços para o DHCP entregar; é a ÚNICA fonte da regra — o
     * script e o diagrama (PCs de teste) leem daqui.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; folga negativa (pedido maior que a LAN, que o
     * planejamento já recusa) devolve 0.</p>
     */
    public int reservaDhcpAposGateway(int hostsSuportados, int hostsPedidos) {
        int folga = hostsSuportados - 1 - hostsPedidos;
        return Math.max(0, Math.min(RESERVA_DHCP_MAXIMA, folga));
    }

    /**
     * Os primeiros {@code limite} endereços atribuíveis a host de um bloco, em ordem crescente.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> dar as pontas de um enlace WAN e os PCs de exemplo do diagrama
     * sem percorrer o bloco. A versão anterior materializava o bloco inteiro: um "Prefixo WAN" /10
     * sobre a base 10.0.0.0/8 criava ~4 milhões de objetos e derrubava a JVM de produção
     * ({@code ExitOnOutOfMemoryError}) com um único POST (auditoria CALC-02).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nunca cria mais que {@code limite} endereços, qualquer que
     * seja o tamanho do bloco; em blocos com rede e broadcast (prefixo até /30) os dois ficam de
     * fora; em /31 e /32 todos os endereços contam (RFC 3021); o resultado não carrega prefixo.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@code limite} negativo lança
     * {@link IllegalArgumentException}; bloco com menos hosts que o pedido devolve a lista menor
     * (quem precisa de dois decide o que fazer com um).</p>
     */
    public List<IPv4Address> primeirosHostsUteis(IPv4Address network, int limite) {
        if (limite < 0) {
            throw new IllegalArgumentException("limite negativo: " + limite);
        }
        BigInteger count = network.getCount();
        boolean temRedeEBroadcast = count.compareTo(BigInteger.valueOf(2)) > 0;
        BigInteger disponiveis = temRedeEBroadcast ? count.subtract(BigInteger.valueOf(2)) : count;
        int quantos = disponiveis.min(BigInteger.valueOf(limite)).intValue();
        IPv4Address inicio = network.getLower();
        int deslocamento = temRedeEBroadcast ? 1 : 0;
        List<IPv4Address> hosts = new ArrayList<>(quantos);
        for (int i = 0; i < quantos; i++) {
            hosts.add(inicio.increment(deslocamento + i).withoutPrefixLength());
        }
        return hosts;
    }

    public Iterator<? extends IPv4Address> iterateSubnets(IPv4Address baseNetwork, int prefix) {
        IPv4Address base = baseNetwork.toPrefixBlock();
        int basePrefix = prefixLength(base);
        if (prefix < basePrefix) {
            throw new EntradaInvalidaException(
                    "Prefixo /" + prefix + " é mais amplo que a rede base /" + basePrefix + ".");
        }
        if (prefix == basePrefix) {
            return List.of(base).iterator();
        }
        IPv4Address subdivided = base.setPrefixLength(prefix, false);
        return subdivided.prefixBlockIterator();
    }

    public boolean overlaps(IPv4Address a, IPv4Address b) {
        return a.overlaps(b);
    }

    public long addressCount(IPv4Address network) {
        return network.getCount().longValue();
    }

    /**
     * PROPÓSITO DE NEGÓCIO: converte o IPv4 textual em quatro octetos numéricos, base do
     * planejamento VLSM e da inferência de CIDR por IP na resolução de problemas de rede.
     *
     * INVARIANTES DO DOMÍNIO: a entrada tem exatamente quatro grupos separados por ponto; cada
     * grupo é só de dígitos, tem no máximo 3 caracteres e representa um octeto de 0 a 255. Grupo
     * vazio é proibido — rejeita ponto inicial, final ou duplo (ex.: "192.168.0.10." é inválido).
     *
     * COMPORTAMENTO EM CASO DE FALHA: qualquer violação lança {@link EntradaInvalidaException} com
     * mensagem didática apontando o octeto — nunca propaga {@code NumberFormatException} crua (500)
     * nem aceita entrada malformada. Octeto com mais de 3 dígitos é barrado ANTES do
     * {@code Integer.parseInt}, evitando estouro de int em entradas como "9999999999.1.1.1".
     */
    public int[] parseIpv4Parts(String ipS, String nomeCampo) {
        String txt = ipS == null ? "" : ipS.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException(nomeCampo + " vazio.");
        }
        String[] rawParts = txt.split("\\.", -1);
        if (rawParts.length != 4) {
            throw new EntradaInvalidaException(nomeCampo + " inválido. Use formato x.x.x.x.");
        }
        int[] parts = new int[4];
        for (int idx = 0; idx < rawParts.length; idx++) {
            String raw = rawParts[idx].strip();
            int octetoIdx = idx + 1;
            if (raw.isEmpty()) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " está vazio.");
            }
            // Só dígitos ASCII: Character.isDigit aceitava dígitos árabe-índicos e o parseInt os convertia
            // (auditoria CALC-33).
            if (!raw.chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " não é numérico.");
            }
            // "010" é 10 aqui e 8 (octal) no inet_aton de muitos sistemas: ambíguo, então recusado.
            if (raw.length() > 1 && raw.charAt(0) == '0') {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx
                        + " com zero à esquerda (\"" + raw + "\"); escreva sem o zero.");
            }
            if (raw.length() > 3) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " fora de 0-255.");
            }
            int octeto = org.framework.net.shared.NumeroAscii.parseIntEstrito(raw);
            if (octeto < 0 || octeto > 255) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " fora de 0-255.");
            }
            parts[idx] = octeto;
        }
        return parts;
    }

    public record InferenciaCidr(int cidr, String descricaoOrigem) { }

    public record ClassificacaoIpv4(String classe, String faixaOcteto, String mascaraPadrao) { }

    public record HostRange(String start, String end) { }
}
