package org.framework.net.telemetria.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gera, sob demanda, o pacote publicável do dataset de telemetria.
 *
 * <p><b>Propósito de negócio:</b> transformar a telemetria corrente num
 * {@code .zip} pronto para virar um snapshot no repositório público, sem que o
 * servidor precise de credencial de escrita no GitHub. Quem publica é o dono, da
 * máquina dele, com as credenciais dele — o servidor entrega o arquivo e não
 * ganha poder nenhum sobre o repositório.</p>
 *
 * <p><b>Invariantes do domínio:</b> a pseudonimização é <b>por pacote</b> e sem
 * segredo persistente. O identificador do visitante vira {@code visitante-001},
 * {@code visitante-002}… e o mapa morre junto com a geração. Isso mantém a única
 * informação útil (quantos visitantes distintos) e elimina a obrigação de guardar
 * um sal para sempre — com sal fixo, quem o obtivesse reverteria todos os hashes
 * de IPv4 por força bruta, porque o espaço tem só ~4 bilhões de valores. O campo
 * {@code body} é <b>reconstruído</b> a partir dos atributos já sanitizados, nunca
 * filtrado por expressão regular: ele repetia os valores em texto livre, e limpar
 * só os atributos deixaria o dado sensível no texto.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> registro que não puder ser
 * interpretado é <b>descartado e contabilizado</b>, nunca publicado por via das
 * dúvidas; a contagem aparece em {@code estatisticas.json}. Se a auditoria final
 * encontrar identificador residual, a geração falha com
 * {@link IllegalStateException} em vez de devolver um arquivo suspeito — publicar
 * não tem desfazer.</p>
 */
@ApplicationScoped
public class DatasetPublicavelService {

    private static final char LF = '\n';
    private static final char CR = '\r';

    /** Rotas que são ruído de infraestrutura e não descrevem uso do sistema. */
    private static final List<String> PREFIXOS_RUIDO =
            List.of("/q/", "/web/", "/telemetria/api", "/health", "/favicon", "/pwa/", "/sw.js",
                    "/trafego/api/aovivo");

    /** Atributos que não têm tratamento seguro e saem inteiros. */
    private static final List<String> CAMPOS_REMOVIDOS =
            List.of("framework.field.lat", "framework.field.lon", "framework.field.gps");

    /**
     * Esquema fechado: os únicos campos de evento que vão ao dataset público, além dos identificadores
     * pseudonimizados e da rota generalizada (auditoria OPS-02). São contagens, booleanos e vocabulário
     * fixo do código. Texto livre ou valor digitado pelo visitante — mensagem de exceção, hostname,
     * domínio, regra de ACL, rede, IP de laboratório, login — não está aqui e não sai. Campo novo que um
     * evento passar a gravar fica de fora até alguém o acrescentar, e a contagem por nome aparece em
     * {@code estatisticas.json} ({@code campos_fora_do_esquema}).
     */
    static final java.util.Set<String> CAMPOS_PUBLICAVEIS = java.util.Set.of(
            "achados", "alcanca", "ataques", "atributos", "autenticacoes", "avisos", "bitsComuns", "blocos",
            "bytes", "camadas", "campo", "campos", "cenario", "checagens", "checksumValido", "ciphers",
            "clienteTipo", "conceitos", "concluiu", "consistente", "dados", "dhcpv6", "dispositivos",
            "durationMs", "elapsedMs", "encaminhamentos", "encerramento", "enderecos", "enlaces", "entradas",
            "erro", "errosCorrigidos", "errosSemCorrecao", "estrategia", "exibidas", "exibidos", "export_type",
            "extras", "formato", "formatos", "hardening", "httpStatus", "interacoes", "interfaces", "licao",
            "linhas", "links", "locais", "locations", "locationsCount", "mesmaLan", "metodo", "mitigacoes",
            "modo", "motivo", "naoInterpretadas", "pagina", "pais", "passos", "passosSelecao", "pendencias",
            "portaDestino", "prefixo", "prefixoAlvo", "prefixoBase", "prefixoResumo", "prefixoVlan",
            "protecoes", "protocolo", "redes", "registros", "resultado", "roteadores", "segundos",
            "statusCalc", "students_count", "sucesso", "suprimidosAntes", "suspeitos", "tabelas", "tempo",
            "tipo", "tipoServido", "tipoSolicitado", "tipos", "topologia", "topology", "total", "totalBytes",
            "totalSubredes", "traceId", "transporte", "truncado", "usados", "usedSubnets", "veredito",
            "vlans", "wanLinks");

    /** Campo de rota: generalizado como o {@code http.route}, nunca o caminho cru. */
    private static final String CAMPO_ROTA = "framework.field.rota";

    /** Maior valor publicado de um campo do esquema; acima disso não é vocabulário fixo. */
    private static final int MAX_VALOR_PUBLICAVEL = 64;

    /** Atributos cujo valor identifica pessoa e vira pseudônimo do pacote. */
    private static final List<String> CAMPOS_IDENTIFICADORES =
            List.of("framework.field.ip", "framework.field.ip_hash", "framework.field.host",
                    "client.address", "framework.field.endereco", "framework.field.cep");

    @Inject
    TelemetriaStore telemetriaStore;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Monta o pacote completo.
     *
     * <p><b>Comportamento em caso de falha:</b> {@link IllegalStateException}
     * quando a auditoria final reprova; {@link UncheckedIOException} se o ZIP não
     * puder ser escrito em memória.</p>
     */
    public Pacote gerar() {
        List<TelemetriaEvent> eventos = telemetriaStore.snapshotEventos();
        Map<String, String> pseudonimos = new LinkedHashMap<>();
        Map<String, Integer> descartes = new LinkedHashMap<>();
        Map<String, Integer> porEvento = new TreeMap<>();
        Map<String, Integer> foraDoEsquema = new TreeMap<>();
        List<String> linhas = new ArrayList<>();
        List<Map<String, String>> planos = new ArrayList<>();
        int semCorrelacao = 0;

        for (TelemetriaEvent evento : eventos) {
            if (evento == null || evento.evento() == null) {
                descartes.merge("sem_evento", 1, Integer::sum);
                continue;
            }
            if (ehRuido(evento.httpPath())) {
                descartes.merge("ruido_tecnico", 1, Integer::sum);
                continue;
            }
            try {
                Map<String, Object> registro = registroSanitizado(evento, pseudonimos, foraDoEsquema);
                linhas.add(objectMapper.writeValueAsString(registro));
                // O CSV nasce do registro JA SANITIZADO, nunca do evento cru: duas
                // trilhas separadas divergiriam, e a mais permissiva venceria.
                planos.add(achatar(registro));
                porEvento.merge(evento.evento(), 1, Integer::sum);
                if (evento.traceId() == null || evento.traceId().isBlank()) {
                    semCorrelacao++;
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                descartes.merge("nao_interpretado", 1, Integer::sum);
            }
        }

        String ndjson = String.join("\n", linhas) + (linhas.isEmpty() ? "" : "\n");
        auditar(ndjson);

        String data = LocalDate.now(ZoneOffset.UTC).toString();
        Map<String, Object> estatisticas = montarEstatisticas(
                data, linhas.size(), pseudonimos.size(), descartes, porEvento, semCorrelacao, foraDoEsquema);

        Map<String, String> arquivos = new LinkedHashMap<>();
        String base = "dataset/" + data + "/";
        try {
            arquivos.put(base + "eventos.jsonl", ndjson);
            arquivos.put(base + "eventos.csv", csv(planos));
            arquivos.put(base + "estatisticas.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(estatisticas));
            arquivos.put(base + "schema.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema()));
            arquivos.put(base + "README.md", leiaMe(data, estatisticas));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar o dataset", ex);
        }

        telemetriaLogger.logEvent("info", "telemetria", "dataset_gerado", Map.of(
                "registros", linhas.size(),
                "identificadores", pseudonimos.size(),
                "descartados", descartes.values().stream().mapToInt(Integer::intValue).sum()));

        return new Pacote(data, arquivos, linhas.size(), pseudonimos.size());
    }

    // --------------------------------------------------------------- sanitização

    private Map<String, Object> registroSanitizado(TelemetriaEvent evento,
                                                   Map<String, String> pseudonimos,
                                                   Map<String, Integer> foraDoEsquema) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        Map<String, String> camposDoBody = new LinkedHashMap<>();

        atributo(atributos, "framework.event_id", evento.id());
        atributo(atributos, "framework.module", evento.modulo());
        atributo(atributos, "event.name", evento.evento());
        atributo(atributos, "framework.status", evento.status());
        atributo(atributos, "http.request.method", evento.httpMethod());
        atributo(atributos, "http.route", rotaPublicavel(evento.httpPath()));
        if (evento.httpStatus() != null) {
            atributo(atributos, "http.response.status_code", String.valueOf(evento.httpStatus()));
        }
        if (evento.durationMs() != null) {
            atributo(atributos, "framework.duration_ms", String.valueOf(evento.durationMs()));
        }

        Map<String, Object> campos = evento.fields() == null ? Map.of() : evento.fields();
        for (Map.Entry<String, Object> entrada : campos.entrySet()) {
            String chave = "framework.field." + entrada.getKey();
            String valor = entrada.getValue() == null ? "" : String.valueOf(entrada.getValue());

            if (CAMPOS_REMOVIDOS.contains(chave)) {
                continue;
            }
            if (CAMPOS_IDENTIFICADORES.contains(chave)) {
                valor = pseudonimo(valor, pseudonimos);
            } else if (CAMPO_ROTA.equals(chave)) {
                valor = rotaPublicavel(valor);
            } else if (!CAMPOS_PUBLICAVEIS.contains(entrada.getKey())) {
                foraDoEsquema.merge(entrada.getKey(), 1, Integer::sum);
                continue;
            } else if (valor.length() > MAX_VALOR_PUBLICAVEL || valor.indexOf('@') >= 0) {
                foraDoEsquema.merge(entrada.getKey() + " (valor fora do formato)", 1, Integer::sum);
                continue;
            }
            if (valor.isBlank()) {
                continue;
            }
            atributo(atributos, chave, valor);
            camposDoBody.put(entrada.getKey(), valor);
        }

        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("timeUnixNano", String.valueOf(
                evento.timestamp() == null ? 0L : evento.timestamp().getEpochSecond() * 1_000_000_000L));
        registro.put("severityText", evento.level() == null ? "INFO" : evento.level());
        // body RECONSTRUÍDO: nunca filtrado. Ver invariante na documentação da classe.
        registro.put("body", Map.of("stringValue",
                montarBody(evento.evento(), evento.status(), camposDoBody)));
        registro.put("attributes", atributos);
        if (evento.traceId() != null && !evento.traceId().isBlank()) {
            registro.put("traceId", evento.traceId());
        }
        return registro;
    }

    /**
     * Versao plana do registro ja sanitizado, para o CSV.
     *
     * <p><b>Invariantes do dominio:</b> le o MESMO objeto que virou JSONL. O CSV e
     * derivado, nao paralelo — se fosse gerado a partir do evento cru, uma das duas
     * trilhas poderia sanitizar menos que a outra e a mais permissiva venceria.</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> achatar(Map<String, Object> registro) {
        Map<String, String> plano = new LinkedHashMap<>();
        plano.put("timeUnixNano", String.valueOf(registro.getOrDefault("timeUnixNano", "")));
        plano.put("severity", String.valueOf(registro.getOrDefault("severityText", "")));
        plano.put("traceId", String.valueOf(registro.getOrDefault("traceId", "")));

        Object corpo = registro.get("body");
        if (corpo instanceof Map<?, ?> mapa) {
            plano.put("body", textoDe(mapa.get("stringValue")));
        }
        Object atributos = registro.get("attributes");
        if (atributos instanceof List<?> lista) {
            for (Object item : lista) {
                if (item instanceof Map<?, ?> atributo) {
                    Object chave = atributo.get("key");
                    Object valor = atributo.get("value");
                    if (chave != null && valor instanceof Map<?, ?> v) {
                        plano.put(String.valueOf(chave), textoDe(v.get("stringValue")));
                    }
                }
            }
        }
        return plano;
    }

    /**
     * CSV com colunas estaveis.
     *
     * <p><b>Invariantes do dominio:</b> as colunas sao fixas e vem da uniao de
     * todas as chaves encontradas, ordenadas — planilha cujas colunas mudam de
     * ordem entre snapshots nao serve para comparar nada. Valor com virgula, aspas
     * ou quebra de linha sai entre aspas: sem isso uma linha desloca as colunas
     * seguintes e a planilha mostra dado errado com cara de certo.</p>
     */
    private String csv(List<Map<String, String>> planos) {
        java.util.TreeSet<String> colunas = new java.util.TreeSet<>();
        planos.forEach(p -> colunas.addAll(p.keySet()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", colunas.stream().map(this::campoCsv).toList())).append(LF);
        for (Map<String, String> plano : planos) {
            List<String> celulas = new ArrayList<>();
            for (String coluna : colunas) {
                celulas.add(campoCsv(plano.getOrDefault(coluna, "")));
            }
            sb.append(String.join(",", celulas)).append(LF);
        }
        return sb.toString();
    }

    /** Texto de um valor possivelmente ausente; nunca devolve a palavra "null" na planilha. */
    private String textoDe(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private String campoCsv(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        boolean precisa = valor.indexOf(',') >= 0 || valor.indexOf('"') >= 0
                || valor.indexOf(LF) >= 0 || valor.indexOf(CR) >= 0;
        String limpo = valor.replace("\"", "\"\"");
        return precisa ? "\"" + limpo + "\"" : limpo;
    }

    private String montarBody(String evento, String status, Map<String, String> campos) {
        StringBuilder sb = new StringBuilder("evento=").append(evento)
                .append(" status=").append(status == null ? "ok" : status);
        campos.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(' ').append(e.getKey()).append('=').append(e.getValue()));
        return sb.toString();
    }

    /**
     * Pseudônimo estável dentro deste pacote e sem sentido fora dele.
     *
     * <p><b>Invariantes do domínio:</b> o mapa vive só durante a geração. Não há
     * sal, não há chave, não há nada para vazar depois — a irreversibilidade vem
     * da construção, não do sigilo de um segredo que alguém teria de guardar para
     * sempre.</p>
     */
    private String pseudonimo(String valor, Map<String, String> pseudonimos) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return pseudonimos.computeIfAbsent(valor,
                v -> String.format(Locale.ROOT, "id-%03d", pseudonimos.size() + 1));
    }

    private void atributo(List<Map<String, Object>> destino, String chave, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        destino.add(Map.of("key", chave, "value", Map.of("stringValue", valor)));
    }

    /** Segmento "estático": palavra minúscula com hífen e no máximo 2 dígitos no fim (ipv6) — sem ponto, @ ou tamanho livre. */
    private static final java.util.regex.Pattern SEGMENTO_ESTATICO =
            java.util.regex.Pattern.compile("^[a-z][a-z-]{0,37}[0-9]{0,2}$");

    /**
     * PROPÓSITO: a rota que vai para o dataset PÚBLICO.
     * INVARIANTES: nunca publica o caminho cru — segmento que não é palavra estática vira
     * {@code {param}}. Sem isso qualquer visitante injetava texto livre no dataset (IP, e-mail) e,
     * como a auditoria final reprova IP público, travava a publicação do dono.
     * FALHA: não lança; {@code null} vira texto vazio.
     */
    static String rotaPublicavel(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return "";
        }
        String[] partes = caminho.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            String p = partes[i];
            if (p.isEmpty()) {
                continue;
            }
            sb.append(SEGMENTO_ESTATICO.matcher(p).matches() ? p : "{param}");
        }
        return sb.toString();
    }

    private boolean ehRuido(String rota) {
        if (rota == null || rota.isBlank()) {
            return false;
        }
        return PREFIXOS_RUIDO.stream().anyMatch(rota::startsWith);
    }

    /**
     * Auditoria final do texto gerado.
     *
     * <p><b>Invariantes do domínio:</b> varre o arquivo pronto, não os objetos —
     * é assim que se pega vazamento que escapou pelo {@code body} em texto livre.
     * Endereço de faixa privada, loopback e documentação permanecem de propósito:
     * são valores de laboratório digitados pelos usuários, não identificadores.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> lança em vez de devolver o pacote.
     * Publicar não tem desfazer.</p>
     */
    private void auditar(String ndjson) {
        List<String> problemas = new ArrayList<>();
        var ipv4 = java.util.regex.Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").matcher(ndjson);
        while (ipv4.find()) {
            if (ehPublico(ipv4.group())) {
                problemas.add("IP público residual: " + ipv4.group());
            }
        }
        if (java.util.regex.Pattern.compile("\\b-?\\d{1,3}\\.\\d{4,}\\b").matcher(ndjson).find()) {
            problemas.add("coordenada com precisão de GPS residual");
        }
        if (java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+").matcher(ndjson).find()) {
            problemas.add("endereço de e-mail residual");
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException(
                    "A auditoria reprovou o dataset e ele NÃO foi gerado: " + String.join("; ", problemas));
        }
    }

    private boolean ehPublico(String ip) {
        try {
            var endereco = java.net.InetAddress.getByName(ip);
            return !(endereco.isSiteLocalAddress() || endereco.isLoopbackAddress()
                    || endereco.isLinkLocalAddress() || endereco.isAnyLocalAddress()
                    || ip.startsWith("192.0.2.") || ip.startsWith("198.51.100.")
                    || ip.startsWith("203.0.113.") || ip.startsWith("100.64."));
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ pacote

    private Map<String, Object> montarEstatisticas(
            String data, int publicados, int identificadores, Map<String, Integer> descartes,
            Map<String, Integer> porEvento, int semCorrelacao, Map<String, Integer> foraDoEsquema) {

        Map<String, Object> estatisticas = new LinkedHashMap<>();
        estatisticas.put("gerado_em_utc", java.time.Instant.now().toString());
        estatisticas.put("snapshot", data);
        estatisticas.put("registros_publicados", publicados);
        // São os IPs, hosts e endereços CONSULTADOS (digitados nas ferramentas), pseudonimizados — não é
        // contagem de visitantes do site, que o servidor nem guarda (auditoria OPS-02).
        estatisticas.put("identificadores_distintos", identificadores);
        estatisticas.put("descartados", descartes);
        estatisticas.put("campos_fora_do_esquema", foraDoEsquema);
        estatisticas.put("qualidade", Map.of("registros_sem_correlacao", semCorrelacao));
        estatisticas.put("pseudonimizacao", Map.of(
                "metodo", "pseudonimo sequencial por pacote",
                "segredo_persistente", false,
                "correlacao_entre_snapshots", false));
        estatisticas.put("eventos", porEvento);
        return estatisticas;
    }

    private Map<String, Object> schema() {
        return Map.of(
                "formato", "NDJSON — um OpenTelemetry LogRecord por linha",
                "campos", List.of("timeUnixNano", "severityText", "body", "attributes", "traceId"),
                "atributos", List.of("framework.event_id", "framework.module", "event.name",
                        "framework.status", "http.request.method", "http.route",
                        "http.response.status_code", "framework.duration_ms"),
                "campos_framework_field", new java.util.TreeSet<>(CAMPOS_PUBLICAVEIS),
                "campos_pseudonimizados", CAMPOS_IDENTIFICADORES,
                "campo_rota_generalizada", CAMPO_ROTA);
    }

    private String leiaMe(String data, Map<String, Object> estatisticas) {
        return """
                # Snapshot %s

                Gerado a partir da telemetria de produção de
                [frameworknet.carminati.dev.br](https://frameworknet.carminati.dev.br).

                - Registros publicados: **%s**
                - Identificadores consultados distintos (IP, host ou endereço digitado nas ferramentas,
                  pseudonimizado): **%s** — não é contagem de visitantes do site

                ## Arquivos

                | Arquivo | Para que serve |
                |---|---|
                | `eventos.jsonl` | **fonte canonica** — NDJSON, um OpenTelemetry `LogRecord` por linha, com os atributos aninhados |
                | `eventos.csv` | mesma informacao achatada, para abrir em planilha, pandas ou R |
                | `schema.json` | formato e atributos |
                | `estatisticas.json` | contagens, descartes e qualidade |

                O CSV e **derivado** do JSONL, gerado a partir dos mesmos registros ja
                sanitizados — nao e uma segunda extracao. Havendo divergencia entre os
                dois, o JSONL manda: ele preserva a estrutura aninhada que o achatamento
                do CSV precisa simplificar.

                ## Sanitização

                | Dado original | Tratamento |
                |---|---|
                | IP, host ou endereço consultado | `id-NNN`, sequencial **dentro deste pacote** |
                | Demais campos de evento | só os do esquema fechado (`schema.json`); texto livre, mensagem de erro, hostname, domínio, regra e rede ficam de fora |
                | Rota | segmento que não é palavra fixa vira `{param}` |
                | Coordenadas GPS | removidas |
                | Campo `body` | reconstruído a partir dos atributos já sanitizados |
                | Estáticos, `/q/*`, `/web/*`, `/telemetria/api*`, health | descartados |

                **Não existe sal nem segredo por trás do pseudônimo.** O mapa de
                identidades vive só durante a geração e é descartado. A consequência
                aceita conscientemente: não dá para correlacionar o mesmo visitante
                entre dois snapshots — em troca, não há segredo algum a guardar, e
                nenhum vazamento futuro torna estes dados reversíveis.

                Endereços IPv4 privados, de loopback e de documentação permanecem: são
                valores de laboratório digitados pelos usuários para estudar, não
                identificadores de pessoas.

                A geração falha e nada é produzido se a auditoria final encontrar IP
                público, coordenada ou e-mail residual no arquivo pronto.
                """.formatted(data,
                estatisticas.get("registros_publicados"),
                estatisticas.get("identificadores_distintos"));
    }

    /**
     * O snapshot pronto para ir ao repositorio.
     *
     * <p><b>Invariantes do dominio:</b> os arquivos existem so em memoria. O
     * servidor nao grava dataset em disco nem guarda copia — ele gera, envia e
     * descarta. Menos um lugar onde dado de visitante pode ficar esquecido.</p>
     */
    public record Pacote(String data, Map<String, String> arquivos,
                         int registros, int visitantes) {
    }
}
