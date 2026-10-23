package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo didático e operacional da página de aprofundamento do TLS.
 *
 * <p><b>Propósito de negócio:</b> TLS é o cadeado do HTTPS e de quase todo
 * protocolo moderno (SMTPS, DoT, QUIC). A página cobre o handshake (com foco no
 * TLS 1.3), a cadeia de certificados que prova a identidade do servidor, as
 * cipher suites e o ponto que confunde: criptografia legítima também esconde
 * tráfego malicioso — o cadeado prova o CANAL, não a INTENÇÃO. O conteúdo vive em
 * {@code src/main/resources/protocolos/tls/conteudo.json}.</p>
 *
 * <p><b>Invariantes do domínio:</b> record exclusivo do TLS, sem parentesco com
 * os demais aprofundamentos — unificar estruturas de conteúdo criaria acoplamento
 * que impede a divergência. As listas nunca são nulas: JSON incompleto reprova na
 * subida (ver {@link TlsAprofundamentoCatalog}).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — é um record de dados. A
 * validação de completude pertence a {@link TlsAprofundamentoCatalog}.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TlsAprofundamento(
        String resumo,
        List<Conceito> conceitos,
        List<PassoHandshake> handshake,
        List<EloCadeia> cadeia,
        List<CipherSuite> ciphers,
        List<Ataque> ataques,
        List<Mitigacao> mitigacoes,
        List<Laboratorio> laboratorios,
        List<Diagnostico> diagnosticos,
        DiagramaArquitetura diagrama,
        CabecalhoBinario cabecalho) {

    /** Bloco conceitual: um cartão de texto com um destaque opcional. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Conceito(String titulo, String icone, String texto, String destaque) {
    }

    /** Um passo do handshake TLS 1.3, na ordem em que acontece. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PassoHandshake(
            int ordem,
            String ator,
            String mensagem,
            String detalhe) {
    }

    /** Um elo da cadeia de certificados (raiz, intermediária, folha). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EloCadeia(String nivel, String papel, String exemplo, String nota) {
    }

    /** Uma cipher suite (ou família), com avaliação de segurança. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CipherSuite(
            String suite,
            String avaliacao,
            String avaliacaoCor,
            String nota) {
    }

    /** Um ataque conhecido contra TLS/PKI, com sinal observável e gravidade. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Ataque(
            String nome,
            String comoFunciona,
            String sinal,
            String gravidade,
            String gravidadeCor) {
    }

    /** Uma defesa, com o que cobre e o que não cobre. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mitigacao(String nome, String comoFunciona, String cobre, String limite) {
    }

    /** Roteiro de laboratório: cenário, comandos e o que observar. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Laboratorio(String titulo, String cenario, String comandos, String explicacao) {
    }

    /** Sintoma observado, causa provável, comando e leitura da saída. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Diagnostico(String sintoma, String causaProvavel, String comando, String leitura) {
    }
}
