package org.framework.net.analiseTrafego.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseTrafego.domain.model.PacoteConstruido;
import org.framework.net.telemetria.TelemetriaLogger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Construtor didático de pacotes: monta os bytes reais de um quadro
 * Ethernet II / IPv4 / (TCP|UDP) a partir dos campos que o aluno edita.
 *
 * <p><b>Propósito de negócio:</b> fechar o par com o decodificador — o aluno
 * edita IPs, portas, flags, TTL, sequência, janela e mensagem, recebe os bytes
 * em hex e os abre no decodificador, vendo o MESMO pacote decomposto. É modelo
 * simulado: não abre socket nem envia nada à rede.</p>
 *
 * <p><b>Invariantes do domínio:</b> os comprimentos declarados batem com os
 * bytes reais (Total Length do IPv4 = 20 + L4 + dados; Length do UDP = 8 +
 * dados) e os checksums (cabeçalho IPv4, e TCP/UDP com pseudo-cabeçalho) são
 * calculados corretamente pelo algoritmo do complemento de um. O modo inválido
 * é EXPLÍCITO e separado: quando pedido, corrompe os checksums de propósito e
 * diz que fez isso — nunca entrega um pacote inválido disfarçado de válido.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> campo inválido devolve
 * {@link PacoteConstruido} com {@code ok=false} e a mensagem — não lança, no
 * mesmo padrão do decodificador desta fatia.</p>
 */
@ApplicationScoped
public class ConstrutorPacoteService {

    private static final int MAX_MENSAGEM = 512;
    // MACs de demonstração fixos (o formulário edita das camadas 3/4 para cima).
    private static final byte[] MAC_ORIGEM = mac(0x02, 0x00, 0x00, 0x00, 0x00, 0x01);
    private static final byte[] MAC_DESTINO = mac(0x02, 0x00, 0x00, 0x00, 0x00, 0x02);

    @Inject
    TelemetriaLogger telemetriaLogger;

    public PacoteConstruido montar(String protocolo, String ipOrigem, String ipDestino,
                                   String portaOrigemRaw, String portaDestinoRaw, String flagsCsv,
                                   String ttlRaw, String seqRaw, String windowRaw, String mensagem,
                                   boolean checksumValido) {
        return telemetriaLogger.medir("analiseTrafego", "construir_pacote", () -> {
            String proto = protocolo == null ? "" : protocolo.trim().toLowerCase(Locale.ROOT);
            if (!proto.equals("tcp") && !proto.equals("udp")) {
                return PacoteConstruido.erro("Protocolo não suportado: escolha tcp ou udp.");
            }
            long ipO = ipParaLong(ipOrigem);
            long ipD = ipParaLong(ipDestino);
            if (ipO < 0) {
                return PacoteConstruido.erro("IP de origem inválido (use IPv4, ex.: 192.168.0.10).");
            }
            if (ipD < 0) {
                return PacoteConstruido.erro("IP de destino inválido (use IPv4, ex.: 203.0.113.5).");
            }
            int portaO = intFaixa(portaOrigemRaw, 0, 65535);
            int portaD = intFaixa(portaDestinoRaw, 0, 65535);
            if (portaO < 0 || portaD < 0) {
                return PacoteConstruido.erro("Porta inválida (informe um número de 0 a 65535).");
            }
            int ttl = intFaixa(ttlRaw, 0, 255);
            if (ttl < 0) {
                return PacoteConstruido.erro("TTL inválido (0 a 255).");
            }
            String msg = mensagem == null ? "" : mensagem;
            if (msg.length() > MAX_MENSAGEM) {
                return PacoteConstruido.erro("Mensagem muito longa (máximo " + MAX_MENSAGEM + " caracteres).");
            }
            byte[] payload = msg.getBytes(StandardCharsets.UTF_8);

            byte[] l4;
            List<String> camadas = new ArrayList<>();
            camadas.add("Ethernet II — 14 bytes (MACs de demonstração)");
            camadas.add("IPv4 — 20 bytes");
            if (proto.equals("tcp")) {
                long seq = longFaixa(seqRaw, 0L, 0xFFFFFFFFL);
                int window = intFaixa(windowRaw, 0, 65535);
                if (seq < 0) {
                    return PacoteConstruido.erro("Sequência inválida (0 a 4294967295).");
                }
                if (window < 0) {
                    return PacoteConstruido.erro("Janela inválida (0 a 65535).");
                }
                int flags = flagsTcp(flagsCsv);
                l4 = montarTcp(portaO, portaD, seq, window, flags, ipO, ipD, payload, checksumValido);
                camadas.add("TCP — 20 bytes (flags " + rotuloFlags(flags) + ")");
            } else {
                l4 = montarUdp(portaO, portaD, ipO, ipD, payload, checksumValido);
                camadas.add("UDP — 8 bytes");
            }
            if (payload.length > 0) {
                camadas.add("Dados — " + payload.length + " bytes");
            }

            byte[] ip = montarIpv4(ipO, ipD, ttl, proto.equals("tcp") ? 6 : 17, l4.length + payload.length,
                    checksumValido);
            byte[] eth = montarEthernet();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(eth);
            out.writeBytes(ip);
            out.writeBytes(l4);
            out.writeBytes(payload);
            byte[] quadro = out.toByteArray();

            telemetriaLogger.logEvent("info", "analiseTrafego", "pacote_construido", Map.of(
                    "protocolo", proto,
                    "bytes", quadro.length,
                    "checksumValido", checksumValido));

            return new PacoteConstruido(true, "", proto.toUpperCase(Locale.ROOT), hex(quadro),
                    quadro.length, checksumValido, camadas);
        });
    }

    // ------------------------------------------------------------------ camadas

    private static byte[] montarEthernet() {
        byte[] b = new byte[14];
        System.arraycopy(MAC_DESTINO, 0, b, 0, 6);
        System.arraycopy(MAC_ORIGEM, 0, b, 6, 6);
        b[12] = 0x08; // EtherType 0x0800 = IPv4
        b[13] = 0x00;
        return b;
    }

    private static byte[] montarIpv4(long ipO, long ipD, int ttl, int proto, int payloadL4, boolean checksumValido) {
        byte[] b = new byte[20];
        b[0] = 0x45;                         // versão 4, IHL 5
        b[1] = 0x00;                         // DSCP/ECN
        put16(b, 2, 20 + payloadL4);         // total length = cabeçalho + L4 + dados
        put16(b, 4, 0x0000);                 // identification
        put16(b, 6, 0x4000);                 // flags = DF, fragment offset 0
        b[8] = (byte) ttl;
        b[9] = (byte) proto;
        put16(b, 10, 0);                     // checksum zerado para o cálculo
        put32(b, 12, ipO);
        put32(b, 16, ipD);
        int ck = checksum(b, 0, 20);
        put16(b, 10, checksumValido ? ck : corromper(ck));
        return b;
    }

    private static byte[] montarTcp(int portaO, int portaD, long seq, int window, int flags,
                                    long ipO, long ipD, byte[] payload, boolean checksumValido) {
        byte[] b = new byte[20];
        put16(b, 0, portaO);
        put16(b, 2, portaD);
        put32(b, 4, seq);
        put32(b, 8, 0);              // ack number
        b[12] = 0x50;               // data offset 5 (20 bytes), reservado 0
        b[13] = (byte) flags;
        put16(b, 14, window);
        put16(b, 16, 0);             // checksum zerado
        put16(b, 18, 0);             // urgent pointer
        int ck = checksumL4(ipO, ipD, 6, b, payload);
        put16(b, 16, checksumValido ? ck : corromper(ck));
        return b;
    }

    private static byte[] montarUdp(int portaO, int portaD, long ipO, long ipD, byte[] payload,
                                    boolean checksumValido) {
        byte[] b = new byte[8];
        put16(b, 0, portaO);
        put16(b, 2, portaD);
        put16(b, 4, 8 + payload.length); // length = cabeçalho + dados
        put16(b, 6, 0);                  // checksum zerado
        int ck = checksumL4(ipO, ipD, 17, b, payload);
        // RFC 768: 0x0000 no campo significa "sem checksum"; um cálculo que dá zero é transmitido
        // como 0xFFFF (o mesmo zero em complemento de um).
        int transmitido = ck == 0 ? 0xFFFF : ck;
        put16(b, 6, checksumValido ? transmitido : corromper(transmitido));
        return b;
    }

    // ------------------------------------------------------------------ checksum

    /** Checksum da Internet (RFC 1071): soma em 16 bits com vai-um e complemento de um. */
    private static int checksum(byte[] data, int off, int len) {
        long soma = 0;
        int i = off;
        while (i + 1 < off + len) {
            soma += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            i += 2;
        }
        if ((len & 1) != 0) {
            soma += (data[off + len - 1] & 0xFF) << 8; // byte ímpar completado com zero
        }
        while ((soma >> 16) != 0) {
            soma = (soma & 0xFFFF) + (soma >> 16);
        }
        return (int) (~soma & 0xFFFF);
    }

    /** Checksum de TCP/UDP: pseudo-cabeçalho (origem, destino, proto, tamanho) + segmento + dados. */
    private static int checksumL4(long ipO, long ipD, int proto, byte[] cabecalho, byte[] payload) {
        int tamL4 = cabecalho.length + payload.length;
        byte[] buf = new byte[12 + tamL4 + (tamL4 & 1)];
        put32(buf, 0, ipO);
        put32(buf, 4, ipD);
        buf[8] = 0;
        buf[9] = (byte) proto;
        put16(buf, 10, tamL4);
        System.arraycopy(cabecalho, 0, buf, 12, cabecalho.length);
        System.arraycopy(payload, 0, buf, 12 + cabecalho.length, payload.length);
        return checksum(buf, 0, buf.length);
    }

    /** Valor deliberadamente errado para o modo inválido — sempre diferente do correto. */
    /**
     * PROPÓSITO: gera um checksum deliberadamente ERRADO para o modo didático "inválido".
     * INVARIANTES: o resultado nunca é equivalente ao correto em complemento de um (0x0000 e 0xFFFF
     * são o mesmo zero — a inversão de bits antiga transformava um no outro e o pacote "errado"
     * passava na verificação) e nunca é 0x0000 (no UDP significaria "sem checksum").
     * FALHA: não lança.
     */
    private static int corromper(int checksumCorreto) {
        int r = (checksumCorreto ^ 0x5555) & 0xFFFF;
        return r == 0 ? 0x0001 : r;
    }

    // ------------------------------------------------------------------ flags TCP

    private static int flagsTcp(String csv) {
        if (csv == null || csv.isBlank()) {
            return 0;
        }
        int f = 0;
        for (String parte : csv.split(",")) {
            switch (parte.trim().toUpperCase(Locale.ROOT)) {
                case "FIN" -> f |= 0x01;
                case "SYN" -> f |= 0x02;
                case "RST" -> f |= 0x04;
                case "PSH" -> f |= 0x08;
                case "ACK" -> f |= 0x10;
                case "URG" -> f |= 0x20;
                default -> { /* rótulo desconhecido é ignorado */ }
            }
        }
        return f;
    }

    private static String rotuloFlags(int f) {
        List<String> on = new ArrayList<>();
        if ((f & 0x02) != 0) on.add("SYN");
        if ((f & 0x10) != 0) on.add("ACK");
        if ((f & 0x01) != 0) on.add("FIN");
        if ((f & 0x04) != 0) on.add("RST");
        if ((f & 0x08) != 0) on.add("PSH");
        if ((f & 0x20) != 0) on.add("URG");
        return on.isEmpty() ? "nenhuma" : String.join("/", on);
    }

    // ------------------------------------------------------------------ util bytes

    private static void put16(byte[] b, int off, int v) {
        b[off] = (byte) ((v >> 8) & 0xFF);
        b[off + 1] = (byte) (v & 0xFF);
    }

    private static void put32(byte[] b, int off, long v) {
        b[off] = (byte) ((v >> 24) & 0xFF);
        b[off + 1] = (byte) ((v >> 16) & 0xFF);
        b[off + 2] = (byte) ((v >> 8) & 0xFF);
        b[off + 3] = (byte) (v & 0xFF);
    }

    private static byte[] mac(int... o) {
        byte[] b = new byte[6];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) o[i];
        }
        return b;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) {
            sb.append(String.format("%02x", value & 0xFF));
        }
        return sb.toString();
    }

    /** IPv4 em long (32 bits) ou -1 se inválido. */
    private static long ipParaLong(String ip) {
        if (ip == null) {
            return -1;
        }
        String[] o = ip.trim().split("\\.", -1);
        if (o.length != 4) {
            return -1;
        }
        long v = 0;
        for (String parte : o) {
            int n;
            try {
                n = org.framework.net.shared.NumeroAscii.parseIntEstrito(parte);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (n < 0 || n > 255) {
                return -1;
            }
            v = (v << 8) | n;
        }
        return v & 0xFFFFFFFFL;
    }

    private static int intFaixa(String raw, int min, int max) {
        try {
            int v = org.framework.net.shared.NumeroAscii.parseIntEstrito(raw == null ? "" : raw.trim());
            return (v < min || v > max) ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long longFaixa(String raw, long min, long max) {
        try {
            long v = org.framework.net.shared.NumeroAscii.parseLongEstrito(raw == null ? "" : raw.trim());
            return (v < min || v > max) ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
