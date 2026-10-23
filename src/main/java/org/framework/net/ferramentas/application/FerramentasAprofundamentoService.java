package org.framework.net.ferramentas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.ferramentas.domain.FerramentasAprofundamentoCatalog;
import org.framework.net.ferramentas.exception.FerramentasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Caso de uso do módulo Ferramentas: entrega a Geral e os aprofundamentos e registra a visita (módulo ferramentas). */
@ApplicationScoped
public class FerramentasAprofundamentoService {

    private static final String MODULO = "ferramentas";

    @Inject FerramentasAprofundamentoCatalog catalog;
    @Inject TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarCatalogo() {
        ProtocoloAprofundamento c = catalog.get(FerramentasAprofundamentoCatalog.CATALOGO);
        if (c == null) { throw new FerramentasException("Catálogo Geral não carregado."); }
        telemetriaLogger.logEvent("info", MODULO, "catalog_load", Map.of("tabelas", c.tabelas() == null ? 0 : c.tabelas().size()));
        return c;
    }

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) { throw new FerramentasException("Aprofundamento sem conteúdo carregado: " + slug); }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos() == null ? 0 : conteudo.conceitos().size());
        telemetriaLogger.logEvent("info", MODULO, "aprofundamento_view", campos);
        return conteudo;
    }
}
