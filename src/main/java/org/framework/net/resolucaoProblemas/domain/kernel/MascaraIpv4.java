package org.framework.net.resolucaoProblemas.domain.kernel;

/**
 * Aritmética de endereço e máscara IPv4 em texto.
 *
 * <p><b>Propósito de negócio:</b> a leitura de uma configuração Cisco lida com
 * máscara escrita à mão, que é onde nascem os erros mais comuns do aluno —
 * máscara com octeto a mais, máscara não contígua, endereço que não pertence à
 * sub-rede que ele mesmo declarou. Este utilitário existe para que parser e
 * auditoria façam essas contas do mesmo jeito, em vez de cada um improvisar a
 * sua.</p>
 *
 * <p><b>Invariantes do domínio:</b> máscara só é aceita como válida quando é
 * <em>contígua</em> — {@code 255.255.240.0} vale, {@code 255.0.255.0} não, ainda
 * que os quatro octetos estejam na faixa. Prefixo inválido é sempre {@code -1},
 * nunca {@code 0}, porque {@code /0} é um prefixo legítimo e confundir os dois
 * transformaria erro em rota default.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum método lança para entrada
 * malformada. {@link #prefixoDe(String)} devolve {@code -1};
 * {@link #ipParaLong(String)} devolve {@code -1}; conversões a partir de valores
 * inválidos devolvem string vazia. Quem chama decide se aquilo é erro do usuário
 * (vira achado) ou ausência esperada.</p>
 */
public final class MascaraIpv4 {

    private MascaraIpv4() {
    }

    /** Quantidade de octetos escritos — 5 denuncia a máscara com grupo a mais. */
    public static int contarOctetos(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        return texto.trim().split("\\.", -1).length;
    }

    /**
     * Prefixo correspondente a uma máscara decimal pontilhada.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code -1} para nulo,
     * quantidade de octetos diferente de quatro, octeto fora de 0–255 ou máscara
     * não contígua.</p>
     */
    public static int prefixoDe(String mascara) {
        long valor = ipParaLong(mascara);
        if (valor < 0) {
            return -1;
        }
        long invertida = (~valor) & 0xFFFFFFFFL;
        // Máscara contígua tem todos os bits 1 à esquerda: o complemento + 1 é potência de 2.
        if (((invertida + 1) & invertida) != 0) {
            return -1;
        }
        return Long.bitCount(valor);
    }

    /** Máscara decimal pontilhada de um prefixo; string vazia se o prefixo for inválido. */
    public static String mascaraDe(int prefixo) {
        if (prefixo < 0 || prefixo > 32) {
            return "";
        }
        long valor = prefixo == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        return longParaIp(valor);
    }

    /** Wildcard (máscara invertida) de um prefixo — formato que o OSPF exige. */
    public static String wildcardDe(int prefixo) {
        if (prefixo < 0 || prefixo > 32) {
            return "";
        }
        long mascara = prefixo == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        return longParaIp((~mascara) & 0xFFFFFFFFL);
    }

    /**
     * Converte IPv4 textual em inteiro sem sinal.
     *
     * <p><b>Comportamento em caso de falha:</b> {@code -1} para nulo, número de
     * octetos diferente de quatro, octeto não numérico ou fora de 0–255.</p>
     */
    public static long ipParaLong(String ip) {
        if (ip == null) {
            return -1;
        }
        String[] partes = ip.trim().split("\\.", -1);
        if (partes.length != 4) {
            return -1;
        }
        long valor = 0;
        for (String parte : partes) {
            if (parte.isEmpty() || parte.length() > 3) {
                return -1;
            }
            int octeto;
            try {
                octeto = org.framework.net.shared.NumeroAscii.parseIntEstrito(parte);
            } catch (NumberFormatException ex) {
                return -1;
            }
            if (octeto < 0 || octeto > 255) {
                return -1;
            }
            valor = (valor << 8) | octeto;
        }
        return valor;
    }

    public static String longParaIp(long valor) {
        if (valor < 0 || valor > 0xFFFFFFFFL) {
            return "";
        }
        return ((valor >> 24) & 0xFF) + "." + ((valor >> 16) & 0xFF) + "."
                + ((valor >> 8) & 0xFF) + "." + (valor & 0xFF);
    }

    public static boolean ipValido(String ip) {
        return ipParaLong(ip) >= 0;
    }

    /** Endereço de rede de um IP dado o prefixo; string vazia se a entrada for inválida. */
    public static String enderecoDeRede(String ip, int prefixo) {
        long valor = ipParaLong(ip);
        if (valor < 0 || prefixo < 0 || prefixo > 32) {
            return "";
        }
        long mascara = prefixo == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        return longParaIp(valor & mascara);
    }

    /** Endereço de broadcast; string vazia se a entrada for inválida. */
    public static String broadcastDe(String ip, int prefixo) {
        long valor = ipParaLong(ip);
        if (valor < 0 || prefixo < 0 || prefixo > 32) {
            return "";
        }
        long mascara = prefixo == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        return longParaIp((valor & mascara) | ((~mascara) & 0xFFFFFFFFL));
    }

    /** Dois endereços caem na mesma sub-rede sob o mesmo prefixo? */
    public static boolean mesmaSubRede(String ipA, String ipB, int prefixo) {
        String redeA = enderecoDeRede(ipA, prefixo);
        String redeB = enderecoDeRede(ipB, prefixo);
        return !redeA.isEmpty() && redeA.equals(redeB);
    }

    /** Rede com prefixo, no formato {@code 200.200.200.0/30}; vazia se inválida. */
    public static String cidrDe(String ip, int prefixo) {
        String rede = enderecoDeRede(ip, prefixo);
        return rede.isEmpty() ? "" : rede + "/" + prefixo;
    }

    /**
     * O outro endereço utilizável de um enlace ponto a ponto.
     *
     * <p><b>Propósito de negócio:</b> num {@code /30} há exatamente dois hosts;
     * saber o par de um endereço é o que permite conferir se o vizinho declarado
     * bate com a interface do outro roteador.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> string vazia quando o prefixo não
     * é {@code /30} ou {@code /31}, ou quando o endereço é inválido — fora de
     * ponto a ponto não existe "o outro" e inventá-lo seria chute.</p>
     */
    public static String parPontoAPonto(String ip, int prefixo) {
        long valor = ipParaLong(ip);
        if (valor < 0 || (prefixo != 30 && prefixo != 31)) {
            return "";
        }
        long mascara = (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        long rede = valor & mascara;
        if (prefixo == 31) {
            return longParaIp(valor == rede ? rede + 1 : rede);
        }
        long primeiro = rede + 1;
        long segundo = rede + 2;
        if (valor == primeiro) {
            return longParaIp(segundo);
        }
        if (valor == segundo) {
            return longParaIp(primeiro);
        }
        return "";
    }

    /** Quantidade de hosts utilizáveis; 0 para prefixo inválido ou sem hosts. */
    public static long hostsUtilizaveis(int prefixo) {
        if (prefixo < 0 || prefixo > 32) {
            return 0;
        }
        if (prefixo >= 31) {
            return prefixo == 31 ? 2 : 1;
        }
        return (1L << (32 - prefixo)) - 2;
    }
}
