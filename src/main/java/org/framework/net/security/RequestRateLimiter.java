package org.framework.net.security;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RequestRateLimiter {

    @ConfigProperty(name = "framework.security.rate-limit-enabled", defaultValue = "true")
    boolean rateLimitEnabled;

    @ConfigProperty(name = "framework.security.rate-limit-per-minute", defaultValue = "120")
    int defaultLimitPerMinute;

    @ConfigProperty(name = "framework.security.rate-limit-heavy-per-minute", defaultValue = "30")
    int heavyLimitPerMinute;

    /** Teto por cliente somando TODAS as rotas: sem ele o total por IP era o limite × número de rotas. */
    @ConfigProperty(name = "framework.security.rate-limit-global-per-minute", defaultValue = "600")
    int globalLimitPerMinute;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong lastPruneMinute = new java.util.concurrent.atomic.AtomicLong(-1);

    public boolean allow(ContainerRequestContext ctx, boolean heavy) {
        return allow(ctx, normalizePath(ctx.getUriInfo().getPath()), heavy);
    }

    /**
     * PROPÓSITO: decide se a requisição cabe no limite de taxa do cliente.
     * INVARIANTES: dois baldes por requisição — (cliente, ROTA) e (cliente, global). A rota é a
     * identidade do endpoint resolvido (classe#método), nunca o caminho bruto: com o caminho, cada
     * {@code /protocolos/<slug>} ganhava balde próprio — limite inexistente e mapa crescendo com a
     * escolha do atacante. O número de chaves fica limitado a clientes × endpoints. Só a requisição
     * aprovada pela rota consome o global — recusa não drena o teto dos outros módulos.
     * FALHA: não lança; limitador desligado devolve sempre true.
     */
    public boolean allow(ContainerRequestContext ctx, String routeKey, boolean heavy) {
        if (!rateLimitEnabled) {
            return true;
        }
        int limit = heavy ? heavyLimitPerMinute : defaultLimitPerMinute;
        String client = clientKey(ctx);
        String key = client + "|" + routeKey + (heavy ? "|heavy" : "");
        long windowMinute = System.currentTimeMillis() / 60_000L;
        // Limpeza oportunista (sem scheduler): no máximo uma vez por minuto, evita crescimento
        // ilimitado do mapa de buckets (memory leak / vetor de DoS).
        long previousPrune = lastPruneMinute.get();
        if (windowMinute != previousPrune && lastPruneMinute.compareAndSet(previousPrune, windowMinute)) {
            prune();
        }
        // A rota decide primeiro e só a aprovada conta no global. Revisão de boa-fé: contando também as
        // recusadas, um painel em polling (Tráfego ao vivo, 1/s) aberto por meia turma atrás do mesmo NAT
        // esgotava o global em segundos e derrubava TODOS os módulos da turma até a janela virar.
        if (incrementar(key, windowMinute) > limit) {
            return false;
        }
        return incrementar(client + "|*", windowMinute) <= globalLimitPerMinute;
    }

    private int incrementar(String key, long windowMinute) {
        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowMinute != windowMinute) {
                return new Bucket(windowMinute, new AtomicInteger(0));
            }
            return existing;
        });
        return bucket.counter.incrementAndGet();
    }

    public void prune() {
        long current = System.currentTimeMillis() / 60_000L;
        buckets.entrySet().removeIf(e -> e.getValue().windowMinute < current - 2);
    }

    /**
     * Identifica o cliente para efeito de limite de taxa.
     *
     * <p><b>Propósito de negócio:</b> é a chave do balde. Se o cliente conseguir
     * escolhê-la, o limite deixa de existir.</p>
     *
     * <p><b>Invariantes do domínio:</b> o endereço vem <b>sempre</b> de
     * {@code request().remoteAddress()}, resolvido pelo próprio Quarkus. Ler
     * {@code X-Forwarded-For} aqui era bypass total: qualquer requisição podia
     * mandar um valor inventado e ganhar um balde novo a cada chamada — inclusive
     * para força bruta da chave administrativa. Quem decide se o cabeçalho é
     * confiável é {@code quarkus.http.proxy.trusted-proxies}, que restringe a
     * substituição à rede do proxy reverso; fora dela o endereço real prevalece.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> sem endereço disponível devolve
     * {@code "anonymous"}, o que agrupa os casos indeterminados num balde único —
     * mais restritivo, nunca mais permissivo.</p>
     */
    private String clientKey(ContainerRequestContext ctx) {
        try {
            if (currentVertxRequest != null && currentVertxRequest.getCurrent() != null
                    && currentVertxRequest.getCurrent().request() != null
                    && currentVertxRequest.getCurrent().request().remoteAddress() != null) {
                return currentVertxRequest.getCurrent().request().remoteAddress().host();
            }
        } catch (Exception ignored) {
            // fallback abaixo
        }
        return "anonymous";
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private record Bucket(long windowMinute, AtomicInteger counter) {
    }
}
