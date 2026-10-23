package org.framework.net.analiseDidatica.application.ipv6;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.Ipv6Calculator;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class Ipv6ModoService {

    private static final Logger LOG = Logger.getLogger(Ipv6ModoService.class);

    @Inject
    Ipv6Calculator ipv6Calculator;

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
            LOG.warnf("IPv6 inválido: %s", ex.getMessage());
            result.setErro(ex.getMessage());
            result.invalidFields().add("ipv6");
        }
        return result;
    }
}
