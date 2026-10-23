package org.framework.net.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.framework.net.paginaErros.presentation.PaginaErroResposta;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Set;

/**
 * Limite de requisições por origem e por rota.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> proteger as rotas que custam CPU, disco ou cota de serviço externo de uma
 * origem que dispara requisições em série, sem travar a turma inteira atrás do mesmo NAT nas rotas baratas.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a recusa é sempre 429. Quem navega (o navegador pedindo uma página)
 * recebe a página de erro do site; quem chama por fetch, htmx ou API recebe o JSON de sempre — a regra é a
 * mesma do mapper de exceções, em {@link PaginaErroResposta} (auditoria FRONT-01: a página inteira virava JSON
 * cru).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> recurso não resolvido usa o caminho normalizado como chave do
 * balde; limite excedido aborta com o 429 (página ou JSON, conforme o cliente).</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 20)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Set<String> HEAVY_PATHS = Set.of(
            "/analise",
            "/resolucao-problemas",
            "/api/informacoes/geo",
            "/informacoes",
            "/calculadora",
            // Tentativa em serie contra a chave de contingencia da Telemetria — e o formulario do painel
            // administrativo, que testa A MESMA chave e ficava so no limite comum, 4 vezes maior (BACK-05).
            "/login/chave",
            "/admin/login",
            // Regrava o arquivo de historico a cada chamada.
            "/history/catalog",
            // IPv6: so o que custa. DNS (resolver, dominio) e CPU sobre entrada grande (projetar, engenharia,
            // dividir). Os calculos locais (calcular, nibbles, eui64, ula...) ficam no limite comum: com o
            // prefixo /ipv6/api/ inteiro, uma turma atras do mesmo NAT batia 30/min no exercicio mais barato.
            "/ipv6/api/resolver",
            "/ipv6/api/dominio",
            "/ipv6/api/projetar",
            "/ipv6/api/engenharia",
            "/ipv6/api/dividir"
    );

    /** Prefixo cujo custo e alto: Localizacao (APIs externas com cota global: ip-api 45/min, Nominatim 1 req/s). */
    private static final java.util.List<String> HEAVY_PREFIXES = java.util.List.of(
            "/localizacao/api/"
    );

    @Context
    ResourceInfo resourceInfo;

    @Inject
    RequestRateLimiter rateLimiter;

    @Inject
    PaginaErroResposta paginaErroResposta;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        // Os cálculos da Calculadora e da Resolução podem gerar centenas de linhas por
        // requisição, então todo o subcaminho conta como pesado — não só a página.
        // Os POST /api dos simuladores (Diagnóstico, Segurança) também: cada requisição
        // grava telemetria (custo de I/O), e é entrada de usuário ecoada — limite estrito.
        boolean postApi = "POST".equals(requestContext.getMethod())
                && (path.startsWith("/diagnostico/api/") || path.startsWith("/seguranca/api/"));
        boolean heavy = HEAVY_PATHS.contains(path)
                || ("POST".equals(requestContext.getMethod()) && path.startsWith("/resolucao-problemas"))
                || path.startsWith("/calculadora/")
                || HEAVY_PREFIXES.stream().anyMatch(path::startsWith)
                || postApi;
        if (!rateLimiter.allow(requestContext, chaveDeRota(path), heavy)) {
            requestContext.abortWith(paginaErroResposta.recusa(requestContext, Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                    "{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}"));
        }
    }

    /**
     * Identidade do endpoint resolvido (classe#método): /protocolos/a e /protocolos/b são a MESMA rota.
     * Sem recurso resolvido, cai no caminho normalizado (mantém o comportamento anterior).
     */
    private String chaveDeRota(String path) {
        try {
            if (resourceInfo != null && resourceInfo.getResourceClass() != null
                    && resourceInfo.getResourceMethod() != null) {
                return resourceInfo.getResourceClass().getSimpleName() + "#" + resourceInfo.getResourceMethod().getName();
            }
        } catch (RuntimeException semContexto) {
            // fallback abaixo
        }
        return path;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }
}
