package org.framework.net.calculadora.domain;

/**
 * Uma VLAN do plano, com a sub-rede e os parâmetros de configuração do switch.
 *
 * <p><b>Propósito de negócio:</b> é a linha que o aluno leva para o Packet Tracer
 * ou para o switch real — VLAN ID, nome, sub-rede, gateway (SVI), faixa de DHCP
 * e a wildcard usada em ACL/OSPF para essa rede.</p>
 *
 * <p><b>Invariantes do domínio:</b> {@code gateway} é sempre o primeiro host
 * utilizável do bloco (convenção do projeto e do laboratório); a faixa DHCP
 * começa após o gateway e termina no último host; blocos /31 e /32 não
 * comportam gateway + pool e por isso são rejeitados antes de virar VlanEntry.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> record imutável sem validação
 * própria; VLAN IDs reservados e blocos pequenos demais são barrados no
 * {@code VlanService} com {@code CalculadoraException}.</p>
 */
public record VlanEntry(
        int vlanId,
        String nome,
        String faixaVlan,
        BlocoIpv4 bloco,
        String gateway,
        String dhcpInicio,
        String dhcpFim,
        long hostsUteis) {
}
