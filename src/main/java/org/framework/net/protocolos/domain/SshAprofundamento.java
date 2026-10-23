package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo didático e operacional da página de aprofundamento do SSH.
 *
 * <p><b>Propósito de negócio:</b> o SSH é a porta de administração de
 * praticamente todo servidor Linux — inclusive a VPS onde este framework roda.
 * A página cobre desde o que acontece na negociação (o aluno) até o
 * endurecimento do {@code sshd_config} e os riscos reais de encaminhamento de
 * agente (o operador). O conteúdo vive em
 * {@code src/main/resources/protocolos/ssh/conteudo.json}.</p>
 *
 * <p><b>Invariantes do domínio:</b> record exclusivo do SSH, sem parentesco com
 * {@link BgpAprofundamento} — protocolos diferentes têm estruturas de conteúdo
 * diferentes, e unificá-las criaria acoplamento que impede a divergência. As
 * listas nunca são nulas: JSON incompleto reprova na subida.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — é um record de dados. A
 * validação pertence a {@link SshAprofundamentoCatalog}.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SshAprofundamento(
        String resumo,
        List<Conceito> conceitos,
        List<Camada> camadas,
        List<Autenticacao> autenticacoes,
        List<TipoChave> chaves,
        List<Encaminhamento> encaminhamentos,
        List<Hardening> hardening,
        List<Laboratorio> laboratorios,
        List<Diagnostico> diagnosticos,
        DiagramaArquitetura diagrama,
        CabecalhoBinario cabecalho) {

    /** Bloco conceitual: um cartão de texto com um destaque opcional. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Conceito(String titulo, String icone, String texto, String destaque) {
    }

    /** Uma das três camadas do SSH-2, na ordem em que entram em ação. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Camada(int ordem, String nome, String rfc, String funcao, String detalhe) {
    }

    /** Método de autenticação do usuário, com força relativa e uso indicado. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Autenticacao(
            String metodo,
            String comoFunciona,
            String forca,
            String forcaCor,
            String quandoUsar) {
    }

    /** Tipo de chave suportado, com recomendação atual e comando de geração. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TipoChave(
            String algoritmo,
            String tamanho,
            String recomendacao,
            String recomendacaoCor,
            String comando) {
    }

    /** Modo de encaminhamento (túnel) com o cenário legítimo e o risco que carrega. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Encaminhamento(
            String tipo,
            String flag,
            String cenario,
            String comando,
            String risco) {
    }

    /** Diretiva de endurecimento do servidor, com o motivo e o que ela evita. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Hardening(String diretiva, String valor, String porque, String risco) {
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
