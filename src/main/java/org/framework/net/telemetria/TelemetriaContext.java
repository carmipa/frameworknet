package org.framework.net.telemetria;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class TelemetriaContext {

    public static final String REQUEST_CONTEXT_PROPERTY = "framework.telemetria.requestContext";

    /** Maior caminho guardado na telemetria; o resto vira a marca {@link #MARCA_TRUNCADO}. */
    public static final int MAX_CAMINHO = 512;

    static final String MARCA_TRUNCADO = "...(truncado)";

    private static final Pattern REQUEST_ID_ACEITO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Abre a correlação de uma requisição.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> todo evento da requisição (acesso, erro, negócio) sai com o mesmo
     * {@code requestId}/{@code traceId} e o mesmo caminho, para a investigação ligar o que o usuário viu ao
     * que foi gravado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o {@code X-Request-Id} do cliente só é aceito com 1 a 64 caracteres
     * de {@code [A-Za-z0-9._-]}; fora disso é substituído por um gerado (auditoria SEC-03 e OPS-01: 15.000
     * caracteres viravam uma linha de 16 KB, ecoada na resposta). O caminho passa por
     * {@link #caminhoParaLog}: caractere de controle não forja linha no log (OPS-11) e caminho gigante não
     * infla o evento.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; valor nulo ou recusado vira identificador gerado
     * e caminho {@code "/"}.</p>
     */
    public TelemetriaRequestContext iniciarRequisicao(String requestId, String httpMethod, String httpPath) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String bruto = requestId == null ? "" : requestId.strip();
        String resolvedRequestId = REQUEST_ID_ACEITO.matcher(bruto).matches() ? bruto : traceId.substring(0, 8);
        TelemetriaRequestContext ctx = new TelemetriaRequestContext(
                resolvedRequestId,
                traceId,
                httpMethod,
                caminhoParaLog(httpPath),
                System.currentTimeMillis()
        );
        aplicarMdc(ctx, null, null);
        return ctx;
    }

    /**
     * Caminho de requisição pronto para ir ao log, ao console e ao evento.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o caminho chega decodificado ({@code %0A} vira quebra de linha) e
     * escolhido por quem chama. Escrito cru, ele forjava uma linha inteira no log e no console da
     * telemetria (auditoria OPS-11).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum caractere de controle (C0, DEL, C1, separadores de linha
     * Unicode) sai cru — vira {@code %XX}/{@code %uXXXX}; texto legítimo, inclusive acentuado, passa
     * igual; no máximo {@value #MAX_CAMINHO} caracteres, com a marca de truncado.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo ou vazio vira {@code "/"}.</p>
     */
    public static String caminhoParaLog(String caminho) {
        if (caminho == null || caminho.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder(Math.min(caminho.length(), MAX_CAMINHO) + 16);
        for (int i = 0; i < caminho.length(); i++) {
            if (sb.length() >= MAX_CAMINHO) {
                return sb.append(MARCA_TRUNCADO).toString();
            }
            char c = caminho.charAt(i);
            if (Character.isISOControl(c)) {
                sb.append(String.format("%%%02X", (int) c));
            } else if (c == ' ' || c == ' ' || c == '\u0085') {
                sb.append(String.format("%%u%04X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public void registrarResposta(TelemetriaRequestContext ctx, int httpStatus) {
        if (ctx == null) {
            return;
        }
        aplicarMdc(ctx, httpStatus, ctx.elapsedMillis());
    }

    public void limpar() {
        MDC.remove(TelemetriaKeys.REQUEST_ID);
        MDC.remove(TelemetriaKeys.TRACE_ID);
        MDC.remove(TelemetriaKeys.HTTP_METHOD);
        MDC.remove(TelemetriaKeys.HTTP_PATH);
        MDC.remove(TelemetriaKeys.HTTP_STATUS);
        MDC.remove(TelemetriaKeys.DURATION_MS);
        MDC.remove(TelemetriaKeys.MODULO);
        MDC.remove(TelemetriaKeys.EVENTO);
        MDC.remove(TelemetriaKeys.STATUS);
    }

    public void aplicarEvento(String modulo, String evento, String status) {
        MDC.put(TelemetriaKeys.MODULO, modulo);
        MDC.put(TelemetriaKeys.EVENTO, evento);
        MDC.put(TelemetriaKeys.STATUS, status);
    }

    /**
     * Reconstrói a correlação da requisição em curso a partir do MDC.
     *
     * <p><b>Propósito de negócio:</b> serviços de aplicação emitem eventos sem
     * ter acesso ao {@code ContainerRequestContext} do JAX-RS. Sem este resgate,
     * todo evento de negócio sai sem {@code traceId} e o dataset publicado não
     * permite ligar um erro à requisição que o causou — era o caso de 29,1% dos
     * registros coletados até 2026-08-03.</p>
     *
     * <p><b>Invariantes do domínio:</b> devolve apenas os campos de correlação.
     * O {@code startedAtMillis} é o instante da reconstrução e <b>não</b> serve
     * para medir duração — quem precisa de duração real usa o contexto original,
     * criado em {@link #iniciarRequisicao}. O MDC é por thread, então só há
     * resgate quando o evento nasce na própria thread da requisição.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code null} quando não
     * há requisição em curso (inicialização da aplicação, tarefas de fundo,
     * outra thread). Nunca lança.</p>
     */
    public TelemetriaRequestContext contextoDoMdc() {
        String traceId = valorMdc(TelemetriaKeys.TRACE_ID);
        if (traceId == null) {
            return null;
        }
        return new TelemetriaRequestContext(
                valorMdc(TelemetriaKeys.REQUEST_ID),
                traceId,
                valorMdc(TelemetriaKeys.HTTP_METHOD),
                valorMdc(TelemetriaKeys.HTTP_PATH),
                System.currentTimeMillis());
    }

    private static String valorMdc(String chave) {
        Object valor = MDC.get(chave);
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).strip();
        return texto.isEmpty() ? null : texto;
    }

    private void aplicarMdc(TelemetriaRequestContext ctx, Integer httpStatus, Long durationMs) {
        MDC.put(TelemetriaKeys.REQUEST_ID, ctx.requestId());
        MDC.put(TelemetriaKeys.TRACE_ID, ctx.traceId());
        MDC.put(TelemetriaKeys.HTTP_METHOD, ctx.httpMethod());
        MDC.put(TelemetriaKeys.HTTP_PATH, ctx.httpPath());
        if (httpStatus != null) {
            MDC.put(TelemetriaKeys.HTTP_STATUS, String.valueOf(httpStatus));
        }
        if (durationMs != null) {
            MDC.put(TelemetriaKeys.DURATION_MS, String.valueOf(durationMs));
        }
    }
}
