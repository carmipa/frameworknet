package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.application.TlsHandshakeService;
import org.framework.net.segurancaRede.domain.SimulacaoHandshake;
import org.framework.net.segurancaRede.domain.SimulacaoHandshake.PassoHandshake;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P03 — handshake TLS 1.3 interativo.
 *
 * <p><b>Propósito de negócio:</b> travar o que a tela ensina: onde as chaves
 * passam a valer (claro → cifrado), a distinção entre cipher suite, troca de
 * chaves e assinatura, e o passo exato em que cada incompatibilidade aborta.</p>
 */
@QuarkusTest
@DisplayName("Segurança: handshake TLS 1.3 interativo (P03)")
class TlsHandshakeServiceTest {

    @Inject
    TlsHandshakeService servico;

    @Test
    @DisplayName("sucesso: distingue cipher suite, troca de chaves e assinatura; o cifrado começa após o ServerHello")
    void sucessoDistingueConceitosEMarcaOCifrado() {
        SimulacaoHandshake h = servico.simular("sucesso");
        assertTrue(h.sucesso());

        PassoHandshake clientHello = passo(h, "ClientHello");
        assertFalse(clientHello.cifrado(), "ClientHello viaja em claro");

        PassoHandshake serverHello = passo(h, "ServerHello");
        assertFalse(serverHello.cifrado(), "ServerHello ainda em claro");
        assertTrue(serverHello.descricao().contains("CIPHER SUITE"), "ServerHello escolhe a cipher suite");
        assertTrue(serverHello.descricao().contains("TROCA DE CHAVES"), "ServerHello faz a troca de chaves (ECDHE)");

        PassoHandshake certificate = passo(h, "Certificate");
        assertTrue(certificate.cifrado(), "no 1.3 o Certificate já vai cifrado");

        PassoHandshake verify = passo(h, "CertificateVerify");
        assertTrue(verify.descricao().contains("ASSINATURA"), "CertificateVerify é a assinatura");

        assertTrue(h.passos().get(h.passos().size() - 1).mensagem().contains("Application Data"),
                "o último passo é o dado de aplicação cifrado (1-RTT)");
    }

    @Test
    @DisplayName("versão incompatível: aborta no servidor com protocol_version, sem passos depois")
    void versaoIncompativelAborta() {
        SimulacaoHandshake h = servico.simular("versao-incompativel");
        assertFalse(h.sucesso());
        assertTrue(h.desfecho().contains("protocol_version"), h.desfecho());

        List<PassoHandshake> falhas = h.passos().stream().filter(PassoHandshake::falha).toList();
        assertEquals(1, falhas.size(), "um único passo de falha");
        // O passo de falha é o último — nada acontece depois do aborto.
        assertTrue(h.passos().get(h.passos().size() - 1).falha(), "a falha é o último passo");
    }

    @Test
    @DisplayName("certificado inválido: quem aborta é o CLIENTE, após receber Certificate e CertificateVerify")
    void certificadoInvalidoAbortaNoCliente() {
        SimulacaoHandshake h = servico.simular("certificado-invalido");
        assertFalse(h.sucesso());

        PassoHandshake falha = h.passos().stream().filter(PassoHandshake::falha).findFirst().orElseThrow();
        assertEquals("CLIENTE", falha.remetente(), "é o cliente que recusa o certificado");
        assertTrue(falha.mensagem().contains("bad_certificate"), falha.mensagem());
        // A negociação chegou a apresentar o certificado e a assinatura antes de o cliente recusar.
        assertTrue(h.passos().stream().anyMatch(p -> p.mensagem().contains("Certificate")));
        assertTrue(h.passos().stream().anyMatch(p -> p.mensagem().contains("CertificateVerify")));
    }

    @Test
    @DisplayName("cenário desconhecido é recusado")
    void cenarioDesconhecidoLanca() {
        assertThrows(SegurancaException.class, () -> servico.simular("nao-existe"));
    }

    @Test
    @DisplayName("determinístico")
    void deterministico() {
        assertEquals(servico.simular("sucesso"), servico.simular("sucesso"));
    }

    private static PassoHandshake passo(SimulacaoHandshake h, String mensagemContem) {
        return h.passos().stream()
                .filter(p -> p.mensagem().contains(mensagemContem))
                .findFirst()
                .orElseThrow(() -> new AssertionError("passo não encontrado: " + mensagemContem));
    }
}
