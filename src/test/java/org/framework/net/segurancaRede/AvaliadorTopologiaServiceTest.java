package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.application.AvaliadorTopologiaService;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P05 fase 2 — motor de alcançabilidade sobre topologia montada.
 *
 * <p><b>Propósito de negócio:</b> travar o veredito sobre um grafo arbitrário —
 * chega/não chega e o ponto exato de bloqueio — e a recusa de entradas inválidas.</p>
 */
@QuarkusTest
@DisplayName("Segurança: motor de topologia montada (P05 fase 2)")
class AvaliadorTopologiaServiceTest {

    @Inject
    AvaliadorTopologiaService servico;

    private static final String TOPO = String.join("\n",
            "host H1 vlan=10 gw=R1",
            "switchl3 R1 vlans=10,20",
            "firewall FW deny=tcp/23",
            "server S1 vlan=20 porta=443",
            "link H1 R1",
            "link R1 FW",
            "link FW S1");

    @Test
    @DisplayName("tudo configurado: o fluxo alcança o servidor")
    void alcancaQuandoConfigurado() {
        DiagnosticoFluxo d = servico.diagnosticar(TOPO, "H1", "S1", "443");
        assertTrue(d.alcanca(), () -> "esperava alcançar; ponto: " + d.pontoBloqueio());
        assertTrue(d.pontoBloqueio().isEmpty());
    }

    /**
     * Auditoria CALC-21/CONT-04 — gabarito (A3): RFC 1122 §3.3.1.1, destino na mesma sub-rede é entregue
     * direto, sem gateway. O switch L3 sem SVI nenhuma ainda comuta a VLAN. Fronteira (A1): o mesmo switch
     * continua bloqueando o fluxo para OUTRA VLAN, e a porta fechada continua recusada.
     */
    @Test
    @DisplayName("mesma VLAN: entrega por camada 2, sem gateway e sem SVI")
    void mesmaVlanNaoPassaPeloGateway() {
        String topo = String.join("\n",
                "host H1 vlan=10",
                "switchl3 SW",
                "server S1 vlan=10 porta=443",
                "server S2 vlan=20 porta=443",
                "link H1 SW",
                "link SW S1",
                "link SW S2");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertTrue(d.alcanca(), () -> "mesma VLAN não depende de gateway nem SVI; parou em " + d.pontoBloqueio());
        assertTrue(d.saltos().stream().noneMatch(s -> s.camada().equals("L3")), d.saltos().toString());

        assertFalse(servico.diagnosticar(topo, "H1", "S2", "443").alcanca(), "outra VLAN sem gateway não chega");
        assertFalse(servico.diagnosticar(topo, "H1", "S1", "80").alcanca(), "porta fechada continua recusada");

        // O firewall do modelo roteia: dois lados dele não são a mesma VLAN comutada.
        String peloFirewall = String.join("\n",
                "host H1 vlan=10",
                "firewall FW",
                "server S1 vlan=10 porta=443",
                "link H1 FW",
                "link FW S1");
        assertFalse(servico.diagnosticar(peloFirewall, "H1", "S1", "443").alcanca(),
                "camada 2 não atravessa firewall nem host");
    }

    @Test
    @DisplayName("ACL nega a porta: bloqueia no firewall")
    void aclNegaBloqueiaNoFirewall() {
        DiagnosticoFluxo d = servico.diagnosticar(TOPO, "H1", "S1", "23");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("FW"), d.pontoBloqueio());
        verificarBloqueioUnicoEFinal(d);
    }

    @Test
    @DisplayName("switch L3 sem a VLAN de destino: bloqueia no roteamento inter-VLAN")
    void vlanNaoRoteadaBloqueia() {
        String topo = TOPO.replace("switchl3 R1 vlans=10,20", "switchl3 R1 vlans=10");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("R1"), d.pontoBloqueio());
        verificarBloqueioUnicoEFinal(d);
    }

    @Test
    @DisplayName("host sem gateway: bloqueia já no host")
    void semGatewayBloqueiaNoHost() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=10");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("H1"), d.pontoBloqueio());
    }

    @Test
    @DisplayName("sem enlace até o destino: sem caminho")
    void semCaminhoBloqueia() {
        String topo = TOPO.replace("link FW S1", "");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertFalse(d.alcanca());
    }

    @Test
    @DisplayName("origem que não é host é recusada")
    void origemNaoHostRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(TOPO, "R1", "S1", "443"));
    }

    @Test
    @DisplayName("id inexistente é recusado")
    void idInexistenteRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(TOPO, "H1", "NAO_EXISTE", "443"));
    }

    @Test
    @DisplayName("tipo não suportado no texto é recusado")
    void tipoInvalidoRecusa() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar("gizmo X\nlink X X", "X", "X", "80"));
    }

    @Test
    @DisplayName("#1: atalho paralelo não pode furar o gateway (firewall que nega a porta)")
    void atalhoNaoFuraGateway() {
        String topo = String.join("\n",
                "host H vlan=10 gw=FW",
                "firewall FW deny=tcp/443",
                "server S vlan=20 porta=443",
                "link H FW",
                "link FW S",
                "link H S");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H", "S", "443");
        assertFalse(d.alcanca(), () -> "o fluxo deveria travar no firewall-gateway, não furar pelo atalho H-S");
        assertTrue(d.pontoBloqueio().contains("FW"), d.pontoBloqueio());
    }

    @Test
    @DisplayName("#1: os saltos percorrem o gateway declarado (explicação = caminho real)")
    void saltosPassamPeloGateway() {
        String topo = String.join("\n",
                "host H vlan=10 gw=FW",
                "firewall FW deny=tcp/23",
                "server S vlan=20 porta=443",
                "link H FW",
                "link FW S",
                "link H S");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H", "S", "443");
        assertTrue(d.alcanca(), () -> "sem deny da porta o fluxo alcança, mas via gateway; ponto: " + d.pontoBloqueio());
        assertTrue(d.saltos().stream().anyMatch(s -> s.no().contains("FW")),
                "o firewall-gateway precisa aparecer entre os saltos percorridos");
    }

    @Test
    @DisplayName("#2: vlan não numérica é recusada (não vira VLAN 0)")
    void vlanInvalidaRecusa() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=abc gw=R1");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2: lista de vlans com token inválido é recusada (não vira lista vazia)")
    void vlansInvalidaRecusa() {
        String topo = TOPO.replace("switchl3 R1 vlans=10,20", "switchl3 R1 vlans=10,abc");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2: porta não numérica no atributo é recusada")
    void portaAtributoInvalidaRecusa() {
        String topo = TOPO.replace("server S1 vlan=20 porta=443", "server S1 vlan=20 porta=abc");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2: vlan fora do intervalo 1–4094 é recusada")
    void vlanForaDoIntervaloRecusa() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=99999 gw=R1");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2: atributo ausente segue usando o default (ausência não é erro)")
    void atributoAusentePreservaDefault() {
        DiagnosticoFluxo d = servico.diagnosticar(TOPO, "H1", "S1", "443");
        assertTrue(d.alcanca(), () -> "ausência de vlan em FW/R1 não pode virar erro; ponto: " + d.pontoBloqueio());
    }

    @Test
    @DisplayName("#1b: o caminho não volta pelo host de origem (host não é trânsito)")
    void naoTransitaPeloHostDeOrigem() {
        // R e S só se ligam a H; R só chegaria a S passando por H (host), que não encaminha.
        String topo = String.join("\n",
                "host H vlan=10 gw=R",
                "switchl3 R vlans=10,20",
                "server S vlan=20 porta=443",
                "link H R",
                "link H S");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H", "S", "443");
        assertFalse(d.alcanca(), () -> "S só é alcançável via o host, que não encaminha: deveria bloquear. ponto: " + d.pontoBloqueio());
    }

    @Test
    @DisplayName("#1b: servidor no meio não serve de trânsito")
    void servidorNaoEhTransito() {
        String topo = String.join("\n",
                "host H vlan=10 gw=R",
                "switchl3 R vlans=10,20",
                "server MEIO vlan=20 porta=80",
                "server S vlan=20 porta=443",
                "link H R",
                "link R MEIO",
                "link MEIO S");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H", "S", "443");
        assertFalse(d.alcanca(), () -> "um servidor (ponta) não pode encaminhar até S. ponto: " + d.pontoBloqueio());
    }

    @Test
    @DisplayName("#2b: atributo desconhecido (denny) é recusado, não ignorado")
    void atributoDesconhecidoRecusa() {
        String topo = String.join("\n",
                "host H vlan=10 gw=R",
                "switchl3 R vlans=10,20",
                "firewall FW denny=tcp/443",
                "server S vlan=20 porta=443",
                "link H R", "link R FW", "link FW S");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H", "S", "443"));
    }

    @Test
    @DisplayName("#2b: atributo de outro tipo de equipamento é recusado")
    void atributoDeOutroTipoRecusa() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=10 gw=R1 porta=443");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2b: atributo repetido é recusado")
    void atributoRepetidoRecusa() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=10 vlan=20 gw=R1");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2b: token solto (sem =) é recusado")
    void tokenSoltoRecusa() {
        String topo = TOPO.replace("host H1 vlan=10 gw=R1", "host H1 vlan=10 gw=R1 lixo");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2b: token extra em link é recusado")
    void linkTokenExtraRecusa() {
        String topo = TOPO.replace("link H1 R1", "link H1 R1 extra");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2b: regra deny malformada é recusada")
    void denyMalformadaRecusa() {
        String topo = TOPO.replace("firewall FW deny=tcp/23", "firewall FW deny=23");
        assertThrows(SegurancaException.class, () -> servico.diagnosticar(topo, "H1", "S1", "443"));
    }

    @Test
    @DisplayName("#2b: deny com udp válido é aceito (não trava o que não deve)")
    void denyUdpAceito() {
        String topo = TOPO.replace("firewall FW deny=tcp/23", "firewall FW deny=udp/53,tcp/23");
        DiagnosticoFluxo d = servico.diagnosticar(topo, "H1", "S1", "443");
        assertTrue(d.alcanca(), () -> "porta 443 não está nos denies; deveria alcançar. ponto: " + d.pontoBloqueio());
    }

    private static void verificarBloqueioUnicoEFinal(DiagnosticoFluxo d) {
        assertEquals(1, d.saltos().stream().filter(s -> !s.ok()).count(), "um único ponto de bloqueio");
        assertFalse(d.saltos().get(d.saltos().size() - 1).ok(), "o bloqueio é o último salto");
    }
}
