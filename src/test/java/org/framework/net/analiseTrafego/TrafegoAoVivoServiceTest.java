package org.framework.net.analiseTrafego;

import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo;
import org.framework.net.analiseTrafego.aovivo.TrafegoAoVivoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafegoAoVivoServiceTest {

    private final TrafegoAoVivoService service = new TrafegoAoVivoService();

    @Test
    void demoGeraPacotesProtocolosESerie() {
        SnapshotAoVivo s1 = service.snapshotDemo();
        SnapshotAoVivo s2 = service.snapshotDemo();
        assertEquals("demo", s1.modo());
        assertTrue(s2.totalPacotes() > s1.totalPacotes(), "total deve crescer a cada tick");
        assertFalse(s2.porProtocolo().isEmpty(), "deve haver contagem por protocolo");
        assertFalse(s2.ultimosPacotes().isEmpty(), "deve haver pacotes");
        assertEquals(2, s2.serie().size(), "série cresce um ponto por tick");
    }

    /**
     * Auditoria CALC-38/CONT-35: números de pacote únicos; ARP só na LAN; Client Hello só do cliente para o
     * servidor (porta de destino 443); WEP não é "aberta". Roda vários ticks porque o tráfego é sorteado.
     */
    @Test
    void demoCoerenteComOsProtocolos() {
        java.util.Set<Long> numeros = new java.util.HashSet<>();
        for (int tick = 0; tick < 40; tick++) {
            SnapshotAoVivo s = service.snapshotDemo();
            for (SnapshotAoVivo.PacoteResumo p : s.ultimosPacotes()) {
                numeros.add(p.seq());
                if ("ARP".equals(p.protocolo())) {
                    assertTrue(p.origem().startsWith("192.168.") && p.destino().startsWith("192.168."),
                            "ARP com IP de fora da LAN: " + p);
                }
                if ("Client Hello".equals(p.info())) {
                    assertEquals(443, p.portaDestino(), "Client Hello saindo do servidor: " + p);
                }
            }
            assertEquals(s.ultimosPacotes().size(),
                    s.ultimosPacotes().stream().map(SnapshotAoVivo.PacoteResumo::seq).distinct().count(),
                    "número de pacote repetido no mesmo snapshot");
            assertTrue(s.wifi().stream().filter(w -> w.seguranca().startsWith("WEP"))
                    .noneMatch(SnapshotAoVivo.RedeWifi::aberta), "WEP cifra: não é rede aberta");
        }
        assertTrue(numeros.size() > 25, "o instrumento precisa ter visto pacotes de verdade");
    }

    @Test
    void demoDetectaRedesAbertasInseguras() {
        SnapshotAoVivo s = service.snapshotDemo();
        assertTrue(s.redesAbertas() >= 1, "demo tem redes abertas para o alerta");
        boolean temAberta = s.wifi().stream().anyMatch(SnapshotAoVivo.RedeWifi::aberta);
        assertTrue(temAberta);
    }
}
