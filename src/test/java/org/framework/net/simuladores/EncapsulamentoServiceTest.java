package org.framework.net.simuladores;

import org.framework.net.simuladores.application.EncapsulamentoService;
import org.framework.net.simuladores.domain.ResultadoEncapsulamento;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncapsulamentoServiceTest {

    private final EncapsulamentoService service = new EncapsulamentoService();

    @Test
    void encapsulaTcpHttpEmQuatroCamadas() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET / HTTP/1.1", "TCP", "192.168.0.10", "142.250.79.14", "51000", "80");
        assertTrue(r.ok());
        assertEquals("TCP", r.transporte());
        assertEquals(4, r.camadas().size());
        // Ordem de encapsulamento: 7 → 4 → 3 → 2
        assertEquals(7, r.camadas().get(0).nivel());
        assertEquals(2, r.camadas().get(3).nivel());
        // App detectada como HTTP pela porta 80
        assertEquals("HTTP", r.camadas().get(0).protocolo());
        // O quadro é maior que a mensagem (cabeçalhos somados)
        int appBytes = "GET / HTTP/1.1".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(r.totalBytes() > appBytes);
        // CALC-37: o segmento que leva o GET vai numa conexão aberta (ACK/PSH), nunca com SYN.
        String flags = r.camadas().get(1).cabecalho().stream().filter(c -> c.nome().equals("Flags"))
                .findFirst().orElseThrow().valor();
        assertTrue(flags.contains("ACK") && !flags.contains("SYN"), flags);
    }

    @Test
    void udpUsaProtocolo17EDetectaDns() {
        ResultadoEncapsulamento r = service.encapsular(
                "example.com A?", "UDP", "192.168.0.10", "8.8.8.8", "51000", "53");
        assertTrue(r.ok());
        assertEquals("UDP", r.transporte());
        assertEquals("DNS", r.camadas().get(0).protocolo());
        boolean temProto17 = r.camadas().stream()
                .filter(c -> c.nivel() == 3)
                .flatMap(c -> c.cabecalho().stream())
                .anyMatch(campo -> campo.valor().contains("17"));
        assertTrue(temProto17, "IPv4 deve indicar protocolo 17 (UDP)");
    }

    @Test
    void quadroRespeitaPayloadMinimoEthernet() {
        // Mensagem curta → payload IP < 46 bytes → quadro mínimo (14 + 46 + 4 = 64)
        ResultadoEncapsulamento r = service.encapsular(
                "oi", "UDP", "192.168.0.10", "8.8.8.8", "51000", "53");
        assertTrue(r.ok());
        assertEquals(64, r.totalBytes(), "quadro Ethernet mínimo de 64 bytes com padding");
    }

    @Test
    void rejeitaMensagemVazia() {
        ResultadoEncapsulamento r = service.encapsular(
                "  ", "TCP", "192.168.0.10", "8.8.8.8", "51000", "80");
        assertFalse(r.ok());
    }

    @Test
    void rejeitaIpInvalido() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET /", "TCP", "999.999.0.1", "8.8.8.8", "51000", "80");
        assertFalse(r.ok());
    }

    @Test
    void rejeitaPortaInvalida() {
        ResultadoEncapsulamento r = service.encapsular(
                "GET /", "TCP", "192.168.0.10", "8.8.8.8", "51000", "70000");
        assertFalse(r.ok());
    }

    @Test
    void rotulaNaturezaDosCampos() {
        // M01: cada valor diz sua natureza — nada de exemplo se passando por calculado.
        ResultadoEncapsulamento r = service.encapsular(
                "GET / HTTP/1.1", "TCP", "192.168.0.10", "142.250.79.14", "51000", "80");
        assertTrue(r.ok());

        assertEquals("fornecido", campo(r, 3, "IP origem").natureza(), "o IP de origem veio do usuário");
        assertEquals("calculado", campo(r, 3, "Comprimento total").natureza(), "o comprimento é derivado dos tamanhos");
        assertEquals("ilustrativo", campo(r, 3, "Checksum").natureza(), "o checksum não é computado aqui");
        assertFalse(campo(r, 3, "Checksum").valor().equals("calculado"),
                "o VALOR do checksum não pode se apresentar como 'calculado' quando não é");
    }

    private static ResultadoEncapsulamento.Campo campo(ResultadoEncapsulamento r, int nivel, String nome) {
        return r.camadas().stream()
                .filter(c -> c.nivel() == nivel)
                .flatMap(c -> c.cabecalho().stream())
                .filter(f -> f.nome().equals(nome))
                .findFirst()
                .orElseThrow(() -> new AssertionError("campo não encontrado: nível " + nivel + " / " + nome));
    }

    /** F15: "10.0.0.1." é IPv4 malformado e não pode ser ecoado como válido; controle legítimo passa (A1). */
    @Test
    void ipComPontoSobrandoEhRecusado() {
        assertFalse(service.encapsular("oi", "TCP", "10.0.0.1.", "10.0.0.2", "1000", "80").ok());
        assertTrue(service.encapsular("oi", "TCP", "10.0.0.1", "10.0.0.2", "1000", "80").ok());
    }
}
