package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correlação dos eventos de negócio com a requisição que os originou.
 *
 * <p><b>Propósito de negócio:</b> a telemetria vai virar dataset publicado. Sem
 * {@code traceId} em todo evento, ninguém que consumir o dataset consegue
 * responder "qual requisição causou este erro" — os eventos viram linhas soltas.
 * Em 2026-08-03, 29,1% dos registros coletados estavam nessa situação porque
 * {@code TelemetriaLogger.medir()} emitia sem contexto.</p>
 *
 * <p><b>Invariantes do domínio:</b> todo evento nascido dentro de uma requisição
 * HTTP carrega {@code traceId} e {@code requestId}; eventos do mesmo incidente
 * compartilham o mesmo {@code traceId}, o que permite tratá-los como um
 * incidente só em vez de dois erros independentes.</p>
 */
@QuarkusTest
@DisplayName("Telemetria: eventos de negócio correlacionados com a requisição")
class CorrelacaoEventosTest {

    @Inject
    TelemetriaStore store;

    @Test
    @DisplayName("evento emitido por medir() herda o traceId da requisição")
    void eventoDeNegocioNasceComTraceId() {
        String traceId = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("bloco", "192.168.0.0/21")
                .formParam("criterio", "prefixo")
                .formParam("prefixoAlvo", "24")
                .when().post("/calculadora/api/dividir")
                .then().statusCode(200)
                .extract().header("X-Trace-Id");

        assertNotNull(traceId, "A resposta deve devolver X-Trace-Id");

        List<TelemetriaEvent> doTrace = eventosDoTrace(traceId);
        assertFalse(doTrace.isEmpty(), "Nenhum evento correlacionado ao trace " + traceId);

        Set<String> eventos = doTrace.stream().map(TelemetriaEvent::evento).collect(Collectors.toSet());
        assertTrue(eventos.contains("dividir_por_prefixo"),
                "O evento de negócio deveria estar correlacionado. Eventos no trace: " + eventos);
        assertTrue(eventos.contains("divisao_calculada"),
                "Eventos: " + eventos);
    }

    @Test
    @DisplayName("regressão: um erro de entrada é UM incidente, não dois erros soltos")
    void erroDeEntradaGeraEventosSobOMesmoTrace() {
        String traceId = given()
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .formParam("bloco", "192.168.0.0/21")
                .formParam("criterio", "quantidade")
                .formParam("quantidade", "")
                .when().post("/calculadora/api/dividir")
                .then().statusCode(400)
                .extract().header("X-Trace-Id");

        assertNotNull(traceId);

        List<TelemetriaEvent> erros = eventosDoTrace(traceId).stream()
                .filter(evento -> "error".equals(evento.status()))
                .toList();
        Set<String> nomes = erros.stream().map(evento -> evento.evento()).collect(Collectors.toSet());

        // O incidente rende três eventos — a operação de negócio que falhou, a exceção
        // mapeada e o acesso HTTP 400. O que importa não é a quantidade: é que os três
        // caiam sob o MESMO traceId, para o consumidor do dataset agrupar e contar UM
        // incidente. Antes da correção, "dividir_por_quantidade" saía órfão.
        assertTrue(nomes.contains("dividir_por_quantidade"),
                "A operação de negócio que falhou deveria estar no trace. Eventos: " + nomes);
        assertTrue(nomes.contains("domain_exception"),
                "A exceção mapeada deveria estar no trace. Eventos: " + nomes);

        for (TelemetriaEvent erro : erros) {
            assertEquals(traceId, erro.traceId(), "Evento sem correlação: " + erro.evento());
            assertNotNull(erro.requestId(), "Evento sem requestId: " + erro.evento());
        }
    }

    @Test
    @DisplayName("evento de módulos diferentes também correlaciona")
    void outrosModulosTambemCorrelacionam() {
        String traceId = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("regra", "permit ip any any")
                .formParam("ipOrigem", "192.168.1.5")
                .formParam("ipDestino", "8.8.8.8")
                .formParam("portaDestino", "80")
                .when().post("/seguranca/api/testar")
                .then().statusCode(200)
                .extract().header("X-Trace-Id");

        assertNotNull(traceId);
        List<TelemetriaEvent> doTrace = eventosDoTrace(traceId);
        assertTrue(doTrace.stream().anyMatch(e -> "teste_acl".equals(e.evento())),
                "O evento teste_acl deveria estar correlacionado ao trace " + traceId);
    }

    @Test
    @DisplayName("evento fora de requisição continua sem traceId, e isso é correto")
    void eventoSemRequisicaoNaoInventaCorrelacao() {
        // app_start nasce na inicialização, sem requisição alguma: traceId nulo é o
        // valor honesto. Correlação fabricada seria pior que correlação ausente.
        List<TelemetriaEvent> semRequisicao = store.snapshotEventos().stream()
                .filter(e -> "app_start".equals(e.evento()))
                .toList();
        for (TelemetriaEvent evento : semRequisicao) {
            assertEquals(null, evento.traceId(),
                    "app_start não pode carregar correlação de requisição");
        }
    }

    private List<TelemetriaEvent> eventosDoTrace(String traceId) {
        return store.snapshotEventos().stream()
                .filter(e -> traceId.equals(e.traceId()))
                .toList();
    }
}
