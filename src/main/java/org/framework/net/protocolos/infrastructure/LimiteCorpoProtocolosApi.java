package org.framework.net.protocolos.infrastructure;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.Locale;

/**
 * Recusa corpo grande na API dos aprofundamentos de protocolo antes de qualquer outra camada lê-lo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a API do teste rápido é aberta (a página funciona sem login) e recebe só três
 * campos; sem teto próprio, o pedido anônimo poderia mandar até o limite global do site (1 MB) para cada clique.
 * Mesmo desenho do teto da API da Academia, duplicado de propósito (fatias não se importam).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> vale para todo caminho que, sem parâmetros de matriz ({@code ;x}) e sem diferença
 * de caixa, comece por {@code /protocolos/api/}; roda no roteador do Vert.x, antes do RESTEasy e dos filtros JAX-RS;
 * teto de {@link #TETO_BYTES} pelo {@code Content-Length} declarado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@code Content-Length} acima do teto ou ilegível ⇒ 413 e o pedido não chega
 * à aplicação (falha fechada). Corpo sem {@code Content-Length} (envio em pedaços) segue para o limite global do
 * Quarkus: risco residual declarado, o mesmo da API da Academia.</p>
 */
@ApplicationScoped
public class LimiteCorpoProtocolosApi {

    /** Teto do corpo na API dos aprofundamentos: a resposta do teste rápido tem poucas dezenas de bytes. */
    public static final long TETO_BYTES = 1024;

    void registrar(@Observes Router router) {
        router.route().order(-1000).handler(contexto -> {
            if (!ehApiDeProtocolos(contexto.normalizedPath())) {
                contexto.next();
                return;
            }
            String declarado = contexto.request().getHeader("Content-Length");
            if (declarado != null && excede(declarado)) {
                contexto.response().setStatusCode(413)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"resultado\":\"RECUSADO\",\"motivo\":\"corpo acima de 1 KB\"}");
                return;
            }
            contexto.next();
        });
    }

    /** O caminho, sem parâmetros de matriz e sem diferença de caixa, é da API dos aprofundamentos? */
    static boolean ehApiDeProtocolos(String caminho) {
        if (caminho == null) {
            return false;
        }
        String semMatriz = caminho.replaceAll(";[^/]*", "").toLowerCase(Locale.ROOT);
        return semMatriz.startsWith("/protocolos/api/") || semMatriz.equals("/protocolos/api");
    }

    /** {@code true} se o {@code Content-Length} declarado passa do teto ou não é um número. */
    static boolean excede(String contentLength) {
        try {
            return Long.parseLong(contentLength.strip()) > TETO_BYTES;
        } catch (NumberFormatException ilegivel) {
            return true;
        }
    }
}
