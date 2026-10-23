package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo.Salto;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Diagnóstico de alcançabilidade de um fluxo, por cenário.
 *
 * <p><b>Propósito de negócio:</b> mostrar se um pacote sai da origem e chega ao
 * destino, percorrendo o caminho salto a salto e apontando o ponto exato de
 * bloqueio quando não chega — isolamento de VLAN, gateway errado ou ACL/firewall
 * negando. É a v1 (diagnóstico) do editor de cenários; a montagem visual
 * arrastando equipamentos fica para uma etapa posterior.</p>
 *
 * <p><b>Invariantes do domínio:</b> determinístico; um cenário bloqueado tem
 * exatamente um salto {@code ok=false}, que é o {@code pontoBloqueio}, e nenhum
 * salto depois dele. Cenário fora do catálogo é recusado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@code cenarioId} desconhecido lança
 * {@link SegurancaException} (HTTP 400).</p>
 */
@ApplicationScoped
public class DiagnosticoFluxoService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    public List<Map<String, String>> cenariosDisponiveis() {
        List<Map<String, String>> lista = new ArrayList<>();
        for (Cenario c : Cenario.values()) {
            lista.add(Map.of("id", c.id, "titulo", c.titulo));
        }
        return lista;
    }

    public DiagnosticoFluxo diagnosticar(String cenarioId) {
        return telemetriaLogger.medir("seguranca", "diagnostico_fluxo", () -> {
            Cenario cenario = Cenario.porId(cenarioId);
            List<Salto> saltos = cenario.saltos();
            Salto bloqueio = saltos.stream().filter(s -> !s.ok()).findFirst().orElse(null);
            boolean alcanca = bloqueio == null;
            telemetriaLogger.logEvent("info", "seguranca", "diagnostico_fluxo", Map.of(
                    "cenario", cenario.id,
                    "alcanca", alcanca));
            return new DiagnosticoFluxo(cenario.id, cenario.titulo, cenario.origem, cenario.destino,
                    alcanca, bloqueio == null ? "" : bloqueio.no(), cenario.resumo(alcanca), saltos);
        });
    }

    private enum Cenario {
        OK("alcanca", "Fluxo permitido — chega ao destino",
                "10.10.10.5 (VLAN 10)", "10.10.20.5:443 (VLAN 20)"),
        VLAN("vlan-isolada", "VLANs isoladas — sem rota entre elas",
                "10.10.10.5 (VLAN 10)", "10.10.20.5:443 (VLAN 20)"),
        GATEWAY("gateway-errado", "Gateway errado — o pacote nem sai da LAN",
                "10.10.10.5 (gateway inválido)", "10.10.20.5:443 (VLAN 20)"),
        ACL("acl-nega", "Firewall/ACL nega a porta",
                "10.10.10.5 (VLAN 10)", "10.10.20.5:23 (Telnet)");

        private final String id;
        private final String titulo;
        private final String origem;
        private final String destino;

        Cenario(String id, String titulo, String origem, String destino) {
            this.id = id;
            this.titulo = titulo;
            this.origem = origem;
            this.destino = destino;
        }

        String resumo(boolean alcanca) {
            return alcanca
                    ? "O tráfego percorre todo o caminho e a resposta volta pelo firewall com estado."
                    : "O tráfego para no primeiro ponto que o recusa — os saltos seguintes nem são tentados.";
        }

        List<Salto> saltos() {
            return switch (this) {
                case OK -> List.of(
                        new Salto(1, "Host de origem", "L3", true,
                                "Gateway correto na sub-rede; encaminha o pacote para fora da LAN."),
                        new Salto(2, "Switch L3 (inter-VLAN)", "L3", true,
                                "Existe SVI/rota entre VLAN 10 e VLAN 20; roteia o pacote."),
                        new Salto(3, "Firewall", "L4", true,
                                "ACL permite tcp/443; estado da conexão registrado para o retorno."),
                        new Salto(4, "Servidor de destino", "L4", true,
                                "Porta 443 aberta: responde, e a volta passa pelo firewall com estado."));
                case VLAN -> List.of(
                        new Salto(1, "Host de origem", "L3", true,
                                "Gateway correto; envia ao roteador porque o destino está em outra sub-rede."),
                        new Salto(2, "Switch L3 (inter-VLAN)", "L3", false,
                                "VLAN 10 e VLAN 20 sem SVI/rota entre si: o L3 não encaminha e descarta."));
                case GATEWAY -> List.of(
                        new Salto(1, "Host de origem", "L3", false,
                                "O gateway configurado não existe na sub-rede: sem ARP do next-hop, o pacote nem sai da LAN."));
                case ACL -> List.of(
                        new Salto(1, "Host de origem", "L3", true,
                                "Gateway correto; encaminha para fora da LAN."),
                        new Salto(2, "Switch L3 (inter-VLAN)", "L3", true,
                                "Roteia entre as VLANs normalmente."),
                        new Salto(3, "Firewall", "L4", false,
                                "ACL nega tcp/23 (Telnet): deny explícito antes de o pacote chegar ao destino."));
            };
        }

        static Cenario porId(String id) {
            for (Cenario c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            throw new SegurancaException("Cenário de fluxo desconhecido: '" + id + "'.");
        }
    }
}
