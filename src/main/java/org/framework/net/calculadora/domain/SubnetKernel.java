package org.framework.net.calculadora.domain;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.calculadora.exception.CalculadoraException;

/**
 * Aritmética IPv4 de 32 bits usada por toda a Calculadora.
 *
 * <p><b>Propósito de negócio:</b> concentra a conversão entre notação pontuada,
 * prefixo e inteiro de 32 bits, e a montagem de um bloco pronto para exibição.
 * É o único ponto do módulo que sabe manipular bits de endereço — os serviços
 * de divisão, VLAN e agregação apenas o orquestram.</p>
 *
 * <p><b>Invariantes do domínio:</b> todo endereço trafega como {@code long} sem
 * sinal mascarado em 32 bits ({@code 0..4294967295}); contagens de endereços
 * são {@code long} porque um /0 tem 4.294.967.296 endereços e estoura
 * {@code int}; o prefixo é sempre 0–32; /31 tem 2 endereços utilizáveis
 * (RFC 3021) e /32 tem 1 — a fórmula {@code total-2} não vale nesses dois casos.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> qualquer entrada malformada
 * (octeto fora de 0–255, prefixo fora de 0–32, texto vazio, máscara não
 * contígua) lança {@link CalculadoraException} com mensagem didática. Nenhum
 * método devolve {@code null}.</p>
 */
@ApplicationScoped
public class SubnetKernel {

    /** Máscara de 32 bits usada para manter os endereços sem sinal. */
    public static final long MASK32 = 0xFFFFFFFFL;

    private static final int MAX_ENTRADA = 64;

    /**
     * Converte {@code "192.168.0.10"} no inteiro de 32 bits correspondente.
     *
     * <p><b>Comportamento em caso de falha:</b> lança {@link CalculadoraException}
     * se o texto não tiver exatamente quatro octetos numéricos de 0 a 255.</p>
     */
    public long parseIpv4(String texto, String nomeCampo) {
        String valor = exigirTexto(texto, nomeCampo);
        String[] partes = valor.split("\\.", -1);
        if (partes.length != 4) {
            throw new CalculadoraException(
                    nomeCampo + " deve ter quatro octetos separados por ponto (ex.: 192.168.0.0). Recebido: " + valor);
        }
        long resultado = 0L;
        for (String parte : partes) {
            // Só dígitos ASCII e sem zero à esquerda ("010" seria octal no inet_aton) — auditoria CALC-33.
            if (parte.isEmpty() || parte.length() > 3 || !parte.chars().allMatch(c -> c >= '0' && c <= '9')
                    || (parte.length() > 1 && parte.charAt(0) == '0')) {
                throw new CalculadoraException(
                        nomeCampo + " tem um octeto inválido (\"" + parte + "\") em " + valor
                                + ": use só dígitos de 0 a 9, sem zero à esquerda.");
            }
            int octeto = Integer.parseInt(parte);
            if (octeto > 255) {
                throw new CalculadoraException(
                        nomeCampo + ": cada octeto vai de 0 a 255, e " + octeto + " está fora dessa faixa.");
            }
            resultado = (resultado << 8) | octeto;
        }
        return resultado & MASK32;
    }

    /** Converte o inteiro de 32 bits de volta para notação pontuada. */
    public String formatIpv4(long endereco) {
        long v = endereco & MASK32;
        return ((v >>> 24) & 0xFF) + "." + ((v >>> 16) & 0xFF) + "." + ((v >>> 8) & 0xFF) + "." + (v & 0xFF);
    }

    /**
     * Interpreta o campo de bloco base aceitando as três formas usadas em aula:
     * {@code 192.168.0.0/21}, {@code 192.168.0.0 255.255.248.0} e
     * {@code 192.168.0.0} (sem prefixo, quando o prefixo vem de outro campo).
     *
     * <p><b>Invariantes do domínio:</b> o endereço devolvido já vem alinhado à
     * rede (bits de host zerados), então informar um IP de host no lugar da rede
     * não produz um plano inconsistente.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * o prefixo estiver ausente e nenhum padrão for informado, se o prefixo
     * estiver fora de 0–32 ou se a máscara pontuada não for contígua.</p>
     */
    public Rede parseRede(String texto, Integer prefixoPadrao, String nomeCampo) {
        String valor = exigirTexto(texto, nomeCampo);

        String enderecoTxt = valor;
        Integer prefixo = prefixoPadrao;

        int barra = valor.indexOf('/');
        if (barra >= 0) {
            enderecoTxt = valor.substring(0, barra).strip();
            prefixo = parsePrefixo(valor.substring(barra + 1).strip(), nomeCampo);
        } else {
            String[] doisCampos = valor.split("\\s+");
            if (doisCampos.length == 2) {
                enderecoTxt = doisCampos[0];
                prefixo = mascaraParaPrefixo(doisCampos[1], nomeCampo);
            }
        }

        // O endereço é validado ANTES do prefixo: a mensagem de prefixo ausente repete o endereço como
        // exemplo, e com o prefixo primeiro ela sugeria "informe xyz!@#/24" (auditoria FRONT-20).
        long endereco = parseIpv4(enderecoTxt, nomeCampo);
        if (prefixo == null) {
            throw new CalculadoraException(
                    nomeCampo + " precisa do prefixo: informe " + enderecoTxt + "/24, "
                            + enderecoTxt + " 255.255.255.0 ou preencha o campo de prefixo.");
        }

        long rede = endereco & mascara(prefixo);
        return new Rede(rede, prefixo, rede != endereco, formatIpv4(endereco));
    }

    /**
     * Valida e converte o texto de um prefixo CIDR.
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * não for inteiro ou se estiver fora de 0–32.</p>
     */
    public int parsePrefixo(String texto, String nomeCampo) {
        String valor = exigirTexto(texto, nomeCampo);
        int prefixo;
        try {
            prefixo = org.framework.net.shared.NumeroAscii.parseIntEstrito(valor);
        } catch (NumberFormatException ex) {
            throw new CalculadoraException(nomeCampo + " deve ser um número inteiro de 0 a 32. Recebido: " + valor);
        }
        if (prefixo < 0 || prefixo > 32) {
            throw new CalculadoraException(nomeCampo + " deve estar entre 0 e 32. Recebido: /" + prefixo);
        }
        return prefixo;
    }

    /**
     * Converte máscara pontuada em prefixo, exigindo contiguidade.
     *
     * <p><b>Invariantes do domínio:</b> só aceita máscaras contíguas
     * (255.255.248.0 vale, 255.0.255.0 não) porque máscara descontínua não
     * descreve um bloco CIDR.</p>
     */
    public int mascaraParaPrefixo(String texto, String nomeCampo) {
        long valor = parseIpv4(texto, nomeCampo);
        long invertido = (~valor) & MASK32;
        if (((invertido + 1) & invertido) != 0) {
            throw new CalculadoraException(
                    "A máscara " + texto + " não é contígua — os bits 1 precisam ficar todos à esquerda "
                            + "(ex.: 255.255.248.0).");
        }
        return Long.bitCount(valor);
    }

    /** Máscara de rede do prefixo, como inteiro de 32 bits. */
    public long mascara(int prefixo) {
        return prefixo == 0 ? 0L : (MASK32 << (32 - prefixo)) & MASK32;
    }

    /** Quantidade total de endereços de um prefixo — {@code 2^(32-prefixo)}. */
    public long totalIps(int prefixo) {
        return 1L << (32 - prefixo);
    }

    /**
     * Hosts endereçáveis de um prefixo.
     *
     * <p><b>Invariantes do domínio:</b> /32 vale 1 (rota de host) e /31 vale 2
     * (RFC 3021, link ponto a ponto) — nesses dois casos não existe rede nem
     * broadcast a descontar. Do /30 para cima aplica-se {@code total - 2}.</p>
     */
    public long hostsUteis(int prefixo) {
        if (prefixo >= 32) {
            return 1L;
        }
        if (prefixo == 31) {
            return 2L;
        }
        return totalIps(prefixo) - 2L;
    }

    /**
     * Monta o bloco exibível a partir do endereço de rede e do prefixo.
     *
     * <p><b>Invariantes do domínio:</b> em /31 e /32 os campos de faixa apontam
     * para os próprios endereços do bloco e o broadcast é marcado como não
     * aplicável, porque tratá-los como /30 produziria faixa vazia ou invertida.</p>
     */
    public BlocoIpv4 bloco(int indice, long rede, int prefixo) {
        long total = totalIps(prefixo);
        long broadcast = (rede + total - 1) & MASK32;
        String primeiro;
        String ultimo;
        String broadcastTxt;
        if (prefixo >= 32) {
            primeiro = formatIpv4(rede);
            ultimo = formatIpv4(rede);
            broadcastTxt = "N/A (/32)";
        } else if (prefixo == 31) {
            primeiro = formatIpv4(rede);
            ultimo = formatIpv4(broadcast);
            broadcastTxt = "N/A (/31 RFC 3021)";
        } else {
            primeiro = formatIpv4(rede + 1);
            ultimo = formatIpv4(broadcast - 1);
            broadcastTxt = formatIpv4(broadcast);
        }
        return new BlocoIpv4(
                indice,
                formatIpv4(rede),
                prefixo,
                formatIpv4(mascara(prefixo)),
                formatIpv4((~mascara(prefixo)) & MASK32),
                primeiro,
                ultimo,
                broadcastTxt,
                total,
                hostsUteis(prefixo));
    }

    /** Wildcard (máscara curinga de ACL/OSPF) do prefixo, em notação pontuada. */
    public String wildcard(int prefixo) {
        return formatIpv4((~mascara(prefixo)) & MASK32);
    }

    /** Último endereço (broadcast) do bloco que começa em {@code rede}. */
    public long broadcast(long rede, int prefixo) {
        return (rede + totalIps(prefixo) - 1) & MASK32;
    }

    /**
     * Menor expoente {@code n} tal que {@code 2^n >= quantidade}.
     *
     * <p><b>Invariantes do domínio:</b> usa {@code Long.numberOfLeadingZeros}
     * sobre 64 bits — os bits significativos de um valor são {@code 64 - nlz}.
     * Calcular com {@code 32 -} produz expoentes negativos e planos absurdos.</p>
     */
    public int bitsPara(long quantidade) {
        if (quantidade <= 1L) {
            return 0;
        }
        return 64 - Long.numberOfLeadingZeros(quantidade - 1L);
    }

    private String exigirTexto(String texto, String nomeCampo) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.isEmpty()) {
            throw new CalculadoraException(nomeCampo + " não foi informado.");
        }
        if (valor.length() > MAX_ENTRADA) {
            throw new CalculadoraException(nomeCampo + " é longo demais para um endereço IPv4.");
        }
        return valor;
    }

    /**
     * Bloco base já normalizado.
     *
     * @param endereco       endereço de rede com os bits de host zerados
     * @param prefixo        prefixo CIDR de 0 a 32
     * @param ajustado       {@code true} quando o usuário digitou um IP de host e o kernel alinhou à rede
     * @param entradaOriginal o que o usuário digitou, para explicar o ajuste na tela
     */
    public record Rede(long endereco, int prefixo, boolean ajustado, String entradaOriginal) {
    }
}
