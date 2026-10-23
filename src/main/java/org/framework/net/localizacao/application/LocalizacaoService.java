package org.framework.net.localizacao.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.infrastructure.geo.GeoLookupService;
import org.framework.net.localizacao.infrastructure.CepLookupService;
import org.framework.net.localizacao.infrastructure.NominatimGeocoder;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestra as duas fontes de localização:
 * <ul>
 *     <li><b>Por IP</b> — reaproveita o GeoIP existente (cidade/ISP/risco). Nível de precisão: cidade/provedor.</li>
 *     <li><b>Por CEP</b> — ViaCEP (endereço informado pelo usuário) + geocoding OSM para o mapa.</li>
 * </ul>
 *
 * <p><b>Enquadramento didático:</b> o IP <em>não</em> localiza a pessoa — entrega a cidade/registro do
 * provedor. O endereço preciso vem do CEP, que é fornecido pelo usuário, não derivado do IP.
 */
@ApplicationScoped
public class LocalizacaoService {

    @Inject
    GeoLookupService geoLookupService;

    @Inject
    CepLookupService cepLookupService;

    @Inject
    NominatimGeocoder nominatimGeocoder;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Localização aproximada por IP (cidade/ISP). Delega ao GeoIP já existente. */
    public Map<String, Object> localizarPorIp(String ip) {
        String alvo = ip == null ? "" : ip.strip();
        telemetriaLogger.logEvent("info", "localizacao", "lookup_ip",
                Map.of("ip", alvo.isBlank() ? "-" : alvo));
        Map<String, Object> geo = geoLookupService.lookupRegiaoGeografica(alvo);
        Map<String, Object> out = new LinkedHashMap<>(geo);
        out.put("origem", "ip");
        out.put("precisao", "cidade/provedor (o IP não identifica a residência da pessoa)");
        return out;
    }

    /** Endereço preciso por CEP (ViaCEP) + coordenadas para o mapa (Nominatim/OSM). */
    public Map<String, Object> localizarPorCep(String cep) {
        Map<String, Object> endereco = cepLookupService.buscar(cep);
        if (!Boolean.TRUE.equals(endereco.get("ok"))) {
            telemetriaLogger.logEvent("warn", "localizacao", "lookup_cep", "error",
                    Map.of("motivo", String.valueOf(endereco.getOrDefault("motivo", "-"))));
            return endereco;
        }
        // Tenta o endereço completo; se o Nominatim não achar, cai para cidade/UF (centra o mapa na cidade).
        Optional<Map<String, Object>> ponto = nominatimGeocoder.geocodificar(montarConsulta(endereco));
        if (ponto.isEmpty()) {
            ponto = nominatimGeocoder.geocodificar(montarConsultaCidade(endereco));
        }
        Map<String, Object> out = new LinkedHashMap<>(endereco);
        out.put("origem", "cep");
        if (ponto.isPresent()) {
            out.put("lat", ponto.get().get("lat"));
            out.put("lon", ponto.get().get("lon"));
            out.put("display_name", ponto.get().get("display_name"));
            out.put("geocoded", true);
        } else {
            out.put("geocoded", false);
            out.put("aviso", "Endereço encontrado, mas o mapa não conseguiu posicioná-lo agora: o serviço de mapas pode estar "
                    + "ocupado ou não conhecer esta rua. Tente de novo em alguns segundos.");
        }
        telemetriaLogger.logEvent("info", "localizacao", "lookup_cep",
                Map.of("cidade", String.valueOf(out.getOrDefault("cidade", "-")),
                        "geocoded", out.get("geocoded")));
        return out;
    }

    /** Localização exata por coordenadas do navegador (GPS) — consentida pelo usuário. */
    public Map<String, Object> localizarPorCoordenadas(double lat, double lon) {
        telemetriaLogger.logEvent("info", "localizacao", "lookup_gps",
                Map.of("lat", lat, "lon", lon));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("origem", "gps");
        out.put("lat", lat);
        out.put("lon", lon);
        nominatimGeocoder.reverse(lat, lon).ifPresentOrElse(
                out::putAll,
                () -> out.put("aviso", "Coordenadas obtidas, mas o endereço não pôde ser consultado agora "
                        + "(serviço de mapas ocupado ou indisponível). Tente de novo em alguns segundos."));
        // Reforça as coordenadas exatas do GPS (o reverse pode ajustar levemente).
        out.put("lat", lat);
        out.put("lon", lon);
        out.put("geocoded", true);
        return out;
    }

    private static String montarConsultaCidade(Map<String, Object> endereco) {
        String cidade = str(endereco.get("cidade"));
        String uf = str(endereco.get("uf"));
        StringBuilder sb = new StringBuilder();
        if (!cidade.isBlank()) {
            sb.append(cidade);
            if (!uf.isBlank()) {
                sb.append(" - ").append(uf);
            }
            sb.append(", ");
        }
        sb.append("Brasil");
        return sb.toString();
    }

    private static String montarConsulta(Map<String, Object> endereco) {
        StringBuilder sb = new StringBuilder();
        appendParte(sb, endereco.get("logradouro"));
        appendParte(sb, endereco.get("bairro"));
        String cidade = str(endereco.get("cidade"));
        String uf = str(endereco.get("uf"));
        if (!cidade.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(cidade);
            if (!uf.isBlank()) {
                sb.append(" - ").append(uf);
            }
        }
        appendParte(sb, endereco.get("cep"));
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append("Brasil");
        return sb.toString();
    }

    private static void appendParte(StringBuilder sb, Object valor) {
        String parte = str(valor);
        if (!parte.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(parte);
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
