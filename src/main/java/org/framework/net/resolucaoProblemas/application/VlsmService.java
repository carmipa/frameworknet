package org.framework.net.resolucaoProblemas.application;

import inet.ipaddr.ipv4.IPv4Address;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.export.ExportTxtService;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.planning.VlsmPlanningService;
import org.framework.net.resolucaoProblemas.application.routing.VlsmRoutingService;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.GrowthForecast;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.TopologyInsights;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class VlsmService {

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    VlsmNormalizationService normalizationService;

    @Inject
    VlsmPlanningService planningService;

    @Inject
    VlsmRoutingService routingService;

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public NetworkScenarioResult solveNetworkProblem(
            String baseNetworkInput,
            List<LocationInput> locationsInput,
            String topologyType,
            int wanPrefix,
            int eigrpAs,
            String remoteAccess,
            String routingMode,
            int ospfProcess) {

        return telemetriaLogger.medir("resolucaoProblemas", "problem_solve", () ->
                solveNetworkProblemInterno(baseNetworkInput, locationsInput, topologyType, wanPrefix,
                        eigrpAs, remoteAccess, routingMode, ospfProcess));
    }

    private NetworkScenarioResult solveNetworkProblemInterno(
            String baseNetworkInput,
            List<LocationInput> locationsInput,
            String topologyType,
            int wanPrefix,
            int eigrpAs,
            String remoteAccess,
            String routingMode,
            int ospfProcess) {

        telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_solve",
                Map.of("status", "start", "topology", topologyType, "locations", locationsInput.size()));

        IPv4Address baseNetwork = ipv4Kernel.parseNetwork(
                baseNetworkInput == null ? "" : baseNetworkInput.strip(), "Rede base");

        List<LanBlock> locations = normalizationService.normalizeLocationsInput(locationsInput);
        String normalizedTopology = planningService.normalizeTopologyType(
                topologyType == null || topologyType.isBlank() ? "star" : topologyType);
        String normalizedRemoteAccess = normalizationService.normalizeRemoteAccess(remoteAccess);

        validateEigrpAs(eigrpAs);
        validateOspfProcess(ospfProcess);

        VlsmPlanningService.PlanningResult planning = planningService.buildLanBlocks(baseNetwork, locations);
        List<LanBlock> lanBlocks = planning.locations();
        List<IPv4Address> usedSubnets = new ArrayList<>(planning.usedSubnets());

        List<String> locationKeys = lanBlocks.stream().map(LanBlock::getLocationKey).toList();
        List<WanLink> wanLinks = planningService.buildWanLinks(
                baseNetwork, usedSubnets, locationKeys, normalizedTopology, wanPrefix);
        List<LanBlock> cleanedLans = planningService.cleanupLanBlocks(lanBlocks);

        String baseIp = baseNetwork.getLower().withoutPrefixLength().toCanonicalString();
        int primeiroOcteto = ipv4Kernel.parseIpv4Parts(baseIp, "Rede base")[0];
        Ipv4Kernel.ClassificacaoIpv4 classificacao = ipv4Kernel.classificacaoIpv4(primeiroOcteto);

        int totalHostsSupported = cleanedLans.stream().mapToInt(LanBlock::getHostsSupported).sum();
        int totalHostsRequested = lanBlocks.stream().mapToInt(LanBlock::getHostsRequired).sum();
        long totalLanAddresses = cleanedLans.stream().mapToLong(l -> 1L << (32 - l.getPrefix())).sum();
        long totalWanAddresses = wanLinks.stream().mapToLong(l -> 1L << (32 - l.getPrefix())).sum();
        long baseTotalAddresses = ipv4Kernel.addressCount(baseNetwork);
        long totalConsumedAddresses = totalLanAddresses + totalWanAddresses;
        long freeAddresses = Math.max(baseTotalAddresses - totalConsumedAddresses, 0);
        double usedPct = baseTotalAddresses > 0
                ? round2((double) totalConsumedAddresses / baseTotalAddresses * 100.0) : 0.0;
        double freePct = baseTotalAddresses > 0
                ? round2((double) freeAddresses / baseTotalAddresses * 100.0) : 0.0;
        double overallEfficiency = totalHostsSupported > 0
                ? round2((double) totalHostsRequested / totalHostsSupported * 100.0) : 0.0;

        TopologyInsights topologyInsights = buildTopologyInsights(cleanedLans, normalizedTopology);
        List<String> hostsCapacityWarnings = hostsCapacityWarnings(cleanedLans);
        List<GrowthForecast> growthForecast = growthForecast(cleanedLans);
        int suggestedPrefix = suggestedBasePrefix(totalConsumedAddresses, ipv4Kernel.prefixLength(baseNetwork));

        var routingPlan = routingService.buildRoutingPlan(cleanedLans, wanLinks, routingMode);

        NetworkScenarioResult scenario = new NetworkScenarioResult();
        scenario.setBaseNetwork(baseNetwork.toPrefixLengthString());
        scenario.setBaseNetworkIp(baseIp);
        scenario.setBaseNetworkPrefix(ipv4Kernel.prefixLength(baseNetwork));
        scenario.setBaseNetworkMask(ipv4Kernel.netmask(baseNetwork));
        scenario.setBasePrimeiroOcteto(primeiroOcteto);
        scenario.setBaseClasse(classificacao.classe());
        scenario.setBaseFaixaOcteto(classificacao.faixaOcteto());
        scenario.setBaseMascaraPadrao(classificacao.mascaraPadrao());
        scenario.setTotalHostsRequested(totalHostsRequested);
        scenario.setTotalHostsSupported(totalHostsSupported);
        scenario.setOverallEfficiencyPct(overallEfficiency);
        scenario.setBaseTotalAddresses(baseTotalAddresses);
        scenario.setTotalConsumedAddresses(totalConsumedAddresses);
        scenario.setFreeAddresses(freeAddresses);
        scenario.setUsedAddressPct(usedPct);
        scenario.setFreeAddressPct(freePct);
        scenario.setSuggestedBasePrefix(suggestedPrefix);
        scenario.setTotalLocations(cleanedLans.size());
        scenario.setTopologyType(normalizedTopology);
        scenario.setWanPrefix(wanPrefix);
        scenario.setEigrpAs(eigrpAs);
        scenario.setOspfProcess(ospfProcess);
        scenario.setRoutingMode(routingPlan.getRoutingMode());
        scenario.setRoutingPlan(routingPlan);
        scenario.setRemoteAccess(normalizedRemoteAccess);
        scenario.setLanBlocks(cleanedLans);
        scenario.setWanLinks(wanLinks);
        scenario.setTopologyInsights(topologyInsights);
        scenario.setHostsCapacityWarnings(hostsCapacityWarnings);
        scenario.setGrowthForecast(growthForecast);

        scenario.setRouterCommands(exportTxtService.generateRouterLabBlocks(scenario));
        scenario.setPtRouterTables(exportTxtService.buildPtRouterTables(scenario));
        scenario.setRouterCliExplanations(buildCliExplanations(cleanedLans, wanLinks, eigrpAs, ospfProcess, routingPlan));

        String dhcpGatewaysRef = cleanedLans.stream()
                .map(loc -> loc.getLocationName() + ": " + loc.getGateway() + " (" + loc.getNetmask() + ")")
                .collect(Collectors.joining(" | "));

        scenario.setPacketTracerSteps(packetTracerSteps(
                cleanedLans, normalizedTopology, wanPrefix, eigrpAs, ospfProcess,
                normalizedRemoteAccess, routingPlan, dhcpGatewaysRef));
        scenario.setPacketTracerChecklist(packetTracerChecklist(
                eigrpAs, ospfProcess, routingPlan, dhcpGatewaysRef));

        VlsmPlanningService.MermaidResult mermaid = planningService.mermaidTopology(lanBlocks, wanLinks);
        scenario.setTopologyMermaid(mermaid.mermaid());
        scenario.setTopologyDetails(mermaid.details());

        telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_solve",
                Map.of("status", "ok",
                        "baseNetwork", scenario.getBaseNetwork(),
                        "locations", scenario.getTotalLocations(),
                        "wanLinks", wanLinks.size()));
        return scenario;
    }

    private void validateEigrpAs(int eigrpAs) {
        if (eigrpAs < 1 || eigrpAs > 65535) {
            throw new EntradaInvalidaException("AS EIGRP deve estar entre 1 e 65535.");
        }
    }

    private void validateOspfProcess(int ospfProcess) {
        if (ospfProcess < 1 || ospfProcess > 65535) {
            throw new EntradaInvalidaException("Processo OSPF deve estar entre 1 e 65535.");
        }
    }

    /**
     * Explicação, linha a linha, do script de cada roteador.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno entende o que cada comando do script faz.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> os números citados (AS do EIGRP, processo OSPF) são os do cenário,
     * os mesmos do script — o texto dizia "router ospf 1" com o processo 7 escolhido (auditoria CALC-39).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; papel de roteamento ausente cai em EIGRP.</p>
     */
    private Map<String, List<String>> buildCliExplanations(
            List<LanBlock> lanBlocks, List<WanLink> wanLinks, int eigrpAs, int ospfProcess,
            org.framework.net.resolucaoProblemas.domain.model.RoutingPlan routingPlan) {
        Map<String, Integer> linksPorLocal = new LinkedHashMap<>();
        for (WanLink link : wanLinks) {
            for (String endpoint : link.getEndpoints()) {
                linksPorLocal.merge(endpoint, 1, Integer::sum);
            }
        }
        Map<String, String> roles = routingPlan.getRouterRoles() == null ? Map.of() : routingPlan.getRouterRoles();
        Map<String, List<String>> explicacoes = new LinkedHashMap<>();
        for (LanBlock lan : lanBlocks) {
            String key = lan.getLocationKey();
            int countLinks = linksPorLocal.getOrDefault(key, 0);
            String role = roles.getOrDefault(key, "eigrp");
            List<String> routingBits = new ArrayList<>();
            if ("eigrp".equals(role) || "boundary".equals(role)) {
                routingBits.add("router eigrp " + eigrpAs + " + network <rede> <wildcard> (AS " + eigrpAs + ").");
            }
            if ("ospf".equals(role) || "boundary".equals(role)) {
                routingBits.add("router ospf " + ospfProcess + " + network <rede> <wildcard> area 0 (domínio OSPF).");
            }
            if ("boundary".equals(role)) {
                routingBits.add("redistribute: troca rotas entre EIGRP e OSPF neste roteador de fronteira.");
            }
            List<String> lines = new ArrayList<>();
            lines.add("enable / configure terminal: entra no modo privilegiado e configuração global.");
            lines.add("interface GigabitEthernet0/0 + ip address " + lan.getGateway() + " " + lan.getNetmask()
                    + ": define gateway da LAN.");
            lines.add("no shutdown: ativa a interface para sair de estado administrativamente down.");
            lines.add("ip dhcp pool LAN_" + lan.getCliId() + " + network " + lan.getNetwork() + " " + lan.getNetmask()
                    + ": distribui IPs da sub-rede.");
            lines.addAll(routingBits);
            lines.add("Este roteador possui " + countLinks + " link(s) WAN serial no cenário atual.");
            explicacoes.put(lan.getLocationName(), lines);
        }
        return explicacoes;
    }

    private TopologyInsights buildTopologyInsights(List<LanBlock> lans, String selected) {
        int totalLocations = lans.size();
        String selectedNorm = planningService.normalizeTopologyType(selected);
        int starLinks = planningService.wanLinksCount("star", totalLocations);
        int extendedStarLinks = planningService.wanLinksCount("extended_star", totalLocations);
        int meshLinks = planningService.wanLinksCount("mesh", totalLocations);
        int linkUnitCost = 100;
        Map<String, Integer> serialMap = planningService.serialWanCountByLocation(totalLocations, selectedNorm);
        int routersWith3Serial = (int) serialMap.values().stream().filter(n -> n >= 3).count();
        boolean fiapSerialOk = routersWith3Serial >= 2;

        String recommendation;
        String reason;
        if (totalLocations == 4) {
            recommendation = "extended_star";
            reason = "Laboratório FIAP (4 sites): estrela estendida — Matriz como hub principal e "
                    + "Data Center (última linha) agregando filiais; dois roteadores com 3 seriais, "
                    + "padrão WAN corporativo atual.";
        } else if (totalLocations <= 6) {
            recommendation = "star";
            reason = "WAN em estrela (hub = 1ª localidade): padrão hub-and-spoke usado em redes "
                    + "corporativas e operadoras; mínimo de links seriais.";
        } else {
            recommendation = "mesh";
            reason = "Para mais de 6 localidades, malha completa maximiza redundância entre filiais.";
        }

        String selectedNote = selectedNorm.equals(recommendation)
                ? "A topologia escolhida está alinhada com a recomendação."
                : "A topologia escolhida difere da recomendação automática; "
                + "valide custo, redundância e requisitos do enunciado (ex.: 2 roteadores com 3 seriais).";

        String fiapNote = "No cenário atual (" + selectedNorm.toUpperCase(Locale.ROOT) + "), "
                + routersWith3Serial + " roteador(es) com ≥3 interfaces seriais WAN. ";
        fiapNote += fiapSerialOk
                ? "Atende o requisito típico do checkpoint FIAP (dois roteadores com 3 seriais)."
                : "Não atende o requisito de dois roteadores com 3 seriais — "
                + "use ESTRELA ESTENDIDA (4 sites, 1ª=Matriz e última=Data Center) ou MESH.";

        TopologyInsights insights = new TopologyInsights();
        insights.setStarLinks(starLinks);
        insights.setExtendedStarLinks(extendedStarLinks);
        insights.setMeshLinks(meshLinks);
        insights.setStarCost(starLinks * linkUnitCost);
        insights.setExtendedStarCost(extendedStarLinks * linkUnitCost);
        insights.setMeshCost(meshLinks * linkUnitCost);
        insights.setRecommended(recommendation);
        insights.setRecommendedReason(reason);
        insights.setSelectedNote(selectedNote);
        // As chaves internas "loc_1", "loc_2"... saíam no lugar do nome da localidade (CALC-39).
        Map<String, Integer> porNome = new LinkedHashMap<>();
        serialMap.forEach((chave, n) -> {
            int indice = Integer.parseInt(chave.substring(chave.indexOf('_') + 1)) - 1;
            porNome.put(indice >= 0 && indice < lans.size() ? lans.get(indice).getLocationName() : chave, n);
        });
        insights.setSerialWanByLocation(porNome);
        insights.setRoutersWith3PlusSerial(routersWith3Serial);
        insights.setFiapCheckpointSerialOk(fiapSerialOk);
        insights.setFiapCheckpointNote(fiapNote);
        return insights;
    }

    private List<String> hostsCapacityWarnings(List<LanBlock> lanBlocks) {
        List<String> warnings = new ArrayList<>();
        for (LanBlock lan : lanBlocks) {
            int hosts = lan.getHostsRequired();
            if (hosts <= 254) {
                continue;
            }
            int need = planningService.requiredPrefixForHosts(hosts);
            warnings.add(lan.getLocationName() + ": " + hosts + " hosts → prefixo mínimo /" + need
                    + " (uma /24 suporta no máximo 254 hosts úteis).");
        }
        return warnings;
    }

    private List<GrowthForecast> growthForecast(List<LanBlock> lanBlocks) {
        double[] fatores = {1.25, 1.5, 2.0};
        List<GrowthForecast> previsoes = new ArrayList<>();
        for (LanBlock lan : lanBlocks) {
            GrowthForecast item = new GrowthForecast();
            item.setLocationName(lan.getLocationName());
            item.setCurrentPrefix(lan.getPrefix());
            List<GrowthForecast.GrowthScenario> cenarios = new ArrayList<>();
            for (double fator : fatores) {
                int futurosHosts = (int) Math.ceil(lan.getHostsRequired() * fator);
                int needed = futurosHosts + 2;
                int hostBits = 32 - Integer.numberOfLeadingZeros(needed - 1);
                int requiredPrefix = 32 - hostBits;
                GrowthForecast.GrowthScenario c = new GrowthForecast.GrowthScenario();
                c.setFactorLabel((int) ((fator - 1) * 100) + "%");
                c.setFutureHosts(futurosHosts);
                c.setRequiredPrefix(requiredPrefix);
                c.setFitsCurrent(lan.getPrefix() <= requiredPrefix);
                cenarios.add(c);
            }
            item.setScenarios(cenarios);
            previsoes.add(item);
        }
        return previsoes;
    }

    private int suggestedBasePrefix(long totalConsumedAddresses, int currentPrefix) {
        if (totalConsumedAddresses <= 0) {
            return currentPrefix;
        }
        // Long.numberOfLeadingZeros opera em 64 bits: os bits significativos de um
        // valor são 64 - nlz. Usar "32 -" produzia prefixos negativos/absurdos (>32).
        int bitsHost = 64 - Long.numberOfLeadingZeros(totalConsumedAddresses - 1);
        int requiredPrefix = Math.max(0, 32 - bitsHost);
        return Math.max(requiredPrefix, currentPrefix);
    }

    /**
     * Roteiro de montagem do cenário no Packet Tracer, na ordem em que o aluno executa.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> transformar o plano calculado em passos de laboratório (equipamentos,
     * enlaces, comandos, DHCP e acesso remoto), para o aluno não travar em detalhe do simulador.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o passo de acesso remoto cita as mesmas credenciais que o script
     * gerado usa (constantes de {@link ExportTxtService}), nunca uma cópia.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; modo de acesso desconhecido cai no texto do
     * Telnet.</p>
     */
    private List<String> packetTracerSteps(
            List<LanBlock> cleanedLans, String topologyType, int wanPrefix,
            int eigrpAs, int ospfProcess, String remoteAccess,
            org.framework.net.resolucaoProblemas.domain.model.RoutingPlan routingPlan,
            String dhcpLanGatewaysRef) {
        List<String> steps = new ArrayList<>();
        steps.add("Adicionar " + cleanedLans.size() + " roteadores e " + cleanedLans.size()
                + " switches (uma LAN por localidade).");
        String wanStep = "Conectar os roteadores conforme topologia WAN '" + topologyType
                + "' com links seriais /" + wanPrefix + ".";
        if ("extended_star".equals(topologyType)) {
            wanStep += " Estrela estendida: hub na 1ª localidade (ex. Matriz); "
                    + "última linha agrega filiais do meio (ex. Data Center → Filial I/II).";
        }
        steps.add(wanStep);

        String accessNote = switch (remoteAccess) {
            case "ssh" -> " Acesso remoto: SSH (transport input ssh, usuário " + ExportTxtService.LAB_SSH_USER
                    + " / senha " + ExportTxtService.LAB_VTY_PASSWORD + ") — no PT use "
                    + "Desktop → SSH ou um PC com cliente SSH para o IP de Gi0/0 do roteador.";
            case "both" -> " Acesso remoto: SSH e Telnet habilitados nas VTY (usuário " + ExportTxtService.LAB_SSH_USER
                    + " / senha " + ExportTxtService.LAB_VTY_PASSWORD + ").";
            default -> " Acesso remoto: Telnet nas VTY (padrão laboratório anterior).";
        };
        steps.add("Aplicar os comandos CLI gerados em cada roteador, validando interfaces up/up." + accessNote);
        steps.add("No Cisco Packet Tracer, o DHCP integrado no roteador só passa a atender os PCs "
                + "quando a interface LAN (GigabitEthernet0/0) já tiver o IP do gateway e estiver up/up. "
                + "Aplique o script na ordem gerada (LAN antes do bloco ip dhcp pool) ou confira isso "
                + "se «Obter endereço automaticamente» nos PCs falhar. "
                + "Neste cenário, Gi0/0 (gateway LAN) por localidade: " + dhcpLanGatewaysRef + ".");
        steps.add("Em cada LAN, ligar pelo menos 2 PCs ao switch da localidade (prática usual na prova): "
                + "com isso você testa ping entre todas as filiais com mais de uma origem e mais de um "
                + "destino, como o enunciado costuma exigir («todos os equipamentos alcançáveis entre si»).");
        steps.add("Validar pools DHCP nos roteadores (já no script CLI) e IP/gateway automático em cada PC.");

        String mode = routingPlan.getRoutingMode();
        if ("dual_split".equals(mode)) {
            steps.add("Roteamento automático: metade EIGRP (AS " + eigrpAs + ") → "
                    + String.join(", ", routingPlan.getEigrpLocationNames()) + "; "
                    + "metade OSPF (processo " + ospfProcess + ") → "
                    + String.join(", ", routingPlan.getOspfLocationNames()) + ". "
                    + "Fronteira com redistribuição: "
                    + (routingPlan.getBoundaryLocationNames().isEmpty() ? "—"
                    : String.join(", ", routingPlan.getBoundaryLocationNames())) + ".");
            steps.add("Após convergência, testar ping entre PC de site EIGRP e PC de site OSPF; "
                    + "validar rotas redistribuídas com show ip route.");
        } else if ("ospf_only".equals(mode)) {
            steps.add("Roteamento OSPF (processo " + ospfProcess + ", area 0) em todos os roteadores — "
                    + "show ip ospf neighbor.");
        } else {
            steps.add("Roteamento EIGRP (AS " + eigrpAs + ") em todos os roteadores — show ip eigrp neighbors.");
        }
        return steps;
    }

    private List<String> packetTracerChecklist(
            int eigrpAs, int ospfProcess,
            org.framework.net.resolucaoProblemas.domain.model.RoutingPlan routingPlan,
            String dhcpLanGatewaysRef) {
        List<String> checklist = new ArrayList<>();
        checklist.add("Todas as interfaces estão up/up (show ip interface brief).");
        String mode = routingPlan.getRoutingMode();
        if ("eigrp_only".equals(mode) || "dual_split".equals(mode)) {
            checklist.add("EIGRP AS " + eigrpAs + " ativo, sem auto-summary, com network LAN/WAN.");
        }
        if ("ospf_only".equals(mode) || "dual_split".equals(mode)) {
            checklist.add("OSPF processo " + ospfProcess + " (area 0) com adjacências — show ip ospf neighbor.");
        }
        if ("dual_split".equals(mode)) {
            checklist.add("Roteadores de fronteira redistribuem EIGRP↔OSPF; rotas aparecem em show ip route.");
        }
        checklist.add("Em cada localidade há pelo menos 2 PCs na LAN para repetir testes de ping com credibilidade.");
        checklist.add("Ping entre PCs de localidades diferentes funciona em várias combinações (origem↔destino).");
        checklist.add("DHCP entrega IP/gateway correto para os hosts.");
        checklist.add("Packet Tracer: Gi0/0 com IP de gateway e up/up antes de testar DHCP nos PCs "
                + "(sem IP na LAN o roteador não atende DHCP). Gateways: " + dhcpLanGatewaysRef + ".");
        return checklist;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
