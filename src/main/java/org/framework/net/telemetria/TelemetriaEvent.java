package org.framework.net.telemetria;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record TelemetriaEvent(
        String id,
        Instant timestamp,
        String level,
        String modulo,
        String evento,
        String status,
        String requestId,
        String traceId,
        String httpMethod,
        String httpPath,
        Integer httpStatus,
        Long durationMs,
        String message,
        Map<String, Object> fields
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("timestamp", timestamp.toString());
        map.put("level", level);
        map.put("modulo", modulo);
        map.put("evento", evento);
        map.put("status", status);
        if (requestId != null) {
            map.put("requestId", requestId);
        }
        if (traceId != null) {
            map.put("traceId", traceId);
        }
        if (httpMethod != null) {
            map.put("httpMethod", httpMethod);
        }
        if (httpPath != null) {
            map.put("httpPath", httpPath);
        }
        if (httpStatus != null) {
            map.put("httpStatus", httpStatus);
        }
        if (durationMs != null) {
            map.put("durationMs", durationMs);
        }
        if (message != null && !message.isBlank()) {
            map.put("message", message);
        }
        if (fields != null && !fields.isEmpty()) {
            map.put("fields", fields);
        }
        return map;
    }
}
