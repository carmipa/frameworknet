package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.academia.eventos.domain.EventoAcademia;
import org.framework.net.analiseDidatica.application.cidr.CidrModoService;
import org.framework.net.telemetria.infrastructure.TelemetriaAcademiaAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linhas de log que levam texto do visitante (Lote 1, revisão adversarial, 2ª passada): o CIDR digitado no modo CIDR
 * ia cru ao WARN, e a mensagem de erro JS da Academia passava pelo saneador dela, que não troca U+2028/U+2029. A prova
 * captura o registro de log de verdade, na categoria de cada classe, e confere a forma que chegou ao console.
 * A2: sem a regra única ({@code TelemetriaContext.paraLog}) na chamada, o caractere cru aparece e o teste cai.
 */
@QuarkusTest
class LogNeutralizadoNasBordasTest {

    @Inject
    CidrModoService cidrModoService;

    @Inject
    TelemetriaAcademiaAdapter academiaAdapter;

    /** {@code %s} do formato de log do Quarkus: a mensagem já formatada, como sai no console. */
    private static final java.util.logging.Formatter SO_MENSAGEM =
            new org.jboss.logmanager.formatters.PatternFormatter("%s");

    /** Coleta as mensagens já formatadas de uma categoria de log enquanto a ação roda. */
    private static List<String> capturar(Class<?> categoria, Runnable acao) {
        List<String> linhas = new CopyOnWriteArrayList<>();
        Handler coletor = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                linhas.add(SO_MENSAGEM.format(registro));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger jul = java.util.logging.Logger.getLogger(categoria.getName());
        jul.addHandler(coletor);
        try {
            acao.run();
        } finally {
            jul.removeHandler(coletor);
        }
        return linhas;
    }

    @Test
    void cidrDigitadoChegaAoLogNeutralizado() {
        List<String> linhas = capturar(CidrModoService.class, () -> cidrModoService.processar("10.0.0.1", "2\nCIDR forjado"));
        String linha = linhas.stream().filter(l -> l.contains("CIDR inválido")).findFirst()
                .orElseThrow(() -> new AssertionError("o WARN do CIDR inválido não foi capturado: " + linhas));
        assertFalse(linha.contains("\n"), "quebra de linha crua no log: " + linha);
        assertTrue(linha.contains("2%0ACIDR forjado"), linha);
    }

    @Test
    void mensagemDeErroJsChegaAoLogSemSeparadorDeLinhaCru() {
        List<String> linhas = capturar(TelemetriaAcademiaAdapter.class, () -> academiaAdapter.registrar(
                new EventoAcademia.ErroJs("licao-teste", EventoAcademia.TipoErroJs.ERRO, "falhou\u2028linha forjada")));
        String linha = linhas.stream().filter(l -> l.contains("erro_js")).findFirst()
                .orElseThrow(() -> new AssertionError("o WARN do erro JS não foi capturado: " + linhas));
        assertFalse(linha.contains("\u2028"), "separador de linha cru no log: " + linha);
        assertTrue(linha.contains("falhou%u2028linha forjada"), linha);
    }
}
