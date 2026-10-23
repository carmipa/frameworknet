package org.framework.net.analiseTrafego.domain.model;

import java.util.List;

/**
 * Laboratório didático de padrões DNS/ICMP sobre dados FICTÍCIOS.
 *
 * <p><b>Propósito de negócio:</b> deixar o aluno comparar tráfego "normal" e
 * "suspeito" (ex.: tunneling por DNS ou ICMP), ver qual indicador levanta a
 * suspeita e entender por que o mesmo indicador gera falso positivo em tráfego
 * legítimo — que padrão é pista, não prova.</p>
 *
 * <p><b>Invariantes do domínio:</b> os dados são SIMULADOS e declarados como
 * tais; a classificação é determinística e sempre acompanhada da evidência (o
 * indicador que a motivou); e cada cenário carrega a nota de falso positivo,
 * para que a tela nunca sugira diagnóstico real a partir de uma animação.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cenário fora do catálogo é recusado
 * na camada de aplicação; este record só transporta dados montados e nunca tem
 * listas nulas.</p>
 */
public record AnaliseTrafegoLab(
        String cenario,
        String titulo,
        String descricao,
        String falsoPositivo,
        List<RegistroTrafego> registros) {

    public AnaliseTrafegoLab {
        registros = registros == null ? List.of() : List.copyOf(registros);
    }

    public long suspeitos() {
        return registros.stream().filter(RegistroTrafego::suspeito).count();
    }

    /**
     * Um registro fictício de tráfego, já classificado com a evidência.
     *
     * <p><b>Invariantes:</b> {@code classificacao} é {@code NORMAL} ou
     * {@code SUSPEITO}; {@code indicador} nomeia a característica observada e
     * {@code explicacao} diz por que ela é (ou não) preocupante.</p>
     */
    public record RegistroTrafego(
            String protocolo,
            String amostra,
            String indicador,
            String classificacao,
            String explicacao) {

        public boolean suspeito() {
            return "SUSPEITO".equals(classificacao);
        }
    }
}
