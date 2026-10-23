package org.framework.net.resolucaoProblemas.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LanBlock {

    private String locationKey;
    private String locationName;
    private int hostsRequired;
    private String routerName;
    private String cliId;
    private int hostsSupported;
    private int hostsNeededTotal;
    private int hostBits;
    private int calculatedPrefix;
    private double efficiencyPct;
    private String network;
    private int prefix;
    private String netmask;
    private String wildcard;
    private String gateway;
    private String hostRangeStart;
    private String hostRangeEnd;
    private Map<String, Object> calculationBreakdown;

    public String getLocationKey() {
        return locationKey;
    }

    public void setLocationKey(String locationKey) {
        this.locationKey = locationKey;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public int getHostsRequired() {
        return hostsRequired;
    }

    public void setHostsRequired(int hostsRequired) {
        this.hostsRequired = hostsRequired;
    }

    public String getRouterName() {
        return routerName;
    }

    public void setRouterName(String routerName) {
        this.routerName = routerName;
    }

    public String getCliId() {
        return cliId;
    }

    public void setCliId(String cliId) {
        this.cliId = cliId;
    }

    public int getHostsSupported() {
        return hostsSupported;
    }

    public void setHostsSupported(int hostsSupported) {
        this.hostsSupported = hostsSupported;
    }

    public int getHostsNeededTotal() {
        return hostsNeededTotal;
    }

    public void setHostsNeededTotal(int hostsNeededTotal) {
        this.hostsNeededTotal = hostsNeededTotal;
    }

    public int getHostBits() {
        return hostBits;
    }

    public void setHostBits(int hostBits) {
        this.hostBits = hostBits;
    }

    public int getCalculatedPrefix() {
        return calculatedPrefix;
    }

    public void setCalculatedPrefix(int calculatedPrefix) {
        this.calculatedPrefix = calculatedPrefix;
    }

    public double getEfficiencyPct() {
        return efficiencyPct;
    }

    public void setEfficiencyPct(double efficiencyPct) {
        this.efficiencyPct = efficiencyPct;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public int getPrefix() {
        return prefix;
    }

    public void setPrefix(int prefix) {
        this.prefix = prefix;
    }

    public String getNetmask() {
        return netmask;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public String getWildcard() {
        return wildcard;
    }

    public void setWildcard(String wildcard) {
        this.wildcard = wildcard;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getHostRangeStart() {
        return hostRangeStart;
    }

    public void setHostRangeStart(String hostRangeStart) {
        this.hostRangeStart = hostRangeStart;
    }

    public String getHostRangeEnd() {
        return hostRangeEnd;
    }

    public void setHostRangeEnd(String hostRangeEnd) {
        this.hostRangeEnd = hostRangeEnd;
    }

    public Map<String, Object> getCalculationBreakdown() {
        return calculationBreakdown;
    }

    public void setCalculationBreakdown(Map<String, Object> calculationBreakdown) {
        this.calculationBreakdown = calculationBreakdown;
    }

    public LanBlock copyWithoutInternal() {
        LanBlock copy = new LanBlock();
        copy.locationKey = locationKey;
        copy.locationName = locationName;
        copy.hostsRequired = hostsRequired;
        copy.routerName = routerName;
        copy.cliId = cliId;
        copy.hostsSupported = hostsSupported;
        copy.hostsNeededTotal = hostsNeededTotal;
        copy.hostBits = hostBits;
        copy.calculatedPrefix = calculatedPrefix;
        copy.efficiencyPct = efficiencyPct;
        copy.network = network;
        copy.prefix = prefix;
        copy.netmask = netmask;
        copy.wildcard = wildcard;
        copy.gateway = gateway;
        copy.hostRangeStart = hostRangeStart;
        copy.hostRangeEnd = hostRangeEnd;
        copy.calculationBreakdown = calculationBreakdown == null ? null : new LinkedHashMap<>(calculationBreakdown);
        return copy;
    }
}
