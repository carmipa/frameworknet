package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;
import java.util.Map;

public class NetworkScenarioResult {

    private String baseNetwork;
    private String baseNetworkIp;
    private int baseNetworkPrefix;
    private String baseNetworkMask;
    private int basePrimeiroOcteto;
    private String baseClasse;
    private String baseFaixaOcteto;
    private String baseMascaraPadrao;
    private int totalHostsRequested;
    private int totalHostsSupported;
    private double overallEfficiencyPct;
    private long baseTotalAddresses;
    private long totalConsumedAddresses;
    private long freeAddresses;
    private double usedAddressPct;
    private double freeAddressPct;
    private int suggestedBasePrefix;
    private int totalLocations;
    private String topologyType;
    private int wanPrefix;
    private int eigrpAs;
    private int ospfProcess;
    private String routingMode;
    private RoutingPlan routingPlan;
    private String remoteAccess;
    private List<LanBlock> lanBlocks;
    private List<WanLink> wanLinks;
    private TopologyInsights topologyInsights;
    private List<String> hostsCapacityWarnings;
    private List<GrowthForecast> growthForecast;
    private Map<String, String> routerCommands;
    private List<PtRouterTable> ptRouterTables;
    private Map<String, List<String>> routerCliExplanations;
    private List<String> packetTracerSteps;
    private List<String> packetTracerChecklist;
    private String topologyMermaid;
    private Map<String, Map<String, Object>> topologyDetails;

    public String getBaseNetwork() {
        return baseNetwork;
    }

    public void setBaseNetwork(String baseNetwork) {
        this.baseNetwork = baseNetwork;
    }

    public String getBaseNetworkIp() {
        return baseNetworkIp;
    }

    public void setBaseNetworkIp(String baseNetworkIp) {
        this.baseNetworkIp = baseNetworkIp;
    }

    public int getBaseNetworkPrefix() {
        return baseNetworkPrefix;
    }

    public void setBaseNetworkPrefix(int baseNetworkPrefix) {
        this.baseNetworkPrefix = baseNetworkPrefix;
    }

    public String getBaseNetworkMask() {
        return baseNetworkMask;
    }

    public void setBaseNetworkMask(String baseNetworkMask) {
        this.baseNetworkMask = baseNetworkMask;
    }

    public int getBasePrimeiroOcteto() {
        return basePrimeiroOcteto;
    }

    public void setBasePrimeiroOcteto(int basePrimeiroOcteto) {
        this.basePrimeiroOcteto = basePrimeiroOcteto;
    }

    public String getBaseClasse() {
        return baseClasse;
    }

    public void setBaseClasse(String baseClasse) {
        this.baseClasse = baseClasse;
    }

    public String getBaseFaixaOcteto() {
        return baseFaixaOcteto;
    }

    public void setBaseFaixaOcteto(String baseFaixaOcteto) {
        this.baseFaixaOcteto = baseFaixaOcteto;
    }

    public String getBaseMascaraPadrao() {
        return baseMascaraPadrao;
    }

    public void setBaseMascaraPadrao(String baseMascaraPadrao) {
        this.baseMascaraPadrao = baseMascaraPadrao;
    }

    public int getTotalHostsRequested() {
        return totalHostsRequested;
    }

    public void setTotalHostsRequested(int totalHostsRequested) {
        this.totalHostsRequested = totalHostsRequested;
    }

    public int getTotalHostsSupported() {
        return totalHostsSupported;
    }

    public void setTotalHostsSupported(int totalHostsSupported) {
        this.totalHostsSupported = totalHostsSupported;
    }

    public double getOverallEfficiencyPct() {
        return overallEfficiencyPct;
    }

    public void setOverallEfficiencyPct(double overallEfficiencyPct) {
        this.overallEfficiencyPct = overallEfficiencyPct;
    }

    public long getBaseTotalAddresses() {
        return baseTotalAddresses;
    }

    public void setBaseTotalAddresses(long baseTotalAddresses) {
        this.baseTotalAddresses = baseTotalAddresses;
    }

    public long getTotalConsumedAddresses() {
        return totalConsumedAddresses;
    }

    public void setTotalConsumedAddresses(long totalConsumedAddresses) {
        this.totalConsumedAddresses = totalConsumedAddresses;
    }

    public long getFreeAddresses() {
        return freeAddresses;
    }

    public void setFreeAddresses(long freeAddresses) {
        this.freeAddresses = freeAddresses;
    }

    public double getUsedAddressPct() {
        return usedAddressPct;
    }

    public void setUsedAddressPct(double usedAddressPct) {
        this.usedAddressPct = usedAddressPct;
    }

    public double getFreeAddressPct() {
        return freeAddressPct;
    }

    public void setFreeAddressPct(double freeAddressPct) {
        this.freeAddressPct = freeAddressPct;
    }

    public int getSuggestedBasePrefix() {
        return suggestedBasePrefix;
    }

    public void setSuggestedBasePrefix(int suggestedBasePrefix) {
        this.suggestedBasePrefix = suggestedBasePrefix;
    }

    public int getTotalLocations() {
        return totalLocations;
    }

    public void setTotalLocations(int totalLocations) {
        this.totalLocations = totalLocations;
    }

    public String getTopologyType() {
        return topologyType;
    }

    public void setTopologyType(String topologyType) {
        this.topologyType = topologyType;
    }

    public int getWanPrefix() {
        return wanPrefix;
    }

    public void setWanPrefix(int wanPrefix) {
        this.wanPrefix = wanPrefix;
    }

    public int getEigrpAs() {
        return eigrpAs;
    }

    public void setEigrpAs(int eigrpAs) {
        this.eigrpAs = eigrpAs;
    }

    public int getOspfProcess() {
        return ospfProcess;
    }

    public void setOspfProcess(int ospfProcess) {
        this.ospfProcess = ospfProcess;
    }

    public String getRoutingMode() {
        return routingMode;
    }

    public void setRoutingMode(String routingMode) {
        this.routingMode = routingMode;
    }

    public RoutingPlan getRoutingPlan() {
        return routingPlan;
    }

    public void setRoutingPlan(RoutingPlan routingPlan) {
        this.routingPlan = routingPlan;
    }

    public String getRemoteAccess() {
        return remoteAccess;
    }

    public void setRemoteAccess(String remoteAccess) {
        this.remoteAccess = remoteAccess;
    }

    public List<LanBlock> getLanBlocks() {
        return lanBlocks;
    }

    public void setLanBlocks(List<LanBlock> lanBlocks) {
        this.lanBlocks = lanBlocks;
    }

    public List<WanLink> getWanLinks() {
        return wanLinks;
    }

    public void setWanLinks(List<WanLink> wanLinks) {
        this.wanLinks = wanLinks;
    }

    public TopologyInsights getTopologyInsights() {
        return topologyInsights;
    }

    public void setTopologyInsights(TopologyInsights topologyInsights) {
        this.topologyInsights = topologyInsights;
    }

    public List<String> getHostsCapacityWarnings() {
        return hostsCapacityWarnings;
    }

    public void setHostsCapacityWarnings(List<String> hostsCapacityWarnings) {
        this.hostsCapacityWarnings = hostsCapacityWarnings;
    }

    public List<GrowthForecast> getGrowthForecast() {
        return growthForecast;
    }

    public void setGrowthForecast(List<GrowthForecast> growthForecast) {
        this.growthForecast = growthForecast;
    }

    public Map<String, String> getRouterCommands() {
        return routerCommands;
    }

    public void setRouterCommands(Map<String, String> routerCommands) {
        this.routerCommands = routerCommands;
    }

    public List<PtRouterTable> getPtRouterTables() {
        return ptRouterTables;
    }

    public void setPtRouterTables(List<PtRouterTable> ptRouterTables) {
        this.ptRouterTables = ptRouterTables;
    }

    public Map<String, List<String>> getRouterCliExplanations() {
        return routerCliExplanations;
    }

    public void setRouterCliExplanations(Map<String, List<String>> routerCliExplanations) {
        this.routerCliExplanations = routerCliExplanations;
    }

    public List<String> getPacketTracerSteps() {
        return packetTracerSteps;
    }

    public void setPacketTracerSteps(List<String> packetTracerSteps) {
        this.packetTracerSteps = packetTracerSteps;
    }

    public List<String> getPacketTracerChecklist() {
        return packetTracerChecklist;
    }

    public void setPacketTracerChecklist(List<String> packetTracerChecklist) {
        this.packetTracerChecklist = packetTracerChecklist;
    }

    public String getTopologyMermaid() {
        return topologyMermaid;
    }

    public void setTopologyMermaid(String topologyMermaid) {
        this.topologyMermaid = topologyMermaid;
    }

    public Map<String, Map<String, Object>> getTopologyDetails() {
        return topologyDetails;
    }

    public void setTopologyDetails(Map<String, Map<String, Object>> topologyDetails) {
        this.topologyDetails = topologyDetails;
    }
}
