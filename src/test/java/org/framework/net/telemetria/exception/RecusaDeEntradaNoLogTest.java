package org.framework.net.telemetria.exception;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recusa de entrada (4xx) no log: uma linha WARN sem pilha; falha do servidor (5xx) continua ERROR com a pilha.
 * Medido no ar em 25/11/2026: cada pedido inválido no IPv6 virava ERROR com cerca de 20 linhas de pilha — sob o limite
 * global de pedidos, a janela de log (10 MB x 3) girava em minutos e apagava a evidência de incidente real.
 * A1 (fronteira): o mesmo tipo de exceção, mapeado para 500, continua com a pilha. A2: com o registro antigo nos
 * mapeadores, o evento do IPv6 sai ERROR e com exceção anexada.
 */
@QuarkusTest
class RecusaDeEntradaNoLogTest {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TelemetriaStore store;

    /** Registros da categoria do logger da telemetria enquanto a ação roda. */
    private static List<LogRecord> capturar(Runnable acao) {
        List<LogRecord> registros = new CopyOnWriteArrayList<>();
        Handler coletor = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                registros.add(registro);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger("framework.telemetria");
        jul.addHandler(coletor);
        try {
            acao.run();
        } finally {
            jul.removeHandler(coletor);
        }
        return new ArrayList<>(registros);
    }

    private TelemetriaEvent evento(String nome) {
        return store.snapshotEventos().stream().filter(e -> nome.equals(e.evento())).findFirst()
                .orElseThrow(() -> new AssertionError("evento não gravado: " + nome));
    }

    @Test
    void recusaDeEntradaViraUmaLinhaWarnSemPilhaEFalhaNossaContinuaComPilha() {
        String marca = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        List<LogRecord> recusa = capturar(() -> TelemetriaExceptionSupport.registrar(telemetriaLogger, "teste",
                "recusa_" + marca, null, new IllegalArgumentException("entrada recusada " + marca), 400));
        List<LogRecord> falha = capturar(() -> TelemetriaExceptionSupport.registrar(telemetriaLogger, "teste",
                "falha_" + marca, null, new IllegalArgumentException("falha nossa " + marca), 500));

        LogRecord linhaRecusa = recusa.stream().filter(r -> String.valueOf(r.getMessage()).contains("recusa_" + marca))
                .findFirst().orElseThrow(() -> new AssertionError("a recusa não foi para o log"));
        assertEquals(Level.WARNING.intValue(), linhaRecusa.getLevel().intValue(), "recusa de entrada não é ERROR");
        assertNull(linhaRecusa.getThrown(), "recusa de entrada não leva pilha ao log");
        TelemetriaEvent eventoRecusa = evento("recusa_" + marca);
        assertEquals("WARN", eventoRecusa.level());
        assertEquals("error", eventoRecusa.status(), "o status do incidente não muda: só o nível e a pilha");
        assertEquals("IllegalArgumentException", eventoRecusa.fields().get("erro"));
        assertEquals("400", String.valueOf(eventoRecusa.fields().get("httpStatus")));

        LogRecord linhaFalha = falha.stream().filter(r -> String.valueOf(r.getMessage()).contains("falha_" + marca))
                .findFirst().orElseThrow(() -> new AssertionError("a falha não foi para o log"));
        assertEquals(Level.SEVERE.intValue(), linhaFalha.getLevel().intValue(), "falha do servidor continua ERROR");
        assertNotNull(linhaFalha.getThrown(), "falha do servidor continua com a pilha");
        assertEquals("ERROR", evento("falha_" + marca).level());
    }

    /** Caminho real: a sumarização com 65 prefixos é recusada (400) e o registro sai WARN, sem pilha. */
    @Test
    void recusaDoIpv6NoArViraWarnSemPilha() {
        List<String> prefixos = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            prefixos.add("2001:db8:0:" + Integer.toHexString(i) + "::/64");
        }
        Instant antes = Instant.now();
        List<LogRecord> registros = capturar(() -> given()
                .header("HX-Request", "true")
                .contentType("application/x-www-form-urlencoded")
                .formParam("prefixos", String.join("\n", prefixos))
                .when().post("/ipv6/api/sumarizar")
                .then().statusCode(400));

        TelemetriaEvent evento = store.snapshotEventos().stream()
                .filter(e -> "ipv6".equals(e.modulo()) && "domain_exception".equals(e.evento()))
                .filter(e -> e.timestamp() != null && !e.timestamp().isBefore(antes))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a recusa do IPv6 não gerou evento"));
        assertEquals("WARN", evento.level(), "recusa de entrada registrada como " + evento.level());
        assertEquals("Ipv6Exception", evento.fields().get("erro"));
        assertTrue(String.valueOf(evento.fields().get("mensagem")).contains("64"), String.valueOf(evento.fields()));
        assertTrue(registros.stream().filter(r -> String.valueOf(r.getMessage()).contains("domain_exception"))
                        .allMatch(r -> r.getThrown() == null),
                "a recusa do IPv6 levou pilha ao log");
    }
}
