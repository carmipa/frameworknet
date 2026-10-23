package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simulador didático de avaliação de UMA linha de ACL Cisco contra um pacote.
 *
 * <p><b>Propósito de negócio:</b> mostrar ao estudante como o roteador decide se uma linha de ACL
 * captura um pacote (MATCH) e o que essa linha manda fazer (permit libera, deny descarta). A sintaxe
 * é a do IOS, porque o aluno leva a linha para o Packet Tracer e para a prova.</p>
 *
 * <p><b>Invariantes do domínio (auditoria de 01/10/2026, CALC-10/CONT-01 — fonte: Cisco IOS Security
 * Command Reference, "access-list (IP extended)" e "access-list (IP standard)"):</b></p>
 * <ul>
 *   <li>ACL estendida: {@code {permit|deny} protocolo origem [op porta] destino [op porta]} — o
 *       DESTINO é obrigatório, e o operador logo depois da origem compara a porta de ORIGEM; o que vem
 *       depois do destino compara a porta de destino;</li>
 *   <li>ACL padrão (número 1–99 ou 1300–1999, ou linha sem protocolo): só a origem;</li>
 *   <li>o protocolo da regra precisa casar o do pacote ({@code ip} casa qualquer um): uma regra
 *       icmp não captura TCP;</li>
 *   <li>comparação exata e tipada — porta é inteiro, endereço casa por máscara-curinga;</li>
 *   <li>MATCH é correspondência; permitir ou bloquear é a AÇÃO da linha. "Não corresponde" é outro
 *       resultado: o roteador seguiria para a próxima linha.</li>
 * </ul>
 *
 * <p><b>Comportamento em caso de falha:</b> entrada inválida (IPv4 malformado, porta fora de faixa,
 * sintaxe fora do suportado, caractere perigoso) lança {@link SegurancaException}, convertida em
 * HTTP 400 com a mensagem — o simulador nunca "chuta" veredito diante do que não entende.</p>
 */
@ApplicationScoped
public class AclSimulatorService {

    /** Porta de origem quando o formulário não informa: efêmera de cliente (IANA 49152–65535). */
    public static final int PORTA_ORIGEM_PADRAO = 49152;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Compatibilidade: pacote TCP saindo da porta efêmera padrão. */
    public String testarPacote(String regra, String ipOrigem, String ipDestino, String portaDestinoRaw) {
        return testarPacote(regra, "tcp", ipOrigem, String.valueOf(PORTA_ORIGEM_PADRAO), ipDestino, portaDestinoRaw);
    }

    /**
     * Avalia a linha contra o pacote informado.
     *
     * <p><b>Comportamento em caso de falha:</b> ver a classe — entrada inválida lança
     * {@link SegurancaException}.</p>
     */
    public String testarPacote(String regra, String protocoloRaw, String ipOrigem, String portaOrigemRaw,
                               String ipDestino, String portaDestinoRaw) {
        return telemetriaLogger.medir("seguranca", "teste_acl", () -> {
            validarEntradas(regra, ipOrigem, ipDestino);
            String protocolo = protocoloDoPacote(protocoloRaw);
            boolean temPorta = !protocolo.equals("icmp");
            int portaOrigem = temPorta ? lerPortaPacote(portaOrigemRaw, PORTA_ORIGEM_PADRAO, "origem") : -1;
            int portaDestino = temPorta ? lerPortaPacote(portaDestinoRaw, -1, "destino") : -1;
            long origem = ipParaLong(ipOrigem);
            long destino = ipParaLong(ipDestino);

            RegraAcl r = RegraAcl.parse(regra);

            telemetriaLogger.logEvent("info", "seguranca", "acl_avaliada", Map.of(
                    "regra", regra,
                    "ipOrigem", ipOrigem,
                    "ipDestino", ipDestino,
                    "portaDestino", portaDestino));

            List<String> falhas = new ArrayList<>();
            if (!r.protocolo().equals("ip") && !r.protocolo().equals(protocolo)) {
                falhas.add("protocolo diferente (a regra é " + r.protocolo() + ", o pacote é " + protocolo + ")");
            }
            if (!r.origem().casa(origem)) {
                falhas.add("origem fora do alcance");
            }
            if (!r.destino().casa(destino)) {
                falhas.add("destino fora do alcance");
            }
            if (r.portaOrigem() != null && !r.portaOrigem().casa(portaOrigem)) {
                falhas.add("porta de origem fora do critério (" + r.portaOrigem().descrever() + ")");
            }
            if (r.portaDestino() != null && !r.portaDestino().casa(portaDestino)) {
                falhas.add("porta de destino fora do critério (" + r.portaDestino().descrever() + ")");
            }

            if (falhas.isEmpty()) {
                String tipo = r.padrao() ? "ACL padrão: só a origem é conferida" : "protocolo, origem, destino e portas conferem";
                return r.permite()
                        ? "MATCH (PERMITIDO) — a regra captura o pacote (" + tipo + ") e a ação da linha é permit: o pacote é liberado."
                        : "MATCH (BLOQUEADO) — a regra captura o pacote (" + tipo + ") e a ação da linha é deny: o pacote é descartado.";
            }
            return "NÃO CORRESPONDE (NO MATCH) — a regra não captura este pacote (" + String.join("; ", falhas)
                    + "). O roteador avaliaria a próxima linha (ou o deny implícito no fim da lista).";
        });
    }

    private void validarEntradas(String regra, String ipOrigem, String ipDestino) {
        exigir(regra, "Regra ACL não informada.");
        exigir(ipOrigem, "IP de Origem não informado.");
        exigir(ipDestino, "IP de Destino não informado.");
        if (regra.length() > 200 || ipOrigem.length() > 45 || ipDestino.length() > 45) {
            throw new SegurancaException("Entrada muito longa.");
        }
        // Defesa em profundidade: rejeita caracteres de HTML/scripts nos campos.
        if (contemPerigoso(regra) || contemPerigoso(ipOrigem) || contemPerigoso(ipDestino)) {
            throw new SegurancaException("Caracteres inválidos detectados nas entradas.");
        }
        if (!ipValido(ipOrigem)) {
            throw new SegurancaException("IP de Origem inválido (use IPv4, ex.: 192.168.1.5).");
        }
        if (!ipValido(ipDestino)) {
            throw new SegurancaException("IP de Destino inválido (use IPv4, ex.: 10.0.0.1).");
        }
    }

    private static String protocoloDoPacote(String raw) {
        String p = raw == null || raw.isBlank() ? "tcp" : raw.trim().toLowerCase(Locale.ROOT);
        if (!p.equals("tcp") && !p.equals("udp") && !p.equals("icmp")) {
            throw new SegurancaException("Protocolo do pacote inválido: use tcp, udp ou icmp.");
        }
        return p;
    }

    private static int lerPortaPacote(String raw, int padrao, String qual) {
        if ((raw == null || raw.isBlank()) && padrao > 0) {
            return padrao;
        }
        int porta;
        try {
            porta = org.framework.net.shared.NumeroAscii.parseIntEstrito(raw == null ? "" : raw.trim());
        } catch (NumberFormatException ex) {
            throw new SegurancaException("Porta de " + qual + " inválida (informe um número).");
        }
        if (porta <= 0 || porta > 65535) {
            throw new SegurancaException("Porta de " + qual + " fora do intervalo (1–65535).");
        }
        return porta;
    }

    private static void exigir(String valor, String mensagem) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new SegurancaException(mensagem);
        }
    }

    private static boolean contemPerigoso(String valor) {
        return valor.indexOf('<') >= 0 || valor.indexOf('>') >= 0
                || valor.indexOf('"') >= 0 || valor.indexOf('\'') >= 0 || valor.indexOf('`') >= 0;
    }

    // ---- IPv4 tipado, contido nesta fatia (sem acoplar a outro módulo) ----

    private static boolean ipValido(String ip) {
        String[] octetos = ip.trim().split("\\.", -1);
        if (octetos.length != 4) {
            return false;
        }
        for (String parte : octetos) {
            if (parte.isEmpty() || parte.length() > 3 || !parte.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return false;
            }
            if (org.framework.net.shared.NumeroAscii.parseIntEstrito(parte) > 255) {
                return false;
            }
        }
        return true;
    }

    private static long ipParaLong(String ip) {
        String[] octetos = ip.trim().split("\\.", -1);
        long valor = 0L;
        for (String parte : octetos) {
            valor = (valor << 8) | (org.framework.net.shared.NumeroAscii.parseIntEstrito(parte) & 0xFF);
        }
        return valor & 0xFFFFFFFFL;
    }

    /**
     * Endereço da regra: base + máscara-curinga. Bit 0 no wildcard exige igualdade;
     * bit 1 é "não importa". {@code host} → wildcard 0.0.0.0; {@code any} → 255.255.255.255.
     */
    private record EnderecoAcl(long base, long wildcard) {

        static final EnderecoAcl ANY = new EnderecoAcl(0L, 0xFFFFFFFFL);

        boolean casa(long ip) {
            return ((ip ^ base) & (~wildcard) & 0xFFFFFFFFL) == 0L;
        }
    }

    /** Critério de porta: eq, neq, lt, gt ou range (inclusivo nas duas pontas). */
    private record CriterioPorta(String op, int a, int b) {

        boolean casa(int porta) {
            return switch (op) {
                case "eq" -> porta == a;
                case "neq" -> porta != a;
                case "lt" -> porta < a;
                case "gt" -> porta > a;
                default -> porta >= a && porta <= b;
            };
        }

        String descrever() {
            return op.equals("range") ? "range " + a + " " + b : op + " " + a;
        }
    }

    /**
     * Uma linha de ACL já tipada. {@code portaOrigem}/{@code portaDestino} nulos = sem critério de porta;
     * {@code padrao} = ACL padrão (só origem).
     */
    private record RegraAcl(boolean permite, String protocolo, EnderecoAcl origem, CriterioPorta portaOrigem,
                            EnderecoAcl destino, CriterioPorta portaDestino, boolean padrao) {

        static RegraAcl parse(String texto) {
            String[] t = texto.trim().toLowerCase(Locale.ROOT).split("\\s+");
            int i = 0;
            Integer numero = null;
            if (i < t.length && t[i].equals("access-list")) {
                if (i + 1 >= t.length) {
                    throw new SegurancaException("Regra ACL não suportada: 'access-list' sem número.");
                }
                try {
                    numero = org.framework.net.shared.NumeroAscii.parseIntEstrito(t[i + 1]);
                } catch (NumberFormatException e) {
                    throw new SegurancaException("Regra ACL não suportada: número de access-list inválido '" + t[i + 1] + "'.");
                }
                i += 2;
            }
            if (i >= t.length || !(t[i].equals("permit") || t[i].equals("deny"))) {
                throw new SegurancaException("Regra ACL não suportada: comece com 'permit' ou 'deny'.");
            }
            boolean permite = t[i].equals("permit");
            i++;
            if (i >= t.length) {
                throw new SegurancaException("Regra ACL não suportada: falta o protocolo ou a origem.");
            }
            boolean numeroPadrao = numero != null && ((numero >= 1 && numero <= 99) || (numero >= 1300 && numero <= 1999));
            boolean numeroEstendido = numero != null && ((numero >= 100 && numero <= 199) || (numero >= 2000 && numero <= 2699));
            if (numero != null && !numeroPadrao && !numeroEstendido) {
                throw new SegurancaException("Número de access-list fora das faixas IPv4 (1–199, 1300–2699).");
            }
            int[] cursor = {i};
            if (numeroPadrao || (!numeroEstendido && ehInicioEndereco(t[i]))) {
                // ACL padrão: só a origem. Palavra de protocolo aqui é erro de quem confundiu os tipos.
                if (ehProtocolo(t[i])) {
                    throw new SegurancaException("ACL padrão (1–99, 1300–1999) não tem protocolo nem destino: "
                            + "use 'access-list " + numero + " permit <origem> [wildcard]'.");
                }
                EnderecoAcl origem = lerEndereco(t, cursor, "origem");
                exigirFim(t, cursor);
                return new RegraAcl(permite, "ip", origem, null, EnderecoAcl.ANY, null, true);
            }
            if (!ehProtocolo(t[i])) {
                throw new SegurancaException("Protocolo não suportado: use ip, tcp, udp ou icmp.");
            }
            String protocolo = t[i];
            boolean portaPermitida = protocolo.equals("tcp") || protocolo.equals("udp");
            cursor[0] = i + 1;
            EnderecoAcl origem = lerEndereco(t, cursor, "origem");
            CriterioPorta portaOrigem = lerCriterioPorta(t, cursor, portaPermitida, "origem");
            if (cursor[0] >= t.length) {
                throw new SegurancaException("ACL estendida exige o destino depois da origem "
                        + "(ex.: 'permit tcp any any eq 80'). Sem protocolo e destino, use uma ACL padrão (1–99).");
            }
            EnderecoAcl destino = lerEndereco(t, cursor, "destino");
            CriterioPorta portaDestino = lerCriterioPorta(t, cursor, portaPermitida, "destino");
            exigirFim(t, cursor);
            return new RegraAcl(permite, protocolo, origem, portaOrigem, destino, portaDestino, false);
        }

        private static void exigirFim(String[] t, int[] cursor) {
            if (cursor[0] < t.length) {
                throw new SegurancaException("Regra ACL não suportada: trecho não reconhecido '" + t[cursor[0]] + "'.");
            }
        }

        private static boolean ehProtocolo(String s) {
            return s.equals("ip") || s.equals("tcp") || s.equals("udp") || s.equals("icmp");
        }

        private static boolean ehInicioEndereco(String s) {
            return s.equals("any") || s.equals("host") || ipValido(s);
        }

        private static boolean ehOperador(String s) {
            return s.equals("eq") || s.equals("neq") || s.equals("lt") || s.equals("gt") || s.equals("range");
        }

        private static CriterioPorta lerCriterioPorta(String[] t, int[] cursor, boolean portaPermitida, String qual) {
            int i = cursor[0];
            if (i >= t.length || !ehOperador(t[i])) {
                return null;
            }
            if (!portaPermitida) {
                throw new SegurancaException("Operador de porta '" + t[i] + "' só vale para tcp/udp.");
            }
            String op = t[i];
            if (op.equals("range")) {
                if (i + 2 >= t.length) {
                    throw new SegurancaException("Regra ACL não suportada: 'range' precisa de duas portas.");
                }
                int a = lerPorta(t[i + 1]);
                int b = lerPorta(t[i + 2]);
                if (a > b) {
                    throw new SegurancaException("Regra ACL não suportada: 'range " + a + " " + b + "' com início maior que o fim.");
                }
                cursor[0] = i + 3;
                return new CriterioPorta(op, a, b);
            }
            if (i + 1 >= t.length) {
                throw new SegurancaException("Regra ACL não suportada: '" + op + "' sem número de porta (" + qual + ").");
            }
            int a = lerPorta(t[i + 1]);
            cursor[0] = i + 2;
            return new CriterioPorta(op, a, a);
        }

        private static EnderecoAcl lerEndereco(String[] t, int[] cursor, String qual) {
            int i = cursor[0];
            if (i >= t.length) {
                throw new SegurancaException("Regra ACL não suportada: falta o endereço de " + qual + ".");
            }
            if (t[i].equals("any")) {
                cursor[0] = i + 1;
                return EnderecoAcl.ANY;
            }
            if (t[i].equals("host")) {
                if (i + 1 >= t.length || !ipValido(t[i + 1])) {
                    throw new SegurancaException("Regra ACL não suportada: 'host' sem IPv4 válido.");
                }
                cursor[0] = i + 2;
                return new EnderecoAcl(ipParaLong(t[i + 1]), 0L);
            }
            if (ipValido(t[i])) {
                // <ip> <wildcard>: dois IPv4 seguidos = endereço + máscara-curinga.
                if (i + 1 < t.length && ipValido(t[i + 1])) {
                    cursor[0] = i + 2;
                    return new EnderecoAcl(ipParaLong(t[i]), ipParaLong(t[i + 1]));
                }
                cursor[0] = i + 1;
                return new EnderecoAcl(ipParaLong(t[i]), 0L);
            }
            throw new SegurancaException("Regra ACL não suportada: endereço de " + qual
                    + " inválido '" + t[i] + "'.");
        }

        private static int lerPorta(String s) {
            int p;
            try {
                p = org.framework.net.shared.NumeroAscii.parseIntEstrito(s);
            } catch (NumberFormatException e) {
                throw new SegurancaException("Porta da regra inválida: '" + s + "'.");
            }
            if (p < 0 || p > 65535) {
                throw new SegurancaException("Porta da regra fora do intervalo (0–65535).");
            }
            return p;
        }
    }
}
