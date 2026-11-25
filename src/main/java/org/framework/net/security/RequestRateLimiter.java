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

    /**
     * Recusas de CSRF aceitas por cliente e minuto. Balde PRÓPRIO, fora do limite da rota: token vencido de uma turma
     * inteira atrás do mesmo NAT (cada aluno erra uma vez e reenvia) não esgota o limite pesado da rota; quem manda
     * pedido sem token válido em série é freado, e acima do teto o corpo nem chega a ser lido.
     */
    @ConfigProperty(name = "framework.security.rate-limit-csrf-recusas-per-minute", defaultValue = "120")
    int csrfRecusasPorMinuto;

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
        podarSeVirouMinuto(windowMinute);
        // A rota decide primeiro e só a aprovada conta no global. Revisão de boa-fé: contando também as
        // recusadas, um painel em polling (Tráfego ao vivo, 1/s) aberto por meia turma atrás do mesmo NAT
        // esgotava o global em segundos e derrubava TODOS os módulos da turma até a janela virar.
        if (incrementar(key, windowMinute) > limit) {
            return false;
        }
        return incrementar(client + "|*", windowMinute) <= globalLimitPerMinute;
    }

    /** Resultado de {@link #reservarLeituraSemToken}: a vaga, o minuto dela e se o teto estourou neste pedido. */
    public record ReservaCsrf(boolean dentroDoTeto, long minuto, boolean acabouDeEstourar) {
    }

    /**
     * Reserva, ANTES de ler o corpo, uma vaga no balde de pedidos sem token válido no cabeçalho.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> frear quem manda pedido sem token válido em série — cada um obriga o filtro do
     * CSRF a ler um corpo de até 1 MB — sem gastar o limite da rota, que uma turma atrás do mesmo NAT divide.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> conta e decide no mesmo passo (incremento atômico): pedidos simultâneos não
     * passam todos de uma vez, como passavam com "olhar e só depois contar". O balde {@code cliente|csrf-recusa} é
     * separado do limite da rota e do global; quem lê o corpo e acha token válido devolve a vaga
     * ({@link #devolverReserva}), então só a RECUSA gasta o teto. A chave do cliente é a mesma dos outros baldes, nunca
     * um cabeçalho escolhido por quem chama.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; limitador desligado devolve sempre "dentro do teto".</p>
     */
    public ReservaCsrf reservarLeituraSemToken(ContainerRequestContext ctx) {
        long windowMinute = System.currentTimeMillis() / 60_000L;
        if (!rateLimitEnabled) {
            return new ReservaCsrf(true, windowMinute, false);
        }
        podarSeVirouMinuto(windowMinute);
        int usados = incrementar(clientKey(ctx) + "|csrf-recusa", windowMinute);
        return new ReservaCsrf(usados <= csrfRecusasPorMinuto, windowMinute, usados == csrfRecusasPorMinuto + 1);
    }

    /**
     * Devolve a vaga de um pedido que, lido, trouxe token válido no corpo.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o formulário comum (sem htmx) manda o token no corpo; ele não pode gastar o teto
     * de recusas da rede.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só devolve no MESMO minuto da reserva (virado o minuto, o balde já é outro) e
     * nunca deixa a contagem negativa.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; limitador desligado não faz nada.</p>
     */
    public void devolverReserva(ContainerRequestContext ctx, ReservaCsrf reserva) {
        if (!rateLimitEnabled || reserva == null) {
            return;
        }
        Bucket bucket = buckets.get(clientKey(ctx) + "|csrf-recusa");
        if (bucket != null && bucket.windowMinute() == reserva.minuto()) {
            bucket.counter().updateAndGet(v -> v > 0 ? v - 1 : 0);
        }
    }

    /**
     * Limpeza oportunista (sem scheduler): no máximo uma vez por minuto, evita crescimento ilimitado do mapa de
     * baldes (memory leak / vetor de DoS).
     */
    private void podarSeVirouMinuto(long windowMinute) {
        long previousPrune = lastPruneMinute.get();
        if (windowMinute != previousPrune && lastPruneMinute.compareAndSet(previousPrune, windowMinute)) {
            prune();
        }
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
