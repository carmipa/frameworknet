package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;
import java.util.Map;

public class RoutingPlan {

    private String routingMode;
    private String routingModeInput;
    private String routingLabel;
    private List<String> eigrpLocationKeys;
    private List<String> ospfLocationKeys;
    private List<String> eigrpLocationNames;
    private List<String> ospfLocationNames;
    private List<String> boundaryLocationKeys;
    private List<String> boundaryLocationNames;
    private Map<String, String> routerRoles;

    public String getRoutingMode() {
        return routingMode;
    }

    public void setRoutingMode(String routingMode) {
        this.routingMode = routingMode;
    }

    public String getRoutingModeInput() {
        return routingModeInput;
    }

    public void setRoutingModeInput(String routingModeInput) {
        this.routingModeInput = routingModeInput;
    }

    public String getRoutingLabel() {
        return routingLabel;
    }

    public void setRoutingLabel(String routingLabel) {
        this.routingLabel = routingLabel;
    }

    public List<String> getEigrpLocationKeys() {
        return eigrpLocationKeys;
    }

    public void setEigrpLocationKeys(List<String> eigrpLocationKeys) {
        this.eigrpLocationKeys = eigrpLocationKeys;
    }

    public List<String> getOspfLocationKeys() {
        return ospfLocationKeys;
    }

    public void setOspfLocationKeys(List<String> ospfLocationKeys) {
        this.ospfLocationKeys = ospfLocationKeys;
    }

    public List<String> getEigrpLocationNames() {
        return eigrpLocationNames;
    }

    public void setEigrpLocationNames(List<String> eigrpLocationNames) {
        this.eigrpLocationNames = eigrpLocationNames;
    }

    public List<String> getOspfLocationNames() {
        return ospfLocationNames;
    }

    public void setOspfLocationNames(List<String> ospfLocationNames) {
        this.ospfLocationNames = ospfLocationNames;
    }

    public List<String> getBoundaryLocationKeys() {
        return boundaryLocationKeys;
    }

    public void setBoundaryLocationKeys(List<String> boundaryLocationKeys) {
        this.boundaryLocationKeys = boundaryLocationKeys;
    }

    public List<String> getBoundaryLocationNames() {
        return boundaryLocationNames;
    }

    public void setBoundaryLocationNames(List<String> boundaryLocationNames) {
        this.boundaryLocationNames = boundaryLocationNames;
    }

    public Map<String, String> getRouterRoles() {
        return routerRoles;
    }

    public void setRouterRoles(Map<String, String> routerRoles) {
        this.routerRoles = routerRoles;
    }
}
