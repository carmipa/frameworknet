package org.framework.net.analiseDidatica.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GrcService {

    public List<String> resumo(Map<String, Object> res) {
        if (res == null || Boolean.TRUE.equals(res.get("somente_mascara"))) {
            return List.of();
        }
        int cidr = ((Number) res.getOrDefault("cidr", 0)).intValue();
        long total = ((Number) res.getOrDefault("total", 0L)).longValue();
        String tipo = String.valueOf(res.getOrDefault("ip_tipo_privacidade", "N/A"));
        String risco = String.valueOf(res.getOrDefault("nivel_tema", "N/A"));
        String superficie = total >= 65536 ? "Alta" : total >= 256 ? "Média" : "Baixa";
        String recomendacao = cidr <= 16
                ? "Segmentar sub-redes e aplicar ACL por zona."
                : "Manter hardening e revisão periódica de regras.";
        List<String> linhas = new ArrayList<>();
        linhas.add("Risco atual: " + risco + " (" + tipo + ").");
        linhas.add("Superfície estimada: " + superficie + " (" + total + " IPs no bloco).");
        linhas.add("Recomendação objetiva: " + recomendacao);
        return linhas;
    }
}
