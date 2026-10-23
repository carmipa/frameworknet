package org.framework.net.analiseTrafego.domain.model;

import java.util.List;

/**
 * Resultado da montagem de um pacote sintético no construtor didático.
 *
 * <p><b>Propósito de negócio:</b> devolver os bytes reais (em hex) de um quadro
 * Ethernet/IPv4/TCP-UDP montado a partir dos campos que o aluno editou, para que
 * ele copie e abra no decodificador — vendo o mesmo pacote nas duas pontas.</p>
 *
 * <p><b>Invariantes do domínio:</b> quando {@code ok} é verdadeiro, {@code hex}
 * tem um número par de dígitos hexadecimais e {@code totalBytes} é exatamente
 * metade desse comprimento; os comprimentos declarados nos cabeçalhos (Total
 * Length do IPv4, Length do UDP) batem com os bytes efetivos. {@code camadas}
 * descreve cada cabeçalho e seu tamanho; nenhuma lista é nula.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> entrada inválida NÃO lança — devolve
 * {@code ok=false} com {@code erro} preenchido e {@code hex} vazio, no mesmo
 * padrão do decodificador desta fatia.</p>
 */
public record PacoteConstruido(
        boolean ok,
        String erro,
        String protocolo,
        String hex,
        int totalBytes,
        boolean checksumValido,
        List<String> camadas) {

    public PacoteConstruido {
        camadas = camadas == null ? List.of() : List.copyOf(camadas);
    }

    public static PacoteConstruido erro(String mensagem) {
        return new PacoteConstruido(false, mensagem, "", "", 0, false, List.of());
    }
}
