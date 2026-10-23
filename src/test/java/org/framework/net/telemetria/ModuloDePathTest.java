package org.framework.net.telemetria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Atribuição de módulo no dashboard de telemetria.
 *
 * <p><b>Propósito de negócio:</b> o painel "por módulo" é o que o Paulo olha
 * para saber onde o sistema é usado e onde falha. Uma rota atribuída ao módulo
 * errado não quebra nada — só mente. Este teste trava a tabela de atribuição.</p>
 *
 * <p><b>Invariantes do domínio:</b> toda rota real do projeto tem módulo
 * explícito, e rota desconhecida cai em "Outros" — nunca em um módulo concreto,
 * porque isso inflaria as estatísticas de quem não fez a requisição.</p>
 */
@DisplayName("Telemetria: atribuição de módulo por caminho HTTP")
class ModuloDePathTest {

    /**
     * F16: a lista escrita à mão deixou 6 módulos (laboratorios, certificados, camadas, criptografia,
     * wifi, ferramentas) caírem em "Outros". Esta verificação DERIVA os primeiros segmentos dos {@code @Path}
     * de classe do código real — módulo novo sem mapeamento reprova o build.
     * Isentos com motivo: infraestrutura sem módulo de negócio (health, manifest, sitemap, ícone da marca).
     */
    @Test
    void todoPrimeiroSegmentoDeRotaRealTemModulo() throws java.io.IOException {
        java.util.Set<String> isentos = java.util.Set.of("health", "manifest.webmanifest", "sitemap.xml", "icone.png");
        java.util.regex.Pattern pathDeClasse = java.util.regex.Pattern.compile(
                "@Path\\(\"/([^/\"{]+)[^\"]*\"\\)\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*public\\s+(?:final\\s+)?class");
        java.util.Set<String> segmentos = new java.util.TreeSet<>();
        try (var arquivos = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            for (var p : arquivos.filter(x -> x.toString().endsWith(".java")).toList()) {
                var m = pathDeClasse.matcher(java.nio.file.Files.readString(p));
                while (m.find()) {
                    segmentos.add(m.group(1));
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(segmentos.size() > 15,
                "instrumento cego: só " + segmentos.size() + " segmentos de @Path encontrados: " + segmentos);
        java.util.List<String> semModulo = segmentos.stream()
                .filter(s -> !isentos.contains(s))
                .filter(s -> "Outros".equals(TelemetriaDashboardService.moduloDePath("/" + s)))
                .toList();
        assertEquals(java.util.List.of(), semModulo);
    }

    @Test
    void rotasDeMenuTemModuloProprio() {
        assertEquals("Início", TelemetriaDashboardService.moduloDePath("/"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/analise"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora"));
        assertEquals("IPv6", TelemetriaDashboardService.moduloDePath("/ipv6"));
        assertEquals("IPv6", TelemetriaDashboardService.moduloDePath("/ipv6/api/dividir"));
        assertEquals("Portas", TelemetriaDashboardService.moduloDePath("/portas"));
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos"));
        assertEquals("Resolução", TelemetriaDashboardService.moduloDePath("/resolucao-problemas"));
        assertEquals("Localização", TelemetriaDashboardService.moduloDePath("/localizacao"));
        assertEquals("Tráfego", TelemetriaDashboardService.moduloDePath("/trafego"));
        assertEquals("Diagnóstico", TelemetriaDashboardService.moduloDePath("/diagnostico"));
        assertEquals("Segurança", TelemetriaDashboardService.moduloDePath("/seguranca"));
        assertEquals("Telemetria", TelemetriaDashboardService.moduloDePath("/telemetria"));
        assertEquals("Documentação", TelemetriaDashboardService.moduloDePath("/documentacao"));
        assertEquals("Sobre", TelemetriaDashboardService.moduloDePath("/sobre"));
        assertEquals("Academia", TelemetriaDashboardService.moduloDePath("/academia/fundamentos/binario"));
        assertEquals("Academia", TelemetriaDashboardService.moduloDePath("/academia/api/eventos"));
    }

    @Test
    void subcaminhosSeguemOModuloDaRaiz() {
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/api/dividir"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/api/vlan"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/export/divisao.csv"));
        assertEquals("Segurança", TelemetriaDashboardService.moduloDePath("/seguranca/api/testar"));
        assertEquals("Segurança", TelemetriaDashboardService.moduloDePath("/seguranca/api/tls"));
        assertEquals("Diagnóstico", TelemetriaDashboardService.moduloDePath("/diagnostico/api/ping"));
        assertEquals("Tráfego", TelemetriaDashboardService.moduloDePath("/trafego/api/decodificar"));
        assertEquals("Localização", TelemetriaDashboardService.moduloDePath("/localizacao/api/cep"));
        // Aprofundamentos por protocolo: são páginas do módulo Protocolos, não
        // módulos próprios — creditá-las a "bgp"/"ssh" partiria as estatísticas
        // do módulo em pedaços que ninguém somaria de volta.
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos/bgp"));
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos/ssh"));
    }

    @Test
    void resourcesFilhosDaAnaliseContamComoAnalise() {
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/export/json"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/export/pdf"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/history"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/history/catalog"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/mascara-referencia"));
    }

    @Test
    @DisplayName("regressão: rota desconhecida não pode ser creditada a um módulo real")
    void rotaDesconhecidaCaiEmOutros() {
        assertEquals("Outros", TelemetriaDashboardService.moduloDePath("/rota-que-nao-existe"));
        assertEquals("Outros", TelemetriaDashboardService.moduloDePath("/favicon-inexistente"));
    }

    @Test
    @DisplayName("regressão: /calculadora, /sobre, /admin e /simuladores não são mais Análise Didática")
    void rotasQueEramMalAtribuidas() {
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora"));
        assertEquals("Sobre", TelemetriaDashboardService.moduloDePath("/sobre"));
        assertEquals("Admin", TelemetriaDashboardService.moduloDePath("/admin"));
        assertEquals("Admin", TelemetriaDashboardService.moduloDePath("/login"));
        assertEquals("Simuladores", TelemetriaDashboardService.moduloDePath("/simuladores/api/encapsular"));
        assertEquals("Simuladores", TelemetriaDashboardService.moduloDePath("/simuladores/api/anomalia-tcp"));
    }

    @Test
    void apisNaRaizSaoResolvidasPeloSegundoSegmento() {
        assertEquals("GeoIP", TelemetriaDashboardService.moduloDePath("/api/informacoes/geo"));
    }

    @Test
    void caminhoNuloOuVazioNaoQuebra() {
        assertEquals("Início", TelemetriaDashboardService.moduloDePath(null));
        assertEquals("Início", TelemetriaDashboardService.moduloDePath(""));
        assertEquals("Início", TelemetriaDashboardService.moduloDePath("   "));
    }
}
