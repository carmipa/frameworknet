package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Decomposição binária do cabeçalho de um protocolo — o "cálculo binário".
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> tipo COMPARTILHADO por todas as páginas de
 * aprofundamento (genéricas e dedicadas). Cada campo traz sua largura em bits e
 * a descrição; o template desenha a régua proporcional e a tabela, como a régua
 * de 32 bits da Análise Didática. É o que transforma "o cabeçalho TCP tem 20
 * bytes" em algo visto e contado.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> record puro de dados. A soma dos
 * {@code bits} dos campos é o tamanho do cabeçalho; a {@code nota} deve declarar
 * esse total (o teste/varredura e o leitor conferem).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nenhum — ausência tratada no template
 * ({@code {#if conteudo.cabecalho}}) e no carregador.</p>
 *
 * @param titulo título da seção do cabeçalho
 * @param nota   observação com o total em bits/bytes e detalhes de subcampos
 * @param campos os campos do cabeçalho, na ordem em que aparecem no pacote
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CabecalhoBinario(String titulo, String nota, List<Campo> campos) {

    /**
     * Um campo do cabeçalho: nome, largura em bits, o que carrega e a cor da régua.
     *
     * @param nome      nome do campo
     * @param bits      largura do campo em bits
     * @param descricao o que o campo carrega
     * @param cor       cor HEX do campo na régua (identidade visual)
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Campo(String nome, int bits, String descricao, String cor) {
    }
}
