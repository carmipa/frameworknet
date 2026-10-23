package org.framework.net.certificados.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.certificados.domain.CatalogoCertificados;
import org.framework.net.certificados.domain.CertificadosCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * Caso de uso da aba Geral do módulo de Certificados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entregar as três tabelas de referência à
 * apresentação e registrar a abertura do catálogo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> catálogo ausente já derrubou a
 * aplicação no boot (o catálogo falha fechado); aqui não há caminho de erro.</p>
 */
@ApplicationScoped
public class CertificadosService {

    @Inject
    CertificadosCatalog catalogo;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public CatalogoCertificados montarCatalogoExibicao() {
        CatalogoCertificados c = catalogo.getCatalogo();
        telemetriaLogger.logEvent("info", "certificados", "catalog_load", Map.of(
                "formatos", c.formatos().size(),
                "campos", c.campos().size(),
                "tipos", c.tipos().size()));
        return c;
    }
}
