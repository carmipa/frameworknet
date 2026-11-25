package org.framework.net.telemetria;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class TelemetriaLogger {

    private static final Logger LOG = Logger.getLogger("framework.telemetria");

    /** Maior valor de texto por campo de evento (um IPv6 ou um domínio cabem com folga). */
    static final int MAX_VALOR_CAMPO = 256;

    /** Maior mensagem de evento (console do painel, stdout e o próprio evento). */
    static final int MAX_MENSAGEM = 1024;

    /** Profundidade do escopo {@link #semEventosInfo} na thread atual; ausente fora de qualquer escopo. */
    private static final ThreadLocal<int[]> SILENCIO_INFO = new ThreadLocal<>();

    /**
     * Executa a ação sem gravar os eventos {@code info} e {@code debug} que ela produzir nesta thread.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> operação em lote — o pacote da turma resolve um cenário por aluno — gravava um
     * evento por item: até 200 alunos e três eventos cada, numa janela de 500, empurravam para fora tudo o que havia
     * antes, inclusive a evidência de outros pedidos. O lote grava UM evento de resumo, fora deste escopo.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só {@code info} e {@code debug} (e nível desconhecido, que é gravado como info)
     * calam; {@code warn} e {@code error} passam sempre — falha nunca some. O escopo vale só para a thread que chamou
     * (outro pedido continua gravando) e é desfeito no {@code finally}, mesmo com exceção; escopo dentro de escopo
     * conta profundidade.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não engole exceção da ação: ela sobe igual, com o escopo já desfeito.</p>
     */
    public static <T> T semEventosInfo(java.util.function.Supplier<T> acao) {
        int[] nivel = SILENCIO_INFO.get();
        if (nivel == null) {
            nivel = new int[1];
            SILENCIO_INFO.set(nivel);
        }
        nivel[0]++;
        try {
            return acao.get();
        } finally {
            nivel[0]--;
            if (nivel[0] == 0) {
                SILENCIO_INFO.remove();
            }
        }
    }

    /** Este nível está calado pelo escopo {@link #semEventosInfo} da thread atual? */
    static boolean silenciado(String level) {
        int[] nivel = SILENCIO_INFO.get();
        if (nivel == null || nivel[0] == 0) {
            return false;
        }
        String l = level == null ? "" : level.toLowerCase(java.util.Locale.ROOT);
        return !(l.equals("warn") || l.equals("warning") || l.equals("error"));
    }

    @Inject
    TelemetriaContext context;

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaConsoleBuffer consoleBuffer;

    @ConfigProperty(name = "framework.telemetry.enabled", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes @Priority(20) StartupEvent event) {
        logEvent("info", "sistema", "app_start", Map.of("status", "ok"));
    }

    /**
     * Evento cujo status vem do próprio mapa de campos.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> 38 chamadas passam o status em {@code Map.of("status", …)}; antes este
     * sobrecarregado gravava sempre {@code ok}, e o dataset público mostrava timeout e erro de DNS, erro do IPv6 e
     * recusa de CSRF como sucesso.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o status do evento é o mesmo valor do campo {@code status} (ver
     * {@link #statusDosCampos}); sem esse campo, {@code ok}, como sempre foi.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> mapa nulo, sem {@code status}, com valor em branco ou que não é texto
     * grava {@code ok}; não lança.</p>
     */
    public void logEvent(String level, String modulo, String evento, Map<String, Object> fields) {
        logEvent(level, modulo, evento, statusDosCampos(fields), null, fields, false, null);
    }

    /**
     * Status do evento tirado do campo {@code status}.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> uma fonte só para o status: o que a chamada escreveu no campo é o que o evento,
     * a mensagem e o dataset dizem.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só texto não vazio vira status, com o mesmo saneamento e teto dos demais campos
     * ({@link #valorParaEvento}); espaços das pontas saem.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> devolve {@code ok} para mapa nulo, campo ausente, em branco ou não
     * textual; não lança.</p>
     */
    static String statusDosCampos(Map<String, Object> fields) {
        Object valor = fields == null ? null : fields.get("status");
        if (valor instanceof CharSequence texto && !texto.toString().isBlank()) {
            return String.valueOf(valorParaEvento(texto.toString().strip()));
        }
        return "ok";
    }

    public void logEvent(String level, String modulo, String evento, String status, Map<String, Object> fields) {
        logEvent(level, modulo, evento, status, null, fields, false, null);
    }

    public void logEvent(
            String level,
            String modulo,
            String evento,
            String status,
            TelemetriaRequestContext requestContext,
            Map<String, Object> fields,
            boolean excInfo,
            Throwable error) {

        if (!enabled || silenciado(level)) {
            return;
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (value != null && !String.valueOf(value).isBlank()) {
                    cleaned.put(key, valorParaEvento(value));
                }
            });
        }

        // Serviços de aplicação chamam logEvent sem o contexto do JAX-RS em mãos.
        // Nesse caso a correlação é resgatada do MDC da própria thread da requisição,
        // senão o evento sai órfão e não há como ligá-lo ao request no dataset.
        TelemetriaRequestContext ctx = requestContext != null ? requestContext : context.contextoDoMdc();
        // Os valores já chegam saneados (valorParaEvento); a mensagem só é cortada — saneá-la de novo dobrava os escapes.
        String message = TelemetriaContext.cortar(montarMensagem(evento, status, cleaned), MAX_MENSAGEM);
        context.aplicarEvento(modulo, evento, status);

        TelemetriaEvent telemetriaEvent = new TelemetriaEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                level.toUpperCase(),
                modulo,
                evento,
                status,
                ctx != null ? ctx.requestId() : null,
                ctx != null ? ctx.traceId() : null,
                ctx != null ? ctx.httpMethod() : null,
                ctx != null ? ctx.httpPath() : null,
                null,
                // Duração só sai do contexto ORIGINAL: o resgatado do MDC nasce agora
                // e reportaria 0 ms para uma requisição que já está em andamento.
                requestContext != null ? requestContext.elapsedMillis() : null,
                message,
                cleaned
        );
        store.registrar(telemetriaEvent);
        consoleBuffer.append(level, message);

        switch (level.toLowerCase()) {
            case "debug" -> {
                if (excInfo && error != null) {
                    LOG.debug(message, error);
                } else {
                    LOG.debug(message);
                }
            }
            case "warn", "warning" -> {
                if (excInfo && error != null) {
                    LOG.warn(message, error);
                } else {
                    LOG.warn(message);
                }
            }
            case "error" -> {
                if (excInfo && error != null) {
                    LOG.error(message, error);
                } else {
                    LOG.error(message);
                }
            }
            default -> {
                if (excInfo && error != null) {
                    LOG.info(message, error);
                } else {
                    LOG.info(message);
                }
            }
        }
    }

    public void logHttpAccess(TelemetriaRequestContext ctx, int httpStatus, String pais, String clienteTipo) {
        logHttpAccess(ctx, httpStatus, pais, clienteTipo, 0);
    }

    /**
     * Evento de acesso HTTP de uma requisição.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> alimenta o painel (volume por módulo, rotas, país, bots) e a trilha
     * de investigação.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> nunca o IP; quando o teto por origem suprimiu eventos iguais no
     * minuto anterior, a quantidade vai no campo {@code suprimidosAntes} — o painel conta a recusa, não a
     * esconde.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> telemetria desligada ou contexto nulo não grava nada.</p>
     */
    public void logHttpAccess(TelemetriaRequestContext ctx, int httpStatus, String pais, String clienteTipo,
                              int suprimidosAntes) {
        if (!enabled || ctx == null) {
            return;
        }
        String status = httpStatus >= 400 ? "error" : "ok";
        String level = httpStatus >= 500 ? "error" : httpStatus >= 400 ? "warn" : "info";
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("httpStatus", httpStatus);
        fields.put("durationMs", ctx.elapsedMillis());
        // Origem agregavel, nunca o IP: pais (CF-IPCountry) e bot/humano (User-Agent).
        fields.put("pais", pais == null ? "??" : pais);
        fields.put("clienteTipo", clienteTipo == null ? "desconhecido" : clienteTipo);
        if (suprimidosAntes > 0) {
            fields.put("suprimidosAntes", suprimidosAntes);
        }

        TelemetriaEvent telemetriaEvent = new TelemetriaEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                level.toUpperCase(),
                "web",
                "http_access",
                status,
                ctx.requestId(),
                ctx.traceId(),
                ctx.httpMethod(),
                ctx.httpPath(),
                httpStatus,
                ctx.elapsedMillis(),
                "HTTP " + ctx.httpMethod() + " " + ctx.httpPath() + " status=" + httpStatus,
                fields
        );
        store.registrar(telemetriaEvent);
        context.aplicarEvento("web", "http_access", status);
        String httpMsg = "HTTP " + ctx.httpMethod() + " " + ctx.httpPath()
                + " status=" + httpStatus + " durationMs=" + ctx.elapsedMillis()
                + " requestId=" + ctx.requestId();
        consoleBuffer.append(level, httpMsg);
        LOG.infof("evento=http_access status=%s method=%s path=%s httpStatus=%d durationMs=%d requestId=%s",
                status, ctx.httpMethod(), ctx.httpPath(), httpStatus, ctx.elapsedMillis(), ctx.requestId());
    }

    /**
     * Evento de erro com a exceção.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> registrar a falha com o tipo, a mensagem e a pilha, para a investigação.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o log recebe uma CÓPIA da exceção com as mensagens saneadas
     * ({@link #comMensagensSaneadas}) e a mesma pilha — a mensagem costuma trazer o que a pessoa digitou (até 256 KB
     * num campo de formulário), e o formato do console imprime a exceção inteira: crua, ela forjava linha no
     * {@code docker logs} e girava os arquivos de log em segundos, apagando a evidência.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; exceção nula registra só o evento.</p>
     */
    public void logException(String modulo, String evento, TelemetriaRequestContext ctx, Throwable error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (error != null) {
            fields.put("erro", error.getClass().getSimpleName());
            if (error.getMessage() != null) {
                fields.put("mensagem", truncar(error.getMessage(), 300));
            }
        }
        logEvent("error", modulo, evento, "error", ctx, fields, true, comMensagensSaneadas(error));
    }

    /** Maior mensagem de exceção que vai ao log (por nível da cadeia de causas). */
    static final int MAX_MENSAGEM_EXCECAO = 300;

    /** Níveis da cadeia de causas copiados (o resto da cadeia não traz informação nova para o log). */
    static final int MAX_NIVEIS_CAUSA = 5;

    /**
     * Cópia da exceção pronta para o log.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> manter a pilha para o diagnóstico sem levar ao log o texto cru de quem chamou.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a cópia começa pelo nome da classe original seguido da mensagem saneada
     * ({@link TelemetriaContext#paraLog}, até {@value #MAX_MENSAGEM_EXCECAO}); a pilha é a mesma (é código, não dado
     * digitado); a causa é copiada do mesmo jeito até {@value #MAX_NIVEIS_CAUSA} níveis, e abaixo disso fica só a
     * causa RAIZ, com a contagem das intermediárias omitidas; as exceções suprimidas (ex.: a falha do {@code close()}
     * de um try-with-resources) também são copiadas, saneadas.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira nulo.</p>
     */
    static Throwable comMensagensSaneadas(Throwable original) {
        return sanear(original, 0);
    }

    private static Throwable sanear(Throwable original, int nivel) {
        if (original == null) {
            return null;
        }
        Throwable causa = nivel < MAX_NIVEIS_CAUSA ? sanear(original.getCause(), nivel + 1) : raiz(original.getCause());
        ExcecaoSaneada copia = new ExcecaoSaneada(mensagemSaneada("", original), causa);
        copia.setStackTrace(original.getStackTrace());
        if (nivel < MAX_NIVEIS_CAUSA) {
            for (Throwable suprimida : original.getSuppressed()) {
                copia.addSuppressed(sanear(suprimida, nivel + 1));
            }
        }
        return copia;
    }

    /** Só a causa raiz de uma cadeia funda demais, dizendo quantas intermediárias ficaram de fora. */
    private static Throwable raiz(Throwable resto) {
        if (resto == null) {
            return null;
        }
        Throwable raiz = resto;
        int omitidas = 0;
        while (raiz.getCause() != null && raiz.getCause() != raiz && omitidas < 100) {
            raiz = raiz.getCause();
            omitidas++;
        }
        ExcecaoSaneada copia = new ExcecaoSaneada(mensagemSaneada(omitidas > 0
                ? "[" + omitidas + " causa(s) intermediária(s) omitida(s)] " : "", raiz), null);
        copia.setStackTrace(raiz.getStackTrace());
        return copia;
    }

    private static String mensagemSaneada(String prefixo, Throwable t) {
        return prefixo + (t.getMessage() == null ? t.getClass().getName()
                : t.getClass().getName() + ": " + TelemetriaContext.paraLog(t.getMessage(), MAX_MENSAGEM_EXCECAO));
    }

    /** Exceção-cópia: imprime só a classe original e a mensagem saneada, sem o próprio nome na frente. */
    static final class ExcecaoSaneada extends RuntimeException {
        ExcecaoSaneada(String mensagem, Throwable causa) {
            super(mensagem, causa, true, true);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    public <T> T medir(String modulo, String evento, Supplier<T> acao) {
        long inicio = System.currentTimeMillis();
        try {
            T resultado = acao.get();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("durationMs", System.currentTimeMillis() - inicio);
            logEvent("info", modulo, evento, "ok", fields);
            return resultado;
        } catch (RuntimeException ex) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("durationMs", System.currentTimeMillis() - inicio);
            fields.put("erro", ex.getClass().getSimpleName());
            logEvent("error", modulo, evento, "error", fields);
            throw ex;
        }
    }

    public void medirVoid(String modulo, String evento, Runnable acao) {
        medir(modulo, evento, () -> {
            acao.run();
            return null;
        });
    }

    /**
     * Linha "evento=… status=… chave=valor" do evento.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a forma legível do evento no console do painel e no stdout.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> texto já saneado entra como está (não é reescapado); valor de outro tipo
     * (lista, mapa, objeto) passa por {@link TelemetriaContext#paraLog} no próprio texto — o {@code toString} dele pode
     * trazer quebra de linha.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança.</p>
     */
    private static String montarMensagem(String evento, String status, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("evento=").append(evento).append(" status=").append(status);
        fields.entrySet().stream()
                .filter(entry -> !"status".equals(entry.getKey())) // 'status' já vai no prefixo — evita "status=ok status=ok"
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(' ').append(entry.getKey()).append('=').append(
                        entry.getValue() instanceof CharSequence
                                ? entry.getValue()
                                : TelemetriaContext.paraLog(String.valueOf(entry.getValue()), MAX_VALOR_CAMPO)));
        return sb.toString();
    }

    /**
     * Valor de um campo de evento pronto para guardar.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> campos de evento carregam o que a pessoa digitou (domínio, IP de
     * consulta). Sem teto, um campo de 1 MB por requisição esgotava a memória em ~150 pedidos e trocava a
     * trilha durável (JSONL e Stream) por lixo; com quebra de linha crua, forjava linha no console.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> texto sai neutralizado e com no máximo {@value #MAX_VALOR_CAMPO}
     * caracteres (regra única: {@link TelemetriaContext#paraLog}); número, booleano e os demais tipos passam
     * como estão — o painel e o dataset dependem do tipo.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança.</p>
     */
    static Object valorParaEvento(Object valor) {
        if (valor instanceof CharSequence texto) {
            return TelemetriaContext.paraLog(texto.toString(), MAX_VALOR_CAMPO);
        }
        return valor;
    }

    private static String truncar(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
