package org.framework.net.analiseDidatica.application.comparador;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.ModoResult;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ComparadorModoService {

    @Inject
    Ipv4Kernel ipv4Kernel;

    public ModoResult processar(String ipP, String comparadorCidrA, String comparadorCidrB) {
        ModoResult result = new ModoResult();
        result.setComparadorOnly(true);
        List<Map<String, Object>> cards = new ArrayList<>();

        if (ipP == null || ipP.isBlank()) {
            result.setErro("No modo Comparador CIDR, informe um endereço IP.");
            result.invalidFields().add("ip");
            return result;
        }

        String[] cidrsTxt = {comparadorCidrA, comparadorCidrB};
        String[] fieldNames = {"comparador_cidr_a", "comparador_cidr_b"};
        for (int idx = 0; idx < cidrsTxt.length; idx++) {
            String cidrTxt = cidrsTxt[idx];
            if (org.framework.net.shared.NumeroAscii.inteiro(cidrTxt, 2).isEmpty()) {
                result.setErro("CIDR " + (idx + 1) + " do comparador deve ser número inteiro entre 0 e 32.");
                result.invalidFields().add(fieldNames[idx]);
                return result;
            }
            int cidrCmp = org.framework.net.shared.NumeroAscii.parseIntEstrito(cidrTxt);
            if (cidrCmp < 0 || cidrCmp > 32) {
                result.setErro("CIDR " + (idx + 1) + " do comparador deve estar entre 0 e 32.");
                result.invalidFields().add(fieldNames[idx]);
                return result;
            }
        }

        try {
            result.setComparadorIp(ipP);
            for (String cidrTxt : cidrsTxt) {
                int cidrCmp = org.framework.net.shared.NumeroAscii.parseIntEstrito(cidrTxt);
                Map<String, Object> cmpRes = ipv4Kernel.processar(ipP, cidrCmp, 5);
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("cidr", cidrCmp);
                card.put("mask", cmpRes.get("mask"));
                card.put("pulo", cmpRes.get("pulo"));
                card.put("uteis", cmpRes.get("uteis"));
                card.put("rede", cmpRes.get("rede"));
                card.put("broadcast", cmpRes.get("broad"));
                card.put("nivel_tema", cmpRes.get("nivel_tema"));
                cards.add(card);
            }
            result.setComparadorCards(cards);
        } catch (EntradaInvalidaException ex) {
            result.setErro(ex.getMessage());
            result.invalidFields().add("ip");
        }
        return result;
    }
}
