package org.framework.net.ferramentasDiagnostico.domain;

import java.util.List;

/**
 * Resultado dissecado de uma ferramenta de diagnóstico simulada.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> transformar a saída crua de uma ferramenta
 * (ping, traceroute, dig, varredura…) numa peça didática no mesmo nível da
 * Calculadora e da Análise — indicadores, o comando real que aquilo representa,
 * uma tabela campo a campo, a legenda dos termos, o "como funciona" e o "o que
 * observar". A saída bruta é preservada em {@link #saidaBruta()} para o aluno
 * comparar a explicação com o que a ferramenta de verdade imprimiria.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> record puro de dados, sem framework. Listas
 * nunca nulas; {@code saidaBruta} nunca em branco. A {@code tabela} pode ser nula
 * quando a ferramenta não tem saída tabular.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nenhum — a validação de entrada e a
 * montagem ficam no serviço de aplicação.</p>
 */
public record ResultadoDiagnostico(
        String ferramenta,
        String titulo,
        String icone,
        String comandoReal,
        String resumo,
        List<Kpi> kpis,
        Tabela tabela,
        List<Termo> legenda,
        List<String> comoFunciona,
        List<String> observar,
        String nota,
        String saidaBruta) {

    /**
     * Construtor compacto que HONRA o invariante "listas nunca nulas": copia e
     * blinda cada lista obrigatória (imutável e não-nula). {@code tabela} pode ser
     * nula de propósito — nem toda ferramenta tem saída tabular.
     */
    public ResultadoDiagnostico {
        kpis = kpis == null ? List.of() : List.copyOf(kpis);
        legenda = legenda == null ? List.of() : List.copyOf(legenda);
        comoFunciona = comoFunciona == null ? List.of() : List.copyOf(comoFunciona);
        observar = observar == null ? List.of() : List.copyOf(observar);
    }

    /** Indicador de topo: rótulo, valor e uma nota curta, com cor de acento. */
    public record Kpi(String label, String valor, String cor, String nota) {
    }

    /** Tabela dissecada: um título, os cabeçalhos e as linhas. */
    public record Tabela(String titulo, List<String> colunas, List<Linha> linhas) {

        /**
         * Uma linha da tabela.
         *
         * @param cor cor de acento da linha ({@code ""}, {@code success},
         *            {@code warning} ou {@code danger}) — colore, por exemplo,
         *            porta aberta × filtrada × fechada.
         */
        public record Linha(List<String> celulas, String cor) {
        }
    }

    /** Um termo do glossário: o que aquela palavra da saída significa. */
    public record Termo(String termo, String significado) {
    }
}
