package org.framework.net.protocolos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.domain.BgpAprofundamento;
import org.framework.net.protocolos.domain.BgpAprofundamentoCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caso de uso da página de aprofundamento do BGP.
 *
 * <p><b>Propósito de negócio:</b> entregar o conteúdo do BGP à camada de
 * apresentação e registrar, na telemetria, que a página foi consultada. O painel
 * "por módulo" só mostra o que o acesso HTTP revela; o evento explícito é o que
 * permite responder "os alunos estão realmente abrindo o aprofundamento, ou só
 * passando pelo catálogo?" — que é a pergunta que justifica manter e ampliar as
 * páginas por protocolo.</p>
 *
 * <p><b>Invariantes do domínio:</b> o evento é sempre atribuído ao módulo
 * {@code protocolos} (nunca a um módulo próprio "bgp"), coerente com
 * {@code TelemetriaDashboardService.moduloDePath}, que credita
 * {@code /protocolos/bgp} a Protocolos. Módulo divergente entre o evento e a
 * rota faria o dashboard contar a mesma visita em dois lugares.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o conteúdo já foi validado na subida
 * pelo catálogo, então este serviço não tem caminho de erro próprio. Falha de
 * telemetria é absorvida por {@link TelemetriaLogger} e nunca derruba a página —
 * página didática indisponível por causa de log seria trocar o essencial pelo
 * acessório.</p>
 */
@ApplicationScoped
public class BgpAprofundamentoService {

    private static final String MODULO = "protocolos";
    private static final String EVENTO_VISITA = "aprofundamento_view";

    @Inject
    BgpAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Devolve o conteúdo da página e registra a visita.
     *
     * <p><b>Comportamento em caso de falha:</b> nenhum caminho de erro — o
     * conteúdo é imutável e validado no boot.</p>
     */
    public BgpAprofundamento carregarParaExibicao() {
        BgpAprofundamento conteudo = catalog.getConteudo();

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("protocolo", "bgp");
        campos.put("conceitos", conteudo.conceitos().size());
        campos.put("atributos", conteudo.atributos().size());
        campos.put("passosSelecao", conteudo.selecaoMelhorRota().size());
        campos.put("protecoes", conteudo.protecoes().size());
        telemetriaLogger.logEvent("info", MODULO, EVENTO_VISITA, campos);

        return conteudo;
    }
}
