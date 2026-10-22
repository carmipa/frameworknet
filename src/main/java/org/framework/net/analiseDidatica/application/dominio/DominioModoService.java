package org.framework.net.analiseDidatica.application.dominio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver;
import org.framework.net.analiseDidatica.support.HostnameNormalizer;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DominioModoService {

    private static final Logger LOG = Logger.getLogger(DominioModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    DnsResolver dnsResolver;

    public ModoResult processar(String ipEntradaBruta, String cidrRaw) {
        ModoResult result = new ModoResult();
        String dominioDigitado = HostnameNormalizer.normalizar(ipEntradaBruta);

        if (dominioDigitado.isEmpty()) {
            result.setErro(
                    "No modo Decompor Domínio para IP, informe um domínio/hostname (ex.: google.com).");
            result.invalidFields().add("ip");
            return result;
        }

        if (!dominioDigitado.contains(".")
                && !dominioDigitado.replace("-", "").chars().allMatch(Character::isLetterOrDigit)) {
            result.setErro("Domínio/hostname inválido. Use algo como google.com ou servidor.local.");
            result.invalidFields().add("ip");
            return result;
        }

        try {
            LOG.infof("Iniciando resolução DNS modo domínio: %s", dominioDigitado);
            String ipP = dnsResolver.resolverComCache(dominioDigitado);
            result.setIpP(ipP);

            if (cidrRaw != null && !cidrRaw.isBlank()) {
                int cidrVal = org.framework.net.shared.NumeroAscii.parseIntEstrito(cidrRaw.trim());
                result.setCidrVal(cidrVal);
                result.setCidrOrigem(
                        "Domínio '" + dominioDigitado + "' resolvido para " + ipP + ". CIDR informado manualmente.");
            } else {
                Ipv4Kernel.InferenciaCidr inferencia = ipv4Kernel.inferirCidrPorIp(ipP);
                result.setCidrVal(inferencia.cidr());
                result.setCidrOrigem(
                        "Domínio '" + dominioDigitado + "' resolvido para " + ipP + ". " + inferencia.descricaoOrigem() + ".");
            }
        } catch (NumberFormatException ex) {
            LOG.warn("CIDR inválido no modo domínio");
            result.setErro("No modo Domínio, o CIDR (se informado) deve ser um número inteiro entre 0 e 32.");
            result.invalidFields().add("cidr");
        } catch (DnsResolucaoException ex) {
            LOG.warnf("Falha DNS modo domínio: %s", ex.getMessage());
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        } catch (EntradaInvalidaException ex) {
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        }
        return result;
    }
}
