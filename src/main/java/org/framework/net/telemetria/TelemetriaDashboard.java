package org.framework.net.telemetria;

import java.util.List;
import java.util.Map;

/**
 * Dados agregados do painel de telemetria, calculados sobre uma janela de tempo.
 * Percentis de latência, classes de status HTTP e top endpoints derivam dos eventos
 * {@code http_access} já coletados (método, path, status, duração), excluindo ruído
 * operacional (polling do próprio dashboard e arquivos estáticos).
 *
 * <p>{@code soPessoas} diz sobre o que os números foram calculados: {@code false} é todo o tráfego (a carga), e
 * {@code true} só os acessos de quem o User-Agent classifica como {@code humano}. O painel escreve esse estado a
 * partir deste campo, nunca do que foi pedido, para o rótulo não descrever números de outra consulta.</p>
 */
public record TelemetriaDashboard(
        TelemetriaResumo resumo,
        int janelaMinutos,
        String atualizadoEm,
        long eventosJanela,
        String coberturaDesde,
        boolean coberturaParcial,
        boolean soPessoas,
        long httpTotal,
        long http2xx,
        long http3xx,
        long http4xx,
        long http5xx,
        double taxaSucesso,
        double taxaErroServidor,
        Latencia latencia,
        Map<String, Long> metodos,
        Map<String, Long> paises,
        Map<String, Long> clientes,
        List<ModuloStat> porModulo,
        List<EndpointStat> topLentos,
        List<EndpointStat> topErros,
        List<EndpointStat> topRotas,
        List<AtividadeMinuto> atividadePorMinuto,
        List<String> consoleLinhas,
        String pastaLogs
) {

    public record AtividadeMinuto(String minuto, long total, long erros) {
    }

    public record Latencia(long p50, long p90, long p95, long p99, long max, long media) {
    }

    public record ModuloStat(String modulo, long total, long ok, long erro, long p95) {
    }

    public record EndpointStat(String metodo, String endpoint, long chamadas, long p95, long erros, long err4xx, long err5xx) {
    }
}
