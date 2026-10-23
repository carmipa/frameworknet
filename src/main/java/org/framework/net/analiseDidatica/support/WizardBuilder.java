package org.framework.net.analiseDidatica.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WizardBuilder {

    private WizardBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> montar(Map<String, Object> res) {
        if (res == null || Boolean.TRUE.equals(res.get("somente_mascara"))) {
            return List.of();
        }
        String textoClasse = "Classe " + res.get("classe") + " (" + res.get("classe_faixa") + ").";
        Object obs = res.get("classe_observacao");
        if (obs != null && !obs.toString().isBlank()) {
            textoClasse += " " + obs;
        }
        List<Map<String, Object>> passos = new ArrayList<>();
        passos.add(passo("🧭", "Classe/faixa",
                "Identificar o 1º octeto (" + res.get("primeiro_octeto") + ")", textoClasse));
        passos.add(passo("📏", "Máscara",
                "Converter /" + res.get("cidr") + " para máscara",
                res.get("mask") + " (wildcard " + res.get("wildcard") + ")."));
        passos.add(passo("🧠", "Rede (AND)", "Aplicar IP & máscara",
                "Rede calculada: " + res.get("rede") + "."));
        passos.add(passo("📣", "Hosts/Broadcast", "Calcular intervalo de hosts",
                "1º útil " + res.get("primeiro_host") + " | último útil " + res.get("ultimo_host")
                        + " | broadcast " + res.get("broad") + "."));
        return passos;
    }

    private static Map<String, Object> passo(String icone, String etapa, String acao, String resultado) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("icone", icone);
        m.put("etapa", etapa);
        m.put("acao", acao);
        m.put("resultado", resultado);
        return m;
    }
}
