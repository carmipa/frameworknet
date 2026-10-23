package org.framework.net.simuladores.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.simuladores.domain.ResultadoHandshake;
import org.framework.net.simuladores.domain.ResultadoHandshake.Passo;

import java.util.ArrayList;
import java.util.List;

/**
 * Gera a sequência didática de um handshake TCP: 3-way (SYN, SYN-ACK, ACK),
 * troca de dados opcional e encerramento 4-way (FIN/ACK). Números de sequência
 * partem de ISNs fixos (didáticos) e evoluem segundo as regras do TCP.
 */
@ApplicationScoped
public class HandshakeService {

    private static final long ISN_CLIENTE = 1000L;
    private static final long ISN_SERVIDOR = 5000L;
    private static final int BYTES_DADOS = 20;

    public ResultadoHandshake simular(boolean incluirDados, boolean incluirEncerramento) {
        long c = ISN_CLIENTE;
        long s = ISN_SERVIDOR;
        List<Passo> passos = new ArrayList<>();
        int ordem = 1;

        // ---- 3-way handshake ----
        passos.add(new Passo(ordem++, "cliente", "servidor", "SYN",
                c, 0, 0, "SYN_SENT", "SYN_RECEIVED",
                "SYN",
                "O cliente inicia a conexão: envia SYN=1 com seu ISN (Initial Sequence Number)."));
        passos.add(new Passo(ordem++, "servidor", "cliente", "SYN, ACK",
                s, c + 1, 0, "ESTABLISHED", "SYN_RECEIVED",
                "SYN-ACK",
                "O servidor aceita: envia seu próprio ISN e confirma o do cliente (ack = ISNc + 1)."));
        passos.add(new Passo(ordem++, "cliente", "servidor", "ACK",
                c + 1, s + 1, 0, "ESTABLISHED", "ESTABLISHED",
                "ACK",
                "O cliente confirma o ISN do servidor. Conexão ESTABLISHED — 3-way completo."));

        long cSeq = c + 1;
        long sSeq = s + 1;

        // ---- Troca de dados (opcional) ----
        if (incluirDados) {
            passos.add(new Passo(ordem++, "cliente", "servidor", "PSH, ACK",
                    cSeq, sSeq, BYTES_DADOS, "ESTABLISHED", "ESTABLISHED",
                    "Dados",
                    "O cliente envia " + BYTES_DADOS + " bytes de dados (ex.: uma requisição)."));
            cSeq += BYTES_DADOS;
            passos.add(new Passo(ordem++, "servidor", "cliente", "ACK",
                    sSeq, cSeq, 0, "ESTABLISHED", "ESTABLISHED",
                    "ACK dos dados",
                    "O servidor confirma o recebimento (ack avança " + BYTES_DADOS + " bytes)."));
        }

        // ---- Encerramento 4-way (opcional) ----
        if (incluirEncerramento) {
            passos.add(new Passo(ordem++, "cliente", "servidor", "FIN, ACK",
                    cSeq, sSeq, 0, "FIN_WAIT_1", "CLOSE_WAIT",
                    "FIN (cliente)",
                    "O cliente encerra sua metade da conexão (FIN). Um FIN consome 1 número de sequência."));
            passos.add(new Passo(ordem++, "servidor", "cliente", "ACK",
                    sSeq, cSeq + 1, 0, "FIN_WAIT_2", "CLOSE_WAIT",
                    "ACK do FIN",
                    "O servidor confirma o FIN; o cliente aguarda o FIN do servidor (FIN_WAIT_2)."));
            passos.add(new Passo(ordem++, "servidor", "cliente", "FIN, ACK",
                    sSeq, cSeq + 1, 0, "TIME_WAIT", "LAST_ACK",
                    "FIN (servidor)",
                    "O servidor encerra sua metade (FIN) e aguarda o último ACK (LAST_ACK)."));
            passos.add(new Passo(ordem++, "cliente", "servidor", "ACK",
                    cSeq + 1, sSeq + 1, 0, "TIME_WAIT", "CLOSED",
                    "ACK final",
                    "O cliente confirma. O servidor fecha; o cliente aguarda em TIME_WAIT antes de CLOSED."));
        }

        return new ResultadoHandshake(ISN_CLIENTE, ISN_SERVIDOR, incluirDados, incluirEncerramento, passos);
    }
}
