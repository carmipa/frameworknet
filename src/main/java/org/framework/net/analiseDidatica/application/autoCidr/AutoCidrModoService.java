package org.framework.net.analiseDidatica.application.autoCidr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;
import org.framework.net.telemetria.TelemetriaContext;

@ApplicationScoped
public class AutoCidrModoService {

    private static final Logger LOG = Logger.getLogger(AutoCidrModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    /**
     * Modo Auto-CIDR: deduz o prefixo a partir só do endereço digitado.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> Modo Auto-CIDR: deduz o prefixo a partir só do endereço digitado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> entrada inválida nunca vira resultado: o cálculo só segue com o que o kernel aceitou.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança por entrada inválida: o erro volta no {@link ModoResult},
     * com o campo marcado; a mensagem vai ao log neutralizada ({@code TelemetriaContext.paraLog}) — ela costuma
     * trazer o que a pessoa digitou, e crua forjava linha no log.</p>
     */
    public ModoResult processar(String ipP) {
        ModoResult result = new ModoResult();

        if (ipP == null || ipP.isBlank()) {
            result.setErro("No modo Descobrir CIDR do IP, informe um endereço IP.");
            result.invalidFields().add("ip");
            return result;
        }

        try {
            Ipv4Kernel.InferenciaCidr inferencia = ipv4Kernel.inferirCidrPorIp(ipP);
            result.setCidrVal(inferencia.cidr());
            result.setCidrOrigem(inferencia.descricaoOrigem());
        } catch (EntradaInvalidaException ex) {
            LOG.warnf("IP inválido no modo autoip: %s", TelemetriaContext.paraLog(ex.getMessage(), 512));
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        }
        return result;
    }
}
