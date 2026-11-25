package org.framework.net.protocolos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;
import org.framework.net.protocolos.domain.ProtocolosAprofundamentoCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registra a resposta do teste rápido de um aprofundamento de protocolo (piloto de autoavaliação, proposta C03).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> saber se o teste é usado e em que pergunta as pessoas erram, para decidir se ele
 * cresce para os outros aprofundamentos. A contagem acontece no clique da resposta, e não na saída da página: medido
 * em 25/11/2026 (D22), o resumo de visita mandado na saída se perde quando a pessoa fecha o navegador.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só conta resposta de pergunta que existe no conteúdo do protocolo (slug com
 * teste rápido, pergunta e alternativa dentro das listas); quem decide se acertou é o servidor, pelo gabarito do
 * conteúdo, nunca o navegador; o evento leva só protocolo, pergunta, alternativa e acerto, nada de quem respondeu.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> protocolo sem teste rápido, campo ausente ou índice fora da lista devolve
 * {@link Optional#empty()} (o recurso responde 422, como a API de eventos da Academia); não lança.</p>
 */
@ApplicationScoped
public class AutoavaliacaoService {

    static final String MODULO = "protocolos";
    static final String EVENTO = "autoavaliacao_resposta";

    @Inject
    ProtocolosAprofundamentoCatalog catalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Confere a resposta contra o gabarito e conta o evento.
     *
     * @return se a alternativa escolhida é a correta; vazio se a resposta não corresponde a nenhuma pergunta
     */
    public Optional<Boolean> registrar(String protocolo, Integer pergunta, Integer alternativa) {
        if (protocolo == null || pergunta == null || alternativa == null) {
            return Optional.empty();
        }
        String slug = protocolo.strip().toLowerCase(Locale.ROOT);
        ProtocoloAprofundamento conteudo = catalog.get(slug);
        List<ProtocoloAprofundamento.Pergunta> perguntas = conteudo == null ? null : conteudo.autoavaliacao();
        if (perguntas == null || pergunta < 0 || pergunta >= perguntas.size()) {
            return Optional.empty();
        }
        ProtocoloAprofundamento.Pergunta p = perguntas.get(pergunta);
        if (p.alternativas() == null || alternativa < 0 || alternativa >= p.alternativas().size()) {
            return Optional.empty();
        }
        boolean acertou = alternativa == p.indiceCorreta();

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("protocolo", slug);
        campos.put("pergunta", pergunta);
        campos.put("alternativa", alternativa);
        campos.put("acertou", acertou);
        telemetriaLogger.logEvent("info", MODULO, EVENTO, campos);
        return Optional.of(acertou);
    }
}
