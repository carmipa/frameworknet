package org.framework.net.resolucaoProblemas.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WanLink {

    private String name;
    private List<String> endpoints;
    private String network;
    private int prefix;
    private String netmask;
    private String wildcard;
    private Map<String, String> ips = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
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

    public Map<String, String> getIps() {
        return ips;
    }

    public void setIps(Map<String, String> ips) {
        this.ips = ips;
    }

    public String getEndpointA() {
        return endpoints != null && !endpoints.isEmpty() ? endpoints.get(0) : "";
    }

    public String getEndpointB() {
        return endpoints != null && endpoints.size() > 1 ? endpoints.get(1) : "";
    }

    public String getIpEndpointA() {
        return ips.getOrDefault(getEndpointA(), "-");
    }

    public String getIpEndpointB() {
        return ips.getOrDefault(getEndpointB(), "-");
    }
}
