package org.framework.net.telemetria.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.telemetria.TelemetriaEvent;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaStore;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exportação da telemetria em formatos legíveis por gente e por planilha.
 *
 * <p><b>Propósito de negócio:</b> o JSON compartilhável serve para máquina e para
 * o dataset público; ele não serve para anexar numa entrega nem para abrir no
 * Excel. Aqui saem os formatos que fecham essa lacuna — CSV para planilha, TXT
 * para leitura direta e HTML para imprimir ou mandar por e-mail.</p>
 *
 * <p><b>Invariantes do domínio:</b> nenhum formato inventa dado. Campo ausente
 * sai vazio, nunca com zero ou com "N/A" que pareça medição. O CSV escapa aspas e
 * quebra de linha para que um valor com vírgula não desloque colunas — planilha
 * desalinhada é dado errado com aparência de certo. O HTML escapa o conteúdo:
 * evento vem de entrada de usuário e não pode virar marcação executável no
 * arquivo que alguém vai abrir no navegador.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> não lança. Telemetria vazia produz
 * arquivo com cabeçalho e nenhuma linha, o que é o retrato honesto de "não há
 * eventos" — diferente de um arquivo que falhou ao ser gerado.</p>
 */
@ApplicationScoped
public class TelemetriaExportService {

    /** Formatos oferecidos na tela. */
    public enum Formato {
        JSON("json", "application/json"),
        CSV("csv", "text/csv; charset=utf-8"),
        TXT("txt", "text/plain; charset=utf-8"),
        HTML("html", "text/html; charset=utf-8");

        private final String extensao;
        private final String tipoMime;

        Formato(String extensao, String tipoMime) {
            this.extensao = extensao;
            this.tipoMime = tipoMime;
        }

        public String extensao() {
            return extensao;
        }

        public String tipoMime() {
            return tipoMime;
        }

        /**
         * Resolve o formato pedido na URL.
         *
         * <p><b>Comportamento em caso de falha:</b> valor desconhecido cai em
         * {@link #JSON}, que é o comportamento histórico da rota — nunca em erro.</p>
         */
        public static Formato de(String texto) {
            if (texto == null) {
                return JSON;
            }
            for (Formato f : values()) {
                if (f.extensao.equalsIgnoreCase(texto.strip())) {
                    return f;
                }
            }
            return JSON;
        }
    }

    @Inject
    TelemetriaStore store;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Nome do arquivo entregue ao navegador. */
    public String nomeArquivo(Formato formato) {
        return "telemetria." + formato.extensao();
    }

    public String gerar(Formato formato) {
        List<TelemetriaEvent> eventos = store.snapshotEventos();
        telemetriaLogger.logEvent("info", "telemetria", "exportacao", Map.of(
                "formato", formato.extensao(), "registros", eventos.size()));

        return switch (formato) {
            case CSV -> csv(eventos);
            case TXT -> txt(eventos);
            case HTML -> html(eventos);
            case JSON -> "";
        };
    }

    // ------------------------------------------------------------------- CSV

    private String csv(List<TelemetriaEvent> eventos) {
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp,nivel,modulo,evento,status,metodo,rota,http_status,duracao_ms,trace_id,mensagem\n");
        for (TelemetriaEvent e : eventos) {
            sb.append(campo(e.timestamp() == null ? "" : e.timestamp().toString())).append(',')
              .append(campo(e.level())).append(',')
              .append(campo(e.modulo())).append(',')
              .append(campo(e.evento())).append(',')
              .append(campo(e.status())).append(',')
              .append(campo(e.httpMethod())).append(',')
              .append(campo(e.httpPath())).append(',')
              .append(campo(e.httpStatus() == null ? "" : String.valueOf(e.httpStatus()))).append(',')
              .append(campo(e.durationMs() == null ? "" : String.valueOf(e.durationMs()))).append(',')
              .append(campo(e.traceId())).append(',')
              .append(campo(e.message())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Escapa um valor de CSV.
     *
     * <p><b>Invariantes do domínio:</b> qualquer valor com vírgula, aspas ou quebra
     * de linha sai entre aspas, com as aspas internas duplicadas. Sem isso, uma
     * mensagem com vírgula desloca todas as colunas seguintes e a planilha mostra
     * dado errado com cara de certo.</p>
     */
    private String campo(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        boolean precisaAspas = valor.indexOf(',') >= 0 || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0;
        String limpo = valor.replace("\"", "\"\"");
        return precisaAspas ? "\"" + limpo + "\"" : limpo;
    }

    // ------------------------------------------------------------------- TXT

    private String txt(List<TelemetriaEvent> eventos) {
        String linha = "=".repeat(96);
        StringBuilder sb = new StringBuilder();
        sb.append(linha).append('\n')
          .append("TELEMETRIA — FRAMEWORK DE REDES A&D\n")
          .append("Gerado em ").append(java.time.Instant.now()).append(" UTC\n")
          .append(eventos.size()).append(" evento(s) na janela em memoria\n")
          .append(linha).append("\n\n");

        Map<String, Long> porModulo = eventos.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.modulo() == null ? "-" : e.modulo(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        sb.append("EVENTOS POR MODULO\n");
        porModulo.forEach((m, q) -> sb.append(String.format(Locale.ROOT, "  %-24s %5d%n", m, q)));
        sb.append('\n').append(linha).append('\n').append("EVENTOS\n").append(linha).append('\n');

        for (TelemetriaEvent e : eventos) {
            sb.append(String.format(Locale.ROOT, "%-30s %-5s %-18s %-22s %s%n",
                    e.timestamp() == null ? "" : e.timestamp().toString(),
                    vazioSe(e.level()), vazioSe(e.modulo()), vazioSe(e.evento()), vazioSe(e.status())));
            if (e.httpPath() != null) {
                sb.append(String.format(Locale.ROOT, "%32s%s %s -> %s (%s ms)%n", "",
                        vazioSe(e.httpMethod()), e.httpPath(),
                        e.httpStatus() == null ? "-" : e.httpStatus(),
                        e.durationMs() == null ? "-" : e.durationMs()));
            }
            if (e.message() != null && !e.message().isBlank()) {
                sb.append(String.format(Locale.ROOT, "%32s%s%n", "", e.message()));
            }
            if (e.traceId() != null && !e.traceId().isBlank()) {
                sb.append(String.format(Locale.ROOT, "%32strace=%s%n", "", e.traceId()));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ HTML

    private String html(List<TelemetriaEvent> eventos) {
        StringBuilder linhas = new StringBuilder();
        for (TelemetriaEvent e : eventos) {
            String cor = e.httpStatus() != null && e.httpStatus() >= 500 ? "erro"
                    : e.httpStatus() != null && e.httpStatus() >= 400 ? "aviso" : "";
            linhas.append("<tr class=\"").append(cor).append("\">")
                  .append(td(e.timestamp() == null ? "" : e.timestamp().toString()))
                  .append(td(e.level())).append(td(e.modulo())).append(td(e.evento()))
                  .append(td(e.status())).append(td(e.httpMethod())).append(td(e.httpPath()))
                  .append(td(e.httpStatus() == null ? "" : String.valueOf(e.httpStatus())))
                  .append(td(e.durationMs() == null ? "" : e.durationMs() + " ms"))
                  .append(td(e.traceId()))
                  .append("</tr>\n");
        }

        return """
                <!DOCTYPE html>
                <html lang="pt-br"><head><meta charset="UTF-8">
                <title>Telemetria — Framework de Redes A&amp;D</title>
                <style>
                  body{background:#05070d;color:#eef2f8;font-family:system-ui,sans-serif;margin:0;padding:2rem}
                  h1{font-size:1.4rem;margin:0 0 .3rem}
                  .sub{color:#8b97ad;font-size:.85rem;margin-bottom:1.5rem}
                  table{width:100%%;border-collapse:collapse;font-size:.78rem}
                  th{background:#101725;color:#7dd3fc;text-align:left;padding:.5rem;
                     text-transform:uppercase;font-size:.68rem;letter-spacing:.04em}
                  td{padding:.4rem .5rem;border-bottom:1px solid rgba(255,255,255,.06);
                     vertical-align:top;word-break:break-word}
                  tr.aviso td{color:#fcd34d} tr.erro td{color:#fda4af}
                  @media print{body{background:#fff;color:#111;padding:0}
                    th{background:#eee;color:#111} td{border-color:#ddd}
                    tr.aviso td{color:#8a6d00} tr.erro td{color:#a00}}
                </style></head><body>
                <h1>Telemetria — Framework de Redes A&amp;D</h1>
                <div class="sub">Gerado em %s UTC · %d evento(s) · frameworknet.carminati.dev.br</div>
                <table><thead><tr>
                <th>Quando</th><th>Nível</th><th>Módulo</th><th>Evento</th><th>Status</th>
                <th>Método</th><th>Rota</th><th>HTTP</th><th>Duração</th><th>Trace</th>
                </tr></thead><tbody>
                %s
                </tbody></table></body></html>
                """.formatted(java.time.Instant.now(), eventos.size(), linhas);
    }

    private String td(String valor) {
        return "<td>" + escapar(vazioSe(valor)) + "</td>";
    }

    /**
     * Escapa para HTML.
     *
     * <p><b>Invariantes do domínio:</b> conteúdo de evento nasce de entrada de
     * usuário. Sem escapar, um valor com marcação viraria HTML executável dentro
     * do arquivo que alguém vai abrir no navegador.</p>
     */
    private String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String vazioSe(String valor) {
        return valor == null ? "" : valor;
    }
}
