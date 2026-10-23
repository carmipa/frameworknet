package org.framework.net.shared;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Cache em memória com validade e teto de entradas (L1 das APIs externas).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> os caches do Nominatim e do CEP guardavam cada resposta até o processo
 * reiniciar ou o mapa encher — endereço corrigido na origem nunca era visto (auditoria OPS-17). Aqui toda
 * entrada vence depois do TTL configurado, como o L2 já fazia.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> entrada vencida nunca é devolvida (e sai do mapa ao ser encontrada);
 * no máximo {@code teto} entradas — ao encher, o mapa é limpo inteiro (o comportamento que os serviços já
 * tinham); relógio monotônico.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; chave nula é ignorada.</p>
 *
 * @param <V> tipo do valor guardado
 */
public final class CacheLocal<V> {

    private record Entrada<V>(V valor, long venceEm) {
    }

    private final ConcurrentHashMap<String, Entrada<V>> mapa = new ConcurrentHashMap<>();
    private final int teto;
    private final long ttlNanos;
    private final LongSupplier relogio;

    public CacheLocal(int teto, Duration ttl, LongSupplier relogioNanos) {
        this.teto = Math.max(1, teto);
        this.ttlNanos = Math.max(1L, ttl.toNanos());
        this.relogio = relogioNanos;
    }

    public CacheLocal(int teto, Duration ttl) {
        this(teto, ttl, System::nanoTime);
    }

    /** O valor ainda válido para a chave. */
    public Optional<V> obter(String chave) {
        if (chave == null) {
            return Optional.empty();
        }
        Entrada<V> e = mapa.get(chave);
        if (e == null) {
            return Optional.empty();
        }
        if (relogio.getAsLong() - e.venceEm() >= 0) {
            mapa.remove(chave, e);
            return Optional.empty();
        }
        return Optional.of(e.valor());
    }

    /** Guarda o valor com a validade do cache. */
    public void guardar(String chave, V valor) {
        if (chave == null || valor == null) {
            return;
        }
        if (mapa.size() >= teto) {
            mapa.clear();
        }
        mapa.put(chave, new Entrada<>(valor, relogio.getAsLong() + ttlNanos));
    }

    public int tamanho() {
        return mapa.size();
    }
}
