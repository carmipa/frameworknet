package org.framework.net.analiseDidatica.application.wildcard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;
import org.framework.net.telemetria.TelemetriaContext;

@ApplicationScoped
public class WildcardModoService {

    private static final Logger LOG = Logger.getLogger(WildcardModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    /**
     * Modo Wildcard: prepara a análise a partir da wildcard.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> Modo Wildcard: prepara a análise a partir da wildcard.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> entrada inválida nunca vira resultado: o cálculo só segue com o que o kernel aceitou.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança por entrada inválida: o erro volta no {@link ModoResult},
     * com o campo marcado; a mensagem vai ao log neutralizada ({@code TelemetriaContext.paraLog}) — ela costuma
     * trazer o que a pessoa digitou, e crua forjava linha no log.</p>
     */
    public ModoResult processar(String ipP, String wildcardP) {
        ModoResult result = new ModoResult();
        Integer cidrVal = null;

        boolean ipVazio = ipP == null || ipP.isBlank();
        boolean wildVazio = wildcardP == null || wildcardP.isBlank();

        if (ipVazio && wildVazio) {
            result.setErro("No modo Wildcard, informe os dois campos: IP e wildcard mask.");
            result.invalidFields().add("ip");
            result.invalidFields().add("wildcard_mask");
        } else if (ipVazio) {
            result.setErro("No modo Wildcard, informe também o endereço IP.");
            result.invalidFields().add("ip");
        } else if (wildVazio) {
            result.setErro("No modo Wildcard, preencha também a wildcard mask (ex.: 0.0.15.255).");
            result.invalidFields().add("wildcard_mask");
        } else {
            cidrVal = ipv4Kernel.wildcardDottedParaCidr(wildcardP);
            if (cidrVal == null) {
                try {
                    ipv4Kernel.parseIpv4Parts(wildcardP, "Wildcard mask");
                    result.setErro(
                            "Wildcard inválida. Use formato x.x.x.x com inverso de máscara contígua "
                                    + "(ex.: 0.0.15.255).");
                } catch (EntradaInvalidaException ex) {
                    LOG.warnf("Wildcard inválida: %s", TelemetriaContext.paraLog(ex.getMessage(), 512));
                    result.setErro(ex.getMessage());
                }
                result.invalidFields().add("wildcard_mask");
            }
        }

        result.setCidrVal(cidrVal);
        return result;
    }
}
