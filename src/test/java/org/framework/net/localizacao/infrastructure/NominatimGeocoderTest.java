package org.framework.net.localizacao.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.framework.net.shared.CacheDistribuido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F29: a política do Nominatim é ~1 req/s no TOTAL (é o IP da VPS inteira que pode ser banido) e o
 * reverse (GPS) não tinha cache nem freio. Servidor local falso conta as chamadas — nenhum tráfego sai
 * para a internet neste teste.
 *
 * <p>Revisão pós-implementação: o freio do F29 RECUSAVA a segunda chamada do mesmo segundo, e isso matou o
 * fallback cidade/UF do CEP (endereço completo não achado → busca da cidade logo em seguida → freada).
 * O freio passou a esperar o próximo slot livre, com teto.
 */
class NominatimGeocoderTest {

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final List<Long> chegadas = Collections.synchronizedList(new ArrayList<>());
    private NominatimGeocoder geo;

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            chamadas.incrementAndGet();
            chegadas.add(System.nanoTime());
            String caminho = troca.getRequestURI().getPath();
            String consulta = String.valueOf(troca.getRequestURI().getQuery());
            String json;
            if (caminho.endsWith("/reverse")) {
                json = "{\"display_name\":\"Rua X, Guarulhos\",\"address\":{\"road\":\"Rua X\",\"city\":\"Guarulhos\"}}";
            } else if (consulta.contains("inexistente")) {
                json = "[]";
            } else {
                json = "[{\"lat\":\"-23.46\",\"lon\":\"-46.53\",\"display_name\":\"Guarulhos, SP\"}]";
            }
            byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        servidor.start();
        geo = new NominatimGeocoder();
        geo.nominatimUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/search";
        geo.userAgent = "FrameworkNetRedes/teste";
        geo.timeoutSeconds = 3;
        geo.cacheTtlSeconds = 60;
        geo.esperaMaximaMs = 1200;
        geo.objectMapper = new ObjectMapper();
        geo.cacheDistribuido = new CacheDistribuido();
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    @Test
    void reverseRepetidoUsaCache() {
        assertTrue(geo.reverse(-23.4543, -46.5333).isPresent());
        assertTrue(geo.reverse(-23.4543, -46.5333).isPresent());
        assertEquals(1, chamadas.get(), "a mesma coordenada não pode ir duas vezes ao Nominatim");
    }

    @Test
    void duasChamadasNoMesmoSegundoSaemEspacadasDeUmSegundo() {
        assertTrue(geo.reverse(-23.1, -46.1).isPresent());
        assertTrue(geo.reverse(-22.2, -45.2).isPresent(), "a segunda chamada legítima espera o slot, não é descartada");
        assertEquals(2, chamadas.get());
        long intervaloMs = (chegadas.get(1) - chegadas.get(0)) / 1_000_000L;
        assertTrue(intervaloMs >= 950, "política de 1 req/s violada: chegadas a " + intervaloMs + " ms");
    }

    @Test
    void fallbackCidadeLogoAposEnderecoNaoEncontradoFunciona() {
        // Caso real do LocalizacaoService.localizarPorCep: completo não acha, cidade/UF vem no mesmo segundo.
        assertTrue(geo.geocodificar("Rua inexistente, 999, Guarulhos - SP, Brasil").isEmpty());
        assertTrue(geo.geocodificar("Guarulhos - SP, Brasil").isPresent(),
                "o fallback cidade/UF não pode ser freado pela chamada que acabou de falhar");
        assertEquals(2, chamadas.get());
    }

    @Test
    void rajadaAlemDaEsperaMaximaERecusadaSemSairParaFora() {
        // Relógio parado + sono que só anota: simula três pedidos chegando no MESMO instante.
        AtomicLong agora = new AtomicLong(System.nanoTime());
        List<Long> esperas = new ArrayList<>();
        geo.relogio = agora::get;
        geo.dormidor = esperas::add;

        assertTrue(geo.reverse(-10.1, -40.1).isPresent(), "1º: slot livre, sai na hora");
        assertTrue(geo.reverse(-10.2, -40.2).isPresent(), "2º: próximo slot a 1 s, dentro do teto de 1,2 s");
        assertTrue(geo.reverse(-10.3, -40.3).isEmpty(), "3º: próximo slot a 2 s, além do teto — recusado");
        assertEquals(2, chamadas.get(), "o recusado não pode sair para o Nominatim");
        assertEquals(List.of(1_000_000_000L), esperas, "só o 2º esperou, e exatamente 1 s");
    }

    @Test
    void coordenadaInvalidaNaoSaiParaFora() {
        assertTrue(geo.reverse(Double.NaN, 0).isEmpty());
        assertTrue(geo.reverse(91, 0).isEmpty());
        assertTrue(geo.reverse(0, 181).isEmpty());
        assertEquals(0, chamadas.get());
    }
}
