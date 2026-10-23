package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.domain.SimulacaoFirewall;
import org.framework.net.segurancaRede.domain.SimulacaoFirewall.AvaliacaoPacote;
import org.framework.net.segurancaRede.domain.SimulacaoFirewall.PacoteFluxo;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simulador didático de firewall SEM estado × COM estado sobre um fluxo TCP.
 *
 * <p><b>Propósito de negócio:</b> ensinar por que um firewall com estado libera
 * o tráfego de retorno de uma conexão iniciada de dentro, enquanto um sem estado
 * ou bloqueia esse retorno ou precisa de um furo permanente na entrada. O aluno
 * escolhe um cenário e vê, passo a passo, os dois vereditos lado a lado.</p>
 *
 * <p><b>Invariantes do domínio:</b> a simulação é DETERMINÍSTICA (nenhum uso de
 * relógio ou aleatoriedade); o estado da tabela é explícito a cada passo; e uma
 * flag de entrada isolada é rotulada "fora de estado", nunca "ataque" — flag
 * isolada não é prova universal de ataque. Cenário fora do catálogo é recusado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@code cenarioId} desconhecido lança
 * {@link SegurancaException} (HTTP 400 pelo mapper); nunca devolve uma simulação
 * "chutada" para um cenário que não existe.</p>
 */
@ApplicationScoped
public class FirewallEstadoService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    private static final String PERMITIDO = "PERMITIDO";
    private static final String BLOQUEADO = "BLOQUEADO";
    private static final String NEUTRO = "—";

    // Estados da tabela de conexões (fluxo único, para foco didático).
    private static final String SEM_CONEXAO = "sem conexão";
    private static final String SYN_ENVIADO = "SYN enviado";
    private static final String ESTABELECIDA = "estabelecida";
    private static final String FECHANDO = "fechando";

    private static final String POLITICA_SEM_ESTADO =
            "Saída (LAN→WAN): permitida. Entrada (WAN→LAN): negada por padrão "
            + "(sem regra 'established', cada pacote é julgado isolado).";
    private static final String POLITICA_COM_ESTADO =
            "Saída: permitida e registra a conexão. Entrada: permitida só quando casa "
            + "uma conexão na tabela; o resto cai no deny.";

    /** Cenários oferecidos na tela, na ordem de exibição. */
    public List<Map<String, String>> cenariosDisponiveis() {
        List<Map<String, String>> lista = new ArrayList<>();
        for (Cenario c : Cenario.values()) {
            lista.add(Map.of("id", c.id, "titulo", c.titulo));
        }
        return lista;
    }

    public SimulacaoFirewall simular(String cenarioId) {
        return telemetriaLogger.medir("seguranca", "firewall_estado", () -> {
            Cenario cenario = Cenario.porId(cenarioId);
            List<AvaliacaoPacote> avaliacoes = avaliar(cenario.pacotes());
            telemetriaLogger.logEvent("info", "seguranca", "firewall_estado_simulado", Map.of(
                    "cenario", cenario.id,
                    "passos", avaliacoes.size()));
            return new SimulacaoFirewall(cenario.id, cenario.titulo,
                    POLITICA_SEM_ESTADO, POLITICA_COM_ESTADO, avaliacoes);
        });
    }

    /**
     * Percorre a sequência mantendo o estado da conexão; cada pacote recebe o
     * veredito dos dois modelos com a razão e o estado antes/depois.
     */
    private List<AvaliacaoPacote> avaliar(List<PacoteFluxo> pacotes) {
        List<AvaliacaoPacote> saida = new ArrayList<>();
        String estado = SEM_CONEXAO;
        for (PacoteFluxo p : pacotes) {
            String antes = estado;

            String semEstado;
            String razaoSem;
            if (p.saida()) {
                semEstado = PERMITIDO;
                razaoSem = "saída (LAN→WAN) é permitida pela política";
            } else if (p.entrada()) {
                semEstado = BLOQUEADO;
                razaoSem = "entrada sem regra que a permita → deny implícito (o retorno legítimo cai junto)";
            } else {
                semEstado = NEUTRO;
                razaoSem = "não é um pacote — o modelo sem estado não tem tabela para expirar";
            }

            String comEstado;
            String razaoCom;
            switch (p.direcao()) {
                case "TEMPO" -> {
                    estado = SEM_CONEXAO;
                    comEstado = NEUTRO;
                    razaoCom = "a conexão ociosa expira e sai da tabela de estados";
                }
                case "SAIDA" -> {
                    comEstado = PERMITIDO;
                    if ("SYN".equals(p.flag()) && SEM_CONEXAO.equals(estado)) {
                        estado = SYN_ENVIADO;
                        razaoCom = "saída abre a conexão e a registra na tabela (SYN enviado)";
                    } else if ("ACK".equals(p.flag()) && SYN_ENVIADO.equals(estado)) {
                        estado = ESTABELECIDA;
                        razaoCom = "ACK final do handshake — conexão estabelecida";
                    } else if (p.flag().startsWith("FIN")) {
                        estado = FECHANDO;
                        razaoCom = "saída inicia o encerramento da conexão";
                    } else {
                        razaoCom = "saída pertence à conexão em curso";
                    }
                }
                case "ENTRADA" -> {
                    if ("SYN-ACK".equals(p.flag())) {
                        if (SYN_ENVIADO.equals(estado)) {
                            estado = ESTABELECIDA;
                            comEstado = PERMITIDO;
                            razaoCom = "resposta casa a conexão registrada — conexão estabelecida";
                        } else {
                            comEstado = BLOQUEADO;
                            razaoCom = "SYN-ACK sem SYN de saída correspondente: fora de estado "
                                    + "(apenas não casa nenhuma conexão da tabela; sem inferir intenção)";
                        }
                    } else if ("SYN".equals(p.flag())) {
                        comEstado = BLOQUEADO;
                        razaoCom = "SYN de entrada não solicitado: nenhuma conexão foi iniciada por dentro";
                    } else if (p.flag().startsWith("FIN")) {
                        if (ESTABELECIDA.equals(estado) || FECHANDO.equals(estado)) {
                            estado = SEM_CONEXAO;
                            comEstado = PERMITIDO;
                            razaoCom = "encerramento pertencente à conexão";
                        } else {
                            comEstado = BLOQUEADO;
                            razaoCom = "encerramento sem conexão na tabela: fora de estado";
                        }
                    } else {
                        // ACK, DADOS
                        if (ESTABELECIDA.equals(estado)) {
                            comEstado = PERMITIDO;
                            razaoCom = "pertence à conexão estabelecida na tabela";
                        } else {
                            comEstado = BLOQUEADO;
                            razaoCom = "pacote de entrada sem conexão na tabela: fora de estado";
                        }
                    }
                }
                default -> throw new SegurancaException("Direção de pacote não suportada: " + p.direcao());
            }

            saida.add(new AvaliacaoPacote(p, semEstado, razaoSem, comEstado, razaoCom, antes, estado));
        }
        return saida;
    }

    /** Catálogo fechado de cenários; escolher fora dele é erro, não improviso. */
    private enum Cenario {
        CONEXAO_SAIDA("conexao-saida", "Navegação de saída (conexão HTTPS iniciada por dentro)", List.of(
                new PacoteFluxo(1, "SAIDA", "SYN", "cliente 10.0.0.5 abre conexão para 203.0.113.10:443"),
                new PacoteFluxo(2, "ENTRADA", "SYN-ACK", "servidor 203.0.113.10 responde o handshake"),
                new PacoteFluxo(3, "SAIDA", "ACK", "cliente confirma — handshake completo"),
                new PacoteFluxo(4, "ENTRADA", "DADOS", "servidor envia a página (tráfego de retorno)"),
                new PacoteFluxo(5, "SAIDA", "FIN", "cliente encerra a conexão"))),

        ENTRADA_NAO_SOLICITADA("entrada-nao-solicitada", "Conexão vinda de fora, sem nada iniciado por dentro", List.of(
                new PacoteFluxo(1, "ENTRADA", "SYN", "host externo tenta abrir conexão para a LAN"),
                new PacoteFluxo(2, "ENTRADA", "SYN", "retransmissão do SYN externo"),
                new PacoteFluxo(3, "ENTRADA", "SYN-ACK", "pacote com flags de resposta, mas sem conexão na tabela"))),

        RESPOSTA_TARDIA("resposta-tardia", "Resposta que chega depois de a conexão expirar", List.of(
                new PacoteFluxo(1, "SAIDA", "SYN", "cliente abre a conexão"),
                new PacoteFluxo(2, "ENTRADA", "SYN-ACK", "servidor responde — conexão estabelecida"),
                new PacoteFluxo(3, "TEMPO", "TIMEOUT", "conexão fica ociosa além do tempo limite"),
                new PacoteFluxo(4, "ENTRADA", "DADOS", "resposta atrasada chega depois da expiração")));

        private final String id;
        private final String titulo;
        private final List<PacoteFluxo> pacotes;

        Cenario(String id, String titulo, List<PacoteFluxo> pacotes) {
            this.id = id;
            this.titulo = titulo;
            this.pacotes = pacotes;
        }

        List<PacoteFluxo> pacotes() {
            return pacotes;
        }

        static Cenario porId(String id) {
            for (Cenario c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            throw new SegurancaException("Cenário de firewall desconhecido: '" + id + "'.");
        }
    }
}
