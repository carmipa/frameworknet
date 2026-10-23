package org.framework.net.segurancaRede;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.application.FirewallEstadoService;
import org.framework.net.segurancaRede.domain.SimulacaoFirewall;
import org.framework.net.segurancaRede.domain.SimulacaoFirewall.AvaliacaoPacote;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P01 — firewall com estado × sem estado.
 *
 * <p><b>Propósito de negócio:</b> travar o contraste didático central: o firewall
 * com estado libera o retorno de uma conexão iniciada de dentro; o sem estado o
 * bloqueia. É o que impede a tela de mentir sobre a diferença entre os modelos.</p>
 */
@QuarkusTest
@DisplayName("Segurança: firewall com estado × sem estado (P01)")
class FirewallEstadoServiceTest {

    @Inject
    FirewallEstadoService servico;

    @Test
    @DisplayName("saída iniciada por dentro: stateful libera o retorno; stateless bloqueia")
    void conexaoSaidaStatefulLiberaRetornoStatelessBloqueia() {
        SimulacaoFirewall s = servico.simular("conexao-saida");

        AvaliacaoPacote syn = s.avaliacoes().get(0);
        assertEquals("PERMITIDO", syn.semEstado(), "SYN de saída é permitido nos dois modelos");
        assertEquals("PERMITIDO", syn.comEstado());

        AvaliacaoPacote synAck = s.avaliacoes().get(1);
        assertEquals("BLOQUEADO", synAck.semEstado(), "sem estado, o retorno (SYN-ACK) cai no deny de entrada");
        assertEquals("PERMITIDO", synAck.comEstado(), "com estado, o retorno casa a conexão registrada");
        assertTrue(synAck.divergem());
        assertEquals("estabelecida", synAck.estadoDepois());

        AvaliacaoPacote dados = s.avaliacoes().get(3);
        assertEquals("BLOQUEADO", dados.semEstado());
        assertEquals("PERMITIDO", dados.comEstado());

        assertTrue(s.divergencias() >= 2, "os passos de retorno devem divergir entre os modelos");
    }

    @Test
    @DisplayName("entrada não solicitada: ambos bloqueiam, e a flag isolada não vira 'ataque'")
    void entradaNaoSolicitadaBloqueadaSemAcusarAtaque() {
        SimulacaoFirewall s = servico.simular("entrada-nao-solicitada");

        for (AvaliacaoPacote a : s.avaliacoes()) {
            assertEquals("BLOQUEADO", a.semEstado(), "entrada sem regra é negada no modelo sem estado");
            assertEquals("BLOQUEADO", a.comEstado(), "entrada sem conexão na tabela é negada no modelo com estado");
        }
        assertEquals(0, s.divergencias(), "aqui os dois concordam — por razões diferentes");

        AvaliacaoPacote synAckSolto = s.avaliacoes().get(2);
        assertTrue(synAckSolto.razaoComEstado().contains("fora de estado"),
                "SYN-ACK solto é 'fora de estado': " + synAckSolto.razaoComEstado());
        assertFalse(synAckSolto.razaoComEstado().toLowerCase().contains("ataque"),
                "flag isolada não deve ser rotulada como ataque: " + synAckSolto.razaoComEstado());
    }

    @Test
    @DisplayName("resposta tardia: depois da expiração, o stateful passa a bloquear o retorno")
    void respostaTardiaBloqueadaAposExpiracao() {
        SimulacaoFirewall s = servico.simular("resposta-tardia");

        assertEquals("PERMITIDO", s.avaliacoes().get(1).comEstado(), "SYN-ACK estabelece a conexão");
        assertEquals("sem conexão", s.avaliacoes().get(2).estadoDepois(), "o timeout limpa a tabela");

        AvaliacaoPacote tardio = s.avaliacoes().get(3);
        assertEquals("BLOQUEADO", tardio.comEstado(), "resposta após expirar fica fora de estado");
        assertTrue(tardio.razaoComEstado().contains("fora de estado"), tardio.razaoComEstado());
    }

    @Test
    @DisplayName("cenário fora do catálogo é recusado, não improvisado")
    void cenarioDesconhecidoLancaExcecao() {
        assertThrows(SegurancaException.class, () -> servico.simular("nao-existe"));
    }

    @Test
    @DisplayName("determinístico: a mesma sequência produz sempre o mesmo resultado")
    void simulacaoEDeterministica() {
        assertEquals(servico.simular("conexao-saida"), servico.simular("conexao-saida"));
    }
}
