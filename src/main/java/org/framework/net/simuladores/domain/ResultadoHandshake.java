package org.framework.net.simuladores.domain;

import java.util.List;

/**
 * Resultado do simulador de <b>handshake TCP</b>: a sequência de segmentos trocados entre
 * cliente e servidor (3-way handshake, dados opcionais e encerramento 4-way), com números
 * de sequência/ack e os estados da máquina de conexão em cada ponto.
 */
public record ResultadoHandshake(
        long isnCliente,
        long isnServidor,
        boolean incluiDados,
        boolean incluiEncerramento,
        List<Passo> passos) {

    /** Um segmento trocado no handshake. */
    public record Passo(
            int ordem,
            String origem,          // "cliente" | "servidor"
            String destino,         // "cliente" | "servidor"
            String flags,           // "SYN", "SYN, ACK", "ACK", "FIN, ACK", "PSH, ACK"
            long seq,
            long ack,
            int bytes,              // bytes de dados (0 nos controles)
            String estadoCliente,   // estado da FSM do cliente após este segmento
            String estadoServidor,  // estado da FSM do servidor após este segmento
            String titulo,
            String descricao) {
    }
}
