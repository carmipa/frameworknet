package org.framework.net.analiseDidatica.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AnaliseDidaticaUiSupport {

    public List<String> maskOctetos(String mask) {
        if (mask == null || !mask.contains(".")) {
            return List.of("0", "0", "0", "0");
        }
        return List.of(mask.split("\\."));
    }

    public void normalizarRes(Map<String, Object> res) {
        if (res == null) {
            return;
        }
        res.putIfAbsent("seguranca_dicas", List.of());
        res.putIfAbsent("and_table", List.of());
        res.putIfAbsent("proximas_subredes", List.of());
        res.putIfAbsent("tabela_referencia", List.of());
        res.putIfAbsent("tabela_conversao_bits", List.of());
        res.putIfAbsent("conversao_atual", List.of());
        res.putIfAbsent("resumo_prova_itens", List.of());
        res.putIfAbsent("classes_abc_fixas", List.of());
        res.putIfAbsent("grc_resumo", List.of());
    }

    public List<Map<String, Object>> octetosBits(String binRaw, String mask) {
        List<String> maskParts = maskOctetos(mask);
        String bits = binRaw == null ? "" : binRaw.replace(" ", "");
        if (bits.length() < 32) {
            bits = String.format("%32s", bits).replace(' ', '0');
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int o = 0; o < 4; o++) {
            List<Map<String, Object>> bitCols = new ArrayList<>();
            for (int b = 0; b < 8; b++) {
                int totalIdx = o * 8 + b;
                int powerTop = 31 - totalIdx;
                int weight = 1 << (7 - b);
                char bitVal = bits.charAt(Math.min(totalIdx, bits.length() - 1));
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("num", totalIdx + 1);
                col.put("power", powerTop);
                col.put("val", String.valueOf(bitVal));
                col.put("weight", weight);
                col.put("css", bitVal == '1' ? "bit-1" : "bit-0");
                bitCols.add(col);
            }
            Map<String, Object> oct = new LinkedHashMap<>();
            oct.put("numero", o + 1);
            oct.put("maskOctet", maskParts.get(o));
            oct.put("bits", bitCols);
            out.add(oct);
        }
        return out;
    }
}
