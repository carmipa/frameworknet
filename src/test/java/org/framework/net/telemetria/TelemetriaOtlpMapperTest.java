package org.framework.net.telemetria;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetriaOtlpMapperTest {

    private TelemetriaEvent httpEvent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("httpStatus", 200);
        fields.put("durationMs", 3L);
        return new TelemetriaEvent(
                "evt-1",
                Instant.parse("2026-07-09T18:40:23.388699300Z"),
                "INFO", "web", "http_access", "ok",
                "8986c297", "8986c297f839412da2af1b00d155cb07",
                "GET", "/telemetria", 200, 3L,
                "HTTP GET /telemetria status=200", fields);
    }

    @Test
    void gerarEstruturaOtlpValida() {
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                "framework-net", "1.0.0", List.of(httpEvent()));

        assertTrue(root.containsKey("resourceLogs"));
        List<?> resourceLogs = (List<?>) root.get("resourceLogs");
        assertEquals(1, resourceLogs.size());
        Map<?, ?> rl = (Map<?, ?>) resourceLogs.get(0);
        assertNotNull(rl.get("resource"));
        List<?> scopeLogs = (List<?>) rl.get("scopeLogs");
        Map<?, ?> sl = (Map<?, ?>) scopeLogs.get(0);
        List<?> logRecords = (List<?>) sl.get("logRecords");
        Map<?, ?> record = (Map<?, ?>) logRecords.get(0);

        assertEquals("INFO", record.get("severityText"));
        assertEquals(9, record.get("severityNumber"));
        // timeUnixNano é string (int64 em OTLP/JSON)
        assertTrue(record.get("timeUnixNano") instanceof String);
        // traceId hex de 32 chars vira spanId de 16
        assertEquals("8986c297f839412da2af1b00d155cb07", record.get("traceId"));
        assertEquals("8986c297f839412d", record.get("spanId"));
    }

    @Test
    void roundTripPreservaCamposEssenciais() {
        TelemetriaEvent original = httpEvent();
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                "framework-net", "1.0.0", List.of(original));
        List<TelemetriaEvent> lidos = TelemetriaOtlpMapper.fromLogsData(root);

        assertEquals(1, lidos.size());
        TelemetriaEvent e = lidos.get(0);
        assertEquals("evt-1", e.id());
        assertEquals("web", e.modulo());
        assertEquals("http_access", e.evento());
        assertEquals("ok", e.status());
        assertEquals("GET", e.httpMethod());
        assertEquals("/telemetria", e.httpPath());
        assertEquals(200, e.httpStatus());
        assertEquals(3L, e.durationMs());
        assertEquals(original.timestamp(), e.timestamp());
        assertEquals("8986c297", e.requestId());
    }

    @Test
    void intValueSerializadoComoStringConformeOtlp() {
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                "framework-net", "1.0.0", List.of(httpEvent()));
        Map<?, ?> record = primeiroLogRecord(root);
        List<?> attrs = (List<?>) record.get("attributes");
        boolean achouStatusCode = false;
        for (Object a : attrs) {
            Map<?, ?> kv = (Map<?, ?>) a;
            if ("http.response.status_code".equals(kv.get("key"))) {
                Map<?, ?> value = (Map<?, ?>) kv.get("value");
                assertEquals("200", value.get("intValue")); // string, não número
                achouStatusCode = true;
            }
        }
        assertTrue(achouStatusCode, "atributo http.response.status_code ausente");
    }

    @Test
    void naoDuplicaCamposReservadosNosAtributos() {
        Map<String, Object> root = TelemetriaOtlpMapper.toLogsData(
                "framework-net", "1.0.0", List.of(httpEvent()));
        Map<?, ?> record = primeiroLogRecord(root);
        List<?> attrs = (List<?>) record.get("attributes");
        for (Object a : attrs) {
            Map<?, ?> kv = (Map<?, ?>) a;
            String key = String.valueOf(kv.get("key"));
            assertFalse(key.equals("framework.field.httpStatus"), "httpStatus não deve virar framework.field.*");
            assertFalse(key.equals("framework.field.durationMs"), "durationMs não deve virar framework.field.*");
        }
    }

    @Test
    void documentoVazioNaoQuebraLeitura() {
        assertTrue(TelemetriaOtlpMapper.fromLogsData(Map.of()).isEmpty());
        assertTrue(TelemetriaOtlpMapper.fromLogsData(null).isEmpty());
    }

    private Map<?, ?> primeiroLogRecord(Map<String, Object> root) {
        List<?> resourceLogs = (List<?>) root.get("resourceLogs");
        Map<?, ?> rl = (Map<?, ?>) resourceLogs.get(0);
        List<?> scopeLogs = (List<?>) rl.get("scopeLogs");
        Map<?, ?> sl = (Map<?, ?>) scopeLogs.get(0);
        List<?> logRecords = (List<?>) sl.get("logRecords");
        return (Map<?, ?>) logRecords.get(0);
    }
}
