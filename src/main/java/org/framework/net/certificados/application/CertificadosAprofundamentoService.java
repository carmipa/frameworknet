package org.framework.net.certificados.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.certificados.domain.CertificadosAprofundamentoCatalog;
import org.framework.net.certificados.exception.CertificadosException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso das páginas de aprofundamento do módulo de Certificados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entregar o conteúdo do slug pedido à
 * apresentação e registrar a visita, separando "abriu o catálogo" de "abriu um
 * aprofundamento".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o evento é do módulo {@code certificados}, com
 * o campo {@code pagina} = slug, coerente com a atribuição de
 * {@code /certificados/&lt;slug&gt;} no dashboard.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> slug sem conteúdo carregado lança
 * {@link CertificadosException}.</p>
 */
@ApplicationScoped
public class CertificadosAprofundamentoService {

    private static final String MODULO = "certificados";
    private static final String EVENTO_VISITA = "aprofundamento_view";

    @Inject
    CertificadosAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) {
            throw new CertificadosException("Aprofundamento de certificados sem conteúdo carregado: " + slug);
        }

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos().size());
        campos.put("ataques", conteudo.ataques() == null ? 0 : conteudo.ataques().size());
        telemetriaLogger.logEvent("info", MODULO, EVENTO_VISITA, campos);

        return conteudo;
    }
}
