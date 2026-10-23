package org.framework.net.analiseTrafego.domain.model;

import java.util.List;

/**
 * Resultado da decodificação didática de um pacote (hex dump → camadas explicadas).
 */
public record ResultadoDecodificacao(
        boolean ok,
        String mensagem,
        int totalBytes,
        String camadaInicial,
        List<Camada> camadas) {

    /** Uma camada de protocolo decodificada (Ethernet, IPv4, TCP, ...). */
    public record Camada(String nome, String resumo, List<Campo> campos) {
    }

    /** Um campo dentro de uma camada, com valor e explicação didática. */
    public record Campo(String nome, String valor, String descricao) {
    }
}
