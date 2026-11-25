package org.framework.net.analiseDidatica.application.dominio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver;
import org.framework.net.analiseDidatica.support.HostnameNormalizer;
import org.framework.net.shared.InputLimits;
import org.framework.net.telemetria.TelemetriaContext;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DominioModoService {

    private static final Logger LOG = Logger.getLogger(DominioModoService.class);

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    DnsResolver dnsResolver;

    /**
     * Resolve o domínio digitado no modo "Decompor Domínio para IP" e prepara o CIDR da análise.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno digita um nome (ou cola uma URL) e vê o IPv4 que ele publica,
     * já decomposto pela Análise.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nome acima de {@link InputLimits#MAX_NOME_HOST} caracteres é recusado
     * antes do DNS (não existe no DNS), com mensagem que diz se o problema é o tamanho do nome ou uma URL que não deu
     * para ler; o nome vai ao log neutralizado (quebra de linha não forja linha).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; entrada vazia, longa ou inválida, CIDR malformado e
     * falha de DNS voltam como erro no {@link ModoResult}, com o campo marcado.</p>
     */
    public ModoResult processar(String ipEntradaBruta, String cidrRaw) {
        ModoResult result = new ModoResult();
        String dominioDigitado = HostnameNormalizer.normalizar(ipEntradaBruta);

        if (dominioDigitado.isEmpty()) {
            result.setErro(
                    "No modo Decompor Domínio para IP, informe um domínio/hostname (ex.: google.com).");
            result.invalidFields().add("ip");
            return result;
        }

        if (dominioDigitado.length() > InputLimits.MAX_NOME_HOST) {
            // URL que o normalizador não conseguiu ler volta inteira; chamá-la de "nome longo" levava a pessoa a
            // encurtar o domínio, que nem era o problema.
            boolean urlIlegivel = dominioDigitado.chars().anyMatch(c -> "/?#:@".indexOf(c) >= 0 || Character.isWhitespace(c));
            result.setErro(urlIlegivel
                    ? "Não deu para ler o nome do domínio nesse endereço. Cole só o nome (ex.: google.com)."
                    : "Nome de domínio longo demais: o máximo é " + InputLimits.MAX_NOME_HOST
                            + " caracteres (RFC 1035). Confira o que foi colado.");
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
            LOG.infof("Iniciando resolução DNS modo domínio: %s",
                    TelemetriaContext.paraLog(dominioDigitado, InputLimits.MAX_NOME_HOST));
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
            LOG.warnf("Falha DNS modo domínio: %s", TelemetriaContext.paraLog(ex.getMessage(), 512));
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        } catch (EntradaInvalidaException ex) {
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        }
        return result;
    }
}
