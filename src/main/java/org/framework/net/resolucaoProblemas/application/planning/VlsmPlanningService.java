package org.framework.net.resolucaoProblemas.application.planning;

import inet.ipaddr.ipv4.IPv4Address;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.shared.UserInputSanitizer;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class VlsmPlanningService {

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public record PlanningResult(List<LanBlock> locations, List<IPv4Address> usedSubnets) { }

    public record MermaidResult(String mermaid, Map<String, Map<String, Object>> details) { }

    public int requiredPrefixForHosts(int hostCount) {
        return ipv4Kernel.requiredPrefixForHosts(hostCount);
    }

    public String normalizeTopologyType(String topologyType) {
        String value = topologyType == null || topologyType.isBlank() ? "star" : topologyType.strip().toLowerCase(Locale.ROOT);
        Map<String, String> aliases = Map.of(
                "ring", "star",
                "ring_redundant", "extended_star",
                "estrela", "star",
                "estrela_estendida", "extended_star",
                "extended", "extended_star"
        );
        return aliases.getOrDefault(value, value);
    }

    public PlanningResult buildLanBlocks(IPv4Address baseNetwork, List<LanBlock> locations) {
        telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_lan_planning",
                Map.of("status", "start", "locationsCount", locations.size()));
        List<LanBlock> ordered = locations.stream()
                .sorted(Comparator.comparingInt(LanBlock::getHostsRequired).reversed())
                .toList();
        List<IPv4Address> used = new ArrayList<>();

        for (LanBlock location : ordered) {
            int hosts = location.getHostsRequired();
            int needed = hosts + 2;
            int hostBits = 32 - Integer.numberOfLeadingZeros(needed - 1);
            int prefix = requiredPrefixForHosts(hosts);
            if (prefix < ipv4Kernel.prefixLength(baseNetwork)) {
                throw new EntradaInvalidaException(
                        "A LAN " + location.getLocationName() + " exige /" + prefix
                                + ", menor que a rede base /" + ipv4Kernel.prefixLength(baseNetwork) + "."
                );
            }
            IPv4Address subnet = findNextAvailableSubnet(baseNetwork, prefix, used);
            used.add(subnet);
            Ipv4Kernel.HostRange range = ipv4Kernel.hostsRange(subnet);
            int hostsSupported = ipv4Kernel.hostsSupported(subnet);
            location.setHostsSupported(hostsSupported);
            location.setHostsNeededTotal(needed);
            location.setHostBits(hostBits);
            location.setCalculatedPrefix(prefix);
            location.setEfficiencyPct(hostsSupported > 0
                    ? round2((double) location.getHostsRequired() / hostsSupported * 100.0)
                    : 0.0);
            location.setNetwork(subnet.getLower().withoutPrefixLength().toCanonicalString());
            location.setPrefix(ipv4Kernel.prefixLength(subnet));
            location.setNetmask(ipv4Kernel.netmask(subnet));
            location.setWildcard(ipv4Kernel.wildcard(subnet));
            location.setGateway(ipv4Kernel.gateway(subnet));
            location.setHostRangeStart(range.start());
            location.setHostRangeEnd(range.end());

            int powerOf2 = 1 << hostBits;
            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("hosts_requested", hosts);
            breakdown.put("overhead_hosts", 2);
            breakdown.put("total_needed", needed);
            breakdown.put("next_power_of_2", powerOf2);
            breakdown.put("host_bits_required", hostBits);
            breakdown.put("formula_used", "2^" + hostBits + " = " + powerOf2 + " ≥ " + needed + " ✓");
            breakdown.put("prefix_calculation", "32 - " + hostBits + " = /" + prefix);
            breakdown.put("explanation_steps", List.of(
                    "1. Hosts solicitados: " + hosts,
                    "2. Adicionar network + broadcast: " + hosts + " + 2 = " + needed,
                    "3. Próxima potência de 2: 2^" + hostBits + " = " + powerOf2,
                    "4. Bits de host necessários: " + hostBits,
                    "5. Prefix resultante: 32 - " + hostBits + " = /" + prefix,
                    "6. Rede alocada: " + subnet.toCanonicalString(),
                    "7. Hosts disponíveis: " + hostsSupported,
                    "8. Eficiência: " + location.getHostsRequired() + "/" + hostsSupported + " = " + location.getEfficiencyPct() + "%"
            ));
            location.setCalculationBreakdown(breakdown);
        }
        telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_lan_planning",
                Map.of("status", "end", "usedSubnets", used.size()));
        return new PlanningResult(locations, used);
    }

    public List<WanLink> buildWanLinks(
            IPv4Address baseNetwork,
            List<IPv4Address> usedSubnets,
            List<String> locationKeys,
            String topologyType,
            int wanPrefix) {
        if (wanPrefix < 0 || wanPrefix > 30) {
            throw new EntradaInvalidaException("Prefixo WAN invalido. Informe um inteiro entre 0 e 30.");
        }
        List<List<String>> wanPairs = buildWanPairs(locationKeys, topologyType);
        List<WanLink> links = new ArrayList<>();
        int index = 1;
        for (List<String> pair : wanPairs) {
            IPv4Address subnet = findNextAvailableSubnet(baseNetwork, wanPrefix, usedSubnets);
            usedSubnets.add(subnet);
            List<IPv4Address> hosts = ipv4Kernel.primeirosHostsUteis(subnet, 2);
            if (hosts.size() < 2) {
                throw new EntradaInvalidaException(
                        "A sub-rede WAN /" + wanPrefix + " nao oferece dois IPs utilizaveis "
                                + "para o link " + pair.get(0) + " <-> " + pair.get(1) + "."
                );
            }
            WanLink link = new WanLink();
            link.setName("WAN-" + index);
            link.setEndpoints(List.of(pair.get(0), pair.get(1)));
            link.setNetwork(subnet.getLower().withoutPrefixLength().toCanonicalString());
            link.setPrefix(ipv4Kernel.prefixLength(subnet));
            link.setNetmask(ipv4Kernel.netmask(subnet));
            link.setWildcard(ipv4Kernel.wildcard(subnet));
            Map<String, String> ips = new LinkedHashMap<>();
            ips.put(pair.get(0), hosts.get(0).toCanonicalString());
            ips.put(pair.get(1), hosts.get(1).toCanonicalString());
            link.setIps(ips);
            links.add(link);
            index++;
        }
        return links;
    }

    public List<LanBlock> cleanupLanBlocks(List<LanBlock> locations) {
        return locations.stream().map(LanBlock::copyWithoutInternal).toList();
    }

    public MermaidResult mermaidTopology(List<LanBlock> locations, List<WanLink> wanLinks) {
        List<String> lines = new ArrayList<>();
        lines.add("graph LR");
        Map<String, String> nameMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> detailsMap = new LinkedHashMap<>();

        int index = 1;
        for (LanBlock location : locations) {
            String nodeId = "R_" + index;
            String swId = "SW_" + index;
            String pcA = "PC_" + index + "A";
            String pcB = "PC_" + index + "B";
            nameMap.put(location.getLocationKey(), nodeId);
            String locLabel = mermaidEscape(location.getLocationName());
            if (locLabel.isEmpty()) {
                locLabel = "Local " + index;
            }
            String gw = location.getGateway();
            String[] pcIps = suggestedPcIpsForDiagram(location);
            lines.add("    subgraph LAN_" + index + "[\"" + locLabel + "\"]");
            lines.add("        " + nodeId + "[\"" + location.getRouterName() + "\\nGi0/0: " + gw + "\\n"
                    + location.getNetwork() + "/" + location.getPrefix() + "\"]");
            lines.add("        " + swId + "[\"Switch\\n" + location.getNetwork() + "/" + location.getPrefix()
                    + "\\n(camada 2)\"]");
            lines.add("        " + pcA + "[\"PC teste 1\\n" + pcIps[0] + "\\nDHCP\"]");
            lines.add("        " + pcB + "[\"PC teste 2\\n" + pcIps[1] + "\\nDHCP\"]");
            lines.add("        " + nodeId + " --- " + swId);
            lines.add("        " + swId + " --- " + pcA);
            lines.add("        " + swId + " --- " + pcB);
            lines.add("    end");
            lines.add("    click " + nodeId + " showTopologyDetail \"" + nodeId + "\"");
            lines.add("    click " + swId + " showTopologyDetail \"" + swId + "\"");
            lines.add("    click " + pcA + " showTopologyDetail \"" + pcA + "\"");
            lines.add("    click " + pcB + " showTopologyDetail \"" + pcB + "\"");

            detailsMap.put(nodeId, detail("router", location.getRouterName(),
                    location.getNetwork() + "/" + location.getPrefix(), location));
            detailsMap.put(swId, switchDetail(location));
            detailsMap.put(pcA, hostDetail("PC teste 1 · " + location.getLocationName(), location, pcIps[0]));
            detailsMap.put(pcB, hostDetail("PC teste 2 · " + location.getLocationName(), location, pcIps[1]));
            index++;
        }

        index = 1;
        for (WanLink link : wanLinks) {
            String left = nameMap.get(link.getEndpoints().get(0));
            String right = nameMap.get(link.getEndpoints().get(1));
            String wanId = "W_" + index;
            String ep0 = link.getEndpoints().get(0);
            String ep1 = link.getEndpoints().get(1);
            String ipWA = link.getIps().get(ep0);
            String ipWB = link.getIps().get(ep1);
            lines.add("    " + wanId + "{\"" + link.getName() + "\\n" + link.getNetwork() + "/" + link.getPrefix()
                    + "\\n" + ipWA + " · " + ipWB + "\"}");
            lines.add("    " + left + " --- " + wanId);
            lines.add("    " + wanId + " --- " + right);
            lines.add("    click " + wanId + " showTopologyDetail \"" + wanId + "\"");

            Map<String, Object> wanDetail = new LinkedHashMap<>();
            wanDetail.put("type", "wan");
            wanDetail.put("title", safeTitle(link.getName()));
            wanDetail.put("network", link.getNetwork() + "/" + link.getPrefix());
            wanDetail.put("mask", link.getNetmask());
            wanDetail.put("wildcard", link.getWildcard());
            wanDetail.put("endpoint_a", ep0 + " - " + ipWA);
            wanDetail.put("endpoint_b", ep1 + " - " + ipWB);
            detailsMap.put(wanId, wanDetail);
            index++;
        }
        return new MermaidResult(String.join("\n", lines), detailsMap);
    }

    public int wanLinksCount(String topology, int totalLocations) {
        topology = normalizeTopologyType(topology);
        if (totalLocations <= 1) {
            return 0;
        }
        return switch (topology) {
            case "star" -> totalLocations - 1;
            case "extended_star" -> totalLocations <= 2 ? totalLocations - 1 : (totalLocations - 1) + (totalLocations - 2);
            case "mesh" -> (totalLocations * (totalLocations - 1)) / 2;
            default -> 0;
        };
    }

    public Map<String, Integer> serialWanCountByLocation(int totalLocations, String topology) {
        topology = normalizeTopologyType(topology);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 1; i <= totalLocations; i++) {
            counts.put("loc_" + i, 0);
        }
        if (totalLocations <= 1) {
            return counts;
        }
        if ("star".equals(topology)) {
            for (int i = 1; i < totalLocations; i++) {
                bump(counts, 0, i);
            }
            return counts;
        }
        if ("extended_star".equals(topology)) {
            for (int i = 1; i < totalLocations; i++) {
                bump(counts, 0, i);
            }
            int secondary = totalLocations - 1;
            for (int i = 1; i < secondary; i++) {
                bump(counts, secondary, i);
            }
            return counts;
        }
        if ("mesh".equals(topology)) {
            for (int left = 0; left < totalLocations; left++) {
                for (int right = left + 1; right < totalLocations; right++) {
                    bump(counts, left, right);
                }
            }
        }
        return counts;
    }

    private void bump(Map<String, Integer> counts, int a, int b) {
        counts.merge("loc_" + (a + 1), 1, Integer::sum);
        counts.merge("loc_" + (b + 1), 1, Integer::sum);
    }

    /**
     * PROPÓSITO DE NEGÓCIO: first-fit do VLSM — o primeiro bloco /prefix alinhado, em ordem de
     * endereço dentro da base, que não se sobrepõe a nenhum bloco já alocado.
     *
     * INVARIANTES DO DOMÍNIO: resultado idêntico ao first-fit ingênuo (testar candidato a candidato
     * contra todos os usados — travado por teste com oráculo). Quando o candidato colide com um bloco
     * usado, todo candidato até o fim daquele bloco também colide; por isso salta direto para o
     * próximo endereço alinhado após ele. Custo O(k·log n) por alocação em vez de O(candidatos·n): o
     * ingênuo levava ~10 s para 60 localidades em malha e horas para a importação de turma.
     *
     * COMPORTAMENTO EM CASO DE FALHA: prefixo mais amplo que a base ou base sem espaço lançam
     * {@link EntradaInvalidaException}.
     */
    private IPv4Address findNextAvailableSubnet(
            IPv4Address baseNetwork, int prefix, List<IPv4Address> usedSubnets) {
        IPv4Address base = baseNetwork.toPrefixBlock();
        int basePrefix = ipv4Kernel.prefixLength(base);
        if (prefix < basePrefix) {
            throw new EntradaInvalidaException(
                    "Prefixo /" + prefix + " é mais amplo que a rede base /" + basePrefix + ".");
        }
        java.util.TreeMap<Long, Long> ocupados = new java.util.TreeMap<>();
        for (IPv4Address used : usedSubnets) {
            ocupados.put(used.getLower().longValue(), used.getUpper().longValue());
        }
        long fimBase = base.getUpper().longValue();
        long tamanho = 1L << (32 - prefix);
        long inicio = base.getLower().longValue();
        while (inicio + tamanho - 1 <= fimBase) {
            long fim = inicio + tamanho - 1;
            Map.Entry<Long, Long> anterior = ocupados.floorEntry(fim);
            if (anterior != null && anterior.getValue() >= inicio) {
                long depois = anterior.getValue() + 1;
                inicio = ((depois + tamanho - 1) / tamanho) * tamanho;
                continue;
            }
            return new IPv4Address((int) inicio, prefix).toPrefixBlock();
        }
        throw new EntradaInvalidaException(
                "Não há espaço suficiente na rede base para acomodar todas as LANs e links WAN."
        );
    }

    private List<List<String>> buildWanPairs(List<String> locationKeys, String topologyType) {
        int total = locationKeys.size();
        if (total <= 1) {
            return List.of();
        }
        topologyType = normalizeTopologyType(topologyType);
        return switch (topologyType) {
            case "star" -> starWanPairs(locationKeys);
            case "extended_star" -> extendedStarWanPairs(locationKeys);
            case "mesh" -> meshWanPairs(locationKeys);
            default -> throw new EntradaInvalidaException(
                    "Topologia WAN inválida. Use 'star', 'extended_star' ou 'mesh'."
            );
        };
    }

    private List<List<String>> starWanPairs(List<String> locationKeys) {
        String hub = locationKeys.get(0);
        List<List<String>> pairs = new ArrayList<>();
        for (int i = 1; i < locationKeys.size(); i++) {
            pairs.add(List.of(hub, locationKeys.get(i)));
        }
        return pairs;
    }

    private List<List<String>> extendedStarWanPairs(List<String> locationKeys) {
        int total = locationKeys.size();
        if (total <= 2) {
            return starWanPairs(locationKeys);
        }
        List<List<String>> pairs = new ArrayList<>(starWanPairs(locationKeys));
        String secondaryHub = locationKeys.get(total - 1);
        for (int index = 1; index < total - 1; index++) {
            pairs.add(List.of(secondaryHub, locationKeys.get(index)));
        }
        return pairs;
    }

    private List<List<String>> meshWanPairs(List<String> locationKeys) {
        List<List<String>> pairs = new ArrayList<>();
        for (int left = 0; left < locationKeys.size(); left++) {
            for (int right = left + 1; right < locationKeys.size(); right++) {
                pairs.add(List.of(locationKeys.get(left), locationKeys.get(right)));
            }
        }
        return pairs;
    }

    /**
     * Os dois PCs de teste do diagrama: os primeiros endereços que o DHCP do roteador ENTREGA.
     *
     * <p>Antes eram os dois primeiros hosts depois do gateway, que caíam dentro da faixa que o próprio
     * script exclui do DHCP — o diagrama dizia "PC teste 1 .2 DHCP" para um endereço que o DHCP nunca
     * dá (CALC-16). A reserva vem de {@link Ipv4Kernel#reservaDhcpAposGateway}, a mesma do script.</p>
     */
    private String[] suggestedPcIpsForDiagram(LanBlock location) {
        IPv4Address net = ipv4Kernel.parseNetwork(
                location.getNetwork() + "/" + location.getPrefix(), "LAN");
        IPv4Address gw = new inet.ipaddr.IPAddressString(location.getGateway()).getAddress().toIPv4();
        int reservados = ipv4Kernel.reservaDhcpAposGateway(location.getHostsSupported(), location.getHostsRequired());
        List<IPv4Address> entregues = new ArrayList<>();
        // gateway + reservados + 2 hosts: bastam para achar os dois primeiros entregues.
        for (IPv4Address host : ipv4Kernel.primeirosHostsUteis(net, reservados + 3)) {
            if (host.compareTo(gw) > 0 && host.compareTo(gw.increment(reservados).toIPv4()) > 0) {
                entregues.add(host);
            }
        }
        String ipA = entregues.isEmpty() ? gw.toCanonicalString() : entregues.get(0).toCanonicalString();
        String ipB = entregues.size() > 1 ? entregues.get(1).toCanonicalString() : ipA;
        return new String[]{ipA, ipB};
    }

    private String mermaidEscape(String text) {
        if (text == null) {
            return "";
        }
        return UserInputSanitizer.sanitizeLabel(text).replace("\"", "'").replace("\n", " ").strip();
    }

    private String safeTitle(String title) {
        return UserInputSanitizer.sanitizeLabel(title);
    }

    private Map<String, Object> detail(String type, String title, String network, LanBlock location) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("title", safeTitle(title));
        m.put("network", network);
        m.put("gateway", location.getGateway());
        m.put("host_range", location.getHostRangeStart() + " - " + location.getHostRangeEnd());
        m.put("mask", location.getNetmask());
        m.put("wildcard", location.getWildcard());
        m.put("hosts_required", location.getHostsRequired());
        m.put("hosts_supported", location.getHostsSupported());
        return m;
    }

    private Map<String, Object> switchDetail(LanBlock location) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "switch");
        m.put("title", safeTitle("Switch · " + location.getLocationName()));
        m.put("network", location.getNetwork() + "/" + location.getPrefix());
        m.put("gateway", location.getGateway());
        m.put("note_l2", "Equipamento de camada 2 no diagrama (sem IP de gestão no cenário clássico).");
        return m;
    }

    private Map<String, Object> hostDetail(String title, LanBlock location, String suggestedIp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "host");
        m.put("title", safeTitle(title));
        m.put("gateway", location.getGateway());
        m.put("network", location.getNetwork() + "/" + location.getPrefix());
        m.put("suggested_ip", suggestedIp);
        return m;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
