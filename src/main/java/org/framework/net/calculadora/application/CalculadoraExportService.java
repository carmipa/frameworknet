package org.framework.net.calculadora.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.calculadora.domain.BlocoIpv4;
import org.framework.net.calculadora.domain.PlanoDivisao;
import org.framework.net.calculadora.domain.PlanoVlan;
import org.framework.net.calculadora.domain.VlanEntry;

import java.util.List;

/**
 * Serializa os planos da Calculadora em CSV para planilha.
 *
 * <p><b>Propósito de negócio:</b> o plano de endereçamento raramente termina na
 * tela — vai para a documentação da rede, para a planilha da turma ou para o
 * relatório da prova. Este serviço entrega a mesma tabela vista na tela em um
 * arquivo que abre no Excel e no Google Sheets.</p>
 *
 * <p><b>Invariantes do domínio:</b> o CSV reflete exatamente as linhas
 * renderizadas — se a listagem foi truncada pelo teto de exibição, o arquivo
 * traz o mesmo recorte e uma linha de comentário declarando o total real, para
 * que ninguém leia o arquivo como plano completo; todo campo é escapado no
 * padrão RFC 4180 (aspas duplicadas e valor entre aspas).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> métodos puros sobre records já
 * validados; não lançam exceção de domínio nem devolvem {@code null}.</p>
 */
@ApplicationScoped
public class CalculadoraExportService {

    private static final String CABECALHO_BLOCOS =
            "indice,rede,prefixo,mascara,wildcard,primeiro_host,ultimo_host,broadcast,total_ips,hosts_uteis";

    /** CSV da divisão de blocos, com o cabeçalho de contexto do plano. */
    public String divisaoCsv(PlanoDivisao plano) {
        StringBuilder sb = new StringBuilder();
        sb.append("# bloco_base,").append(plano.blocoBase()).append('/').append(plano.prefixoBase()).append('\n');
        sb.append("# prefixo_alvo,/").append(plano.prefixoAlvo()).append('\n');
        sb.append("# total_subredes,").append(plano.totalSubredes()).append('\n');
        sb.append("# linhas_neste_arquivo,").append(plano.exibidos()).append('\n');
        if (plano.truncado()) {
            sb.append("# ATENCAO,listagem truncada pelo limite de exibicao; o total real esta acima\n");
        }
        sb.append(CABECALHO_BLOCOS).append('\n');
        anexarBlocos(sb, plano.blocos());
        return sb.toString();
    }

    /** CSV do plano de VLANs, incluindo gateway e faixa DHCP. */
    public String vlanCsv(PlanoVlan plano) {
        StringBuilder sb = new StringBuilder();
        sb.append("# bloco_base,").append(plano.blocoBase()).append('/').append(plano.prefixoBase()).append('\n');
        sb.append("# prefixo_por_vlan,/").append(plano.prefixoPorVlan()).append('\n');
        sb.append("# total_vlans,").append(plano.totalVlans()).append('\n');
        sb.append("# trunk_allowed_vlan,").append(escapar(plano.trunkAllowed())).append('\n');
        sb.append("vlan_id,nome,faixa,rede,prefixo,mascara,wildcard,gateway,dhcp_inicio,dhcp_fim,hosts_uteis\n");
        for (VlanEntry vlan : plano.vlans()) {
            BlocoIpv4 bloco = vlan.bloco();
            sb.append(vlan.vlanId()).append(',')
                    .append(escapar(vlan.nome())).append(',')
                    .append(escapar(vlan.faixaVlan())).append(',')
                    .append(bloco.rede()).append(',')
                    .append(bloco.prefixo()).append(',')
                    .append(bloco.mascara()).append(',')
                    .append(bloco.wildcard()).append(',')
                    .append(vlan.gateway()).append(',')
                    .append(vlan.dhcpInicio()).append(',')
                    .append(vlan.dhcpFim()).append(',')
                    .append(vlan.hostsUteis()).append('\n');
        }
        return sb.toString();
    }

    /** CSV genérico de uma lista de blocos (usado pela conversão de faixa). */
    public String blocosCsv(List<BlocoIpv4> blocos) {
        StringBuilder sb = new StringBuilder();
        sb.append(CABECALHO_BLOCOS).append('\n');
        anexarBlocos(sb, blocos);
        return sb.toString();
    }

    private void anexarBlocos(StringBuilder sb, List<BlocoIpv4> blocos) {
        for (BlocoIpv4 bloco : blocos) {
            sb.append(bloco.indice()).append(',')
                    .append(bloco.rede()).append(',')
                    .append(bloco.prefixo()).append(',')
                    .append(bloco.mascara()).append(',')
                    .append(bloco.wildcard()).append(',')
                    .append(bloco.primeiroHost()).append(',')
                    .append(bloco.ultimoHost()).append(',')
                    .append(escapar(bloco.broadcast())).append(',')
                    .append(bloco.totalIps()).append(',')
                    .append(bloco.hostsUteis()).append('\n');
        }
    }

    /**
     * Escapa um campo no padrão RFC 4180.
     *
     * <p><b>Invariantes do domínio:</b> campos com vírgula, aspas ou quebra de
     * linha vão entre aspas com as aspas internas duplicadas — sem isso o
     * {@code trunk_allowed_vlan} (que é uma lista com vírgulas) quebraria a
     * planilha em colunas erradas.</p>
     */
    private String escapar(String valor) {
        String texto = valor == null ? "" : valor;
        if (texto.indexOf(',') < 0 && texto.indexOf('"') < 0 && texto.indexOf('\n') < 0) {
            return texto;
        }
        return '"' + texto.replace("\"", "\"\"") + '"';
    }
}
