package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo didático e operacional da página de aprofundamento do BGP.
 *
 * <p><b>Propósito de negócio:</b> reunir, numa estrutura consultável, o que um
 * aluno precisa para entender BGP (o que é AS, por que é path vector, como a
 * sessão sobe) e o que um operador de borda precisa para trabalhar (ordem de
 * seleção de melhor rota, atributos, proteções contra vazamento e hijack,
 * comandos de diagnóstico). O conteúdo vive em
 * {@code src/main/resources/protocolos/bgp/conteudo.json} para que a correção de
 * um dado didático seja edição de dado, não de template.</p>
 *
 * <p><b>Invariantes do domínio:</b> este record é do BGP e de mais ninguém — não
 * é compartilhado com os outros aprofundamentos de propósito, porque conteúdo
 * didático de protocolos diferentes diverge (duplicação consciente é mais barata
 * que o acoplamento que impede a divergência). As listas nunca são nulas: o
 * carregador reprova o arquivo incompleto na subida da aplicação.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nenhum — é um record de dados. A
 * validação e a explosão em caso de JSON inválido são responsabilidade de
 * {@link BgpAprofundamentoCatalog}.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BgpAprofundamento(
        String resumo,
        List<Conceito> conceitos,
        List<Atributo> atributos,
        List<PassoSelecao> selecaoMelhorRota,
        List<EstadoSessao> estados,
        List<Mensagem> mensagens,
        List<Temporizador> temporizadores,
        List<Topologia> topologias,
        List<Protecao> protecoes,
        List<Laboratorio> laboratorios,
        List<Diagnostico> diagnosticos,
        DiagramaArquitetura diagrama,
        CabecalhoBinario cabecalho) {

    /** Bloco conceitual: um cartão de texto com um destaque opcional. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Conceito(String titulo, String icone, String texto, String destaque) {
    }

    /** Atributo de caminho do BGP, com escopo de propagação e efeito na decisão. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Atributo(
            String nome,
            String tipo,
            String escopo,
            String preferencia,
            String descricao) {
    }

    /** Um degrau da ordem de seleção de melhor rota, na sequência em que o roteador avalia. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PassoSelecao(int ordem, String criterio, String regra, String observacao) {
    }

    /** Estado da máquina de estados finita da sessão BGP. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EstadoSessao(int ordem, String estado, String significado, String travaComum) {
    }

    /** Tipo de mensagem do protocolo, com o momento em que aparece. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mensagem(String tipo, int codigo, String quando, String conteudo) {
    }

    /** Temporizador da sessão, com o padrão Cisco e o efeito prático. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Temporizador(String nome, String padrao, String funcao) {
    }

    /** Recurso de escala do iBGP (full-mesh, route reflector, confederação). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Topologia(String nome, String problema, String solucao, String atencao) {
    }

    /** Proteção operacional contra uma ameaça concreta da borda. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Protecao(String nome, String ameaca, String aplicacao, String comando) {
    }

    /** Roteiro de laboratório: cenário, comandos e o que observar. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Laboratorio(String titulo, String cenario, String comandos, String explicacao) {
    }

    /** Sintoma observado em produção, causa provável, comando e leitura da saída. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Diagnostico(String sintoma, String causaProvavel, String comando, String leitura) {
    }
}
