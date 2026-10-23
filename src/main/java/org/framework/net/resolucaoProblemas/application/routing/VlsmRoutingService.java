package org.framework.net.resolucaoProblemas.application.routing;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.RoutingPlan;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class VlsmRoutingService {

    public String normalizeRoutingMode(String value, int totalLocations) {
        String mode = value == null || value.isBlank() ? "auto" : value.strip().toLowerCase(Locale.ROOT);
        if (Set.of("eigrp", "eigrp_only", "somente_eigrp").contains(mode)) {
            return "eigrp_only";
        }
        if (Set.of("ospf", "ospf_only", "somente_ospf").contains(mode)) {
            return "ospf_only";
        }
        if (Set.of("dual", "dual_split", "metade", "eigrp_ospf").contains(mode)) {
            return "dual_split";
        }
        if ("auto".equals(mode)) {
            return totalLocations >= 4 ? "dual_split" : "eigrp_only";
        }
        if (Set.of("eigrp_only", "ospf_only", "dual_split").contains(mode)) {
            return mode;
        }
        return totalLocations >= 4 ? "dual_split" : "eigrp_only";
    }

    public RoutingPlan buildRoutingPlan(List<LanBlock> lanBlocks, List<WanLink> wanLinks, String routingMode) {
        List<String> keys = sortedLocationKeys(lanBlocks);
        int total = keys.size();
        String mode = normalizeRoutingMode(routingMode, total);
        Map<String, String> nameByKey = lanBlocks.stream()
                .collect(Collectors.toMap(LanBlock::getLocationKey, LanBlock::getLocationName, (a, b) -> a, LinkedHashMap::new));

        Set<String> eigrpKeys;
        Set<String> ospfKeys;
        if ("eigrp_only".equals(mode)) {
            eigrpKeys = new HashSet<>(keys);
            ospfKeys = Set.of();
        } else if ("ospf_only".equals(mode)) {
            eigrpKeys = Set.of();
            ospfKeys = new HashSet<>(keys);
        } else {
            int splitCount = (int) Math.ceil(total / 2.0);
            eigrpKeys = new HashSet<>(keys.subList(0, splitCount));
            ospfKeys = new HashSet<>(keys.subList(splitCount, total));
        }

        Map<String, Set<String>> neighbors = wanNeighborMap(wanLinks);
        Map<String, String> roles = new LinkedHashMap<>();
        Set<String> boundaryKeys = new HashSet<>();

        for (String key : keys) {
            Set<String> nei = neighbors.getOrDefault(key, Set.of());
            boolean inE = eigrpKeys.contains(key);
            boolean inO = ospfKeys.contains(key);
            if (eigrpKeys.isEmpty() || ospfKeys.isEmpty()) {
                roles.put(key, inE ? "eigrp" : "ospf");
                continue;
            }
            boolean crosses = (inE && nei.stream().anyMatch(ospfKeys::contains))
                    || (inO && nei.stream().anyMatch(eigrpKeys::contains));
            if (crosses) {
                roles.put(key, "boundary");
                boundaryKeys.add(key);
            } else if (inE) {
                roles.put(key, "eigrp");
            } else {
                roles.put(key, "ospf");
            }
        }

        List<String> eigrpNames = keys.stream().filter(eigrpKeys::contains).map(nameByKey::get).toList();
        List<String> ospfNames = keys.stream().filter(ospfKeys::contains).map(nameByKey::get).toList();
        List<String> boundaryNames = keys.stream().filter(boundaryKeys::contains).map(nameByKey::get).toList();

        String label;
        if ("eigrp_only".equals(mode)) {
            label = "Somente EIGRP em todos os roteadores";
        } else if ("ospf_only".equals(mode)) {
            label = "Somente OSPF em todos os roteadores";
        } else {
            label = "Metade EIGRP (" + eigrpNames.size() + " sites) + metade OSPF (" + ospfNames.size() + " sites)";
        }

        RoutingPlan plan = new RoutingPlan();
        plan.setRoutingMode(mode);
        plan.setRoutingModeInput(routingMode);
        plan.setRoutingLabel(label);
        plan.setEigrpLocationKeys(sortKeys(eigrpKeys));
        plan.setOspfLocationKeys(sortKeys(ospfKeys));
        plan.setEigrpLocationNames(eigrpNames);
        plan.setOspfLocationNames(ospfNames);
        plan.setBoundaryLocationKeys(sortKeys(boundaryKeys));
        plan.setBoundaryLocationNames(boundaryNames);
        plan.setRouterRoles(roles);
        return plan;
    }

    private List<String> sortedLocationKeys(List<LanBlock> lanBlocks) {
        return lanBlocks.stream()
                .map(LanBlock::getLocationKey)
                .sorted(Comparator.comparingInt(key -> Integer.parseInt(key.split("_", 2)[1])))
                .toList();
    }

    private List<String> sortKeys(Set<String> keys) {
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingInt(key -> Integer.parseInt(key.split("_")[1])));
        return sorted;
    }

    private Map<String, Set<String>> wanNeighborMap(List<WanLink> wanLinks) {
        Map<String, Set<String>> neighbors = new HashMap<>();
        if (wanLinks == null) {
            return neighbors;
        }
        for (WanLink link : wanLinks) {
            List<String> endpoints = link.getEndpoints();
            if (endpoints == null || endpoints.size() != 2) {
                continue;
            }
            String a = endpoints.get(0);
            String b = endpoints.get(1);
            neighbors.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            neighbors.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }
        return neighbors;
    }
}
