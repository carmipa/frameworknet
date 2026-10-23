package org.framework.net.analiseTrafego.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseTrafego.domain.model.AnaliseTrafegoLab;
import org.framework.net.analiseTrafego.domain.model.AnaliseTrafegoLab.RegistroTrafego;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Laboratório didático de padrões DNS/ICMP sobre dados FICTÍCIOS.
 *
 * <p><b>Propósito de negócio:</b> comparar tráfego "normal" e "suspeito" e ensinar
 * quais indicadores levantam a suspeita de tunneling — e por que os mesmos
 * indicadores produzem falso positivo em tráfego legítimo. É análise didática:
 * nenhum pacote real é capturado ou enviado.</p>
 *
 * <p><b>Invariantes do domínio:</b> os dados são SIMULADOS; a classificação é
 * determinística e vem sempre com a evidência (o indicador); cada cenário carrega
 * a nota de falso positivo. Cenário fora do catálogo é recusado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@code cenarioId} desconhecido lança
 * {@link IllegalArgumentException}, que a camada de apresentação converte em HTTP
 * 400 — nunca devolve uma análise "chutada".</p>
 */
@ApplicationScoped
public class LabDnsIcmpService {

    private static final String NORMAL = "NORMAL";
    private static final String SUSPEITO = "SUSPEITO";

    @Inject
    TelemetriaLogger telemetriaLogger;

    public List<Map<String, String>> cenariosDisponiveis() {
        List<Map<String, String>> lista = new ArrayList<>();
        for (Cenario c : Cenario.values()) {
            lista.add(Map.of("id", c.id, "titulo", c.titulo));
        }
        return lista;
    }

    public AnaliseTrafegoLab analisar(String cenarioId) {
        return telemetriaLogger.medir("analiseTrafego", "lab_dns_icmp", () -> {
            Cenario cenario = Cenario.porId(cenarioId);
            AnaliseTrafegoLab lab = new AnaliseTrafegoLab(cenario.id, cenario.titulo,
                    cenario.descricao, cenario.falsoPositivo, cenario.registros());
            telemetriaLogger.logEvent("info", "analiseTrafego", "lab_dns_icmp_analisado", Map.of(
                    "cenario", cenario.id,
                    "registros", lab.registros().size(),
                    "suspeitos", lab.suspeitos()));
            return lab;
        });
    }

    private enum Cenario {
        DNS("dns", "Consultas DNS — normal × tunneling",
                "Cada linha é uma consulta DNS fictícia. Compare o que é rotina com o que tem cara de "
                        + "exfiltração por DNS (rótulos longos de alta entropia, tipos raros e taxa anormal).",
                "Nome longo e alto volume de DNS também aparecem em CDNs, DoH, antivírus e health-checks. "
                        + "Entropia e taxa são PISTAS, não prova — correlacione com o domínio de destino, a "
                        + "reputação e a persistência do padrão antes de concluir."),
        ICMP("icmp", "Pacotes ICMP — normal × tunneling",
                "Cada linha é um pacote ICMP fictício. Compare o ping de rotina com o que sugere tunneling "
                        + "(payload cheio de dados, taxa sustentada e respostas sem pergunta).",
                "Pings grandes e frequentes existem em teste de MTU, monitoração e jogos. Um único indicador "
                        + "(tamanho OU taxa) não fecha diagnóstico — o sinal forte é payload com dados somado a "
                        + "volume sustentado e assimetria entre request e reply.");

        private final String id;
        private final String titulo;
        private final String descricao;
        private final String falsoPositivo;

        Cenario(String id, String titulo, String descricao, String falsoPositivo) {
            this.id = id;
            this.titulo = titulo;
            this.descricao = descricao;
            this.falsoPositivo = falsoPositivo;
        }

        List<RegistroTrafego> registros() {
            return switch (this) {
                case DNS -> List.of(
                        new RegistroTrafego("DNS", "A? www.example.com",
                                "consulta A comum, nome curto", NORMAL,
                                "Resolução típica de um site — nada a assinalar."),
                        new RegistroTrafego("DNS", "AAAA? mail.example.com",
                                "consulta AAAA (IPv6) comum", NORMAL,
                                "Consulta de rotina para um serviço conhecido."),
                        new RegistroTrafego("DNS", "A? cdn.example.com — 300 consultas/min",
                                "alto volume para um mesmo domínio de CDN", NORMAL,
                                "Volume alto, mas para um CDN legítimo: é o caso clássico de FALSO POSITIVO se olhar só a taxa."),
                        new RegistroTrafego("DNS", "TXT? a8f3k2j9q1z7x4b2.dados.tunnel.evil.com",
                                "rótulo longo de alta entropia + registro TXT", SUSPEITO,
                                "Rótulo aleatório e longo em TXT é o padrão de exfiltração por DNS — os dados vão no nome."),
                        new RegistroTrafego("DNS", "NULL? <base32 de 180 chars>.evil.com — 1200 consultas/min",
                                "tipo raro (NULL) + taxa altíssima + payload no nome", SUSPEITO,
                                "Tipo incomum, nome enorme e taxa muito acima do normal: forte indício de canal encoberto."));
                case ICMP -> List.of(
                        new RegistroTrafego("ICMP", "Echo Request 56 bytes (ping padrão)",
                                "tamanho padrão de ping", NORMAL,
                                "Ping comum de diagnóstico — payload previsível, isolado."),
                        new RegistroTrafego("ICMP", "Echo Request 1472 bytes (isolado)",
                                "payload grande, porém pontual", NORMAL,
                                "Teste de MTU usa pacotes grandes de propósito: grande NÃO é suspeito sozinho (FALSO POSITIVO)."),
                        new RegistroTrafego("ICMP", "Time Exceeded (traceroute)",
                                "mensagem de controle de rota", NORMAL,
                                "Parte normal de um traceroute — não carrega dados de aplicação."),
                        new RegistroTrafego("ICMP", "Echo Request 1400 bytes, payload textual repetido — 200/s",
                                "payload cheio de dados + taxa sustentada", SUSPEITO,
                                "Payload com conteúdo e fluxo contínuo em echo é o padrão de tunneling por ICMP."),
                        new RegistroTrafego("ICMP", "Echo Reply com dados, sem Request correspondente",
                                "reply órfão carregando dados", SUSPEITO,
                                "Resposta sem pergunta e com payload sugere canal que usa o reply para trafegar dados."));
            };
        }

        static Cenario porId(String id) {
            for (Cenario c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Cenário de laboratório desconhecido: '" + id + "'.");
        }
    }
}
