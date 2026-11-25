package org.framework.net.analiseDidatica.application.cidr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;
import org.framework.net.telemetria.TelemetriaContext;

@ApplicationScoped
public class CidrModoService {

    private static final Logger LOG = Logger.getLogger(CidrModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    /**
     * Modo CIDR: prepara a análise do endereço com o prefixo informado.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> Modo CIDR: prepara a análise do endereço com o prefixo informado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> entrada inválida nunca vira resultado: o cálculo só segue com o que o kernel aceitou.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança por entrada inválida: o erro volta no {@link ModoResult},
     * com o campo marcado; a mensagem vai ao log neutralizada ({@code TelemetriaContext.paraLog}) — ela costuma
     * trazer o que a pessoa digitou, e crua forjava linha no log.</p>
     */
    public ModoResult processar(String ipP, String cidrRaw) {
        ModoResult result = new ModoResult();
        Integer cidrVal = null;
        String cidrOrigem = "";

        if (cidrRaw != null && !cidrRaw.isBlank()) {
            try {
                cidrVal = org.framework.net.shared.NumeroAscii.parseIntEstrito(cidrRaw.trim());
            } catch (NumberFormatException ex) {
                LOG.warnf("CIDR inválido no modo cidr: %s", TelemetriaContext.paraLog(cidrRaw, 64));
                result.setErro("O CIDR deve ser um número inteiro entre 0 e 32.");
                result.invalidFields().add("cidr");
            }
        } else if (ipP != null && !ipP.isBlank()) {
            try {
                Ipv4Kernel.InferenciaCidr inferencia = ipv4Kernel.inferirCidrPorIp(ipP);
                cidrVal = inferencia.cidr();
                cidrOrigem = "Campo CIDR vazio — prefixo (/barra) inferido pelo 1º octeto do IP "
                        + "(modelo classful didático). " + inferencia.descricaoOrigem();
            } catch (EntradaInvalidaException ex) {
                LOG.warnf("IP inválido no modo cidr: %s", TelemetriaContext.paraLog(ex.getMessage(), 512));
                result.setErro(ex.getMessage());
                result.invalidFields().add("ip");
            }
        } else {
            result.setErro(
                    "No modo CIDR, informe o endereço IPv4 e o CIDR (0–32), "
                            + "ou apenas o IPv4 para descobrir o / automaticamente pelo 1º octeto.");
            result.invalidFields().add("cidr");
            result.invalidFields().add("ip");
        }

        result.setCidrVal(cidrVal);
        result.setCidrOrigem(cidrOrigem);
        return result;
    }
}
