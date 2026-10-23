package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseTrafego.application.ConstrutorPacoteService;
import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.PacoteConstruido;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao.Camada;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao.Campo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P02 — construtor de pacotes.
 *
 * <p><b>Propósito de negócio:</b> travar as duas garantias que o parecer exige:
 * ida e volta (o pacote montado, decodificado, tem os mesmos campos) e checksum
 * correto (recomputado de forma independente, dá zero). É o que impede o
 * construtor de gerar bytes bonitos porém inválidos.</p>
 */
@QuarkusTest
@DisplayName("Tráfego: construtor de pacotes (P02)")
class ConstrutorPacoteServiceTest {

    @Inject
    ConstrutorPacoteService construtor;

    @Inject
    TrafegoDecoderService decoder;

    @Test
    @DisplayName("TCP: build → decode preserva IPs, portas, seq, janela, flags e TTL")
    void tcpRoundTripPreservaCampos() {
        PacoteConstruido p = construtor.montar("tcp", "192.168.0.10", "203.0.113.5",
                "51000", "443", "SYN", "64", "1000", "64240", "", true);
        assertTrue(p.ok(), p.erro());

        ResultadoDecodificacao r = decoder.decodificar(p.hex(), "ethernet");
        assertTrue(r.ok(), r.mensagem());

        assertEquals("192.168.0.10", valor(r, "IPv4", "IP origem"));
        assertEquals("203.0.113.5", valor(r, "IPv4", "IP destino"));
        assertEquals("64", valor(r, "IPv4", "TTL"));
        assertTrue(valor(r, "IPv4", "Protocolo").contains("TCP"));
        assertTrue(valor(r, "TCP", "Porta origem").startsWith("51000"));
        assertTrue(valor(r, "TCP", "Porta destino").startsWith("443"));
        assertEquals("1000", valor(r, "TCP", "Sequence number"));
        assertEquals("64240", valor(r, "TCP", "Window"));
        assertTrue(valor(r, "TCP", "Flags").contains("SYN"));
    }

    /**
     * F43: RFC 768 — checksum UDP calculado 0x0000 é transmitido como 0xFFFF, porque 0 no campo
     * significa "sem checksum". Caso achado por força bruta e conferido no oráculo Python RFC 1071
     * (192.168.1.1→8.8.8.8, 59888→53, "hello!" soma 0x0000). O "inválido" nunca pode sair 0x0000
     * (seria lido como "sem checksum", não como errado).
     */
    @Test
    @DisplayName("UDP: checksum calculado 0 sai como 0xFFFF (RFC 768)")
    void udpChecksumZeroViraFfff() {
        PacoteConstruido ok = construtor.montar("udp", "192.168.1.1", "8.8.8.8",
                "59888", "53", "", "64", "0", "0", "hello!", true);
        assertTrue(ok.ok(), ok.erro());
        assertEquals("ffff", ok.hex().substring(80, 84));

        PacoteConstruido errado = construtor.montar("udp", "192.168.1.1", "8.8.8.8",
                "59888", "53", "", "64", "0", "0", "hello!", false);
        assertNotEquals("ffff", errado.hex().substring(80, 84));
        assertNotEquals("0000", errado.hex().substring(80, 84));
    }

    /** F15: "10.0.0.1." (ponto sobrando) é IPv4 malformado; "10.0.0.1" é o controle legítimo (A1). */
    @Test
    @DisplayName("IP com ponto sobrando é recusado")
    void ipComPontoSobrandoEhRecusado() {
        assertFalse(construtor.montar("tcp", "10.0.0.1.", "10.0.0.2",
                "1000", "80", "SYN", "64", "0", "1024", "", true).ok());
        assertFalse(construtor.montar("tcp", "10.0.0.1", "10.0.0.2.",
                "1000", "80", "SYN", "64", "0", "1024", "", true).ok());
        assertTrue(construtor.montar("tcp", "10.0.0.1", "10.0.0.2",
                "1000", "80", "SYN", "64", "0", "1024", "", true).ok());
    }

    @Test
    @DisplayName("checksums do IPv4 e do TCP recomputam para zero (corretos)")
    void checksumsCorretos() {
        PacoteConstruido p = construtor.montar("tcp", "192.168.0.10", "203.0.113.5",
                "51000", "443", "SYN,ACK", "64", "1000", "64240", "oi", true);
        assertTrue(p.ok(), p.erro());
        byte[] b = hexToBytes(p.hex());

        // Cabeçalho IPv4 = bytes [14, 34). Recomputar (incluindo o campo checksum) dá 0 se correto.
        assertEquals(0, checksum(fatia(b, 14, 20)), "checksum do cabeçalho IPv4 deveria ser correto");

        // Checksum TCP: pseudo-cabeçalho + segmento TCP + dados. Recomputa 0 se correto.
        int tcpLen = b.length - 34;
        byte[] pseudo = new byte[12 + tcpLen + (tcpLen & 1)];
        System.arraycopy(b, 26, pseudo, 0, 4);  // IP origem
        System.arraycopy(b, 30, pseudo, 4, 4);  // IP destino
        pseudo[9] = 6;                           // protocolo TCP
        pseudo[10] = (byte) ((tcpLen >> 8) & 0xFF);
        pseudo[11] = (byte) (tcpLen & 0xFF);
        System.arraycopy(b, 34, pseudo, 12, tcpLen);
        assertEquals(0, checksum(pseudo), "checksum do TCP (com pseudo-cabeçalho) deveria ser correto");
    }

    @Test
    @DisplayName("comprimentos declarados batem com os bytes reais")
    void comprimentosBatem() {
        PacoteConstruido p = construtor.montar("udp", "10.0.0.1", "10.0.0.2",
                "5000", "53", "", "64", "0", "0", "abcd", true);
        assertTrue(p.ok(), p.erro());
        byte[] b = hexToBytes(p.hex());
        assertEquals(b.length, p.totalBytes());

        int totalIp = ((b[16] & 0xFF) << 8) | (b[17] & 0xFF);   // Total Length do IPv4
        assertEquals(20 + 8 + 4, totalIp, "IPv4 total length = 20 (IP) + 8 (UDP) + 4 (dados)");
        int udpLen = ((b[38] & 0xFF) << 8) | (b[39] & 0xFF);    // Length do UDP (offset 14+20+4)
        assertEquals(8 + 4, udpLen, "UDP length = 8 (cabeçalho) + 4 (dados)");
    }

    @Test
    @DisplayName("modo inválido corrompe o checksum de propósito e se declara")
    void modoInvalidoCorrompeChecksum() {
        PacoteConstruido bom = construtor.montar("udp", "10.0.0.1", "10.0.0.2",
                "5000", "53", "", "64", "0", "0", "q", true);
        PacoteConstruido ruim = construtor.montar("udp", "10.0.0.1", "10.0.0.2",
                "5000", "53", "", "64", "0", "0", "q", false);
        assertTrue(bom.ok() && ruim.ok());
        assertNotEquals(bom.hex(), ruim.hex(), "modo inválido deve produzir bytes diferentes");
        assertFalse(ruim.checksumValido(), "o resultado precisa declarar que o checksum é inválido");
    }

    @Test
    @DisplayName("protocolo não suportado não lança — devolve erro amigável")
    void protocoloInvalidoNaoLanca() {
        PacoteConstruido p = construtor.montar("icmp", "1.1.1.1", "2.2.2.2",
                "0", "0", "", "64", "0", "0", "", true);
        assertFalse(p.ok());
        assertTrue(p.erro().contains("Protocolo"));
    }

    // ---- helpers ----

    private static String valor(ResultadoDecodificacao r, String camada, String campo) {
        for (Camada c : r.camadas()) {
            if (c.nome().equals(camada)) {
                for (Campo f : c.campos()) {
                    if (f.nome().equals(campo)) {
                        return f.valor();
                    }
                }
            }
        }
        throw new AssertionError("campo não encontrado: " + camada + "/" + campo);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] fatia(byte[] b, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(b, off, r, 0, len);
        return r;
    }

    /** Mesmo checksum da Internet — se os bytes já trazem o checksum correto, recomputa 0. */
    private static int checksum(byte[] data) {
        long soma = 0;
        for (int i = 0; i + 1 < data.length; i += 2) {
            soma += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        if ((data.length & 1) != 0) {
            soma += (data[data.length - 1] & 0xFF) << 8;
        }
        while ((soma >> 16) != 0) {
            soma = (soma & 0xFFFF) + (soma >> 16);
        }
        return (int) (~soma & 0xFFFF);
    }
}
