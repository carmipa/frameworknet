package org.framework.net.telemetria.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.telemetria.OrigemAcesso;
import org.framework.net.telemetria.TelemetriaContext;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaRequestContext;
import org.framework.net.telemetria.TetoDeEventos;

import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TelemetriaRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEALTH_PATH = "/health";

    /**
     * Endpoints consultados em ciclo pela própria página (polling): uma aba esquecida no Tráfego ao vivo
     * fazia 86.400 requisições por dia, cada uma com evento (auditoria OPS-06). Ficam fora da telemetria
     * por requisição, como o healthcheck; a visita à página que os consulta continua registrada.
     */
    public static final java.util.Set<String> CAMINHOS_DE_POLLING = java.util.Set.of("/trafego/api/aovivo");

    @Inject
    TelemetriaContext telemetriaContext;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TetoDeEventos tetoDeEventos;

    /**
     * O cabeçalho {@code CF-IPCountry} vale alguma coisa neste ambiente?
     *
     * <p><b>Por que isto é configuração e não código:</b> {@code CF-IPCountry} só
     * é confiável quando existe um Cloudflare na borda que o <b>sobrescreve</b>.
     * Sem ele, o cabeçalho é apenas mais um campo que o cliente escolhe — e
     * registrá-lo como se fosse medição publica dado fabricado no painel.
     * Medido em 08/09/2026, com o site respondendo por {@code openresty} e sem
     * Cloudflare nenhum: uma requisição feita do Brasil com
     * {@code CF-IPCountry: JP} entrou na telemetria de produção como
     * {@code framework.field.pais = "JP"}.</p>
     *
     * <p>É a mesma decisão, pelo mesmo motivo, que o
     * {@code quarkus.http.proxy.allow-forwarded=false} do perfil {@code prod} e
     * que o host canônico do {@code SitemapResource}: <b>quem decide se um
     * cabeçalho vale é a configuração, não o código</b>. Padrão {@code false} —
     * ligar só quando houver uma borda que garanta o cabeçalho, e a garantia é
     * ela sobrescrever o que o cliente mandou.</p>
     *
     * <p>Desligado, o país vira {@code "??"}: a métrica fica vazia, que é
     * honesto, em vez de cheia e falsa.</p>
     */
    @ConfigProperty(name = "framework.telemetria.confiar-cf-ipcountry", defaultValue = "false")
    boolean confiarCfIpCountry;

    /**
     * Propósito de negócio: inicia a correlação de uma requisição funcional atendida pela aplicação.
     * Invariantes do domínio: o healthcheck operacional e os endpoints de polling
     * ({@link #CAMINHOS_DE_POLLING}) nunca entram na telemetria funcional nem provocam I/O.
     * Comportamento em caso de falha: propaga a falha de filtro ao runtime JAX-RS e não cria contexto parcial
     * para o endpoint de saúde.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = requestContext.getHeaderString("X-Request-Id");
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (HEALTH_PATH.equals(path) || CAMINHOS_DE_POLLING.contains(path)) {
            return;
        }
        TelemetriaRequestContext ctx = telemetriaContext.iniciarRequisicao(requestId, method, path);
        requestContext.setProperty(TelemetriaContext.REQUEST_CONTEXT_PROPERTY, ctx);
        requestContext.setProperty("requestId", ctx.requestId());
        // Origem AGREGAVEL, sem tocar no IP: pais (borda) e bot/humano (User-Agent).
        // O cabecalho so e lido quando a configuracao diz que ha borda garantindo
        // ele; senao nem chega a ser consultado, e o pais fica "??".
        requestContext.setProperty("tele.pais",
                OrigemAcesso.pais(confiarCfIpCountry
                        ? requestContext.getHeaderString("CF-IPCountry")
                        : null));
        requestContext.setProperty("tele.clienteTipo",
                OrigemAcesso.tipo(requestContext.getHeaderString("User-Agent")));
    }

    /**
     * Propósito de negócio: conclui a correlação e registra o resultado HTTP de uma requisição funcional.
     * Invariantes do domínio: somente requisições com contexto iniciado são registradas, excluindo o healthcheck.
     * Comportamento em caso de falha: sempre limpa o contexto da thread para impedir vazamento entre requisições.
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            Object property = requestContext.getProperty(TelemetriaContext.REQUEST_CONTEXT_PROPERTY);
            if (property instanceof TelemetriaRequestContext ctx) {
                int status = responseContext.getStatus();
                telemetriaContext.registrarResposta(ctx, status);
                String pais = textoOu(requestContext.getProperty("tele.pais"), "??");
                String clienteTipo = textoOu(requestContext.getProperty("tele.clienteTipo"), "desconhecido");
                // Erro de cliente (404, 401, 403, 429...) tem teto por origem: em série, ele apagava a trilha
                // durável. Erro do servidor (5xx) e sucesso gravam sempre.
                TetoDeEventos.Decisao decisao = status >= 400 && status < 500
                        ? tetoDeEventos.avaliar("http-" + status)
                        : new TetoDeEventos.Decisao(true, 0);
                if (decisao.gravar()) {
                    telemetriaLogger.logHttpAccess(ctx, status, pais, clienteTipo, decisao.suprimidosAntes());
                }
                Object requestId = requestContext.getProperty("requestId");
                if (requestId != null) {
                    responseContext.getHeaders().putSingle("X-Request-Id", requestId.toString());
                    responseContext.getHeaders().putSingle("X-Trace-Id", ctx.traceId());
                }
            }
        } finally {
            telemetriaContext.limpar();
        }
    }

    private static String textoOu(Object valor, String padrao) {
        return valor instanceof String s && !s.isBlank() ? s : padrao;
    }
}
