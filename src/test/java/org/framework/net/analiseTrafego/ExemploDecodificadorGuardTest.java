package org.framework.net.analiseTrafego;

import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda do pacote de exemplo do decodificador.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o exemplo pronto é o primeiro pacote que o aluno decodifica; ele tinha
 * os dois checksums errados e MAC de origem de GRUPO (bit I/G ligado), coisa que nenhuma placa envia
 * (auditoria CONT-35).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o exemplo do template e o do botão (trafego.js) são o mesmo pacote; o
 * MAC de origem é unicast; o checksum do cabeçalho IPv4 e o do TCP (com pseudo-cabeçalho) fecham pela
 * soma em complemento de um (RFC 1071), recalculada aqui — gabarito independente do decodificador.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> exemplo não encontrado em algum dos dois arquivos reprova
 * (alvo vazio não é aprovação).</p>
 */
class ExemploDecodificadorGuardTest {

    private static final Path JS = Path.of("src/main/resources/META-INF/resources/trafego/js/trafego.js");
    private static final Path TEMPLATE = Path.of("src/main/resources/templates/trafego/partials/tab_decodificador.html");

    @Test
    void exemploTemChecksumsCertosEMacUnicast() throws IOException {
        String doTemplate = exemploDoTemplate();
        String doJs = exemploDoJs();
        assertEquals(doTemplate, doJs, "o botão e o campo trazem pacotes diferentes");
        assertEquals(List.of(), defeitos(TrafegoDecoderService.normalizar(doTemplate)));
    }

    /** Calibração (A1): o exemplo antigo (checksums e MAC errados) reprova pelas três causas. */
    @Test
    void guardaReprovaOExemploAntigo() {
        String antigo = "aabbccddeeff1122334455660800"
                + "450000281c4640004006b1e6c0a80001c0a80002"
                + "d4310050000000000000000050027210e5770000";
        List<String> d = defeitos(antigo);
        assertTrue(d.stream().anyMatch(x -> x.startsWith("MAC")), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.startsWith("IPv4")), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.startsWith("TCP")), d.toString());
    }

    private static List<String> defeitos(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if ((b[6] & 1) != 0) {
            out.add("MAC de origem com bit de grupo");
        }
        int ip = 14;
        int ihl = (b[ip] & 0x0F) * 4;
        if (somaComplementoUm(b, ip, ihl, 0) != 0xFFFF) {
            out.add("IPv4: checksum do cabeçalho não fecha");
        }
        int total = ((b[ip + 2] & 0xFF) << 8) | (b[ip + 3] & 0xFF);
        int tcp = ip + ihl;
        int tcpLen = total - ihl;
        long pseudo = soma16(b, ip + 12, 8) + 6 + tcpLen;
        if (somaComplementoUm(b, tcp, tcpLen, pseudo) != 0xFFFF) {
            out.add("TCP: checksum não fecha");
        }
        return out;
    }

    private static long soma16(byte[] b, int off, int len) {
        long s = 0;
        for (int i = 0; i < len; i += 2) {
            s += ((b[off + i] & 0xFF) << 8) | (i + 1 < len ? (b[off + i + 1] & 0xFF) : 0);
        }
        return s;
    }

    private static int somaComplementoUm(byte[] b, int off, int len, long inicial) {
        long s = inicial + soma16(b, off, len);
        while ((s >> 16) != 0) {
            s = (s & 0xFFFF) + (s >> 16);
        }
        return (int) s;
    }

    private static String exemploDoTemplate() throws IOException {
        Matcher m = Pattern.compile("id=\"trafego-hex\"[^>]*>([0-9a-fA-F\\s]+)</textarea>").matcher(Files.readString(TEMPLATE));
        assertTrue(m.find(), "exemplo não encontrado no template");
        return m.group(1).replaceAll("\\s", "");
    }

    private static String exemploDoJs() throws IOException {
        Matcher m = Pattern.compile("var EXEMPLO =([^;]+);").matcher(Files.readString(JS));
        assertTrue(m.find(), "exemplo não encontrado no trafego.js");
        String hex = m.group(1).replaceAll("\\\\n", "").replaceAll("[^0-9a-fA-F]", "");
        assertFalse(hex.isEmpty());
        return hex;
    }
}
