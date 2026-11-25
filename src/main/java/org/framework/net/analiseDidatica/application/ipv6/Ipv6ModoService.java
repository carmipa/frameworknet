package org.framework.net.analiseDidatica.application.ipv6;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.Ipv6Calculator;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;

import java.util.Map;
import org.framework.net.telemetria.TelemetriaContext;

@ApplicationScoped
public class Ipv6ModoService {

    private static final Logger LOG = Logger.getLogger(Ipv6ModoService.class);

    @Inject
    Ipv6Calculator ipv6Calculator;

    /**
     * Modo IPv6 da Análise: prepara a decomposição do endereço IPv6 digitado.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> Modo IPv6 da Análise: prepara a decomposição do endereço IPv6 digitado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> entrada inválida nunca vira resultado: o cálculo só segue com o que o kernel aceitou.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança por entrada inválida: o erro volta no {@link ModoResult},
     * com o campo marcado; a mensagem vai ao log neutralizada ({@code TelemetriaContext.paraLog}) — ela costuma
     * trazer o que a pessoa digitou, e crua forjava linha no log.</p>
     */
    public ModoResult processar(String ipv6P) {
        ModoResult result = new ModoResult();

        if (ipv6P == null || ipv6P.isBlank()) {
            result.setErro("No modo IPv6, informe um endereço IPv6 válido.");
            result.invalidFields().add("ipv6");
            return result;
        }

        try {
            Map<String, Object> ipv6Res = ipv6Calculator.processar(ipv6P);
            result.setIpv6Res(ipv6Res);
        } catch (EntradaInvalidaException ex) {
            LOG.warnf("IPv6 inválido: %s", TelemetriaContext.paraLog(ex.getMessage(), 512));
            result.setErro(ex.getMessage());
            result.invalidFields().add("ipv6");
        }
        return result;
    }
}
