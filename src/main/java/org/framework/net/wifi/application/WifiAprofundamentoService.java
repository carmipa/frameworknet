package org.framework.net.wifi.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.wifi.domain.WifiAprofundamentoCatalog;
import org.framework.net.wifi.exception.WifiException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Caso de uso do módulo Wifi: entrega a Geral e os aprofundamentos e registra a visita (módulo wifi). */
@ApplicationScoped
public class WifiAprofundamentoService {

    private static final String MODULO = "wifi";

    @Inject WifiAprofundamentoCatalog catalog;
    @Inject TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarCatalogo() {
        ProtocoloAprofundamento c = catalog.get(WifiAprofundamentoCatalog.CATALOGO);
        if (c == null) { throw new WifiException("Catálogo Geral não carregado."); }
        telemetriaLogger.logEvent("info", MODULO, "catalog_load", Map.of("tabelas", c.tabelas() == null ? 0 : c.tabelas().size()));
        return c;
    }

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) { throw new WifiException("Aprofundamento sem conteúdo carregado: " + slug); }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos() == null ? 0 : conteudo.conceitos().size());
        telemetriaLogger.logEvent("info", MODULO, "aprofundamento_view", campos);
        return conteudo;
    }
}
