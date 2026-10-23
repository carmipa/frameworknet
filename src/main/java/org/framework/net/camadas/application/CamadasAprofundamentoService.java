package org.framework.net.camadas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.camadas.domain.CamadasAprofundamentoCatalog;
import org.framework.net.camadas.exception.CamadasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso do módulo de Camadas: entrega a Geral e os aprofundamentos e
 * registra a visita.
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> eventos do módulo {@code camadas}; o
 * aprofundamento emite {@code aprofundamento_view} com o campo {@code pagina} =
 * slug; a Geral emite {@code catalog_load}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> chave sem conteúdo lança
 * {@link CamadasException}.</p>
 */
@ApplicationScoped
public class CamadasAprofundamentoService {

    private static final String MODULO = "camadas";

    @Inject
    CamadasAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarCatalogo() {
        ProtocoloAprofundamento c = catalog.get(CamadasAprofundamentoCatalog.CATALOGO);
        if (c == null) {
            throw new CamadasException("Catálogo Geral de camadas não carregado.");
        }
        telemetriaLogger.logEvent("info", MODULO, "catalog_load",
                Map.of("tabelas", c.tabelas() == null ? 0 : c.tabelas().size()));
        return c;
    }

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) {
            throw new CamadasException("Aprofundamento de camadas sem conteúdo carregado: " + slug);
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos() == null ? 0 : conteudo.conceitos().size());
        telemetriaLogger.logEvent("info", MODULO, "aprofundamento_view", campos);
        return conteudo;
    }
}
