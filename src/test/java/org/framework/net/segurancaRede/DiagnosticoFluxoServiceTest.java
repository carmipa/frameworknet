package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.application.DiagnosticoFluxoService;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo.Salto;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P05 (v1) — diagnóstico de alcançabilidade de fluxo.
 *
 * <p><b>Propósito de negócio:</b> travar a resposta central — chega ou não, e
 * onde trava — e o invariante de que o pacote para no primeiro ponto que o
 * recusa (nenhum salto é simulado depois do bloqueio).</p>
 */
@QuarkusTest
@DisplayName("Segurança: diagnóstico de alcançabilidade de fluxo (P05 v1)")
class DiagnosticoFluxoServiceTest {

    @Inject
    DiagnosticoFluxoService servico;

    @Test
    @DisplayName("fluxo permitido: alcança, todos os saltos ok, sem ponto de bloqueio")
    void fluxoPermitidoAlcanca() {
        DiagnosticoFluxo d = servico.diagnosticar("alcanca");
        assertTrue(d.alcanca());
        assertTrue(d.pontoBloqueio().isEmpty());
        assertTrue(d.saltos().stream().allMatch(Salto::ok));
        assertTrue(d.saltos().size() >= 4, "caminho completo host→switch→firewall→servidor");
    }

    @Test
    @DisplayName("VLANs isoladas: bloqueia no switch L3 e não simula saltos além")
    void vlanIsoladaBloqueiaNoSwitch() {
        DiagnosticoFluxo d = servico.diagnosticar("vlan-isolada");
        assertFalse(d.alcanca());
        assertTrue(d.pontoBloqueio().contains("Switch L3"), d.pontoBloqueio());
        verificarInvarianteDeBloqueio(d);
    }

    @Test
    @DisplayName("ACL nega: bloqueia no firewall")
    void aclNegaBloqueiaNoFirewall() {
        DiagnosticoFluxo d = servico.diagnosticar("acl-nega");
        assertFalse(d.alcanca());
        assertEquals("Firewall", d.pontoBloqueio());
        verificarInvarianteDeBloqueio(d);
    }

    @Test
    @DisplayName("gateway errado: bloqueia já no host de origem")
    void gatewayErradoBloqueiaNoHost() {
        DiagnosticoFluxo d = servico.diagnosticar("gateway-errado");
        assertFalse(d.alcanca());
        assertEquals("Host de origem", d.pontoBloqueio());
        verificarInvarianteDeBloqueio(d);
    }

    @Test
    @DisplayName("cenário desconhecido é recusado")
    void cenarioDesconhecidoLanca() {
        assertThrows(SegurancaException.class, () -> servico.diagnosticar("nao-existe"));
    }

    /** Bloqueado ⇒ exatamente um salto não-ok, e ele é o ÚLTIMO (nada depois do bloqueio). */
    private static void verificarInvarianteDeBloqueio(DiagnosticoFluxo d) {
        List<Salto> saltos = d.saltos();
        long naoOk = saltos.stream().filter(s -> !s.ok()).count();
        assertEquals(1, naoOk, "um único ponto de bloqueio");
        assertFalse(saltos.get(saltos.size() - 1).ok(), "o bloqueio é o último salto — nada é simulado depois");
    }
}
