package org.framework.net.resolucaoProblemas.domain.model;

public class ResolucaoFormData {

    private String baseNetwork = "";
    private String baseNetworkIp = "";
    private String baseNetworkCidr = "";
    private String topologyType = "star";
    private String wanPrefix = "30";
    private String eigrpAs = "";
    private String ospfProcess = "";
    private String remoteAccess = "telnet";
    private String routingMode = "auto";

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

    public String getBaseNetworkCidr() {
        return baseNetworkCidr;
    }

    public void setBaseNetworkCidr(String baseNetworkCidr) {
        this.baseNetworkCidr = baseNetworkCidr;
    }

    public String getTopologyType() {
        return topologyType;
    }

    public void setTopologyType(String topologyType) {
        this.topologyType = topologyType;
    }

    public String getWanPrefix() {
        return wanPrefix;
    }

    public void setWanPrefix(String wanPrefix) {
        this.wanPrefix = wanPrefix;
    }

    public String getEigrpAs() {
        return eigrpAs;
    }

    public void setEigrpAs(String eigrpAs) {
        this.eigrpAs = eigrpAs;
    }

    public String getOspfProcess() {
        return ospfProcess;
    }

    public void setOspfProcess(String ospfProcess) {
        this.ospfProcess = ospfProcess;
    }

    public String getRemoteAccess() {
        return remoteAccess;
    }

    public void setRemoteAccess(String remoteAccess) {
        this.remoteAccess = remoteAccess;
    }

    public String getRoutingMode() {
        return routingMode;
    }

    public void setRoutingMode(String routingMode) {
        this.routingMode = routingMode;
    }
}
