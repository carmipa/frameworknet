package org.framework.net.academia.core.infrastructure;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Recusa corpo grande nas APIs da Academia antes de qualquer outra camada lê-lo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> as APIs da Academia são abertas (a lição funciona sem login).
 * O filtro de CSRF do site lê o corpo inteiro para procurar o token de formulário; sem um teto
 * antes dele, um corpo de megabytes ocuparia memória da JVM que é a mesma do site inteiro (D12).
 * Os eventos da Academia têm poucas centenas de bytes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> vale para toda requisição cujo caminho, sem parâmetros de
 * matriz ({@code ;x}), comece por {@code /academia/api/} — o RESTEasy ignora o {@code ;x} e entrega
 * {@code /academia;x/api/eventos} ao mesmo recurso, e a rota {@code /academia/api/*} do Vert.x não
 * casava com ele (auditoria ACAD-01); roda no roteador do Vert.x, antes do RESTEasy e dos filtros
 * JAX-RS; teto de {@link #TETO_BYTES} pelo {@code Content-Length} declarado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@code Content-Length} acima do teto ⇒ 413 e a conexão
 * não chega à aplicação; {@code Content-Length} ilegível ⇒ 413 também (falha fechada). Corpo
 * sem {@code Content-Length} (envio em pedaços) segue para o limite global do Quarkus — risco
 * residual declarado.</p>
 */
@ApplicationScoped
public class LimiteCorpoAcademia {

    /** Teto do corpo nas APIs da Academia. */
    public static final long TETO_BYTES = 8 * 1024;

    void registrar(@Observes Router router) {
        router.route().order(-1000).handler(contexto -> {
            if (!ehApiDaAcademia(contexto.normalizedPath())) {
                contexto.next();
                return;
            }
            String declarado = contexto.request().getHeader("Content-Length");
            if (declarado != null && excede(declarado)) {
                contexto.response().setStatusCode(413)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"resultado\":\"RECUSADO\",\"motivo\":\"corpo acima de 8 KB\"}");
                return;
            }
            contexto.next();
        });
    }

    /** O caminho, sem parâmetros de matriz e sem diferença de caixa, é de API da Academia? */
    static boolean ehApiDaAcademia(String caminho) {
        if (caminho == null) {
            return false;
        }
        String semMatriz = caminho.replaceAll(";[^/]*", "").toLowerCase(java.util.Locale.ROOT);
        return semMatriz.startsWith("/academia/api/") || semMatriz.equals("/academia/api");
    }

    /** {@code true} se o {@code Content-Length} declarado passa do teto ou não é um número. */
    public static boolean excede(String contentLength) {
        try {
            return Long.parseLong(contentLength.strip()) > TETO_BYTES;
        } catch (NumberFormatException ilegivel) {
            return true;
        }
    }
}
