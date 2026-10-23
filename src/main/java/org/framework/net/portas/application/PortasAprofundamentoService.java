package org.framework.net.portas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.portas.domain.PortasAprofundamentoCatalog;
import org.framework.net.portas.exception.PortasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso das páginas de aprofundamento do módulo de Portas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entregar o conteúdo do slug pedido à
 * apresentação e registrar a visita, separando "abriu o catálogo de portas" de
 * "abriu um aprofundamento de portas".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o evento é do módulo {@code portas}, com o
 * campo {@code pagina} = slug, coerente com a atribuição de
 * {@code /portas/&lt;slug&gt;} no dashboard.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> slug sem conteúdo carregado lança
 * {@link PortasException} — traduzida em erro pelo mapper, nunca conteúdo de outra
 * página.</p>
 */
@ApplicationScoped
public class PortasAprofundamentoService {

    private static final String MODULO = "portas";
    private static final String EVENTO_VISITA = "aprofundamento_view";

    @Inject
    PortasAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) {
            throw new PortasException("Aprofundamento de portas sem conteúdo carregado: " + slug);
        }

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("pagina", slug);
        campos.put("conceitos", conteudo.conceitos().size());
        campos.put("ataques", conteudo.ataques() == null ? 0 : conteudo.ataques().size());
        telemetriaLogger.logEvent("info", MODULO, EVENTO_VISITA, campos);

        return conteudo;
    }
}
