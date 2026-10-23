package org.framework.net.protocolos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.domain.DnsAprofundamento;
import org.framework.net.protocolos.domain.DnsAprofundamentoCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso da página de aprofundamento do DNS.
 *
 * <p><b>Propósito de negócio:</b> entregar o conteúdo do DNS à apresentação e
 * registrar a visita na telemetria, pelo mesmo motivo do BGP e do SSH: separar
 * "abriu o catálogo" de "abriu o aprofundamento" é o que mostra se as páginas por
 * protocolo têm uso real.</p>
 *
 * <p><b>Invariantes do domínio:</b> o evento é atribuído ao módulo
 * {@code protocolos}, coerente com a atribuição de {@code /protocolos/dns} no
 * dashboard, e traz o campo {@code protocolo} para distinguir uma página da outra
 * dentro do mesmo módulo.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> sem caminho de erro próprio — o
 * conteúdo é validado no boot e falha de telemetria não derruba a página.</p>
 */
@ApplicationScoped
public class DnsAprofundamentoService {

    private static final String MODULO = "protocolos";
    private static final String EVENTO_VISITA = "aprofundamento_view";

    @Inject
    DnsAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Devolve o conteúdo da página e registra a visita.
     *
     * <p><b>Comportamento em caso de falha:</b> nenhum caminho de erro — o
     * conteúdo é imutável e validado no boot.</p>
     */
    public DnsAprofundamento carregarParaExibicao() {
        DnsAprofundamento conteudo = catalog.getConteudo();

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("protocolo", "dns");
        campos.put("conceitos", conteudo.conceitos().size());
        campos.put("ataques", conteudo.ataques().size());
        campos.put("mitigacoes", conteudo.mitigacoes().size());
        telemetriaLogger.logEvent("info", MODULO, EVENTO_VISITA, campos);

        return conteudo;
    }
}
