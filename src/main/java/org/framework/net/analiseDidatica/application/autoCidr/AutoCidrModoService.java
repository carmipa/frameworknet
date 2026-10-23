package org.framework.net.analiseDidatica.application.autoCidr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AutoCidrModoService {

    private static final Logger LOG = Logger.getLogger(AutoCidrModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

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
            LOG.warnf("IP inválido no modo autoip: %s", ex.getMessage());
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        }
        return result;
    }
}
