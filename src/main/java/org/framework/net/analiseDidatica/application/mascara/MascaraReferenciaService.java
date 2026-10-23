package org.framework.net.analiseDidatica.application.mascara;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MascaraReferenciaService {

    @Inject
    Ipv4Kernel ipv4Kernel;

    public List<Map<String, Object>> tabela() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int prefix = 8; prefix <= 32; prefix++) {
            Map<String, Object> core = ipv4Kernel.coreMascara(prefix);
            if (core == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prefix", prefix);
            row.put("mask", core.get("mask"));
            row.put("hosts", core.get("uteis"));
            row.put("wildcard", core.get("wildcard"));
            row.put("usage", usoTipico(prefix));
            rows.add(row);
        }
        return rows;
    }

    private static String usoTipico(int prefix) {
        if (prefix <= 15) {
            return "Subrede ampla";
        }
        if (prefix <= 23) {
            return "Subrede corporativa";
        }
        if (prefix == 24) {
            return "LAN típica";
        }
        if (prefix <= 29) {
            return "LAN pequena";
        }
        if (prefix == 30) {
            return "Link WAN";
        }
        if (prefix == 31) {
            return "Ponto a ponto (RFC 3021)";
        }
        return "Host único";
    }
}
