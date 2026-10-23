package org.framework.net.simuladores.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento.Camada;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento.Campo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o encapsulamento didático de uma mensagem de aplicação sobre TCP/UDP → IPv4 → Ethernet II.
 * Computação pura (VPS-safe): tamanhos, números de protocolo e ethertype são reais; alguns campos
 * (seq/checksum/MAC) são valores ilustrativos, sempre rotulados.
 */
@ApplicationScoped
public class EncapsulamentoService {

    private static final int MAX_MSG = 512;
    private static final int TCP_HDR = 20;
    private static final int UDP_HDR = 8;
    private static final int IP_HDR = 20;
    private static final int ETH_HDR = 14;
    private static final int ETH_FCS = 4;
    private static final int ETH_MIN_PAYLOAD = 46;   // payload mínimo do quadro (padding)

    public ResultadoEncapsulamento encapsular(String mensagem, String transporte,
                                              String ipOrigem, String ipDestino,
                                              String portaOrigem, String portaDestino) {
        if (mensagem == null || mensagem.isBlank()) {
            return ResultadoEncapsulamento.erroDe("Informe a mensagem da aplicação.");
        }
        if (mensagem.length() > MAX_MSG) {
            return ResultadoEncapsulamento.erroDe("Mensagem muito longa (máx. " + MAX_MSG + " caracteres).");
        }
        String proto = "UDP".equalsIgnoreCase(transporte) ? "UDP" : "TCP";
        String ipSrc = validarIpv4(ipOrigem, "192.168.0.10");
        String ipDst = validarIpv4(ipDestino, "142.250.79.14");
        int spNum = validarPorta(portaOrigem, 51000);
        int dpNum = validarPorta(portaDestino, 80);
        if (ipSrc == null) {
            return ResultadoEncapsulamento.erroDe("IP de origem inválido (use IPv4, ex.: 192.168.0.10).");
        }
        if (ipDst == null) {
            return ResultadoEncapsulamento.erroDe("IP de destino inválido (use IPv4, ex.: 142.250.79.14).");
        }
        if (spNum < 0 || dpNum < 0) {
            return ResultadoEncapsulamento.erroDe("Porta inválida (0 a 65535).");
        }

        int appPayload = mensagem.getBytes(StandardCharsets.UTF_8).length;
        int transHdr = proto.equals("TCP") ? TCP_HDR : UDP_HDR;
        int transTotal = transHdr + appPayload;
        int ipTotal = IP_HDR + transTotal;
        int quadroPayload = Math.max(ipTotal, ETH_MIN_PAYLOAD);
        int quadro = ETH_HDR + quadroPayload + ETH_FCS;

        List<Camada> camadas = new ArrayList<>();

        // ---- Nível 7: Aplicação ----
        String appProto = protocoloAplicacao(dpNum);
        camadas.add(new Camada(7, "Aplicação", appProto, "Dados", "apps",
                0, appPayload, appPayload,
                List.of(
                        Campo.calculado("Protocolo", appProto, "Deduzido pela porta de destino " + dpNum + "."),
                        Campo.calculado("Payload", appPayload + " bytes", "A mensagem em si, ainda sem cabeçalhos de rede."),
                        Campo.fornecido("Mensagem", resumir(mensagem), "Conteúdo textual gerado pela aplicação.")),
                hexTexto(mensagem)));

        // ---- Nível 4: Transporte ----
        if (proto.equals("TCP")) {
            camadas.add(new Camada(4, "Transporte", "TCP", "Segmento", "swap_horiz",
                    TCP_HDR, appPayload, transTotal,
                    List.of(
                            Campo.fornecido("Porta origem", String.valueOf(spNum), "Porta efêmera do cliente."),
                            Campo.fornecido("Porta destino", String.valueOf(dpNum), "Identifica o serviço no servidor."),
                            new Campo("Nº sequência", "0x0000000A", "Controle de ordem/confiabilidade (exemplo)."),
                            new Campo("Nº ACK", "0x00000001", "Confirmação do próximo byte esperado (exemplo)."),
                            // Segmento com dados vai numa conexão já aberta: ACK + PSH. SYN só abre a conexão
                            // e não leva a requisição (auditoria CALC-37).
                            new Campo("Flags", "ACK, PSH",
                                    "Conexão já aberta (o SYN do handshake veio antes, sem dados); PSH entrega já à aplicação."),
                            new Campo("Janela", "64240", "Controle de fluxo (bytes que o receptor aceita)."),
                            new Campo("Checksum", "não calculado aqui", "Valor real: use o Construtor de pacotes."),
                            Campo.calculado("Cabeçalho", TCP_HDR + " bytes", "Header TCP mínimo (sem opções).")),
                    "?? ?? " + hex16(spNum) + " " + hex16(dpNum)));
        } else {
            camadas.add(new Camada(4, "Transporte", "UDP", "Datagrama", "swap_horiz",
                    UDP_HDR, appPayload, transTotal,
                    List.of(
                            Campo.fornecido("Porta origem", String.valueOf(spNum), "Porta efêmera do cliente."),
                            Campo.fornecido("Porta destino", String.valueOf(dpNum), "Identifica o serviço no servidor."),
                            Campo.calculado("Comprimento", transTotal + " bytes", "Header (8) + dados."),
                            new Campo("Checksum", "não calculado aqui", "Valor real: use o Construtor de pacotes."),
                            Campo.calculado("Cabeçalho", UDP_HDR + " bytes", "Header UDP é enxuto: sem controle de conexão.")),
                    hex16(spNum) + " " + hex16(dpNum) + " " + hex16(transTotal) + " ????"));
        }

        // ---- Nível 3: Rede ----
        int protoNum = proto.equals("TCP") ? 6 : 17;
        camadas.add(new Camada(3, "Rede", "IPv4", "Pacote", "hub",
                IP_HDR, transTotal, ipTotal,
                List.of(
                        Campo.calculado("Versão / IHL", "4 / 5", "IPv4, cabeçalho de 5×4 = 20 bytes."),
                        Campo.calculado("Comprimento total", ipTotal + " bytes", "Header IP + segmento/datagrama."),
                        new Campo("TTL", "64", "Saltos máximos antes de descartar o pacote."),
                        Campo.calculado("Protocolo", protoNum + " (" + proto + ")", "Aponta a camada de transporte (TCP=6, UDP=17)."),
                        Campo.fornecido("IP origem", ipSrc, "Endereço lógico do emissor."),
                        Campo.fornecido("IP destino", ipDst, "Endereço lógico do destinatário."),
                        new Campo("Checksum", "não calculado aqui", "Valor real: use o Construtor de pacotes.")),
                "45 00 " + hex16(ipTotal) + " .. .. .. " + hex8(protoNum)));

        // ---- Nível 2: Enlace ----
        List<Campo> ethCampos = new ArrayList<>(List.of(
                new Campo("MAC destino", "aa:bb:cc:00:11:22", "Endereço físico do próximo salto (ex.: gateway)."),
                new Campo("MAC origem", "de:ad:be:ef:00:01", "Endereço físico da placa emissora."),
                Campo.calculado("EtherType", "0x0800", "Indica que o payload é IPv4."),
                Campo.calculado("Payload", quadroPayload + " bytes", "Pacote IP" + (ipTotal < ETH_MIN_PAYLOAD
                        ? " + padding (mínimo de 46 bytes)" : "") + "."),
                new Campo("FCS", "CRC-32", "Frame Check Sequence: detecção de erro no quadro."),
                Campo.calculado("Quadro total", quadro + " bytes", "MAC dst+src+type (14) + payload + FCS (4).")));
        camadas.add(new Camada(2, "Enlace", "Ethernet II", "Quadro", "cable",
                ETH_HDR + ETH_FCS, quadroPayload, quadro, ethCampos,
                "aabbcc001122 deadbeef0001 0800"));

        return new ResultadoEncapsulamento(true, "", mensagem, proto, quadro, camadas);
    }

    // ------------------------------------------------------------------ helpers

    private static String protocoloAplicacao(int porta) {
        return switch (porta) {
            case 80 -> "HTTP";
            case 443 -> "HTTPS/TLS";
            case 53 -> "DNS";
            case 25, 587 -> "SMTP";
            case 21 -> "FTP";
            case 22 -> "SSH";
            case 23 -> "Telnet";
            case 110 -> "POP3";
            case 143 -> "IMAP";
            case 67, 68 -> "DHCP";
            default -> "Aplicação";
        };
    }

    private static String validarIpv4(String ip, String padrao) {
        if (ip == null || ip.isBlank()) {
            return padrao;
        }
        String s = ip.strip();
        String[] partes = s.split("\\.", -1);
        if (partes.length != 4) {
            return null;
        }
        for (String p : partes) {
            if (!p.matches("\\d{1,3}")) {
                return null;
            }
            int v = org.framework.net.shared.NumeroAscii.parseIntEstrito(p);
            if (v < 0 || v > 255) {
                return null;
            }
        }
        return s;
    }

    /** Retorna a porta válida, o padrão se vazio, ou -1 se inválida. */
    private static int validarPorta(String porta, int padrao) {
        if (porta == null || porta.isBlank()) {
            return padrao;
        }
        try {
            int v = org.framework.net.shared.NumeroAscii.parseIntEstrito(porta.strip());
            return (v >= 0 && v <= 65535) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String resumir(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= 60 ? t : t.substring(0, 57) + "…";
    }

    private static String hexTexto(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        int n = Math.min(b.length, 8);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x ", b[i] & 0xff));
        }
        if (b.length > n) {
            sb.append("…");
        }
        return sb.toString().strip();
    }

    private static String hex16(int v) {
        return String.format("%04x", v & 0xffff);
    }

    private static String hex8(int v) {
        return String.format("%02x", v & 0xff);
    }
}
