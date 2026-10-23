package org.framework.net.simuladores;

import org.framework.net.simuladores.application.AnomaliaTcpService;
import org.framework.net.simuladores.domain.ResultadoAnomaliaTcp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda do gerador de cenários de anomalias TCP.
 *
 * <p><b>Propósito de negócio:</b> os dois cenários alimentam a sub-aba
 * "Anomalias" da página Tráfego. Vazios ou trocados, a aba ensina errado. Este
 * teste prova que cada cenário chega completo e que o tipo desconhecido cai no
 * padrão em vez de estourar.</p>
 */
@DisplayName("Simuladores: cenários de anomalia TCP")
class AnomaliaTcpServiceTest {

    private final AnomaliaTcpService service = new AnomaliaTcpService();

    @Test
    @DisplayName("SYN flood vem completo e cita a defesa por SYN cookies")
    void synFloodCompleto() {
        ResultadoAnomaliaTcp r = service.simular("syn-flood");
        assertAll(
                () -> assertEquals("syn-flood", r.tipo()),
                () -> assertFalse(r.passos().isEmpty(), "sem passos, não há o que animar"),
                () -> assertFalse(r.kpis().isEmpty(), "sem indicadores, o backlog não se explica"),
                () -> assertFalse(r.mitigacoes().isEmpty(), "página de ataque sem defesa orienta errado"),
                () -> assertFalse(r.licao() == null || r.licao().isBlank(), "a lição não pode faltar"),
                () -> assertTrue(r.passos().stream().anyMatch(p -> "ok".equals(p.nivel())),
                        "o cenário precisa mostrar o passo de mitigação (nível ok)"),
                () -> assertTrue(r.mitigacoes().stream().anyMatch(m -> m.nome().contains("SYN cookies")),
                        "SYN cookies é a defesa raiz do SYN flood e precisa aparecer"));
    }

    /** CONT-07 (RFC 4987 §3.6): o cookie é um hash com SEGREDO do servidor; sem ele, qualquer um forjaria o ACK. */
    @Test
    void synCookieTemSegredo() {
        ResultadoAnomaliaTcp r = service.simular("syn-flood");
        assertTrue(r.mitigacoes().stream().filter(m -> m.nome().equals("SYN cookies"))
                .anyMatch(m -> m.comoFunciona().contains("SEGREDO")), r.mitigacoes().toString());
    }

    @Test
    @DisplayName("Sequestro de sequência vem completo e cita RFC 6528")
    void sequenceHijackCompleto() {
        ResultadoAnomaliaTcp r = service.simular("sequence-hijack");
        assertAll(
                () -> assertEquals("sequence-hijack", r.tipo()),
                () -> assertFalse(r.passos().isEmpty()),
                () -> assertFalse(r.kpis().isEmpty()),
                () -> assertFalse(r.mitigacoes().isEmpty()),
                () -> assertTrue(r.mitigacoes().stream().anyMatch(m -> m.comoFunciona().contains("6528")
                                || m.nome().contains("6528")),
                        "a defesa raiz é o ISN imprevisível da RFC 6528"),
                // CONT-07/CALC-37 (RFC 5961 §3.2): RST FORA da janela é descartado; o challenge ACK é para o
                // RST dentro da janela com número inexato. A chance do palpite cego é janela/2^32.
                () -> assertTrue(r.passos().stream().noneMatch(p -> p.acao().contains("fora da janela → challenge")),
                        r.passos().toString()),
                () -> assertTrue(r.passos().stream().anyMatch(p -> p.detalhe().contains("descartado em silêncio"))),
                () -> assertTrue(r.kpis().stream().noneMatch(k -> k.valor().equals("1 em 2^32")), r.kpis().toString()));
    }

    @Test
    @DisplayName("tipo desconhecido, nulo ou em branco cai no cenário padrão (SYN flood)")
    void tipoDesconhecidoCaiNoPadrao() {
        assertAll(
                () -> assertEquals("syn-flood", service.simular("nao-existe").tipo()),
                () -> assertEquals("syn-flood", service.simular(null).tipo()),
                () -> assertEquals("syn-flood", service.simular("   ").tipo()),
                () -> assertEquals("sequence-hijack", service.simular("SEQUENCE-HIJACK").tipo(),
                        "o tipo é resolvido sem depender de caixa"));
    }
}
