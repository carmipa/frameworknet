package org.framework.net.segurancaRede.domain;

import java.util.List;

/**
 * Diagnóstico de alcançabilidade de um fluxo: da origem ao destino, salto a
 * salto, dizendo se chega e ONDE trava.
 *
 * <p><b>Propósito de negócio:</b> responder a pergunta que fecha o raciocínio de
 * rede — "esse tráfego alcança o destino?" — mostrando o caminho e apontando o
 * ponto exato de bloqueio (isolamento de VLAN, gateway errado, rota ausente ou
 * ACL/firewall negando a porta). É a versão de diagnóstico do editor de cenários
 * (a montagem visual arrastando equipamentos é uma etapa posterior).</p>
 *
 * <p><b>Invariantes do domínio:</b> determinístico por cenário; quando
 * {@code alcanca} é falso, existe exatamente um salto com {@code ok=false} e
 * {@code pontoBloqueio} nomeia esse salto; quando é verdadeiro, todos os saltos
 * são {@code ok} e {@code pontoBloqueio} é vazio. Nenhuma lista é nula.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário fora do catálogo é recusado
 * na camada de aplicação; este record só transporta dados montados.</p>
 */
public record DiagnosticoFluxo(
        String cenario,
        String titulo,
        String origem,
        String destino,
        boolean alcanca,
        String pontoBloqueio,
        String resumo,
        List<Salto> saltos) {

    public DiagnosticoFluxo {
        saltos = saltos == null ? List.of() : List.copyOf(saltos);
    }

    /**
     * Um ponto no caminho do pacote.
     *
     * <p><b>Invariantes:</b> {@code ok} indica se o pacote passou por este nó;
     * {@code camada} situa a decisão (L2/L3/L4) e {@code decisao} explica o que
     * aconteceu ali.</p>
     */
    public record Salto(int ordem, String no, String camada, boolean ok, String decisao) {
    }
}
