package org.framework.net.analiseDidatica.infrastructure.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.config.GeoConfig;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GeoLookupService {

    private static final Logger LOG = Logger.getLogger(GeoLookupService.class);
    /** Teto de entradas do cache geo. Impede crescimento ilimitado dirigido pelo IP do visitante. */
    private static final int MAX_CACHE = 1_000;
    private static final Set<String> ALTO = Set.of("CN", "RU", "KP", "IR", "BY", "SY", "CU");
    private static final Set<String> MEDIO = Set.of("VE", "NG", "GH", "PK", "BD", "TR", "RO", "UA", "VN", "ID", "TH", "IN", "BR");
    private static final Map<String, String> NOMES_PT = Map.ofEntries(
            Map.entry("United States", "Estados Unidos"),
            Map.entry("Brazil", "Brasil"),
            Map.entry("Australia", "Austrália"),
            Map.entry("China", "China"),
            Map.entry("Russia", "Rússia"),
            Map.entry("Germany", "Alemanha"),
            Map.entry("France", "França"),
            Map.entry("United Kingdom", "Reino Unido"),
            Map.entry("Japan", "Japão"),
            Map.entry("Canada", "Canadá"),
            Map.entry("India", "Índia"),
            Map.entry("Spain", "Espanha"),
            Map.entry("Italy", "Itália"),
            Map.entry("Mexico", "México"),
            Map.entry("Argentina", "Argentina"),
            Map.entry("Portugal", "Portugal")
    );

    private static final Map<String, String> BANDEIRAS = Map.ofEntries(
            Map.entry("BR", "🇧🇷"),
            Map.entry("US", "🇺🇸"),
            Map.entry("DE", "🇩🇪"),
            Map.entry("JP", "🇯🇵"),
            Map.entry("GB", "🇬🇧"),
            Map.entry("FR", "🇫🇷"),
            Map.entry("CN", "🇨🇳"),
            Map.entry("RU", "🇷🇺"),
            Map.entry("AU", "🇦🇺"),
            Map.entry("CA", "🇨🇦"),
            Map.entry("AR", "🇦🇷"),
            Map.entry("MX", "🇲🇽"),
            Map.entry("PT", "🇵🇹"),
            Map.entry("NL", "🇳🇱"),
            Map.entry("ES", "🇪🇸"),
            Map.entry("IT", "🇮🇹"),
            Map.entry("IN", "🇮🇳"),
            Map.entry("UA", "🇺🇦"),
            Map.entry("TR", "🇹🇷"),
            Map.entry("VE", "🇻🇪"),
            Map.entry("NG", "🇳🇬")
    );

    @Inject
    GeoConfig geoConfig;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    org.framework.net.shared.CacheDistribuido cacheDistribuido;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile DatabaseReader databaseReader;
    private volatile boolean readerInitialized;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public Map<String, Object> lookupRegiaoGeografica(String ip) {
        String normalized = ip == null ? "" : ip.strip();
        telemetriaLogger.logEvent("info", "analiseDidatica", "geo_lookup",
                Map.of("status", "start", "ip", normalized.isBlank() ? "-" : normalized));
        if (normalized.isEmpty()) {
            return enriquecerRespostaGeo(erroBase("empty", "IP não identificado.", ""));
        }

        try {
            String entradaOriginal = normalized;
            // Só IP LITERAL: com nome de host o getByName resolvia DNS — oráculo de nomes de container
            // (existe × não existe) e thread presa em DNS lento. Literal não gera consulta DNS.
            Optional<InetAddress> literal = org.framework.net.shared.IpLiteral.ler(normalized);
            if (literal.isEmpty()) {
                return enriquecerRespostaGeo(erroBase("invalid", "Endereço IP inválido.", normalized));
            }
            InetAddress addr = literal.get();
            normalized = addr instanceof java.net.Inet6Address
                    ? new inet.ipaddr.IPAddressString(addr.getHostAddress()).getAddress().toCanonicalString()   // RFC 5952
                    : addr.getHostAddress();
            // Mesma classificação da guarda de rede (fonte única): ULA (fc00::/7), documentação (RFC 5737 e
            // 2001:db8::/32), CGNAT e afins são não roteáveis. Antes só o RFC 1918/loopback era reconhecido, e
            // fd00::1 ou 192.0.2.1 iam ao provedor e voltavam como "tente novamente" (BACK-08).
            if (!org.framework.net.shared.NetworkAddressGuard.ehPublico(addr)) {
                String msg = "Endereço local, privado ou reservado (RFC 1918, ULA, documentação, loopback, etc.) — "
                        + "não há geolocalização pública para este IP.";
                // Devolve o que o usuário digitou, NUNCA o endereço resolvido: ecoar o
                // resultado da resolução transformava este endpoint em oráculo de DNS —
                // consultar "framework-net-java" respondia 172.22.0.2 e permitia mapear
                // a rede Docker interna por tentativa de nome de container.
                Map<String, Object> priv = erroBase("private_or_local", msg, entradaOriginal);
                priv.put("reservado", true);
                priv.put("valido", true);
                priv.put("tipo", addr.getAddress().length == 4 ? "IPv4" : "IPv6");
                return enriquecerRespostaGeo(priv);
            }
        } catch (Exception ex) {
            return enriquecerRespostaGeo(erroBase("invalid", "Endereço IP inválido.", normalized));
        }

        Map<String, Object> cached = cacheGet(normalized);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> dados = lookupMaxMind(normalized);
        if (geoConfig.usarFallback() && (dados == null || codigoPaisInvalido(String.valueOf(dados.getOrDefault("pais_codigo", ""))))) {
            LOG.infof("Geo usando fallback HTTP para %s", normalized);
            Map<String, Object> fallback = fallbackIpApi(normalized);
            if (fallback != null) {
                dados = mergeFontes(dados, fallback);
            }
        }

        if (dados == null || codigoPaisInvalido(String.valueOf(dados.getOrDefault("pais_codigo", "")))) {
            Map<String, Object> net = erroBase("network",
                    "Não foi possível obter geolocalização para este IP. Tente novamente em instantes.", normalized);
            net.put("valido", true);
            return enriquecerRespostaGeo(net);
        }

        Map<String, Object> out = enriquecerRespostaGeo(montarSucesso(normalized, dados));
        cacheSet(normalized, out);
        return out;
    }

    public Optional<String> normalizarIpDigitado(String texto) {
        String raw = texto == null ? "" : texto.strip();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String ip = raw;
        for (String prefix : new String[]{"https://", "http://", "ftp://"}) {
            if (ip.toLowerCase().startsWith(prefix)) {
                ip = ip.substring(prefix.length());
            }
        }
        for (String sep : new String[]{"/", "?", "#", " "}) {
            int idx = ip.indexOf(sep);
            if (idx >= 0) {
                ip = ip.substring(0, idx);
            }
        }
        ip = ip.strip().replace("[", "").replace("]", "");
        if (ip.chars().filter(ch -> ch == ':').count() == 1 && !raw.contains("://")) {
            ip = ip.substring(0, ip.lastIndexOf(':'));
        }
        ip = ip.strip();
        if (ip.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseLiteralIp(ip));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String parseLiteralIp(String ip) throws java.net.UnknownHostException {
        return org.framework.net.shared.IpLiteral.ler(ip)
                .map(InetAddress::getHostAddress)
                .orElseThrow(() -> new java.net.UnknownHostException("Somente literais IPv4/IPv6 são aceitos."));
    }

    public String mensagemIpInvalido() {
        return "Endereço IP inválido. Use um IPv4 ou IPv6 válido.";
    }

    public Map<String, Object> enriquecerRespostaGeo(Map<String, Object> out) {
        Map<String, Object> d = new LinkedHashMap<>(out);
        if (!Boolean.TRUE.equals(d.get("ok")) && "private_or_local".equals(String.valueOf(d.get("motivo")))) {
            d.put("reservado", true);
        }
        boolean reservado = Boolean.TRUE.equals(d.get("reservado"));
        boolean ok = Boolean.TRUE.equals(d.get("ok"));

        if (reservado) {
            d.put("pais", "");
            d.put("codigo_pais", "");
            d.put("pais_codigo", "");
            d.put("regiao", "");
            d.put("cidade", "");
            d.put("lat", null);
            d.put("lon", null);
            d.put("latitude", null);
            d.put("longitude", null);
            d.put("timezone", "");
            d.put("isp", "—");
            d.put("org", "");
            d.put("as_name", "");
            d.put("cidr", "");
            d.put("as_cidr", "");
            d.put("maps_url", "");
            d.put("fonte", "");
            d.put("proxy", false);
            d.put("hosting", false);
            d.put("mobile", false);
        }

        String cc = reservado ? "" : String.valueOf(d.getOrDefault("codigo_pais", d.getOrDefault("pais_codigo", ""))).strip().toUpperCase();
        Object lat = d.get("latitude");
        Object lon = d.get("longitude");
        if (lat == null && d.get("lat") != null) {
            lat = d.get("lat");
            d.put("latitude", lat);
        }
        if (lon == null && d.get("lon") != null) {
            lon = d.get("lon");
            d.put("longitude", lon);
        }
        if (!reservado) {
            d.put("pais_codigo", d.getOrDefault("pais_codigo", cc));
            d.put("codigo_pais", d.getOrDefault("codigo_pais", cc));
        }

        d.putIfAbsent("org", "");
        d.putIfAbsent("as_name", "");
        d.putIfAbsent("timezone", "");
        d.putIfAbsent("proxy", false);
        d.putIfAbsent("hosting", false);
        d.putIfAbsent("mobile", false);
        d.putIfAbsent("as_cidr", "");
        d.putIfAbsent("cidr", "");

        d.put("pais_bandeira", bandeira(cc, reservado));

        if (reservado) {
            d.put("proxy_flag", "🏠 Rede local (sem rota pública)");
            d.put("hosting_flag", "");
            d.put("mobile_flag", "");
            d.put("maps_url", "");
            String ipTxt = String.valueOf(d.getOrDefault("ip", "")).strip();
            d.put("reservado_motivo", d.getOrDefault("reservado_motivo", motivoReservado(ipTxt)));
            d.put("risco_nivel", "N/A");
            d.put("risco_badge", "🏠 Rede local");
            d.put("risco_badge_color", "secondary");
            d.put("risco_recomendacao",
                    "Classificação: " + d.get("reservado_motivo") + ". Endereço não roteável na Internet — "
                            + "geolocalização pública não aplicável.");
        } else {
            d.put("proxy_flag", Boolean.TRUE.equals(d.get("proxy")) ? "🔴 Proxy/VPN detectado" : "🟢 Conexão direta");
            d.put("hosting_flag", Boolean.TRUE.equals(d.get("hosting")) ? "🟡 Datacenter / Hosting" : "");
            d.put("mobile_flag", Boolean.TRUE.equals(d.get("mobile")) ? "📱 Rede móvel (operadora)" : "");
            if (lat != null && lon != null) {
                d.put("maps_url", "https://www.google.com/maps?q=" + lat + "," + lon + "&z=10");
            } else {
                d.putIfAbsent("maps_url", "");
            }
            if (ok && !cc.isBlank()) {
                Map<String, String> riscoMap = risco(cc);
                d.put("risco_nivel", riscoMap.get("nivel"));
                d.put("risco_badge", riscoMap.get("badge"));
                d.put("risco_badge_color", riscoMap.get("badge_color"));
                d.put("risco_recomendacao", riscoMap.get("recomendacao"));
            } else {
                d.putIfAbsent("risco_nivel", "Desconhecido");
                d.putIfAbsent("risco_badge", "⚫ Indisponível");
                d.putIfAbsent("risco_badge_color", "secondary");
                d.putIfAbsent("risco_recomendacao", "Não foi possível classificar o risco para esta consulta.");
            }
        }

        String motivo = String.valueOf(d.getOrDefault("motivo", ""));
        if ("invalid".equals(motivo)) {
            d.put("erro", d.getOrDefault("erro", d.getOrDefault("mensagem", "Endereço IP inválido.")));
        } else if ("empty".equals(motivo)) {
            d.put("erro", d.getOrDefault("erro", d.getOrDefault("mensagem", "IP não identificado.")));
        } else {
            d.putIfAbsent("erro", null);
        }

        if ("—".equals(String.valueOf(d.get("regiao")))) {
            d.put("regiao", "");
        }
        if ("—".equals(String.valueOf(d.get("cidade")))) {
            d.put("cidade", "");
        }
        return d;
    }

    private Map<String, Object> lookupMaxMind(String ip) {
        initReader();
        if (databaseReader == null) {
            return null;
        }
        try {
            CityResponse response = databaseReader.city(InetAddress.getByName(ip));
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("fonte", "MaxMind GeoLite2");
            String cc = response.getCountry() != null ? response.getCountry().getIsoCode() : "";
            String nomeEn = response.getCountry() != null ? response.getCountry().getName() : "";
            dados.put("pais_codigo", cc);
            dados.put("pais_en", nomeEn);
            dados.put("pais", paisPt(nomeEn, cc));
            dados.put("regiao", response.getMostSpecificSubdivision() != null
                    ? response.getMostSpecificSubdivision().getName() : "");
            dados.put("cidade", response.getCity() != null ? response.getCity().getName() : "");
            dados.put("latitude", response.getLocation() != null ? response.getLocation().getLatitude() : null);
            dados.put("longitude", response.getLocation() != null ? response.getLocation().getLongitude() : null);
            dados.put("isp", response.getTraits() != null && response.getTraits().getIsp() != null
                    ? response.getTraits().getIsp() : "—");
            return dados;
        } catch (Exception ex) {
            LOG.debugf("MaxMind lookup falhou para %s: %s", ip, ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> fallbackIpApi(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=66846719"))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CyberNetFramework/3.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode data = objectMapper.readTree(response.body());
            if (!"success".equals(data.path("status").asText())) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fonte", "ip-api.com (fallback)");
            out.put("pais", data.path("country").asText(""));
            out.put("pais_en", data.path("country").asText(""));
            out.put("pais_codigo", data.path("countryCode").asText("").toUpperCase());
            out.put("regiao", data.path("regionName").asText(""));
            out.put("cidade", data.path("city").asText(""));
            out.put("isp", data.path("isp").asText(""));
            out.put("org", data.path("org").asText(""));
            String asField = data.path("as").asText("");
            out.put("as_name", asField.isBlank() ? data.path("isp").asText("") : asField);
            out.put("timezone", data.path("timezone").asText(""));
            out.put("latitude", data.path("lat").isNull() ? null : data.path("lat").asDouble());
            out.put("longitude", data.path("lon").isNull() ? null : data.path("lon").asDouble());
            out.put("proxy", data.path("proxy").asBoolean(false));
            out.put("hosting", data.path("hosting").asBoolean(false));
            out.put("mobile", data.path("mobile").asBoolean(false));
            return out;
        } catch (Exception ex) {
            LOG.warnf("Fallback geo HTTP falhou: %s", ex.getMessage());
            return null;
        }
    }

    private void initReader() {
        if (readerInitialized) {
            return;
        }
        synchronized (this) {
            if (readerInitialized) {
                return;
            }
            try {
                Path path = Path.of(geoConfig.databasePath());
                if (Files.exists(path)) {
                    databaseReader = new DatabaseReader.Builder(path.toFile()).build();
                } else {
                    try (InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(geoConfig.databasePath())) {
                        if (in != null) {
                            databaseReader = new DatabaseReader.Builder(in).build();
                        }
                    }
                }
            } catch (Exception ex) {
                LOG.warnf("GeoIP database indisponível: %s", ex.getMessage());
            }
            readerInitialized = true;
        }
    }

    @PreDestroy
    void close() {
        if (databaseReader != null) {
            try {
                databaseReader.close();
            } catch (Exception ignored) {
            }
        }
    }

    private Map<String, Object> montarSucesso(String ip, Map<String, Object> dados) {
        String cc = String.valueOf(dados.getOrDefault("pais_codigo", "")).strip().toUpperCase();
        String nomeEn = String.valueOf(dados.getOrDefault("pais_en", "")).strip();
        if (nomeEn.isBlank()) {
            nomeEn = String.valueOf(dados.getOrDefault("pais", "")).strip();
        }
        String pais = String.valueOf(dados.getOrDefault("pais", "")).strip();
        if (pais.isBlank()) {
            pais = paisPt(nomeEn, cc);
        } else {
            pais = paisPt(pais, cc);
        }
        if (pais.isBlank() && !cc.isBlank()) {
            pais = cc;
        }
        String isp = String.valueOf(dados.getOrDefault("isp", dados.getOrDefault("as_name", "—"))).strip();
        if (isp.isBlank()) {
            isp = "—";
        }
        String org = String.valueOf(dados.getOrDefault("org", "")).strip();
        String asName = String.valueOf(dados.getOrDefault("as_name", dados.getOrDefault("isp", ""))).strip();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("motivo", "");
        out.put("mensagem", "");
        out.put("ip", ip);
        out.put("pais", pais.isBlank() ? "—" : pais);
        out.put("codigo_pais", cc);
        out.put("pais_codigo", cc);
        out.put("regiao", String.valueOf(dados.getOrDefault("regiao", "")).strip());
        out.put("cidade", String.valueOf(dados.getOrDefault("cidade", "")).strip());
        out.put("lat", dados.get("latitude"));
        out.put("lon", dados.get("longitude"));
        out.put("latitude", dados.get("latitude"));
        out.put("longitude", dados.get("longitude"));
        out.put("isp", isp);
        out.put("org", org);
        out.put("as_name", asName);
        out.put("timezone", String.valueOf(dados.getOrDefault("timezone", "")).strip());
        out.put("fonte", dados.getOrDefault("fonte", ""));
        out.put("proxy", dados.getOrDefault("proxy", false));
        out.put("hosting", dados.getOrDefault("hosting", false));
        out.put("mobile", dados.getOrDefault("mobile", false));
        out.put("reservado", false);
        out.put("valido", true);
        out.put("tipo", ip.contains(":") ? "IPv6" : "IPv4");
        out.put("cache", "miss");
        out.put("as_cidr", dados.getOrDefault("as_cidr", ""));
        out.put("cidr", dados.getOrDefault("cidr", ""));
        return out;
    }

    private static Map<String, Object> erroBase(String motivo, String mensagem, String ip) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("motivo", motivo);
        out.put("mensagem", mensagem);
        out.put("ip", ip);
        out.put("pais", "");
        out.put("codigo_pais", "");
        out.put("pais_codigo", "");
        out.put("regiao", "");
        out.put("cidade", "");
        out.put("lat", null);
        out.put("lon", null);
        out.put("isp", "—");
        out.put("reservado", false);
        out.put("valido", false);
        out.put("tipo", "");
        out.put("fonte", "");
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cacheGet(String ip) {
        CacheEntry entry = cache.get(ip);
        if (entry != null && entry.expiresAt >= System.currentTimeMillis()) {
            Map<String, Object> copy = new LinkedHashMap<>(entry.payload);
            copy.put("cache", "hit");
            return enriquecerRespostaGeo(copy);
        }

        // L2: sobrevive a deploy. Sem isto, todo reinício zera o cache e a aplicação
        // volta a bater no ip-api, que corta em 45 requisições por minuto.
        if (geoConfig.cacheTtlSeconds() > 0) {
            var bruto = cacheDistribuido.obter("geo", ip);
            if (bruto.isPresent()) {
                try {
                    Map<String, Object> payload = objectMapper.readValue(bruto.get(), Map.class);
                    // Repovoa o L1 para que as próximas leituras nem toquem no Redis.
                    cache.put(ip, new CacheEntry(
                            System.currentTimeMillis() + geoConfig.cacheTtlSeconds() * 1000L, payload));
                    Map<String, Object> copy = new LinkedHashMap<>(payload);
                    copy.put("cache", "hit");
                    return enriquecerRespostaGeo(copy);
                } catch (Exception ex) {
                    // Valor ilegível no L2 é tratado como ausência: segue para a origem.
                    LOG.debugf("Cache distribuido devolveu payload invalido para %s", ip);
                }
            }
        }
        return null;
    }

    /**
     * Grava o resultado de geolocalização no cache com teto de tamanho e expurgo de vencidos.
     *
     * <p><b>Propósito de negócio:</b> evita reconsultar MaxMind/ip-api para o mesmo IP dentro do
     * TTL. Como a chave é o IP do visitante (entrada externa não controlada) e cada valor é um
     * mapa de ~25 campos, um cache sem teto crescia indefinidamente em heap — causa direta de
     * consumo de memória em produção na VPS.
     *
     * <p><b>Invariantes do domínio:</b> o cache nunca ultrapassa {@link #MAX_CACHE} entradas;
     * entradas vencidas são removidas antes de qualquer descarte por lotação, preservando o TTL
     * de {@link GeoConfig#cacheTtlSeconds()} como política primária. TTL {@code <= 0} desliga o
     * cache por completo.
     *
     * <p><b>Comportamento em caso de falha:</b> não lança. Se após o expurgo o cache continuar
     * cheio (todas as entradas ainda válidas), é limpo integralmente — degrada para cache-miss,
     * nunca para crescimento ilimitado.
     *
     * @param ip      IP normalizado usado como chave
     * @param payload resposta de geolocalização a memorizar
     */
    private void cacheSet(String ip, Map<String, Object> payload) {
        if (geoConfig.cacheTtlSeconds() <= 0) {
            return;
        }
        if (cache.size() >= MAX_CACHE) {
            long agora = System.currentTimeMillis();
            cache.entrySet().removeIf(e -> e.getValue().expiresAt < agora);
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
        }
        Map<String, Object> store = new LinkedHashMap<>(payload);
        store.remove("cache");
        cache.put(ip, new CacheEntry(
                System.currentTimeMillis() + geoConfig.cacheTtlSeconds() * 1000L,
                store));

        // L2 com o MESMO TTL do L1: o cache distribuído não pode sobreviver ao
        // que a política de expiração já considera vencido.
        try {
            cacheDistribuido.guardar("geo", ip, objectMapper.writeValueAsString(store),
                    java.time.Duration.ofSeconds(geoConfig.cacheTtlSeconds()));
        } catch (Exception ex) {
            // Falha de serialização não pode derrubar a consulta que já deu certo.
            LOG.debugf("Nao foi possivel gravar %s no cache distribuido", ip);
        }
    }

    private static boolean codigoPaisInvalido(String cc) {
        return cc == null || cc.isBlank() || Set.of("--", "XX", "ZZ").contains(cc.toUpperCase());
    }

    private static String paisPt(String nomeEn, String codigo) {
        if (nomeEn == null || nomeEn.isBlank()) {
            return codigo == null || codigo.isBlank() ? "—" : codigo;
        }
        return NOMES_PT.getOrDefault(nomeEn, nomeEn);
    }

    private static Map<String, Object> mergeFontes(Map<String, Object> local, Map<String, Object> fallback) {
        if (fallback == null) {
            return local;
        }
        if (local == null) {
            return fallback;
        }
        Map<String, Object> merged = new LinkedHashMap<>(fallback);
        for (String key : new String[]{"cidr", "as_cidr"}) {
            Object value = local.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                merged.putIfAbsent(key, value);
            }
        }
        return merged;
    }

    private static Map<String, String> risco(String cc) {
        if (cc == null || cc.isBlank() || Set.of("--", "XX", "ZZ").contains(cc.toUpperCase())) {
            return Map.of(
                    "nivel", "Desconhecido",
                    "badge", "⚫ Desconhecido",
                    "badge_color", "secondary",
                    "recomendacao", "País não identificado. Monitorar tráfego em contexto de SOC.");
        }
        if (ALTO.contains(cc)) {
            return Map.of(
                    "nivel", "Alto",
                    "badge", "🔴 Alto Risco",
                    "badge_color", "danger",
                    "recomendacao", "País (" + cc + ") frequentemente associado a ameaças avançadas "
                            + "(referências CISA/ENISA). Avaliar geo-blocking seletivo, DPI e correlação no SIEM.");
        }
        if (MEDIO.contains(cc)) {
            return Map.of(
                    "nivel", "Médio",
                    "badge", "🟡 Risco Moderado",
                    "badge_color", "warning",
                    "recomendacao", "País (" + cc + ") com incidência moderada de ameaças. "
                            + "Reforçar MFA em contas privilegiadas e monitorização contínua.");
        }
        return Map.of(
                "nivel", "Baixo",
                "badge", "🟢 Risco Baixo",
                "badge_color", "success",
                "recomendacao", "Nenhuma restrição geográfica especial identificada para este prefixo.");
    }

    private static String bandeira(String cc, boolean reservado) {
        if (reservado) {
            return "🏠";
        }
        if (cc == null || cc.isBlank()) {
            return "🌐";
        }
        return BANDEIRAS.getOrDefault(cc.toUpperCase(), "🌐");
    }

    private static String motivoReservado(String ip) {
        if (ip == null || ip.isBlank()) {
            return "Não-roteável";
        }
        try {
            InetAddress addr = InetAddress.getByName(ip.strip());
            if (addr.isLoopbackAddress()) {
                return "Loopback (localhost)";
            }
            if (addr.isLinkLocalAddress()) {
                return "Link-local (APIPA / fe80::)";
            }
            if (addr.isSiteLocalAddress()) {
                return "Privado (RFC 1918 / ULA IPv6)";
            }
            if (addr.isMulticastAddress()) {
                return "Multicast";
            }
            if (addr.isAnyLocalAddress()) {
                return "Endereço não especificado (::)";
            }
            byte[] octets = addr.getAddress();
            if (octets.length == 4 && (octets[0] & 0xFF) == 100 && (octets[1] & 0xFF) >= 64 && (octets[1] & 0xFF) <= 127) {
                return "CGNAT (RFC 6598)";
            }
        } catch (Exception ignored) {
            return "Não-roteável";
        }
        return "Não-roteável";
    }

    private record CacheEntry(long expiresAt, Map<String, Object> payload) {
    }
}
