package org.framework.net.resolucaoProblemas.application.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.EnlaceReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.LanReconstruida;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.LinhaTabela;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.Pendencia;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.ScriptCorrigido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.TabelaRoteador;
import org.framework.net.resolucaoProblemas.exception.ExportacaoException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exportações da aba "Engenharia reversa".
 *
 * <p><b>Propósito de negócio:</b> tirar o resultado da tela — os scripts prontos
 * para colar no equipamento e o relatório da auditoria, que é o que o aluno anexa
 * na entrega. São exportações próprias, e não as da aba "Projetar", porque aquelas
 * imprimem números de planejamento (hosts solicitados, eficiência de uso) que aqui
 * não existem: exibi-los zerados seria relatar dado que ninguém calculou.</p>
 *
 * <p><b>Invariantes do domínio:</b> o arquivo de scripts preserva os comentários
 * {@code ! CORRIGIDO:} — quem cola a configuração no roteador precisa ver, no
 * próprio arquivo, o que a ferramenta mexeu e por quê. O relatório sempre lista as
 * pendências e as linhas não interpretadas, mesmo quando o cenário fecha, para que
 * o leitor saiba o que ficou de fora da análise.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário nulo ou sem roteadores lança
 * {@link ExportacaoException} — exportar arquivo vazio dá ao usuário a impressão
 * de que havia conteúdo.</p>
 */
@ApplicationScoped
public class ExportEngenhariaReversaService {

    private static final String LINHA = "=".repeat(78);

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Arquivo com o script corrigido de cada roteador.
     *
     * <p><b>Comportamento em caso de falha:</b> {@link ExportacaoException} quando
     * não há nada reconstruído.</p>
     */
    public String gerarScriptsCorrigidos(CenarioReconstruido cenario) {
        exigirConteudo(cenario);
        List<String> linhas = new ArrayList<>();
        linhas.add("!");
        linhas.add("! SCRIPTS CORRIGIDOS - ENGENHARIA REVERSA");
        linhas.add("! Framework de Redes - Analise Didatica Avancada");
        linhas.add("!");
        linhas.add("! " + cenario.resumo().roteadores() + " roteador(es), "
                + cenario.resumo().errosCorrigidos() + " correcao(oes) aplicada(s), "
                + cenario.resumo().errosSemCorrecao() + " erro(s) sem correcao automatica.");
        linhas.add("!");
        linhas.add("! ATENCAO: cada linha alterada vem precedida de \"! CORRIGIDO:\" com o valor");
        linhas.add("! original e a evidencia. Confira antes de aplicar no equipamento.");
        linhas.add("!");

        for (ScriptCorrigido script : cenario.scripts()) {
            linhas.add("!" + LINHA);
            linhas.add("! ROTEADOR: " + script.roteador());
            linhas.add("!" + LINHA);
            linhas.add(script.conteudo());
            linhas.add("");
        }

        if (!cenario.pendencias().isEmpty()) {
            linhas.add("!" + LINHA);
            linhas.add("! PENDENCIAS - o cenario NAO esta completo");
            linhas.add("!" + LINHA);
            for (Pendencia p : cenario.pendencias()) {
                linhas.add("! - " + p.descricao());
                linhas.add("!   " + p.comoResolver());
            }
        }

        telemetriaLogger.logEvent("info", "resolucaoProblemas", "engenharia_reversa_export",
                Map.of("formato", "scripts", "roteadores", cenario.resumo().roteadores()));
        return String.join("\n", linhas).strip() + "\n";
    }

    /**
     * Relatório completo: auditoria, LANs, enlaces, tabelas e topologia.
     *
     * <p><b>Comportamento em caso de falha:</b> {@link ExportacaoException} quando
     * não há nada reconstruído.</p>
     */
    public String gerarRelatorio(CenarioReconstruido cenario) {
        exigirConteudo(cenario);
        List<String> linhas = new ArrayList<>();
        linhas.add(LINHA);
        linhas.add("RELATORIO DE ENGENHARIA REVERSA DE CONFIGURACAO");
        linhas.add(LINHA);
        linhas.add("");
        linhas.add("RESUMO");
        linhas.add("  Roteadores lidos .............. " + cenario.resumo().roteadores());
        linhas.add("  Enlaces ponto a ponto ......... " + cenario.resumo().enlaces());
        linhas.add("  LANs .......................... " + cenario.resumo().lans());
        linhas.add("  Erros corrigidos .............. " + cenario.resumo().errosCorrigidos());
        linhas.add("  Erros sem correcao automatica . " + cenario.resumo().errosSemCorrecao());
        linhas.add("  Avisos ........................ " + cenario.resumo().avisos());
        linhas.add("  Linhas nao interpretadas ...... " + cenario.resumo().linhasNaoInterpretadas());
        linhas.add("  Cenario consistente ........... "
                + (cenario.resumo().cenarioConsistente() ? "sim" : "nao"));
        linhas.add("");

        secao(linhas, "AUDITORIA");
        if (cenario.achados().isEmpty()) {
            linhas.add("  Nenhum achado.");
        }
        for (AchadoConfiguracao a : cenario.achados()) {
            linhas.add("  [" + a.rotuloSeveridade().toUpperCase(java.util.Locale.ROOT) + "] "
                    + a.categoria() + " - " + a.roteador()
                    + (a.linha() > 0 ? " (linha " + a.linha() + ")" : ""));
            linhas.add("    " + a.descricao());
            if (a.temCorrecao()) {
                linhas.add("    ANTES : " + a.original());
                linhas.add("    DEPOIS: " + a.corrigido());
            }
            if (a.temEvidencia()) {
                linhas.add("    EVIDENCIA: " + a.evidencia());
            }
            linhas.add("");
        }

        if (!cenario.pendencias().isEmpty()) {
            secao(linhas, "PENDENCIAS");
            for (Pendencia p : cenario.pendencias()) {
                linhas.add("  - " + p.descricao());
                linhas.add("    " + p.comoResolver());
            }
            linhas.add("");
        }

        secao(linhas, "LANS");
        for (LanReconstruida lan : cenario.lans()) {
            linhas.add("  " + lan.roteador() + " " + lan.nomeInterface() + "  " + lan.cidr()
                    + "  mascara " + lan.mascara() + "  wildcard " + lan.wildcard());
            linhas.add("      gateway " + lan.gateway() + " | faixa " + lan.primeiroHost()
                    + " - " + lan.ultimoHost() + " | broadcast " + lan.broadcast()
                    + " | " + lan.hostsUtilizaveis() + " hosts"
                    + (lan.anunciadaNoRoteamento() ? " | anunciada" : " | NAO anunciada"));
        }
        linhas.add("");

        secao(linhas, "ENLACES PONTO A PONTO");
        for (EnlaceReconstruido e : cenario.enlaces()) {
            linhas.add("  " + e.cidr() + "  " + e.nome());
            linhas.add("      A: " + e.roteadorA() + " " + e.nomeInterfaceA() + " " + e.ipA());
            linhas.add("      B: " + (e.completo()
                    ? e.roteadorB() + " " + e.nomeInterfaceB() + " " + e.ipB()
                    : "ponta ausente (" + e.ipB() + ")"));
            linhas.add("      DCE: " + e.ladoDce());
        }
        linhas.add("");

        secao(linhas, "TABELAS POR ROTEADOR");
        for (TabelaRoteador t : cenario.tabelas()) {
            linhas.add("  " + t.roteador() + (t.asBgp() > 0 ? "  (AS " + t.asBgp() + ")" : ""));
            for (LinhaTabela l : t.linhas()) {
                linhas.add(String.format("      %-22s %-16s %-16s %-20s %s",
                        l.nomeInterface(), l.ip(), l.mascara(), l.papel(), l.observacao()));
            }
            linhas.add("");
        }

        if (!cenario.mermaid().isBlank()) {
            secao(linhas, "TOPOLOGIA (MERMAID)");
            linhas.add(cenario.mermaid());
            linhas.add("");
        }

        if (!cenario.linhasNaoInterpretadas().isEmpty()) {
            secao(linhas, "LINHAS NAO INTERPRETADAS");
            cenario.linhasNaoInterpretadas().forEach(l ->
                    linhas.add("  linha " + l.numero() + ": " + l.conteudo() + "   [" + l.motivo() + "]"));
            linhas.add("");
        }

        secao(linhas, "SCRIPTS CORRIGIDOS");
        for (ScriptCorrigido s : cenario.scripts()) {
            linhas.add(s.conteudo());
            linhas.add("");
        }

        telemetriaLogger.logEvent("info", "resolucaoProblemas", "engenharia_reversa_export",
                Map.of("formato", "relatorio", "roteadores", cenario.resumo().roteadores()));
        return String.join("\n", linhas).strip() + "\n";
    }

    private void secao(List<String> linhas, String titulo) {
        linhas.add(LINHA);
        linhas.add(titulo);
        linhas.add(LINHA);
    }

    private void exigirConteudo(CenarioReconstruido cenario) {
        if (cenario == null || cenario.vazio()) {
            throw new ExportacaoException(
                    "Não há configuração interpretada para exportar. Cole os scripts e clique em Executar.");
        }
    }
}
