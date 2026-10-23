package org.framework.net.academia.eventos.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.academia.core.presentation.AcademiaAberta;
import org.framework.net.academia.eventos.application.RegistrarEventoAcademia;
import org.framework.net.academia.eventos.application.RegistrarEventoAcademia.Resultado;
import org.framework.net.academia.eventos.domain.EventoAcademia.TipoErroJs;

import java.util.Locale;
import java.util.Map;

/**
 * Recebe da página de uma lição o resumo da visita e os erros de JavaScript.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> é a porta pela qual a Academia descobre que uma lição quebrou
 * num navegador real (F5) e quanto ela é usada, sem login e sem dado pessoal.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> esquema fechado — {@code tipo} é {@code visita} ou
 * {@code erro}; campo a mais é ignorado; texto livre só em {@code mensagem}, que é saneado antes de
 * sair do caso de uso; o corpo tem teto antes de chegar aqui (filtro do kernel) e passa pelo CSRF
 * do site como qualquer POST.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> pedido malformado ou de lição desconhecida ⇒ 422 com o
 * motivo em JSON e nada registrado; orçamento esgotado ⇒ 202 com {@code DESCARTADO_ORCAMENTO}
 * (não é culpa do navegador); Academia não pronta ⇒ 503.</p>
 */
@Path("/academia/api/eventos")
public class AcademiaEventosResource {

    @Inject
    AcademiaAberta academiaAberta;

    @Inject
    RegistrarEventoAcademia registrar;

    /**
     * O corpo que a lição envia.
     *
     * @param tipo        {@code visita} ou {@code erro}
     * @param licaoId     lição do catálogo
     * @param segundos    tempo na página (visita)
     * @param interacoes  quantidade de interações (visita)
     * @param concluiu    se concluiu a lição nesta visita (visita)
     * @param tipoErro    {@code erro} ou {@code promessa} (erro)
     * @param mensagem    mensagem do erro, saneada no servidor (erro)
     */
    public record EventoRequest(String tipo, String licaoId, Long segundos, Long interacoes, Boolean concluiu,
                                String tipoErro, String mensagem) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receber(EventoRequest pedido) {
        academiaAberta.exigir();
        if (pedido == null || pedido.tipo() == null) {
            return recusar("tipo ausente");
        }
        Resultado resultado = switch (pedido.tipo().toLowerCase(Locale.ROOT)) {
            case "visita" -> registrar.registrarVisita(pedido.licaoId(), naoNegativo(pedido.segundos()),
                    naoNegativo(pedido.interacoes()), Boolean.TRUE.equals(pedido.concluiu()));
            case "erro" -> {
                TipoErroJs tipoErro = "promessa".equalsIgnoreCase(pedido.tipoErro())
                        ? TipoErroJs.PROMESSA_REJEITADA : TipoErroJs.ERRO;
                yield registrar.registrarErro(pedido.licaoId(), tipoErro, pedido.mensagem());
            }
            default -> null;
        };
        if (resultado == null) {
            return recusar("tipo desconhecido");
        }
        if (resultado == Resultado.LICAO_DESCONHECIDA) {
            return recusar("lição desconhecida");
        }
        return Response.accepted(Map.of("resultado", resultado.name())).build();
    }

    private static long naoNegativo(Long valor) {
        return valor == null || valor < 0 ? 0 : valor;
    }

    private static Response recusar(String motivo) {
        return Response.status(422).entity(Map.of("resultado", "RECUSADO", "motivo", motivo)).build();
    }
}
