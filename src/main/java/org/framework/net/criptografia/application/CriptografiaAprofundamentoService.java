package org.framework.net.criptografia.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.criptografia.domain.CriptografiaAprofundamentoCatalog;
import org.framework.net.criptografia.exception.CriptografiaException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Caso de uso do módulo Criptografia: entrega a Geral e os aprofundamentos e registra a visita (módulo criptografia). */
@ApplicationScoped
public class CriptografiaAprofundamentoService {

    private static final String MODULO = "criptografia";

    @Inject CriptografiaAprofundamentoCatalog catalog;
    @Inject TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarCatalogo() {
        ProtocoloAprofundamento c = catalog.get(CriptografiaAprofundamentoCatalog.CATALOGO);
        if (c == null) { throw new CriptografiaException("Catálogo Geral não carregado."); }
        telemetriaLogger.logEvent("info", MODULO, "catalog_load", Map.of("tabelas", c.tabelas() == null ? 0 : c.tabelas().size()));
        return c;
    }

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) { throw new CriptografiaException("Aprofundamento sem conteúdo carregado: " + slug); }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos() == null ? 0 : conteudo.conceitos().size());
        telemetriaLogger.logEvent("info", MODULO, "aprofundamento_view", campos);
        return conteudo;
    }
}
