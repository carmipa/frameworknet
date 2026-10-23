package org.framework.net.arquitetura;

import org.framework.net.arquitetura.FontesJava.ArquivoJava;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.framework.net.arquitetura.FontesJava.PREFIXO;
import static org.framework.net.arquitetura.FontesJava.camadaDoImport;
import static org.framework.net.arquitetura.FontesJava.mensagem;
import static org.framework.net.arquitetura.FontesJava.moduloDoImport;
import static org.framework.net.arquitetura.FontesJava.sintetico;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda das regras de arquitetura do projeto.
 *
 * <p><b>Propósito de negócio:</b> o framework cresce por módulos (Análise,
 * Resolução, Calculadora, Tráfego…) e cada novo módulo tende a copiar o que
 * encontra pela frente. Este teste transforma as convenções acordadas em
 * verificação automática, para que uma violação apareça no build e não seis
 * meses depois, quando já custou refatoração.</p>
 *
 * <p><b>Invariantes do domínio:</b> (1) {@code domain} não conhece camadas de fora
 * nem HTTP/template; (2) {@code application} não conhece {@code presentation};
 * (3) módulos de negócio não se importam entre si, salvo exceções explicitamente
 * registradas aqui; (4) classe anotada com {@code @Path} mora em
 * {@code presentation}; (5) todo fonte tem camada reconhecida. A camada é o
 * <b>primeiro segmento conhecido</b> depois do módulo ({@link FontesJava}), e não
 * mais o segundo segmento: lido no segundo, {@code academia/trilha/domain} virava a
 * camada "trilha" e as regras (1) e (2) ficavam cegas. Pacote sem camada reprova,
 * salvo a linha de base por identidade e a raiz dos módulos transversais.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste falha listando arquivo,
 * import e regra violada. Se o diretório de fontes não for encontrado (execução
 * fora da raiz do projeto), os testes são ignorados via
 * {@code Assumptions} em vez de falharem por motivo errado. Cada regra também é
 * exercitada contra fonte sintético (calibração): o caso doente precisa reprovar
 * e o legítimo semelhante precisa passar.</p>
 */
@DisplayName("Arquitetura: camadas e acoplamento entre módulos")
class ArquiteturaCamadasTest {

    /** Pacotes transversais que qualquer módulo pode usar. */
    private static final Set<String> TRANSVERSAIS = Set.of("shared", "telemetria", "security", "web");

    /**
     * Fontes sem camada reconhecida que já existiam quando a regra (5) nasceu
     * (01/10/2026). Linha de base por identidade: arquivo novo sem camada reprova,
     * e arquivo daqui que ganhar camada precisa sair da lista — ela só diminui.
     * {@code analiseTrafego/aovivo} é aplicação de fato (serviço e snapshot do
     * tráfego ao vivo); movê-lo é refatoração de outro escopo.
     */
    private static final Set<String> SEM_CAMADA_LINHA_DE_BASE = Set.of(
            "analiseTrafego/aovivo/SnapshotAoVivo.java",
            "analiseTrafego/aovivo/TrafegoAoVivoService.java");

    /**
     * Acoplamentos entre módulos aceitos conscientemente.
     *
     * <p>A Localização reaproveita o GeoIP que nasceu na Análise Didática quando
     * a aba "Região Geo" migrou de página. É reúso deliberado de infraestrutura,
     * não vazamento de camada — está aqui para que apareça em revisão, e para
     * que qualquer acoplamento NOVO quebre o build.</p>
     */
    private static final Map<String, Set<String>> ACOPLAMENTOS_ACEITOS = Map.of(
            "localizacao", Set.of("analiseDidatica"),
            // Portas reutiliza o modelo didático de aprofundamento dos Protocolos
            // (record ProtocoloAprofundamento, records de diagrama/cabeçalho) e cruza
            // suas 16 portas de serviço conhecido para /protocolos/<slug> via o
            // registro AprofundamentoProtocolo. A dependência é real e assumida.
            "portas", Set.of("protocolos"),
            // Certificados reutiliza o mesmo modelo didático de aprofundamento
            // (ProtocoloAprofundamento) e cruza para o protocolo TLS. Dependência assumida.
            "certificados", Set.of("protocolos"),
            // Camadas, Ferramentas, Criptografia e Wi-Fi também reutilizam o modelo
            // ProtocoloAprofundamento dos protocolos (mesmo padrão catálogo + aprofundamento).
            "camadas", Set.of("protocolos"),
            "ferramentas", Set.of("protocolos"),
            "criptografia", Set.of("protocolos"),
            "wifi", Set.of("protocolos"),
            // A Calculadora IPv6 (aba "Domínio → AAAA") reutiliza o DnsResolver da Análise
            // Didática — a ÚNICA egress DNS endurecida do projeto (bloqueio de hostname interno +
            // recusa de endereço não-público = mitigação de SSRF, cache com teto, timeout).
            // Duplicar essa infraestrutura criaria um segundo caminho de saída sem as mesmas
            // guardas — pior para segurança. Reúso deliberado de infraestrutura, como o GeoIP da
            // Localização acima; registrado para aparecer em revisão e travar acoplamento NOVO.
            "ipv6", Set.of("analiseDidatica"));

    /**
     * Para os acoplamentos mais largos, a aceitação é por CLASSE, não por módulo inteiro: aceitar
     * "ipv6 → analiseDidatica" deixava entrar qualquer classe daquele módulo sem revisão (o
     * PdfSimplesService entrou assim, sem constar na justificativa — auditoria F22).
     */
    private static final Map<String, Set<String>> CLASSES_ACEITAS = Map.of(
            "ipv6", Set.of(
                    "org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver",
                    "org.framework.net.analiseDidatica.exception.DnsResolucaoException",
                    // Gerador de PDF simples sem biblioteca externa; o export IPv6 reusa em vez de duplicar.
                    "org.framework.net.analiseDidatica.support.PdfSimplesService"));

    /** Tipos de camada que o domínio jamais pode enxergar. */
    private static final Set<String> CAMADAS_PROIBIDAS_NO_DOMINIO =
            Set.of("application", "presentation", "infrastructure");

    /** Tecnologias de entrega que não podem entrar no domínio. */
    private static final List<String> TECNOLOGIAS_PROIBIDAS_NO_DOMINIO =
            List.of("jakarta.ws.rs", "io.quarkus.qute");

    // ---------------------------------------------------------------- regras sobre o código real

    @Test
    @DisplayName("acoplamento aceito por classe não admite classe nova sem revisão")
    void acoplamentoPorClasseNaoCresceCalado() {
        List<String> violacoes = violacoesAcoplamentoPorClasse(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Registre a classe em CLASSES_ACEITAS com o motivo, ou extraia para shared.", violacoes));
    }

    @Test
    @DisplayName("domain não importa application, presentation, infrastructure, JAX-RS nem Qute")
    void dominioNaoConheceCamadasDeFora() {
        List<String> violacoes = violacoesDominio(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "O domínio precisa ser a camada mais interna: nada de HTTP, template ou serviço de aplicação.",
                violacoes));
    }

    @Test
    @DisplayName("application não importa presentation")
    void aplicacaoNaoConhecePresentation() {
        List<String> violacoes = violacoesAplicacao(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "A aplicação é chamada pela apresentação, nunca o contrário.", violacoes));
    }

    @Test
    @DisplayName("módulos de negócio não se importam entre si, salvo exceções registradas")
    void modulosDeNegocioNaoSeAcoplam() {
        List<String> violacoes = violacoesAcoplamento(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Cada módulo é um pacote autocontido; o que for comum vive em shared.", violacoes));
    }

    /**
     * O kernel compartilhado não depende de módulo de negócio (auditoria F22 / item 7 anterior):
     * shared/NetworkAddressGuard importava uma exceção da Análise Didática, e a regra acima pula
     * pacotes transversais como IMPORTADORES — a inversão era invisível. web/telemetria/security
     * continuam livres para agregar módulos (menu, painel, filtros); shared não.
     */
    @Test
    @DisplayName("shared não depende de nenhum módulo de negócio")
    void sharedNaoDependeDeModuloDeNegocio() {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : FontesJava.todos()) {
            if (!"shared".equals(arquivo.modulo())) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                String moduloImportado = imp.startsWith(PREFIXO) ? moduloDoImport(imp) : "";
                if (!moduloImportado.isEmpty() && !TRANSVERSAIS.contains(moduloImportado)) {
                    violacoes.add(arquivo.caminho() + " importa " + imp);
                }
            }
        }
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "shared é o kernel: quem depende dele são os módulos, nunca o contrário.", violacoes));
    }

    /** Piso anti-cegueira: varredura vazia (raiz errada, filtro quebrado) não pode aprovar as regras. */
    @Test
    @DisplayName("a varredura enxerga os fontes do projeto")
    void varreduraEnxergaOsFontes() {
        List<ArquivoJava> todos = FontesJava.todos();
        assertTrue(todos.size() > 200, "só " + todos.size() + " fontes lidos: instrumento cego");
        assertTrue(todos.stream().anyMatch(a -> "presentation".equals(a.camada())), "nenhuma camada presentation lida");
        assertTrue(todos.stream().anyMatch(a -> "domain".equals(a.camada())), "nenhuma camada domain lida");
        assertTrue(todos.stream().anyMatch(a -> "application".equals(a.camada())), "nenhuma camada application lida");
        assertTrue(todos.stream().mapToLong(a -> a.imports().size()).sum() > 500, "quase nenhum import lido");
    }

    @Test
    @DisplayName("classe com @Path mora no pacote presentation")
    void resourcesFicamEmPresentation() {
        List<String> violacoes = violacoesResource(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Rota HTTP é apresentação: mantenha os resources em <modulo>/presentation.", violacoes));
    }

    @Test
    @DisplayName("todo fonte tem camada reconhecida (linha de base por identidade)")
    void todoFonteTemCamada() {
        List<ArquivoJava> todos = FontesJava.todos();
        List<String> violacoes = violacoesSemCamada(todos);
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Ponha o fonte numa camada " + FontesJava.CAMADAS_CONHECIDAS
                        + ". Pasta de organização vai DENTRO da camada: <modulo>/application/cidr.", violacoes));

        Set<String> aindaSemCamada = new HashSet<>();
        for (ArquivoJava arquivo : todos) {
            if (arquivo.camada().isEmpty()) {
                aindaSemCamada.add(relativo(arquivo));
            }
        }
        List<String> resolvidos = SEM_CAMADA_LINHA_DE_BASE.stream()
                .filter(base -> !aindaSemCamada.contains(base)).sorted().toList();
        assertTrue(resolvidos.isEmpty(), () -> mensagem(
                "Resolvido ou removido: tire da SEM_CAMADA_LINHA_DE_BASE (a lista só diminui).", resolvidos));
    }

    // ---------------------------------------------------------------- calibração (A1 e A2)

    /**
     * A2: com a camada lida no 2º segmento, um resource legítimo em
     * {@code academia/trilha/presentation} era lido como camada "trilha" e reprovava; e um
     * domínio da Academia importando JAX-RS passava. O primeiro segmento conhecido corrige os dois.
     */
    @Test
    @DisplayName("calibração: segundo nível — @Path legítimo passa, JAX-RS no domínio reprova")
    void calibracaoSegundoNivel() {
        ArquivoJava resource = sintetico("academia/trilha/presentation/TrilhaResource.java", """
                package org.framework.net.academia.trilha.presentation;
                import jakarta.ws.rs.Path;
                @Path("/academia/trilha")
                public class TrilhaResource { }
                """);
        ArquivoJava dominioComHttp = sintetico("academia/trilha/domain/Licao.java", """
                package org.framework.net.academia.trilha.domain;
                import jakarta.ws.rs.core.Response;
                public record Licao(String id) { }
                """);
        ArquivoJava dominioComAplicacao = sintetico("academia/trilha/domain/Nivel.java", """
                package org.framework.net.academia.trilha.domain;
                import org.framework.net.academia.trilha.application.TrilhaService;
                public record Nivel(String id) { }
                """);
        ArquivoJava dominioLegitimo = sintetico("academia/trilha/domain/model/Trilha.java", """
                package org.framework.net.academia.trilha.domain.model;
                import org.framework.net.academia.trilha.domain.Licao;
                import java.util.List;
                // Nada de jakarta.ws.rs aqui: comentário não é dependência.
                public record Trilha(List<Licao> licoes) { }
                """);
        ArquivoJava aplicacaoComPresentation = sintetico("academia/trilha/application/TrilhaService.java", """
                package org.framework.net.academia.trilha.application;
                import org.framework.net.academia.trilha.presentation.TrilhaResource;
                public class TrilhaService { }
                """);

        assertEquals("presentation", resource.camada());
        assertEquals("domain", dominioLegitimo.camada());
        assertEquals(List.of(), violacoesResource(List.of(resource)), "A1: @Path legítimo reprovado");
        assertEquals(1, violacoesDominio(List.of(dominioComHttp)).size(), "A2: JAX-RS no domínio passou");
        assertEquals(1, violacoesDominio(List.of(dominioComAplicacao)).size(), "A2: domínio → application passou");
        assertEquals(List.of(), violacoesDominio(List.of(dominioLegitimo)), "A1: domínio legítimo reprovado");
        assertEquals(1, violacoesAplicacao(List.of(aplicacaoComPresentation)).size(),
                "A2: application → presentation passou");
    }

    @Test
    @DisplayName("calibração: pacote sem camada reprova; raiz de transversal e linha de base passam")
    void calibracaoSemCamada() {
        ArquivoJava soltoNaFatia = sintetico("academia/trilha/Solto.java", "package x;");
        ArquivoJava pastaSemCamada = sintetico("academia/trilha/util/Ajuda.java", "package x;");
        ArquivoJava raizDeNegocio = sintetico("academia/Raiz.java", "package x;");
        ArquivoJava raizTransversal = sintetico("security/Filtro.java", "package x;");
        ArquivoJava daLinhaDeBase = sintetico("analiseTrafego/aovivo/SnapshotAoVivo.java", "package x;");
        ArquivoJava aninhadoLegitimo = sintetico("analiseDidatica/application/cidr/Cidr.java", "package x;");

        assertEquals(3, violacoesSemCamada(List.of(soltoNaFatia, pastaSemCamada, raizDeNegocio)).size(),
                "A2: fonte sem camada passou");
        assertEquals(List.of(), violacoesSemCamada(List.of(raizTransversal, daLinhaDeBase, aninhadoLegitimo)),
                "A1: fonte legítimo reprovado");
    }

    @Test
    @DisplayName("calibração: módulo novo importando módulo de negócio reprova; transversal passa")
    void calibracaoAcoplamento() {
        ArquivoJava acoplado = sintetico("academia/ipv4/application/Mascara.java",
                "import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;");
        ArquivoJava transversal = sintetico("academia/ipv4/application/Entrada.java",
                "import org.framework.net.shared.InputLimits;");
        assertEquals(1, violacoesAcoplamento(List.of(acoplado)).size(), "A2: acoplamento novo passou");
        assertEquals(List.of(), violacoesAcoplamento(List.of(transversal)), "A1: transversal reprovado");
    }

    // ---------------------------------------------------------------- regras

    private static List<String> violacoesAcoplamentoPorClasse(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            Set<String> aceitas = CLASSES_ACEITAS.get(arquivo.modulo());
            if (aceitas == null) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                String outro = imp.startsWith(PREFIXO) ? moduloDoImport(imp) : "";
                if (!outro.isEmpty() && !outro.equals(arquivo.modulo()) && !TRANSVERSAIS.contains(outro)
                        && !aceitas.contains(imp)) {
                    violacoes.add(arquivo.caminho() + " importa " + imp + " (fora da lista por classe)");
                }
            }
        }
        return violacoes;
    }

    private static List<String> violacoesDominio(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (!"domain".equals(arquivo.camada())) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                for (String tecnologia : TECNOLOGIAS_PROIBIDAS_NO_DOMINIO) {
                    if (imp.startsWith(tecnologia)) {
                        violacoes.add(arquivo.caminho() + " importa " + imp
                                + " — domínio não pode depender de tecnologia de entrega.");
                    }
                }
                if (!imp.startsWith(PREFIXO)) {
                    continue;
                }
                String camadaImportada = camadaDoImport(imp);
                if (CAMADAS_PROIBIDAS_NO_DOMINIO.contains(camadaImportada)) {
                    violacoes.add(arquivo.caminho() + " importa " + imp
                            + " — domínio não pode depender da camada " + camadaImportada + ".");
                }
            }
        }
        return violacoes;
    }

    private static List<String> violacoesAplicacao(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (!"application".equals(arquivo.camada())) {
                continue;
            }
            for (String imp : arquivo.imports()) {
                if (imp.startsWith(PREFIXO) && "presentation".equals(camadaDoImport(imp))) {
                    violacoes.add(arquivo.caminho() + " importa " + imp
                            + " — o serviço não pode depender do resource que o expõe.");
                }
            }
        }
        return violacoes;
    }

    private static List<String> violacoesAcoplamento(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            String modulo = arquivo.modulo();
            if (TRANSVERSAIS.contains(modulo)) {
                continue;
            }
            Set<String> aceitos = ACOPLAMENTOS_ACEITOS.getOrDefault(modulo, Set.of());
            for (String imp : arquivo.imports()) {
                if (!imp.startsWith(PREFIXO)) {
                    continue;
                }
                String moduloImportado = moduloDoImport(imp);
                if (moduloImportado.isEmpty()
                        || moduloImportado.equals(modulo)
                        || TRANSVERSAIS.contains(moduloImportado)
                        || aceitos.contains(moduloImportado)) {
                    continue;
                }
                violacoes.add(arquivo.caminho() + " importa " + imp
                        + " — módulo \"" + modulo + "\" não deve depender de \"" + moduloImportado + "\". "
                        + "Extraia para shared ou registre o acoplamento em ACOPLAMENTOS_ACEITOS.");
            }
        }
        return violacoes;
    }

    private static List<String> violacoesResource(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            boolean ehResource = arquivo.conteudo().contains("@Path(")
                    && arquivo.conteudo().contains("import jakarta.ws.rs.Path;");
            if (ehResource && !"presentation".equals(arquivo.camada())) {
                violacoes.add(arquivo.caminho() + " expõe rota HTTP fora do pacote presentation.");
            }
        }
        return violacoes;
    }

    private static List<String> violacoesSemCamada(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (!arquivo.camada().isEmpty()) {
                continue;
            }
            boolean raizDeTransversal = arquivo.pacote().isEmpty() && TRANSVERSAIS.contains(arquivo.modulo());
            if (raizDeTransversal || SEM_CAMADA_LINHA_DE_BASE.contains(relativo(arquivo))) {
                continue;
            }
            violacoes.add(arquivo.caminho() + " não está em nenhuma camada (pacote "
                    + (arquivo.pacote().isEmpty() ? "raiz do módulo" : String.join(".", arquivo.pacote())) + ").");
        }
        return violacoes;
    }

    /** Caminho a partir de {@code org/framework/net}, com {@code /}, para comparar com a linha de base. */
    private static String relativo(ArquivoJava arquivo) {
        String caminho = arquivo.caminho();
        String marca = "org/framework/net/";
        int inicio = caminho.indexOf(marca);
        if (inicio >= 0) {
            return caminho.substring(inicio + marca.length());
        }
        return caminho.startsWith("SINTETICO:") ? caminho.substring("SINTETICO:".length()) : caminho;
    }
}
