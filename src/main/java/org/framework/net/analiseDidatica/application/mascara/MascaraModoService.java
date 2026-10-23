package org.framework.net.analiseDidatica.application.mascara;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MascaraModoService {

    private static final Logger LOG = Logger.getLogger(MascaraModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    public ModoResult processar(String ipP, String maskDecP) {
        ModoResult result = new ModoResult();
        Integer cidrVal = null;
        String cidrOrigem = "";
        boolean forcarSomenteMascara = false;

        Integer cidrI = (ipP != null && !ipP.isBlank()) ? ipv4Kernel.mascaraDottedParaCidr(ipP) : null;
        Integer cidrM = (maskDecP != null && !maskDecP.isBlank()) ? ipv4Kernel.mascaraDottedParaCidr(maskDecP) : null;

        if ((maskDecP == null || maskDecP.isBlank()) && (ipP == null || ipP.isBlank())) {
            result.setErro(
                    "No modo Máscara Decimal, informe a máscara contígua (ex.: 255.255.255.240). "
                            + "Esta aba é só para análise da máscara/prefixo; com IP + máscara use a aba CIDR.");
            result.invalidFields().add("mask_decimal");
            result.invalidFields().add("ip");
        } else if (maskDecP != null && !maskDecP.isBlank() && cidrM == null) {
            try {
                ipv4Kernel.parseIpv4Parts(maskDecP, "Máscara decimal");
                result.setErro(
                        "Máscara decimal inválida. Use máscara contígua "
                                + "(ex.: 255.255.255.0), não valores como 255.0.255.0.");
            } catch (EntradaInvalidaException ex) {
                LOG.warnf("Máscara inválida: %s", ex.getMessage());
                result.setErro(ex.getMessage());
            }
            result.invalidFields().add("mask_decimal");
        } else if ((maskDecP == null || maskDecP.isBlank()) && ipP != null && !ipP.isBlank()) {
            if (cidrI != null) {
                cidrVal = cidrI;
                cidrOrigem = "O valor no campo “Endereço IPv4” é uma máscara contígua (→ /" + cidrVal + "). "
                        + "Dica: coloque a máscara no campo Máscara ou deixe o IP vazio — o / (barra) da aula é o do exercício.";
                forcarSomenteMascara = true;
            } else {
                try {
                    Ipv4Kernel.InferenciaCidr inferencia = ipv4Kernel.inferirCidrPorIp(ipP);
                    cidrVal = inferencia.cidr();
                    cidrOrigem = "CIDR inferido automaticamente pelo IP informado. " + inferencia.descricaoOrigem() + ".";
                } catch (EntradaInvalidaException ex) {
                    LOG.warnf("IP inválido no modo máscara: %s", ex.getMessage());
                    result.setErro(ex.getMessage());
                    result.invalidFields().add("ip");
                }
            }
        } else if (maskDecP != null && !maskDecP.isBlank() && (ipP == null || ipP.isBlank())) {
            cidrVal = cidrM;
            cidrOrigem = "Máscara " + maskDecP + " convertida para /" + cidrVal + ".";
        } else {
            if (cidrI != null && cidrM != null && !cidrI.equals(cidrM)) {
                if (ipP.strip().startsWith("255.")) {
                    cidrVal = cidrI;
                    cidrOrigem = "Conflito: o / (barra) usado na aula é /" + cidrVal
                            + " (máscara 255.x no campo IP, p. ex. /18). "
                            + "O campo Máscara decimal apontava para /" + cidrM + " — deixe só um conjunto coerente.";
                    forcarSomenteMascara = true;
                } else {
                    cidrVal = cidrM;
                    cidrOrigem = "Usando /" + cidrVal + " a partir do campo Máscara decimal. "
                            + "O endereço " + ipP + " também se lê como máscara (→ /" + cidrI + ") — use um host (ex.: 10.0.0.1) "
                            + "se o exercício for o AND com a máscara do outro campo.";
                }
            } else if (cidrI != null && cidrM != null) {
                cidrVal = cidrM;
                cidrOrigem = "Máscara " + maskDecP + " (e o valor no IP) → /" + cidrVal + ".";
            } else {
                cidrVal = cidrM;
                cidrOrigem = "Máscara " + maskDecP + " → /" + cidrVal + " (rede calculada com o IP " + ipP + ").";
            }
        }

        result.setCidrVal(cidrVal);
        result.setCidrOrigem(cidrOrigem);
        result.setForcarSomenteMascara(forcarSomenteMascara);
        return result;
    }
}
