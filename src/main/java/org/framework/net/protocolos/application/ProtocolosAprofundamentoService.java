package org.framework.net.protocolos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.protocolos.domain.ProtocolosAprofundamentoCatalog;
import org.framework.net.protocolos.exception.ProtocolosException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso das páginas de aprofundamento GENÉRICAS (HTTP, FTP, SMTP, Telnet,
 * Handshake…).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> entregar o conteúdo do slug pedido à
 * apresentação e registrar a visita, no mesmo padrão dos aprofundamentos
 * dedicados: separar "abriu o catálogo" de "abriu o aprofundamento".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o evento é do módulo {@code protocolos}, com
 * o campo {@code protocolo} = slug, coerente com a atribuição de
 * {@code /protocolos/&lt;slug&gt;} no dashboard.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> slug sem conteúdo carregado lança
 * {@link ProtocolosException} — traduzida em erro pelo mapper, nunca conteúdo de
 * outro protocolo.</p>
 */
@ApplicationScoped
public class ProtocolosAprofundamentoService {

    private static final String MODULO = "protocolos";
    private static final String EVENTO_VISITA = "aprofundamento_view";

    @Inject
    ProtocolosAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public ProtocoloAprofundamento carregarParaExibicao(String slug) {
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        if (conteudo == null) {
            throw new ProtocolosException("Aprofundamento genérico sem conteúdo carregado: " + slug);
        }

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("protocolo", slug);
        campos.put("conceitos", conteudo.conceitos().size());
        campos.put("ataques", conteudo.ataques() == null ? 0 : conteudo.ataques().size());
        telemetriaLogger.logEvent("info", MODULO, EVENTO_VISITA, campos);

        return conteudo;
    }
}
