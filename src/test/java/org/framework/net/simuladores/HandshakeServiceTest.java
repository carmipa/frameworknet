package org.framework.net.simuladores;

import org.framework.net.simuladores.application.HandshakeService;
import org.framework.net.simuladores.domain.ResultadoHandshake;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeServiceTest {

    private final HandshakeService service = new HandshakeService();

    @Test
    void tresWayTemTresPassosEEstabelece() {
        ResultadoHandshake r = service.simular(false, false);
        assertEquals(3, r.passos().size());
        assertEquals("SYN", r.passos().get(0).flags());
        assertEquals("SYN, ACK", r.passos().get(1).flags());
        assertEquals("ACK", r.passos().get(2).flags());
        // ao final do 3-way, ambos ESTABLISHED
        assertEquals("ESTABLISHED", r.passos().get(2).estadoCliente());
        assertEquals("ESTABLISHED", r.passos().get(2).estadoServidor());
    }

    @Test
    void synAckConfirmaIsnDoClienteMaisUm() {
        ResultadoHandshake r = service.simular(false, false);
        long isnCliente = r.isnCliente();
        assertEquals(isnCliente + 1, r.passos().get(1).ack(), "ack do SYN-ACK = ISNc + 1");
    }

    @Test
    void comDadosEEncerramentoTemNovePassos() {
        ResultadoHandshake r = service.simular(true, true);
        // 3 (3-way) + 2 (dados) + 4 (encerramento) = 9
        assertEquals(9, r.passos().size());
        // encerramento chega a TIME_WAIT/CLOSED
        ResultadoHandshake.Passo ultimo = r.passos().get(r.passos().size() - 1);
        assertEquals("CLOSED", ultimo.estadoServidor());
        assertTrue(ultimo.estadoCliente().equals("TIME_WAIT"));
    }

    @Test
    void encerramentoUsaFin() {
        ResultadoHandshake r = service.simular(false, true);
        boolean temFin = r.passos().stream().anyMatch(p -> p.flags().contains("FIN"));
        assertTrue(temFin);
    }
}
