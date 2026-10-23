package org.framework.net.telemetria;

public record TelemetriaRequestContext(
        String requestId,
        String traceId,
        String httpMethod,
        String httpPath,
        long startedAtMillis
) {

    public long elapsedMillis() {
        return Math.max(System.currentTimeMillis() - startedAtMillis, 0L);
    }
}
