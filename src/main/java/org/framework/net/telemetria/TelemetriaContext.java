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

    private static final Pattern VOCABULARIO = Pattern.compile("[a-z_-]{1,32}");

    /**
     * Valor de vocabulário (modo, topologia) pronto para campo de evento.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> esses campos vão ao dataset público como "vocabulário fixo do código", mas
     * quando o valor vem do formulário quem envia escolhe o texto: um IP fazia a auditoria do dataset abortar a
     * geração inteira (ela recusa IP público), e texto livre saía publicado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só passa palavra minúscula de letras, {@code _} e {@code -}, até 32
     * caracteres — o vocabulário real do código; o resto vira {@code "outro"}. Custo linear.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira {@code "outro"}.</p>
     */
    public static String vocabulario(String valor) {
        String v = valor == null ? "" : valor.strip().toLowerCase(java.util.Locale.ROOT);
        return v.length() <= 32 && VOCABULARIO.matcher(v).matches() ? v : "outro";
    }

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
        return paraLog(caminho, MAX_CAMINHO);
    }

    /**
     * Texto vindo de quem usa o site, pronto para ir ao log, ao console e ao evento da telemetria.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> um valor digitado (domínio, IP, campo de formulário) entra em mensagem de
     * log e em campo de evento. Cru, uma quebra de linha forjava uma linha inteira no console do painel e no
     * {@code docker logs} (um "login" que não aconteceu), e um valor de 1 MB enchia a memória e a trilha.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum caractere de controle (C0, DEL, C1, separadores de linha Unicode)
     * sai cru — vira {@code %XX}/{@code %uXXXX}; metade de par substituto (surrogate órfão) também vira
     * {@code %uXXXX} — crua, ela sairia como "?" no console e no JSONL (medido em 25/11/2026), escondendo o que de
     * fato chegou; controle bidirecional (que reordena o texto na tela) também vira {@code %uXXXX}, e o próprio
     * {@code %} vira {@code %25}, para o escape ser inequívoco; texto legítimo, inclusive acentuado e emoji, passa
     * igual. No máximo {@code max} caracteres antes da marca
     * {@link #MARCA_TRUNCADO}: o corte nunca parte um escape nem um par substituto ao meio. Custo linear e
     * limitado por {@code max}. É a regra única do projeto para isso: {@link #caminhoParaLog} e o
     * {@code TelemetriaLogger} a usam.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira {@code ""}; {@code max} menor que 1 é
     * tratado como 1.</p>
     */
    public static String paraLog(String texto, int max) {
        return sanear(texto, max, true);
    }

    /**
     * Texto que JA passou (ou deveria ter passado) por {@link #paraLog}, pronto para voltar a circular.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> eventos gravados por versão anterior podem trazer controle cru; os gravados por esta
     * versão já vêm saneados. A mesma função tem de servir aos dois sem estragar o segundo.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> mesma neutralização de {@link #paraLog}, exceto o {@code %}, que NÃO é
     * reescapado — por isso é idempotente: aplicada de novo a cada reinício, não dobra escapes ({@code %0A} não vira
     * {@code %250A}). No máximo {@code max} caracteres antes da marca.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira {@code ""}.</p>
     */
    public static String neutralizarLegado(String texto, int max) {
        return sanear(texto, max, false);
    }

    /**
     * Só corta: texto já saneado no teto, sem partir emoji, com a marca {@link #MARCA_TRUNCADO}.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a mensagem do evento é montada com valores já saneados; saneá-la de novo dobrava
     * os escapes ({@code %0A} virava {@code %250A} no console).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> não muda nenhum caractere; no máximo {@code max} caracteres antes da marca.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo continua nulo.</p>
     */
    public static String cortar(String texto, int max) {
        int teto = Math.max(1, max);
        if (texto == null || texto.length() <= teto) {
            return texto;
        }
        int corte = teto;
        if (Character.isHighSurrogate(texto.charAt(corte - 1))) {
            corte--;
        }
        return texto.substring(0, corte) + MARCA_TRUNCADO;
    }

    private static String sanear(String texto, int max, boolean escaparPercent) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        int teto = Math.max(1, max);
        StringBuilder sb = new StringBuilder(Math.min(texto.length(), teto) + MARCA_TRUNCADO.length());
        int i = 0;
        while (i < texto.length()) {
            char c = texto.charAt(i);
            String unidade = null;
            int consumidos = 1;
            if (Character.isHighSurrogate(c) && i + 1 < texto.length()
                    && Character.isLowSurrogate(texto.charAt(i + 1))) {
                unidade = texto.substring(i, i + 2);
                consumidos = 2;
            } else if (Character.isISOControl(c)) {
                unidade = String.format("%%%02X", (int) c);
            } else if (c == '%' && escaparPercent) {
                unidade = "%25";   // sem isto, um "%0A" digitado ficaria igual ao escape de uma quebra de linha real
            } else if (Character.isSurrogate(c) || c == '\u2028' || c == '\u2029' || controleBidi(c)) {
                unidade = String.format("%%u%04X", (int) c);
            }
            int tamanho = unidade == null ? 1 : unidade.length();
            if (sb.length() + tamanho > teto) {
                return sb.append(MARCA_TRUNCADO).toString();
            }
            if (unidade == null) {
                sb.append(c);
            } else {
                sb.append(unidade);
            }
            i += consumidos;
        }
        return sb.toString();
    }

    /** Controle bidirecional (U+061C, U+200E/F, U+202A–202E, U+2066–2069): reordena na tela o texto do log. */
    private static boolean controleBidi(char c) {
        return c == '\u061C' || c == '\u200E' || c == '\u200F' || (c >= '\u202A' && c <= '\u202E')
                || (c >= '\u2066' && c <= '\u2069');
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
