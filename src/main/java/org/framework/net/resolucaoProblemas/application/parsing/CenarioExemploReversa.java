package org.framework.net.resolucaoProblemas.application.parsing;

/**
 * Cenário de exemplo da aba "Engenharia reversa".
 *
 * <p><b>Propósito de negócio:</b> dar ao aluno um caso pronto para ver a
 * ferramenta trabalhar sem ter uma configuração à mão. O texto é um cenário real
 * de três Sistemas Autônomos em eBGP <b>com os erros preservados</b> — comando
 * {@code ip adress} escrito errado, máscara de cinco octetos no anúncio e duas
 * interfaces com o endereço trocado. Um exemplo já correto não mostraria nada do
 * que a aba faz de útil.</p>
 *
 * <p><b>Invariantes do domínio:</b> os erros aqui são intencionais e são a base
 * do teste de referência do algoritmo. Consertar este texto invalida o exemplo e
 * quebra a demonstração — se for preciso outro cenário, acrescente, não
 * substitua.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> não se aplica; é constante.</p>
 */
public final class CenarioExemploReversa {

    private CenarioExemploReversa() {
    }

    /** Três AS em anel (SP 37510, RJ 48880, MG 65480), com erros propositais. */
    public static final String BGP_TRES_AS = """
            ena
            conf t
            hostname SP
            int g0/0
            ip adress 172.19.0.1 255.255.240.0
            no shut
            int s0/3/0
            ip adress 200.200.200.1 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.10 255.255.255.252
            no shut
            router bgp 37510
            neighbor 200.200.200.2 remote-as 48880
            neighbor 200.200.200.9 remote-as 65480
            network 172.19.0.0 mask 255.255.255.240.0

            --------------

            ena
            conf t
            hostname RJ
            int g0/0
            ip adress 172.19.16.1 255.255.248.0
            no shut
            int s0/3/0
            ip adress 200.200.200.5 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.6 255.255.255.252
            no shut
            router bgp 48880
            neighbor 200.200.200.6 remote-as 65480
            neighbor 200.200.200.1 remote-as 37510
            network 172.19.16.0 mask 255.255.255.248.0

            --------------

            ena
            conf t
            hostname MG
            int g0/0
            ip adress 172.19.24.1 255.255.252.0
            no shut
            int s0/3/0
            ip adress 200.200.200.9 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.10 255.255.255.252
            no shut
            router bgp 65480
            neighbor 200.200.200.10 remote-as 37510
            neighbor 200.200.200.5 remote-as 48880
            network 172.19.24.0 mask 255.255.255.252.0
            """;
}
