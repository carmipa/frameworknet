package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo didático e operacional da página de aprofundamento do DNS.
 *
 * <p><b>Propósito de negócio:</b> o DNS é a primeira coisa que acontece em quase
 * toda conexão e o calcanhar de Aquiles de muita rede — phishing, sequestro de
 * sessão e man-in-the-middle costumam começar por uma resposta DNS falsificada.
 * A página mostra a resolução recursiva passo a passo (do resolver ao servidor
 * autoritativo), os tipos de registro, os ataques clássicos (spoofing, cache
 * poisoning) e as defesas reais (DNSSEC, aleatorização de porta, DoT/DoH). O
 * conteúdo vive em {@code src/main/resources/protocolos/dns/conteudo.json}.</p>
 *
 * <p><b>Invariantes do domínio:</b> record exclusivo do DNS, sem parentesco com
 * {@link SshAprofundamento} nem {@link BgpAprofundamento} — protocolos diferentes
 * têm estruturas de conteúdo diferentes, e unificá-las criaria o acoplamento que
 * impede a divergência. As listas nunca são nulas: JSON incompleto reprova na
 * subida (ver {@link DnsAprofundamentoCatalog}). A seção de mitigações é a mais
 * sensível: uma página que perde metade das defesas passa a orientar errado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — é um record de dados. A
 * validação de completude pertence a {@link DnsAprofundamentoCatalog}.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DnsAprofundamento(
        String resumo,
        List<Conceito> conceitos,
        List<PassoResolucao> resolucao,
        List<Registro> registros,
        List<Ataque> ataques,
        List<Mitigacao> mitigacoes,
        List<PassoDnssec> dnssec,
        List<Laboratorio> laboratorios,
        List<Diagnostico> diagnosticos,
        DiagramaArquitetura diagrama,
        CabecalhoBinario cabecalho) {

    /** Bloco conceitual: um cartão de texto com um destaque opcional. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Conceito(String titulo, String icone, String texto, String destaque) {
    }

    /** Um passo da resolução recursiva, do resolver ao autoritativo. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PassoResolucao(
            int ordem,
            String ator,
            String pergunta,
            String resposta,
            String detalhe) {
    }

    /** Um tipo de registro DNS, com função, exemplo e nota de uso/segurança. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Registro(String tipo, String funcao, String exemplo, String nota) {
    }

    /** Um ataque conhecido contra o DNS, com sinal observável e gravidade. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Ataque(
            String nome,
            String comoFunciona,
            String sinal,
            String gravidade,
            String gravidadeCor) {
    }

    /** Uma defesa contra ataque de DNS, com o que cobre e o que não cobre. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mitigacao(String nome, String comoFunciona, String cobre, String limite) {
    }

    /** Um elo da cadeia de confiança do DNSSEC, na ordem em que é verificado. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PassoDnssec(int ordem, String elemento, String papel, String detalhe) {
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
