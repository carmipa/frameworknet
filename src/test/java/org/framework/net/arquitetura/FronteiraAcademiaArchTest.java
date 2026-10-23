package org.framework.net.arquitetura;

import org.framework.net.arquitetura.FontesJava.ArquivoJava;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.framework.net.arquitetura.FontesJava.PREFIXO;
import static org.framework.net.arquitetura.FontesJava.mensagem;
import static org.framework.net.arquitetura.FontesJava.moduloDoImport;
import static org.framework.net.arquitetura.FontesJava.sintetico;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fronteiras da Academia e da conta do site (INV-ACAD-001 e INV-CONTA-001).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a Academia é uma escola inteira dentro do site e cresce
 * lição por lição. Se ela começar a importar o resto do site, deixa de poder ser mudada,
 * testada e retirada sozinha; e se a conta do site alcançar a sessão da Telemetria, qualquer
 * conta Google abriria o painel com os IPs de todo visitante. Esta guarda congela as duas
 * fronteiras antes de existir a primeira linha de código delas.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b></p>
 * <ul>
 *   <li>A Academia só alcança tipo de fora de {@code academia} se o tipo <b>exato</b> estiver em
 *       {@link #TIPOS_EXTERNOS_ACEITOS} (vazia hoje) — nem transversal entra sem revisão.</li>
 *   <li>Dentro da Academia a seta aponta fatia → peer → kernel: {@code core} não conhece
 *       ninguém; peer ({@code trilha}, {@code aluno}) só conhece {@code core} e a si; fatia
 *       conhece {@code core}, peers e a si — <b>fatia não fala com fatia</b>.</li>
 *   <li>{@code conta} e {@code academia} nunca alcançam {@code SessaoTelemetriaService}, e
 *       {@code conta} não alcança nada de {@code security} fora de
 *       {@link #SECURITY_ACEITO_NA_CONTA} (vazia hoje).</li>
 *   <li>Conta tanto {@code import} quanto nome qualificado no corpo: escrever
 *       {@code org.framework.net.security.X} por extenso não escapa.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando arquivo e tipo alcançado. Sem
 * fonte da Academia ainda (Fase 0), as regras sobre o código real passam vazias — por isso cada
 * regra é calibrada contra fonte sintético: o caso doente reprova e o legítimo semelhante passa.
 * Nome qualificado dentro de comentário também conta (falso alarme aceito: no código novo, citar
 * o tipo pelo nome simples resolve).</p>
 */
@DisplayName("Arquitetura: fronteiras da Academia e da conta do site")
class FronteiraAcademiaArchTest {

    private static final String ACADEMIA = "academia";
    private static final String CONTA = "conta";
    private static final String KERNEL = "core";
    private static final Set<String> PEERS = Set.of("trilha", "aluno");

    private static final String SESSAO_TELEMETRIA = PREFIXO + "security.SessaoTelemetriaService";

    /**
     * Tipos de fora da Academia que ela pode alcançar, por nome exato e com o motivo no
     * comentário. Vazia: entrar aqui é decisão de revisão, não conveniência.
     */
    private static final Set<String> TIPOS_EXTERNOS_ACEITOS = Set.of();

    /**
     * Tipos da Academia que código de FORA dela pode alcançar (aresta de entrada, auditoria ACAD-27: a
     * guarda só via a saída, e foi uma entrada — web lendo o catálogo — que derrubou o site no ACAD-02).
     */
    private static final java.util.Map<String, String> ENTRADAS_ACEITAS = java.util.Map.of(
            PREFIXO + "academia.eventos.domain.ports.TelemetriaAcademiaPort", "porta que a telemetria implementa",
            PREFIXO + "academia.eventos.domain.EventoAcademia", "evento que atravessa a porta",
            PREFIXO + "academia.eventos.domain.EventoAcademia.ErroJs", "evento que atravessa a porta",
            PREFIXO + "academia.eventos.domain.EventoAcademia.Visita", "evento que atravessa a porta",
            PREFIXO + "academia.trilha.domain.CatalogoTrilha",
            "PaginasPublicas lê as rotas públicas, protegida por PaginasPublicas.rotasDaAcademia (ACAD-02)");

    /** Tipos de {@code security} que a conta pode alcançar. Vazia; a sessão da Telemetria jamais. */
    private static final Set<String> SECURITY_ACEITO_NA_CONTA = Set.of();

    @Test
    @DisplayName("as listas de exceção nunca liberam a sessão da Telemetria")
    void listasNaoLiberamSessaoTelemetria() {
        assertFalse(TIPOS_EXTERNOS_ACEITOS.contains(SESSAO_TELEMETRIA));
        assertFalse(SECURITY_ACEITO_NA_CONTA.contains(SESSAO_TELEMETRIA));
    }

    @Test
    @DisplayName("Academia não alcança tipo de fora dela sem estar na lista por tipo exato")
    void academiaNaoSaiDaFronteira() {
        List<String> violacoes = violacoesSaidaDaAcademia(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "A Academia é autocontida: duplique conscientemente ou registre o TIPO em "
                        + "TIPOS_EXTERNOS_ACEITOS com o motivo.", violacoes));
    }

    @Test
    @DisplayName("de fora, a Academia só é alcançada pelos tipos de entrada registrados")
    void entradaNaAcademiaSoPeloQueEstaRegistrado() {
        List<String> violacoes = violacoesEntradaNaAcademia(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Quem está fora fala com a Academia por porta; registre o TIPO em ENTRADAS_ACEITAS com o motivo.",
                violacoes));
    }

    @Test
    @DisplayName("calibração: entrada nova reprova, entrada registrada passa, import quebrado em linhas é visto")
    void calibracaoEntrada() {
        ArquivoJava webNoServico = sintetico("web/presentation/A.java",
                "import org.framework.net.academia.trilha.application.TrilhaService;");
        ArquivoJava importEmLinhas = sintetico("telemetria/application/B.java",
                "import\n    org.framework.net.academia.core.application.PortaoAcademia;");
        ArquivoJava importComEspaco = sintetico("web/domain/C.java",
                "import org.framework.net . academia.trilha.domain . Nivel;");
        ArquivoJava portaRegistrada = sintetico("telemetria/infrastructure/D.java",
                "import org.framework.net.academia.eventos.domain.ports.TelemetriaAcademiaPort;");
        ArquivoJava dentroDaAcademia = sintetico("academia/trilha/application/E.java",
                "import org.framework.net.academia.trilha.domain.CatalogoTrilha;");

        assertEquals(3, violacoesEntradaNaAcademia(List.of(webNoServico, importEmLinhas, importComEspaco)).size(),
                "A2: entrada não registrada sem reprovar");
        assertEquals(List.of(), violacoesEntradaNaAcademia(List.of(portaRegistrada, dentroDaAcademia)),
                "A1: entrada registrada ou uso interno reprovado");
    }

    @Test
    @DisplayName("dentro da Academia: fatia → peer → kernel, e fatia não fala com fatia")
    void academiaRespeitaFatiaPeerKernel() {
        List<String> violacoes = violacoesInternasDaAcademia(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "Se a fatia precisa da outra, o que ela quer é um peer ou uma porta.", violacoes));
    }

    @Test
    @DisplayName("conta e Academia não alcançam a sessão da Telemetria; conta não alcança security")
    void contaEAcademiaLongeDaTelemetria() {
        List<String> violacoes = violacoesTelemetria(FontesJava.todos());
        assertTrue(violacoes.isEmpty(), () -> mensagem(
                "A sessão do site nunca abre a Telemetria (INV-CONTA-001).", violacoes));
    }

    // ---------------------------------------------------------------- calibração (A1 e A2)

    @Test
    @DisplayName("calibração: saída da Academia — import e nome qualificado reprovam; interno passa")
    void calibracaoSaida() {
        ArquivoJava porImport = sintetico("academia/ipv4/application/Mascara.java",
                "import org.framework.net.shared.InputLimits;");
        ArquivoJava porNomeQualificado = sintetico("academia/ipv4/application/Cidr.java",
                "class Cidr { org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel k; }");
        ArquivoJava porCuringa = sintetico("academia/ipv4/application/Faixa.java",
                "import org.framework.net.telemetria.*;");
        ArquivoJava interno = sintetico("academia/ipv4/application/Sub.java", """
                import org.framework.net.academia.core.domain.ErroAcademia;
                import org.framework.net.academia.ipv4.domain.Mascara;
                import java.util.List;
                """);

        assertEquals(1, violacoesSaidaDaAcademia(List.of(porImport)).size(), "A2: transversal sem lista passou");
        assertEquals(1, violacoesSaidaDaAcademia(List.of(porNomeQualificado)).size(),
                "A2: nome qualificado escapou");
        assertEquals(1, violacoesSaidaDaAcademia(List.of(porCuringa)).size(), "A2: curinga escapou");
        assertEquals(List.of(), violacoesSaidaDaAcademia(List.of(interno)), "A1: dependência interna reprovada");
    }

    @Test
    @DisplayName("calibração: fatia → fatia, peer → fatia, peer → peer e kernel → peer reprovam")
    void calibracaoInterna() {
        ArquivoJava fatiaParaFatia = sintetico("academia/ipv4/application/A.java",
                "import org.framework.net.academia.fundamentos.domain.Binario;");
        ArquivoJava peerParaFatia = sintetico("academia/trilha/application/B.java",
                "import org.framework.net.academia.progresso.application.Salvar;");
        ArquivoJava peerParaPeer = sintetico("academia/trilha/application/C.java",
                "import org.framework.net.academia.aluno.domain.Aluno;");
        ArquivoJava kernelParaPeer = sintetico("academia/core/domain/D.java",
                "import org.framework.net.academia.trilha.domain.Licao;");
        ArquivoJava fatiaLegitima = sintetico("academia/progresso/application/E.java", """
                import org.framework.net.academia.core.domain.ErroAcademia;
                import org.framework.net.academia.trilha.domain.Licao;
                import org.framework.net.academia.aluno.domain.Aluno;
                import org.framework.net.academia.progresso.domain.Estado;
                """);
        ArquivoJava peerLegitimo = sintetico("academia/aluno/application/F.java",
                "import org.framework.net.academia.core.domain.ErroAcademia;");

        assertEquals(4, violacoesInternasDaAcademia(
                List.of(fatiaParaFatia, peerParaFatia, peerParaPeer, kernelParaPeer)).size(),
                "A2: seta proibida passou");
        assertEquals(List.of(), violacoesInternasDaAcademia(List.of(fatiaLegitima, peerLegitimo)),
                "A1: seta permitida reprovada");
    }

    @Test
    @DisplayName("calibração: sessão da Telemetria reprova na conta e na Academia; fora delas passa")
    void calibracaoTelemetria() {
        ArquivoJava contaComSessao = sintetico("conta/application/Entrar.java",
                "import org.framework.net.security.SessaoTelemetriaService;");
        ArquivoJava contaComSecurity = sintetico("conta/application/Sair.java",
                "import org.framework.net.security.CsrfTokenService;");
        ArquivoJava academiaPorExtenso = sintetico("academia/aluno/infrastructure/G.java",
                "class G { org.framework.net.security.SessaoTelemetriaService s; }");
        ArquivoJava telemetriaLegitima = sintetico("telemetria/presentation/H.java",
                "import org.framework.net.security.SessaoTelemetriaService;");
        ArquivoJava contaLegitima = sintetico("conta/application/I.java",
                "import org.framework.net.shared.InputLimits;");

        assertEquals(3, violacoesTelemetria(List.of(contaComSessao, contaComSecurity, academiaPorExtenso)).size(),
                "A2: sessão da Telemetria alcançada sem reprovar");
        assertEquals(List.of(), violacoesTelemetria(List.of(telemetriaLegitima, contaLegitima)),
                "A1: uso legítimo reprovado");
    }

    // ---------------------------------------------------------------- regras

    private static List<String> violacoesSaidaDaAcademia(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (!ACADEMIA.equals(arquivo.modulo())) {
                continue;
            }
            for (String tipo : arquivo.dependenciasDoProjeto()) {
                if (!ACADEMIA.equals(moduloDoImport(tipo)) && !TIPOS_EXTERNOS_ACEITOS.contains(tipo)) {
                    violacoes.add(arquivo.caminho() + " alcança " + tipo);
                }
            }
        }
        return violacoes;
    }

    private static List<String> violacoesEntradaNaAcademia(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (ACADEMIA.equals(arquivo.modulo())) {
                continue;
            }
            for (String tipo : arquivo.dependenciasDoProjeto()) {
                if (ACADEMIA.equals(moduloDoImport(tipo)) && !ENTRADAS_ACEITAS.containsKey(tipo)) {
                    violacoes.add(arquivo.caminho() + " entra na Academia por " + tipo);
                }
            }
        }
        return violacoes;
    }

    private static List<String> violacoesInternasDaAcademia(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            if (!ACADEMIA.equals(arquivo.modulo()) || arquivo.pacote().isEmpty()) {
                continue;
            }
            String origem = arquivo.pacote().getFirst();
            for (String tipo : arquivo.dependenciasDoProjeto()) {
                String destino = fatiaDaAcademia(tipo);
                if (destino.isEmpty() || destino.equals(origem) || permitido(origem, destino)) {
                    continue;
                }
                violacoes.add(arquivo.caminho() + " (" + papel(origem) + " " + origem + ") alcança "
                        + tipo + " (" + papel(destino) + " " + destino + ")");
            }
        }
        return violacoes;
    }

    private static List<String> violacoesTelemetria(List<ArquivoJava> fontes) {
        List<String> violacoes = new ArrayList<>();
        for (ArquivoJava arquivo : fontes) {
            boolean conta = CONTA.equals(arquivo.modulo());
            if (!conta && !ACADEMIA.equals(arquivo.modulo())) {
                continue;
            }
            for (String tipo : arquivo.dependenciasDoProjeto()) {
                boolean sessao = tipo.startsWith(SESSAO_TELEMETRIA) || tipo.equals(PREFIXO + "security.*");
                boolean securityNaConta = conta && "security".equals(moduloDoImport(tipo))
                        && !SECURITY_ACEITO_NA_CONTA.contains(tipo);
                if (sessao || securityNaConta) {
                    violacoes.add(arquivo.caminho() + " alcança " + tipo);
                }
            }
        }
        return violacoes;
    }

    /** Segmento depois de {@code academia.} — vazio se o tipo não for da Academia. */
    private static String fatiaDaAcademia(String tipo) {
        String prefixo = PREFIXO + ACADEMIA + ".";
        if (!tipo.startsWith(prefixo)) {
            return "";
        }
        String resto = tipo.substring(prefixo.length());
        int ponto = resto.indexOf('.');
        return ponto < 0 ? "" : resto.substring(0, ponto);
    }

    private static final Map<String, String> PAPEIS = Map.of(KERNEL, "kernel", "trilha", "peer", "aluno", "peer");

    private static String papel(String parte) {
        return PAPEIS.getOrDefault(parte, "fatia");
    }

    private static boolean permitido(String origem, String destino) {
        if (KERNEL.equals(origem)) {
            return false;
        }
        if (PEERS.contains(origem)) {
            return KERNEL.equals(destino);
        }
        return KERNEL.equals(destino) || PEERS.contains(destino);
    }
}
