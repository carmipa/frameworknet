package org.framework.net.protocolos.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.protocolos.application.AutoavaliacaoService;

import java.util.Map;

/**
 * API do teste rápido dos aprofundamentos de protocolo (piloto de autoavaliação, proposta C03).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o navegador avisa qual alternativa a pessoa escolheu, no clique; o servidor confere
 * com o gabarito e conta. É assim que se sabe se o teste é usado antes de levá-lo aos outros aprofundamentos.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> esquema fechado {@code {protocolo, pergunta, alternativa}}, só índices; o corpo tem
 * teto antes de chegar aqui ({@code LimiteCorpoProtocolosApi}); a resposta devolve só se acertou, que o navegador já
 * mostrou; nada do visitante é guardado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> corpo ausente, protocolo sem teste ou índice fora da lista ⇒ 422 com
 * {@code {"resultado":"RECUSADO"}}, como a API de eventos da Academia; JSON malformado segue o tratamento de erro do
 * site (4xx, nunca 500).</p>
 */
@Path("/protocolos/api/autoavaliacao")
public class AutoavaliacaoResource {

    /** Resposta escolhida: o protocolo (slug), a pergunta e a alternativa, por posição. */
    public record RespostaAutoavaliacao(String protocolo, Integer pergunta, Integer alternativa) {
    }

    @Inject
    AutoavaliacaoService autoavaliacaoService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response responder(RespostaAutoavaliacao resposta) {
        if (resposta == null) {
            return recusar();
        }
        return autoavaliacaoService.registrar(resposta.protocolo(), resposta.pergunta(), resposta.alternativa())
                .map(acertou -> Response.accepted(Map.of("acertou", acertou)).build())
                .orElseGet(AutoavaliacaoResource::recusar);
    }

    private static Response recusar() {
        return Response.status(422).entity(Map.of("resultado", "RECUSADO")).build();
    }
}
