package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo didático GENÉRICO de um aprofundamento de protocolo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> os primeiros aprofundamentos (BGP, SSH, DNS,
 * TLS) nasceram com um record e um template próprios cada — o certo quando eram
 * poucos e muito distintos. Para cobrir a camada inteira (HTTP, FTP, SMTP,
 * Telnet, TCP, UDP, ICMP, IPv4, IPv6, ARP…) sem multiplicar record e template,
 * este é o modelo COMUM: ficha, conceitos, um diagrama de arquitetura, a
 * decomposição binária do cabeçalho, tabelas, ataques, defesas, laboratório e
 * troubleshooting. Um protocolo novo passa a custar apenas um {@code conteudo.json}
 * e uma linha no registro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> record puro de dados (sem framework além das
 * anotações Jackson). Toda seção é opcional: o template só renderiza o que veio
 * preenchido. O carregador ({@link ProtocolosAprofundamentoCatalog}) exige, por
 * protocolo, ao menos resumo e conceitos — falha fechada na subida.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nenhum — a validação de completude e
 * o tratamento de ausência ficam no catálogo e no serviço.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProtocoloAprofundamento(
        String resumo,
        List<FichaItem> ficha,
        List<Conceito> conceitos,
        DiagramaArquitetura diagrama,
        CabecalhoBinario cabecalho,
        List<Tabela> tabelas,
        List<Ataque> ataques,
        List<Mitigacao> mitigacoes,
        List<Laboratorio> laboratorios,
        List<Diagnostico> diagnosticos) {

    /** Um par rótulo/valor da ficha rápida do cabeçalho (RFC, porta, camada…). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FichaItem(String rotulo, String valor, String dica) {
    }

    /** Bloco conceitual: cartão de texto com destaque opcional. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Conceito(String titulo, String icone, String texto, String destaque) {
    }

    /** Tabela genérica (métodos HTTP, códigos de status, comandos FTP, portas…). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Tabela(String titulo, List<String> colunas, List<List<String>> linhas) {
    }

    /** Um ataque conhecido, com sinal observável e gravidade. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Ataque(String nome, String comoFunciona, String sinal, String gravidade, String gravidadeCor) {
    }

    /** Uma defesa, com o que cobre e o que não cobre. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mitigacao(String nome, String comoFunciona, String cobre, String limite) {
    }

    /** Roteiro de laboratório: cenário, comandos e o que observar. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Laboratorio(String titulo, String cenario, String comandos, String explicacao) {
    }

    /** Sintoma, causa provável, comando e leitura da saída. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Diagnostico(String sintoma, String causaProvavel, String comando, String leitura) {
    }
}
