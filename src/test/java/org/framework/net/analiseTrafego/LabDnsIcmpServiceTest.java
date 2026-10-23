package org.framework.net.analiseTrafego;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseTrafego.application.LabDnsIcmpService;
import org.framework.net.analiseTrafego.domain.model.AnaliseTrafegoLab;
import org.framework.net.analiseTrafego.domain.model.AnaliseTrafegoLab.RegistroTrafego;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P04 — laboratório DNS/ICMP.
 *
 * <p><b>Propósito de negócio:</b> garantir que o laboratório ensina o essencial:
 * cada cenário tem tráfego normal E suspeito, toda suspeita vem com o indicador,
 * e a nota de falso positivo existe — para a tela não virar um detector que
 * "acusa" sozinho.</p>
 */
@QuarkusTest
@DisplayName("Tráfego: laboratório DNS/ICMP (P04)")
class LabDnsIcmpServiceTest {

    @Inject
    LabDnsIcmpService servico;

    @Test
    @DisplayName("DNS: mistura normal e suspeito, com evidência e nota de falso positivo")
    void dnsTemNormalESuspeitoComEvidencia() {
        AnaliseTrafegoLab lab = servico.analisar("dns");

        assertTrue(lab.registros().stream().anyMatch(r -> !r.suspeito()), "precisa ter tráfego normal");
        assertTrue(lab.suspeitos() >= 2, "precisa ter exemplos suspeitos");
        assertTrue(lab.registros().stream().filter(RegistroTrafego::suspeito)
                        .allMatch(r -> r.indicador() != null && !r.indicador().isBlank()),
                "toda suspeita traz o indicador que a motivou");
        assertFalse(lab.falsoPositivo().isBlank(), "o cenário precisa explicar o falso positivo");
    }

    @Test
    @DisplayName("ICMP: o pacote grande isolado é NORMAL (o falso positivo do tamanho)")
    void icmpGrandeIsoladoEhNormal() {
        AnaliseTrafegoLab lab = servico.analisar("icmp");

        RegistroTrafego mtu = lab.registros().stream()
                .filter(r -> r.amostra().contains("1472"))
                .findFirst().orElseThrow();
        assertFalse(mtu.suspeito(), "teste de MTU (pacote grande isolado) não deve ser marcado suspeito");
        assertTrue(lab.suspeitos() >= 2, "mas há padrões de tunneling suspeitos no mesmo cenário");
        assertFalse(lab.falsoPositivo().isBlank());
    }

    @Test
    @DisplayName("cenário desconhecido é recusado")
    void cenarioDesconhecidoLanca() {
        assertThrows(IllegalArgumentException.class, () -> servico.analisar("nao-existe"));
    }

    @Test
    @DisplayName("determinístico")
    void deterministico() {
        assertEquals(servico.analisar("dns"), servico.analisar("dns"));
    }
}
