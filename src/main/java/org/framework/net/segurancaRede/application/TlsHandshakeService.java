package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.domain.SimulacaoHandshake;
import org.framework.net.segurancaRede.domain.SimulacaoHandshake.PassoHandshake;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simulador didático do handshake TLS 1.3, passo a passo.
 *
 * <p><b>Propósito de negócio:</b> completar a página de aprofundamento do TLS com
 * um passo a passo interativo — o aluno escolhe um cenário e avança as mensagens,
 * vendo onde as chaves passam a valer e onde uma incompatibilidade derruba a
 * conexão. Complementa o inspetor de certificado (que julga atributos) mostrando
 * a NEGOCIAÇÃO em si.</p>
 *
 * <p><b>Invariantes do domínio:</b> determinístico por cenário; o passo de falha
 * é único e explícito, com o alerta TLS correspondente, e não há passos depois
 * dele. Distingue cipher suite, troca de chaves (ECDHE) e assinatura
 * (CertificateVerify). Baseado na RFC 8446.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário fora do catálogo lança
 * {@link SegurancaException} (HTTP 400); nunca inventa um handshake.</p>
 */
@ApplicationScoped
public class TlsHandshakeService {

    private static final String CLIENTE = "CLIENTE";
    private static final String SERVIDOR = "SERVIDOR";
    private static final boolean CLARO = false;
    private static final boolean CIFRADO = true;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public List<Map<String, String>> cenariosDisponiveis() {
        List<Map<String, String>> lista = new ArrayList<>();
        for (Cenario c : Cenario.values()) {
            lista.add(Map.of("id", c.id, "titulo", c.titulo));
        }
        return lista;
    }

    public SimulacaoHandshake simular(String cenarioId) {
        return telemetriaLogger.medir("seguranca", "tls_handshake", () -> {
            Cenario cenario = Cenario.porId(cenarioId);
            List<PassoHandshake> passos = cenario.montar();
            boolean sucesso = passos.stream().noneMatch(PassoHandshake::falha);
            telemetriaLogger.logEvent("info", "seguranca", "tls_handshake_simulado", Map.of(
                    "cenario", cenario.id,
                    "sucesso", sucesso,
                    "passos", passos.size()));
            return new SimulacaoHandshake(cenario.id, cenario.titulo, sucesso, cenario.desfecho, passos);
        });
    }

    private enum Cenario {
        SUCESSO("sucesso", "Handshake completo (TLS 1.3, 1-RTT)",
                "Conexão estabelecida: cipher suite negociada, chaves ECDHE derivadas e servidor autenticado."),
        VERSAO("versao-incompativel", "Versão incompatível",
                "Abortado: cliente e servidor não têm versão em comum (alerta protocol_version)."),
        CIPHER("cipher-incompativel", "Cipher suite incompatível",
                "Abortado: nenhuma cipher suite em comum (alerta handshake_failure)."),
        CERTIFICADO("certificado-invalido", "Certificado recusado pelo cliente",
                "Abortado: o cliente recusa o certificado do servidor (alerta bad_certificate).");

        private final String id;
        private final String titulo;
        private final String desfecho;

        Cenario(String id, String titulo, String desfecho) {
            this.id = id;
            this.titulo = titulo;
            this.desfecho = desfecho;
        }

        List<PassoHandshake> montar() {
            List<PassoHandshake> p = new ArrayList<>();
            switch (this) {
                case SUCESSO -> {
                    p.add(passo(p, CLIENTE, "ClientHello", CLARO, false,
                            "Oferece versões (TLS 1.3), a lista de cipher suites, o key share ECDHE e o SNI. Tudo em claro."));
                    p.add(passo(p, SERVIDOR, "ServerHello", CLARO, false,
                            "Escolhe a versão e a CIPHER SUITE, e envia o próprio key share ECDHE (TROCA DE CHAVES). "
                                    + "A partir daqui deriva-se o segredo de sessão: o resto do handshake já é cifrado."));
                    p.add(passo(p, SERVIDOR, "EncryptedExtensions", CIFRADO, false,
                            "Parâmetros restantes da sessão, já sob as chaves de handshake."));
                    p.add(passo(p, SERVIDOR, "Certificate", CIFRADO, false,
                            "Cadeia de certificados do servidor (cifrada — no 1.3 o certificado não trafega em claro)."));
                    p.add(passo(p, SERVIDOR, "CertificateVerify", CIFRADO, false,
                            "ASSINATURA sobre o transcript do handshake, provando a posse da chave privada do certificado."));
                    p.add(passo(p, SERVIDOR, "Finished", CIFRADO, false,
                            "MAC que fecha e valida o handshake do lado do servidor."));
                    p.add(passo(p, CLIENTE, "Finished", CIFRADO, false,
                            "Cliente valida tudo e confirma — handshake completo."));
                    p.add(passo(p, CLIENTE, "Application Data", CIFRADO, false,
                            "Primeira requisição (ex.: HTTP GET) já cifrada — 1-RTT."));
                }
                case VERSAO -> {
                    p.add(passo(p, CLIENTE, "ClientHello", CLARO, false,
                            "Oferece apenas TLS 1.3 na extensão supported_versions."));
                    p.add(passo(p, SERVIDOR, "Alert: protocol_version", CLARO, true,
                            "O servidor só suporta até TLS 1.2 — não há versão em comum. Envia o alerta e ABORTA."));
                }
                case CIPHER -> {
                    p.add(passo(p, CLIENTE, "ClientHello", CLARO, false,
                            "Oferece um conjunto de cipher suites (ex.: só uma suíte antiga)."));
                    p.add(passo(p, SERVIDOR, "Alert: handshake_failure", CLARO, true,
                            "Nenhuma cipher suite oferecida é aceitável para o servidor. Envia o alerta e ABORTA."));
                }
                case CERTIFICADO -> {
                    p.add(passo(p, CLIENTE, "ClientHello", CLARO, false,
                            "Oferece versões, cipher suites e key share."));
                    p.add(passo(p, SERVIDOR, "ServerHello", CLARO, false,
                            "Escolhe versão e cipher suite; troca de chaves ECDHE. Passa a cifrar."));
                    p.add(passo(p, SERVIDOR, "Certificate", CIFRADO, false,
                            "Envia a cadeia de certificados."));
                    p.add(passo(p, SERVIDOR, "CertificateVerify", CIFRADO, false,
                            "Assina o transcript — a criptografia está correta, mas isso não garante a IDENTIDADE."));
                    p.add(passo(p, CLIENTE, "Alert: bad_certificate", CIFRADO, true,
                            "O cliente valida a cadeia (nome/SAN, validade, âncora de confiança) e RECUSA. "
                                    + "Aborta antes de enviar qualquer dado de aplicação."));
                }
            }
            return p;
        }

        private static PassoHandshake passo(List<PassoHandshake> jaAdicionados, String remetente,
                                            String mensagem, boolean cifrado, boolean falha, String descricao) {
            return new PassoHandshake(jaAdicionados.size() + 1, remetente, mensagem, cifrado, falha, descricao);
        }

        static Cenario porId(String id) {
            for (Cenario c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            throw new SegurancaException("Cenário de handshake TLS desconhecido: '" + id + "'.");
        }
    }
}
