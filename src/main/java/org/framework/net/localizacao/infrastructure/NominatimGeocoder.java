package org.framework.net.localizacao.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geocodificação (endereço → coordenadas) via
 * <a href="https://nominatim.org/release-docs/latest/api/Search/">Nominatim/OpenStreetMap</a>.
 *
 * <p>A política de uso do Nominatim exige um {@code User-Agent} identificável e no máximo
 * ~1 req/s NO TOTAL — é o IP da VPS inteira que pode ser banido. Por isso: busca e reverso têm cache
 * (memória + L2) e toda chamada externa passa por um freio GLOBAL de uma por segundo, que ESPERA pelo
 * próximo slot livre até um teto curto; além do teto a chamada devolve {@code Optional.empty()} (o
 * chamador já mostra "sem endereço disponível") em vez de furar a política.
 */
@ApplicationScoped
public class NominatimGeocoder {

    private static final Logger LOG = Logger.getLogger(NominatimGeocoder.class);
    private static final int MAX_CACHE = 500;

    @ConfigProperty(name = "framework.localizacao.nominatim-url",
            defaultValue = "https://nominatim.openstreetmap.org/search")
    String nominatimUrl;

    @ConfigProperty(name = "framework.localizacao.user-agent", defaultValue = "FrameworkNetRedes/1.0")
    String userAgent;

    @ConfigProperty(name = "framework.localizacao.http-timeout-seconds", defaultValue = "5")
    int timeoutSeconds;

    @ConfigProperty(name = "framework.localizacao.cache-ttl-seconds", defaultValue = "86400")
    int cacheTtlSeconds;

    @Inject
    org.framework.net.shared.CacheDistribuido cacheDistribuido;

    @Inject
    ObjectMapper objectMapper;

    /** L1 com validade (OPS-17: sem expiração, endereço corrigido na origem nunca era visto). */
    private volatile org.framework.net.shared.CacheLocal<Map<String, Object>> cacheLocal;

    private org.framework.net.shared.CacheLocal<Map<String, Object>> cache() {
        if (cacheLocal == null) {
            synchronized (this) {
                if (cacheLocal == null) {
                    cacheLocal = new org.framework.net.shared.CacheLocal<>(MAX_CACHE,
                            Duration.ofSeconds(Math.max(1, cacheTtlSeconds)));
                }
            }
        }
        return cacheLocal;
    }
    private volatile HttpClient httpClient;

    private static final long JANELA_NANOS = 1_000_000_000L;

    @ConfigProperty(name = "framework.localizacao.nominatim-espera-maxima-ms", defaultValue = "1200")
    long esperaMaximaMs = 1200;

    /** Costuras de teste: relógio monotônico e sono. Produção usa {@code System.nanoTime} e parkNanos real. */
    interface Dormidor {
        void dormir(long nanos) throws InterruptedException;
    }

    java.util.function.LongSupplier relogio = System::nanoTime;
    Dormidor dormidor = nanos -> java.util.concurrent.TimeUnit.NANOSECONDS.sleep(nanos);

    /** Instante (nanoTime) do último slot de 1 s já reservado — freio global de 1 req/s. */
    private final java.util.concurrent.atomic.AtomicLong ultimaChamada =
            new java.util.concurrent.atomic.AtomicLong(System.nanoTime() - 2 * JANELA_NANOS);

    /**
     * Reserva o próximo slot livre de 1 s para uma chamada externa ao Nominatim e espera até ele.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> cumprir a política de uso do Nominatim (~1 req/s no total, sob pena
     * de o IP da VPS ser banido e o mapa sumir para todos) sem descartar a segunda chamada legítima que
     * chega no mesmo segundo — o fallback cidade/UF do CEP é exatamente esse caso.
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> dois slots reservados distam sempre ≥ 1 s (CAS sobre o último
     * slot, então chamadas concorrentes recebem slots sucessivos, nunca o mesmo); a espera de uma chamada
     * nunca passa de {@code esperaMaximaMs} — rajada que exigiria fila maior é recusada, não enfileirada
     * sem fim prendendo threads de trabalho.
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> devolve {@code false} quando o próximo slot livre fica além
     * da espera máxima, ou quando a thread é interrompida durante a espera (a interrupção é restaurada e o
     * slot reservado se perde, o que só reduz a vazão, nunca a excede). Não lança.
     */
    private boolean reservarJanela() {
        long limite = Math.max(0, esperaMaximaMs) * 1_000_000L;
        while (true) {
            long anterior = ultimaChamada.get();
            long agora = relogio.getAsLong();
            long slot = Math.max(agora, anterior + JANELA_NANOS);
            long espera = slot - agora;
            if (espera > limite) {
                return false;
            }
            if (ultimaChamada.compareAndSet(anterior, slot)) {
                if (espera <= 0) {
                    return true;
                }
                try {
                    dormidor.dormir(espera);
                    return true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /** Geocodifica a consulta (livre) restrita ao Brasil; {@code Optional.empty()} se nada for encontrado. */
    public Optional<Map<String, Object>> geocodificar(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String chave = query.strip().toLowerCase();
        Optional<Map<String, Object>> cached = cache().obter(chave);
        if (cached.isPresent()) {
            return Optional.of(new LinkedHashMap<>(cached.get()));
        }
        // L2: o Nominatim pede ~1 req/s por política de uso, e endereço é dado
        // estático. Perder o cache a cada deploy só gera tráfego desnecessário lá.
        Optional<Map<String, Object>> doL2 = lerDoCacheDistribuido(chave);
        if (doL2.isPresent()) {
            return doL2;
        }
        if (!reservarJanela()) {
            LOG.debug("Nominatim: busca freada (política de 1 req/s).");
            return Optional.empty();
        }
        try {
            String uri = nominatimUrl + "?format=jsonv2&limit=1&addressdetails=0&countrycodes=br&q="
                    + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "application/json")
                    .header("Accept-Language", "pt-BR")
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode arr = objectMapper.readTree(response.body());
            if (!arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = arr.get(0);
            String lat = first.path("lat").asText("");
            String lon = first.path("lon").asText("");
            if (lat.isBlank() || lon.isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lat", parseDouble(lat));
            out.put("lon", parseDouble(lon));
            out.put("display_name", first.path("display_name").asText(""));
            out.put("fonte", "OpenStreetMap/Nominatim");
            guardarCache(chave, out);
            return Optional.of(new LinkedHashMap<>(out));
        } catch (Exception ex) {
            LOG.warnf("Falha no geocoding Nominatim para \"%s\": %s", query, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Geocodificação reversa (coordenadas → endereço), usada pela localização via GPS do navegador. */
    public Optional<Map<String, Object>> reverse(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return Optional.empty();
        }
        // ~11 m de resolução: o mesmo ponto (ou vizinho imediato) não volta ao Nominatim.
        String chave = String.format(java.util.Locale.ROOT, "rev|%.4f|%.4f", lat, lon);
        Optional<Map<String, Object>> cached = cache().obter(chave);
        if (cached.isPresent()) {
            return Optional.of(new LinkedHashMap<>(cached.get()));
        }
        Optional<Map<String, Object>> doL2 = lerDoCacheDistribuido(chave);
        if (doL2.isPresent()) {
            return doL2;
        }
        if (!reservarJanela()) {
            LOG.debug("Nominatim: reverso freado (política de 1 req/s).");
            return Optional.empty();
        }
        try {
            String base = nominatimUrl.contains("/search")
                    ? nominatimUrl.replace("/search", "/reverse")
                    : "https://nominatim.openstreetmap.org/reverse";
            String uri = base + "?format=jsonv2&zoom=18&addressdetails=1&lat=" + lat + "&lon=" + lon;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "application/json")
                    .header("Accept-Language", "pt-BR")
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.body());
            if (data.has("error") || !data.has("display_name")) {
                return Optional.empty();
            }
            JsonNode addr = data.path("address");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lat", lat);
            out.put("lon", lon);
            out.put("display_name", data.path("display_name").asText(""));
            out.put("logradouro", addr.path("road").asText(""));
            out.put("bairro", firstNonBlank(addr, "suburb", "neighbourhood", "quarter"));
            out.put("cidade", firstNonBlank(addr, "city", "town", "village", "municipality"));
            out.put("uf", addr.path("state").asText(""));
            out.put("cep", addr.path("postcode").asText(""));
            out.put("fonte", "OpenStreetMap/Nominatim (reverse)");
            guardarCache(chave, out);
            return Optional.of(new LinkedHashMap<>(out));
        } catch (Exception ex) {
            LOG.warnf("Falha no reverse geocoding Nominatim (%s, %s): %s", lat, lon, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String firstNonBlank(JsonNode node, String... campos) {
        for (String c : campos) {
            String v = node.path(c).asText("");
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void guardarCache(String chave, Map<String, Object> value) {
        cache().guardar(chave, new LinkedHashMap<>(value));
        try {
            cacheDistribuido.guardar("nominatim", chave, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(Math.max(1, cacheTtlSeconds)));
        } catch (Exception ex) {
            // Cache é otimização: falhar aqui não pode invalidar a resposta já obtida.
            LOG.debugf("Nao foi possivel gravar %s no cache distribuido", chave);
        }
    }

    /**
     * Lê do cache distribuído e repovoa o cache em memória.
     *
     * <p><b>Comportamento em caso de falha:</b> valor ausente, ilegível ou Redis
     * indisponível devolvem {@link Optional#empty()} — o chamador segue para o
     * Nominatim como se não houvesse L2.</p>
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> lerDoCacheDistribuido(String chave) {
        var bruto = cacheDistribuido.obter("nominatim", chave);
        if (bruto.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> valor = objectMapper.readValue(bruto.get(), Map.class);
            cache().guardar(chave, new LinkedHashMap<>(valor));
            return Optional.of(new LinkedHashMap<>(valor));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private HttpClient client() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                            .build();
                }
            }
        }
        return httpClient;
    }
}
