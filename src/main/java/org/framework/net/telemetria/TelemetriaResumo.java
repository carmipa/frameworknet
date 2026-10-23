package org.framework.net.telemetria;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TelemetriaResumo(
        String projeto,
        String versao,
        Instant atualizadoEm,
        int totalEventos,
        Map<String, Long> contagemPorModulo,
        Map<String, Long> contagemPorNivel,
        List<TelemetriaEvent> ultimosEventos
) {
}
