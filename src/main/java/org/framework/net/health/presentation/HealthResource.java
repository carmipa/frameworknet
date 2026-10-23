package org.framework.net.health.presentation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Propósito de negócio: oferece uma verificação mínima de disponibilidade para o orquestrador do container.
 * Invariantes do domínio: não acessa dados de negócio, não gera telemetria e responde somente quando o runtime
 * HTTP está apto a atender requisições.
 * Comportamento em caso de falha: se o runtime não conseguir executar o recurso, o healthcheck recebe resposta
 * não bem-sucedida ou timeout e o Docker marca o container como não saudável.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    /**
     * Propósito de negócio: confirma ao Docker que o processo HTTP está disponível.
     * Invariantes do domínio: retorna estado imutável e não produz efeitos colaterais ou persistência.
     * Comportamento em caso de falha: não captura exceções do runtime, permitindo que o status HTTP revele a falha.
     */
    @GET
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
