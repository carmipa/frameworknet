package org.framework.net.analiseDidatica.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TimelineBuilder {

    private TimelineBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> montar(Map<String, Object> res) {
        if (res == null || Boolean.TRUE.equals(res.get("somente_mascara"))) {
            return null;
        }
        String papel = String.valueOf(res.getOrDefault("ip_papel", "")).toLowerCase();
        String posicao;
        if (papel.contains("rede")) {
            posicao = "rede";
        } else if (papel.contains("broadcast")) {
            posicao = "broadcast";
        } else {
            posicao = "hosts";
        }

        String ip = "";
        Object itens = res.get("resumo_prova_itens");
        if (itens instanceof List<?> lista && !lista.isEmpty() && lista.get(0) instanceof Map<?, ?> primeiro) {
            Object valor = primeiro.get("valor");
            if (valor != null) {
                ip = valor.toString();
            }
        }

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("rede", res.get("rede"));
        timeline.put("primeiro_host", res.get("primeiro_host"));
        timeline.put("ultimo_host", res.get("ultimo_host"));
        timeline.put("broadcast", res.get("broad"));
        timeline.put("ip", ip);
        timeline.put("posicao", posicao);
        return timeline;
    }
}
