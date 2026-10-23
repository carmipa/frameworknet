package org.framework.net.protocolos.application;

import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.framework.net.protocolos.domain.ProtocoloItem;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Projeção de um protocolo do catálogo para exibição no DataGrid.
 *
 * <p><b>Propósito de negócio:</b> normaliza os campos vindos do JSON (nulo vira
 * texto vazio, alcance ausente vira "N/A") e prepara o texto de busca que o
 * DataGrid usa no filtro geral. Também informa se aquele protocolo tem página de
 * aprofundamento — é o que faz a linha do BGP e a do SSH ganharem o botão
 * "Aprofundar" enquanto as demais não o exibem.</p>
 *
 * <p><b>Invariantes do domínio:</b> nenhum campo de texto é nulo depois da
 * projeção, porque o template renderiza direto e {@code null} viraria a palavra
 * "null" na tela. {@code aprofundamentoRota} só vem preenchido quando
 * {@code temAprofundamento} é verdadeiro.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> protocolo sem página de
 * aprofundamento não é erro — devolve {@code temAprofundamento=false} e rota
 * vazia, e a coluna de ações mostra apenas copiar e detalhes.</p>
 */
public record ProtocoloItemExibicao(
        String nome,
        String camada,
        String transporte,
        String portaComum,
        String funcao,
        String seguro,
        String badge,
        String badgeColor,
        String alcance,
        String alcanceLower,
        String categoria,
        String algoritmo,
        String metrica,
        String distanciaAdministrativa,
        String atualizacao,
        String sintaxeBase,
        String dicaDidatica,
        String convergencia,
        String ecmp,
        String problemasComuns,
        String mitigacoes,
        String casoUsoReal,
        String diagnosticoComandos,
        String searchText,
        boolean roteamento,
        boolean temAprofundamento,
        String aprofundamentoRota) {

    public static ProtocoloItemExibicao from(ProtocoloItem item) {
        String alcance = normalizarOu(item.alcance(), "N/A");
        String categoria = normalizarOu(item.categoria(), "outros");
        String algoritmo = normalizar(item.algoritmo());
        String metrica = normalizar(item.metrica());
        String ad = normalizar(item.distanciaAdministrativa());
        String sintaxe = normalizar(item.sintaxeBase());
        String dica = normalizar(item.dicaDidatica());
        String problemas = normalizar(item.problemasComuns());
        String mitigacoesVal = normalizar(item.mitigacoes());
        String diagnostico = normalizarOu(item.diagnosticoComandos(), "show ip route");

        String searchText = Stream.of(
                        item.nome(),
                        item.camada(),
                        alcance,
                        item.transporte(),
                        item.portaComum(),
                        item.seguro(),
                        item.badge(),
                        item.funcao(),
                        algoritmo,
                        metrica,
                        ad,
                        sintaxe,
                        dica,
                        problemas,
                        mitigacoesVal,
                        diagnostico)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));

        Optional<AprofundamentoProtocolo> aprofundamento =
                AprofundamentoProtocolo.porNomeDoCatalogo(item.nome());

        return new ProtocoloItemExibicao(
                item.nome(),
                item.camada(),
                item.transporte(),
                item.portaComum(),
                item.funcao(),
                item.seguro(),
                item.badge(),
                item.badgeColor(),
                alcance,
                alcance.toLowerCase(Locale.ROOT),
                categoria,
                algoritmo,
                metrica,
                ad,
                normalizar(item.atualizacao()),
                sintaxe,
                dica,
                normalizar(item.convergencia()),
                normalizar(item.ecmp()),
                problemas,
                mitigacoesVal,
                normalizar(item.casoUsoReal()),
                diagnostico,
                searchText,
                "roteamento".equalsIgnoreCase(categoria),
                aprofundamento.isPresent(),
                aprofundamento.map(AprofundamentoProtocolo::rota).orElse(""));
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String normalizarOu(String valor, String fallback) {
        String normalizado = normalizar(valor);
        return normalizado.isEmpty() ? fallback : normalizado;
    }
}
