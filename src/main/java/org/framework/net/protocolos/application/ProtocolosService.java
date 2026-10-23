package org.framework.net.protocolos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.domain.ProtocoloItem;
import org.framework.net.protocolos.domain.ProtocolosCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProtocolosService {

    @Inject
    ProtocolosCatalog protocolosCatalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public List<ProtocoloItem> filtrarProtocolos(String query) {
        String termo = normalizar(query).toLowerCase(Locale.ROOT);
        if (termo.isEmpty()) {
            return protocolosCatalog.getCatalogo();
        }
        return protocolosCatalog.getCatalogo().stream()
                .filter(item -> contem(item.nome(), termo) || contem(item.funcao(), termo))
                .collect(Collectors.toList());
    }

    public Map<String, List<ProtocoloItemExibicao>> agruparPorCamada() {
        Map<String, List<ProtocoloItemExibicao>> grupos = new LinkedHashMap<>();
        for (ProtocoloItemExibicao item : montarProtocolosCatalogoExibicao()) {
            grupos.computeIfAbsent(item.camada(), key -> new java.util.ArrayList<>()).add(item);
        }
        return grupos;
    }

    public List<ProtocoloItemExibicao> montarProtocolosCatalogoExibicao() {
        telemetriaLogger.logEvent("info", "protocolos", "catalog_load",
                Map.of("total", protocolosCatalog.getCatalogo().size()));
        return protocolosCatalog.getCatalogo().stream()
                .map(ProtocoloItemExibicao::from)
                .collect(Collectors.toList());
    }

    public List<ProtocoloItemExibicao> montarTroubleshootingRoteamento() {
        return montarProtocolosCatalogoExibicao().stream()
                .filter(ProtocoloItemExibicao::roteamento)
                .collect(Collectors.toList());
    }

    private static boolean contem(String valor, String termo) {
        return normalizar(valor).toLowerCase(Locale.ROOT).contains(termo);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
