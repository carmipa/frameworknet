package org.framework.net.localizacao.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consulta de endereço por CEP via <a href="https://viacep.com.br">ViaCEP</a> (HTTP público, sem chave).
 *
 * <p>Retorna sempre um {@code Map} com {@code ok} (boolean) e {@code motivo} em caso de falha,
 * seguindo o mesmo padrão resiliente do fallback de GeoIP.
 */
@ApplicationScoped
public class CepLookupService {

    private static final Logger LOG = Logger.getLogger(CepLookupService.class);
    private static final int MAX_CACHE = 500;

    @ConfigProperty(name = "framework.localizacao.viacep-url", defaultValue = "https://viacep.com.br/ws")
    String viacepUrl;

    @ConfigProperty(name = "framework.localizacao.user-agent", defaultValue = "FrameworkNetRedes/1.0")
    String userAgent;

    @ConfigProperty(name = "framework.localizacao.http-timeout-seconds", defaultValue = "5")
    int timeoutSeconds;

    @ConfigProperty(name = "framework.localizacao.cache-ttl-seconds", defaultValue = "86400")
    int cacheTtlSeconds;

    @Inject
    ObjectMapper objectMapper;

    /** L2 (Redis): o Javadoc do cache distribuído prometia o CEP e ele não usava (OPS-17). */
    @Inject
    org.framework.net.shared.CacheDistribuido cacheDistribuido;

    /** L1 com validade (OPS-17: antes sem expiração). */
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

    /** Consulta o CEP (aceita com ou sem máscara). Nunca lança — retorna {@code ok=false} em falha. */
    public Map<String, Object> buscar(String cepRaw) {
        String cep = normalizarCep(cepRaw);
        if (cep == null) {
            return erro("invalid", "CEP inválido: informe 8 dígitos (ex.: 01001-000).", cepRaw);
        }
        java.util.Optional<Map<String, Object>> cached = cache().obter(cep);
        if (cached.isPresent()) {
            return new LinkedHashMap<>(cached.get());
        }
        java.util.Optional<Map<String, Object>> doL2 = lerDoCacheDistribuido(cep);
        if (doL2.isPresent()) {
            return doL2.get();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(viacepUrl + "/" + cep + "/json/"))
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return erro("network", "Não foi possível consultar o ViaCEP (HTTP " + response.statusCode() + ").", cep);
            }
            JsonNode data = objectMapper.readTree(response.body());
            if (data.path("erro").asBoolean(false)) {
                return erro("not_found", "CEP não encontrado na base do ViaCEP.", cep);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("cep", data.path("cep").asText(formatarCep(cep)));
            out.put("logradouro", data.path("logradouro").asText(""));
            out.put("complemento", data.path("complemento").asText(""));
            out.put("bairro", data.path("bairro").asText(""));
            out.put("cidade", data.path("localidade").asText(""));
            out.put("uf", data.path("uf").asText(""));
            out.put("ibge", data.path("ibge").asText(""));
            out.put("ddd", data.path("ddd").asText(""));
            out.put("fonte", "ViaCEP");
            guardarCache(cep, out);
            return new LinkedHashMap<>(out);
        } catch (Exception ex) {
            LOG.warnf("Falha ao consultar ViaCEP para %s: %s", cep, ex.getMessage());
            return erro("network", "Falha de rede ao consultar o ViaCEP. Tente novamente.", cep);
        }
    }

    /** Normaliza para 8 dígitos ou {@code null} se inválido. Pública para testes. */
    public static String normalizarCep(String cepRaw) {
        if (cepRaw == null) {
            return null;
        }
        String digits = cepRaw.replaceAll("\\D", "");
        return digits.length() == 8 ? digits : null;
    }

    private static String formatarCep(String cep8) {
        return cep8.substring(0, 5) + "-" + cep8.substring(5);
    }

    private static Map<String, Object> erro(String motivo, String mensagem, String cep) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("motivo", motivo);
        out.put("mensagem", mensagem);
        out.put("cep", cep == null ? "" : cep);
        return out;
    }

    private void guardarCache(String cep, Map<String, Object> value) {
        cache().guardar(cep, new LinkedHashMap<>(value));
        try {
            cacheDistribuido.guardar("cep", cep, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(Math.max(1, cacheTtlSeconds)));
        } catch (Exception ex) {
            LOG.debugf("Nao foi possivel gravar o CEP %s no cache distribuido", cep);
        }
    }

    /** Lê do L2 e repovoa o L1; ausente, ilegível ou Redis fora devolve vazio (vai ao ViaCEP). */
    @SuppressWarnings("unchecked")
    private java.util.Optional<Map<String, Object>> lerDoCacheDistribuido(String cep) {
        var bruto = cacheDistribuido.obter("cep", cep);
        if (bruto.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            Map<String, Object> valor = objectMapper.readValue(bruto.get(), Map.class);
            cache().guardar(cep, new LinkedHashMap<>(valor));
            return java.util.Optional.of(new LinkedHashMap<>(valor));
        } catch (Exception ex) {
            return java.util.Optional.empty();
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
