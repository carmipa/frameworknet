package org.framework.net.telemetria;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converte eventos de telemetria de/para o formato <strong>OpenTelemetry OTLP/JSON</strong>
 * (Logs data model), o padrão da indústria para compartilhamento de telemetria.
 *
 * <p>Estrutura gerada: {@code LogsData → ResourceLogs[] → ScopeLogs[] → LogRecord[]}.
 * Referência: <a href="https://opentelemetry.io/docs/specs/otlp/">OTLP</a> e
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">Logs Data Model</a>.
 *
 * <p>Regras OTLP/JSON respeitadas aqui: inteiros de 64 bits ({@code timeUnixNano},
 * {@code intValue}) são serializados como <em>string</em>; {@code traceId}/{@code spanId}
 * usam codificação hexadecimal; cada valor de atributo é um {@code AnyValue}.
 */
public final class TelemetriaOtlpMapper {

    /** Schema URL do conjunto de convenções semânticas usadas nos atributos. */
    public static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.27.0";
    private static final String SCOPE_NAME = "org.framework.net.telemetria";
    private static final String FIELD_PREFIX = "framework.field.";

    /** Campos que já viram atributos dedicados — não devem ser duplicados em {@code framework.field.*}. */
    private static final Set<String> RESERVED_FIELDS = Set.of("status", "httpStatus", "durationMs");

    private TelemetriaOtlpMapper() {
    }

    // ------------------------------------------------------------------
    // Serialização: eventos → OTLP/JSON
    // ------------------------------------------------------------------

    /** Documento OTLP {@code LogsData} completo com um recurso e um escopo. */
    public static Map<String, Object> toLogsData(String serviceName, String serviceVersion,
                                                 List<TelemetriaEvent> eventos) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (eventos != null) {
            for (TelemetriaEvent e : eventos) {
                if (e != null) {
                    records.add(toLogRecord(e));
                }
            }
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("name", SCOPE_NAME);
        scope.put("version", serviceVersion == null ? "" : serviceVersion);

        Map<String, Object> scopeLogs = new LinkedHashMap<>();
        scopeLogs.put("scope", scope);
        scopeLogs.put("logRecords", records);

        List<Map<String, Object>> resourceAttrs = new ArrayList<>();
        resourceAttrs.add(keyValue("service.name", av(serviceName == null ? "" : serviceName)));
        resourceAttrs.add(keyValue("service.version", av(serviceVersion == null ? "" : serviceVersion)));
        resourceAttrs.add(keyValue("telemetry.sdk.name", av("framework-net-telemetria")));
        resourceAttrs.add(keyValue("telemetry.sdk.language", av("java")));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("attributes", resourceAttrs);

        Map<String, Object> resourceLogs = new LinkedHashMap<>();
        resourceLogs.put("resource", resource);
        resourceLogs.put("scopeLogs", List.of(scopeLogs));
        resourceLogs.put("schemaUrl", SCHEMA_URL);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("resourceLogs", List.of(resourceLogs));
        return root;
    }

    /** Um único {@code LogRecord} OTLP (usado também no stream NDJSON {@code .jsonl}). */
    public static Map<String, Object> toLogRecord(TelemetriaEvent e) {
        Map<String, Object> rec = new LinkedHashMap<>();
        String nano = toUnixNano(e.timestamp());
        rec.put("timeUnixNano", nano);
        rec.put("observedTimeUnixNano", nano);
        rec.put("severityNumber", severityNumber(e.level()));
        rec.put("severityText", severityText(e.level()));
        rec.put("body", av(e.message() == null ? "" : e.message()));

        List<Map<String, Object>> attrs = new ArrayList<>();
        if (e.id() != null) {
            attrs.add(keyValue("framework.event_id", av(e.id())));
        }
        if (e.modulo() != null) {
            attrs.add(keyValue("framework.module", av(e.modulo())));
        }
        if (e.evento() != null) {
            attrs.add(keyValue("event.name", av(e.evento())));
        }
        if (e.status() != null) {
            attrs.add(keyValue("framework.status", av(e.status())));
        }
        if (e.requestId() != null) {
            attrs.add(keyValue("framework.request_id", av(e.requestId())));
        }
        if (e.httpMethod() != null) {
            attrs.add(keyValue("http.request.method", av(e.httpMethod())));
        }
        if (e.httpPath() != null) {
            attrs.add(keyValue("http.route", av(e.httpPath())));
        }
        if (e.httpStatus() != null) {
            attrs.add(keyValue("http.response.status_code", av(e.httpStatus().longValue())));
        }
        if (e.durationMs() != null) {
            attrs.add(keyValue("framework.duration_ms", av(e.durationMs())));
        }
        if (e.fields() != null) {
            for (Map.Entry<String, Object> f : e.fields().entrySet()) {
                if (f.getKey() == null || RESERVED_FIELDS.contains(f.getKey())) {
                    continue;
                }
                attrs.add(keyValue(FIELD_PREFIX + f.getKey(), anyValue(f.getValue())));
            }
        }
        rec.put("attributes", attrs);

        if (isHex(e.traceId()) && e.traceId().length() >= 16) {
            rec.put("traceId", e.traceId());
            rec.put("spanId", e.traceId().substring(0, 16));
        }
        return rec;
    }

    // ------------------------------------------------------------------
    // Desserialização: OTLP/JSON → eventos
    // ------------------------------------------------------------------

    /** Lê de volta um documento {@code LogsData}; entradas inválidas são ignoradas. */
    public static List<TelemetriaEvent> fromLogsData(Map<String, Object> root) {
        List<TelemetriaEvent> out = new ArrayList<>();
        if (root == null || !(root.get("resourceLogs") instanceof List<?> resourceLogs)) {
            return out;
        }
        for (Object rlo : resourceLogs) {
            if (!(rlo instanceof Map<?, ?> rlm) || !(rlm.get("scopeLogs") instanceof List<?> scopeLogs)) {
                continue;
            }
            for (Object slo : scopeLogs) {
                if (!(slo instanceof Map<?, ?> slm) || !(slm.get("logRecords") instanceof List<?> logRecords)) {
                    continue;
                }
                for (Object lro : logRecords) {
                    if (lro instanceof Map<?, ?> recm) {
                        TelemetriaEvent e = fromLogRecord(recm);
                        if (e != null) {
                            out.add(e);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static TelemetriaEvent fromLogRecord(Map<?, ?> rec) {
        try {
            Instant ts = fromUnixNano(asString(rec.get("timeUnixNano")));
            String level = asString(rec.get("severityText"));
            String message = anyValueToString(rec.get("body"));
            Map<String, Object> attrs = readAttributes(rec.get("attributes"));

            String id = orElse(asString(attrs.remove("framework.event_id")), UUID.randomUUID().toString());
            String modulo = asString(attrs.remove("framework.module"));
            String evento = asString(attrs.remove("event.name"));
            String status = asString(attrs.remove("framework.status"));
            String requestId = asString(attrs.remove("framework.request_id"));
            String httpMethod = asString(attrs.remove("http.request.method"));
            String httpPath = asString(attrs.remove("http.route"));
            Integer httpStatus = toInteger(attrs.remove("http.response.status_code"));
            Long durationMs = toLong(attrs.remove("framework.duration_ms"));
            String traceId = asString(rec.get("traceId"));

            Map<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<String, Object> en : attrs.entrySet()) {
                if (en.getKey().startsWith(FIELD_PREFIX)) {
                    fields.put(en.getKey().substring(FIELD_PREFIX.length()), en.getValue());
                }
            }
            return new TelemetriaEvent(id, ts, level == null || level.isBlank() ? "INFO" : level,
                    modulo, evento, status, requestId, traceId, httpMethod, httpPath,
                    httpStatus, durationMs, message, fields);
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers OTLP
    // ------------------------------------------------------------------

    static int severityNumber(String level) {
        if (level == null) {
            return 9;
        }
        return switch (level.toUpperCase()) {
            case "TRACE" -> 1;
            case "DEBUG" -> 5;
            case "WARN", "WARNING" -> 13;
            case "ERROR" -> 17;
            case "FATAL" -> 21;
            default -> 9; // INFO
        };
    }

    static String severityText(String level) {
        if (level == null || level.isBlank()) {
            return "INFO";
        }
        String up = level.toUpperCase();
        return "WARNING".equals(up) ? "WARN" : up;
    }

    /** {@code timeUnixNano} como string (nanossegundos desde a época). */
    static String toUnixNano(Instant ts) {
        Instant t = ts == null ? Instant.now() : ts;
        long nanos = t.getEpochSecond() * 1_000_000_000L + t.getNano();
        return Long.toString(nanos);
    }

    static Instant fromUnixNano(String nano) {
        if (nano == null || nano.isBlank()) {
            return Instant.now();
        }
        long n = Long.parseLong(nano.trim());
        return Instant.ofEpochSecond(Math.floorDiv(n, 1_000_000_000L), Math.floorMod(n, 1_000_000_000L));
    }

    // ---- AnyValue builders ----
    static Map<String, Object> av(String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stringValue", value);
        return m;
    }

    static Map<String, Object> av(long value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intValue", Long.toString(value)); // OTLP/JSON: int64 como string
        return m;
    }

    static Map<String, Object> av(boolean value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("boolValue", value);
        return m;
    }

    static Map<String, Object> av(double value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("doubleValue", value);
        return m;
    }

    static Map<String, Object> anyValue(Object value) {
        if (value instanceof Boolean b) {
            return av(b.booleanValue());
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return av(((Number) value).longValue());
        }
        if (value instanceof Number n) {
            return av(n.doubleValue());
        }
        return av(value == null ? "" : String.valueOf(value));
    }

    private static Map<String, Object> keyValue(String key, Map<String, Object> value) {
        Map<String, Object> kv = new LinkedHashMap<>();
        kv.put("key", key);
        kv.put("value", value);
        return kv;
    }

    private static Map<String, Object> readAttributes(Object attributes) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(attributes instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> kv) {
                Object key = kv.get("key");
                if (key != null) {
                    out.put(String.valueOf(key), anyValueToObject(kv.get("value")));
                }
            }
        }
        return out;
    }

    private static Object anyValueToObject(Object anyValue) {
        if (!(anyValue instanceof Map<?, ?> m)) {
            return null;
        }
        if (m.containsKey("stringValue")) {
            return m.get("stringValue");
        }
        if (m.containsKey("boolValue")) {
            return m.get("boolValue");
        }
        if (m.containsKey("intValue")) {
            Object v = m.get("intValue");
            try {
                return Long.parseLong(String.valueOf(v));
            } catch (NumberFormatException ex) {
                return v;
            }
        }
        if (m.containsKey("doubleValue")) {
            return m.get("doubleValue");
        }
        return null;
    }

    private static String anyValueToString(Object anyValue) {
        Object v = anyValueToObject(anyValue);
        return v == null ? "" : String.valueOf(v);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return anyValueToString(value);
        }
        return String.valueOf(value);
    }

    private static Integer toInteger(Object value) {
        Long l = toLong(value);
        return l == null ? null : l.intValue();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isHex(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
