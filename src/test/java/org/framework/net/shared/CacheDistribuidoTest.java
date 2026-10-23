package org.framework.net.shared;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Degradação do cache L2 quando o Redis não existe.
 *
 * <p><b>Propósito de negócio:</b> a promessa feita ao adotar o cache distribuído
 * foi explícita — "se o Redis cair, o pior caso é voltar ao comportamento
 * atual". Este teste é o que sustenta essa promessa: a suíte roda sem Redis e
 * sem Docker, exatamente o cenário de indisponibilidade.</p>
 *
 * <p><b>Invariantes do domínio:</b> sem Redis, {@code obter} devolve vazio e
 * {@code guardar} é inócuo — nenhum dos dois lança. Um cache que derruba a
 * requisição quando falha é pior que não ter cache.</p>
 */
@QuarkusTest
@DisplayName("Cache distribuído: degradação sem Redis")
class CacheDistribuidoTest {

    @Inject
    CacheDistribuido cache;

    @Test
    @DisplayName("sem Redis o cache fica inativo, sem quebrar a aplicação")
    void semRedisFicaInativo() {
        // O perfil de teste não configura quarkus.redis.hosts nem liga o cache.
        assertFalse(cache.ativo(), "Sem Redis configurado o L2 não pode se declarar ativo");
    }

    @Test
    @DisplayName("obter devolve vazio em vez de lançar")
    void obterDegradaParaVazio() {
        assertTrue(cache.obter("geo", "8.8.8.8").isEmpty());
        assertTrue(cache.obter("nominatim", "avenida paulista").isEmpty());
    }

    @Test
    @DisplayName("guardar é inócuo em vez de lançar")
    void guardarDegradaParaNoop() {
        assertDoesNotThrow(() ->
                cache.guardar("geo", "8.8.8.8", "{\"pais\":\"US\"}", Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("entrada inválida nunca lança")
    void entradaInvalidaNaoQuebra() {
        assertDoesNotThrow(() -> {
            cache.obter("geo", null);
            cache.obter("geo", "");
            cache.guardar("geo", null, "x", Duration.ofMinutes(1));
            cache.guardar("geo", "k", null, Duration.ofMinutes(1));
            // TTL ausente ou não positivo é ignorado: cache eterno de dado externo
            // é defeito, não otimização.
            cache.guardar("geo", "k", "v", null);
            cache.guardar("geo", "k", "v", Duration.ZERO);
            cache.guardar("geo", "k", "v", Duration.ofSeconds(-1));
        });
    }

    @Test
    @DisplayName("os serviços que usam o L2 seguem funcionando sem ele")
    void servicosFuncionamSemOL2() {
        // Se a ausência do Redis quebrasse GeoIP ou geocodificação, a suíte inteira
        // cairia — este teste torna a dependência explícita em vez de implícita.
        assertDoesNotThrow(() -> cache.obter("geo", "1.1.1.1"));
        assertFalse(cache.ativo());
    }
}
