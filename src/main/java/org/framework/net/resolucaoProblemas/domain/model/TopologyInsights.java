package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;
import java.util.Map;

public class TopologyInsights {

    private int starLinks;
    private int extendedStarLinks;
    private int meshLinks;
    private int starCost;
    private int extendedStarCost;
    private int meshCost;
    private String recommended;
    private String recommendedReason;
    private String selectedNote;
    private Map<String, Integer> serialWanByLocation;
    private int routersWith3PlusSerial;
    private boolean fiapCheckpointSerialOk;
    private String fiapCheckpointNote;

    public int getStarLinks() {
        return starLinks;
    }

    public void setStarLinks(int starLinks) {
        this.starLinks = starLinks;
    }

    public int getExtendedStarLinks() {
        return extendedStarLinks;
    }

    public void setExtendedStarLinks(int extendedStarLinks) {
        this.extendedStarLinks = extendedStarLinks;
    }

    public int getMeshLinks() {
        return meshLinks;
    }

    public void setMeshLinks(int meshLinks) {
        this.meshLinks = meshLinks;
    }

    public int getStarCost() {
        return starCost;
    }

    public void setStarCost(int starCost) {
        this.starCost = starCost;
    }

    public int getExtendedStarCost() {
        return extendedStarCost;
    }

    public void setExtendedStarCost(int extendedStarCost) {
        this.extendedStarCost = extendedStarCost;
    }

    public int getMeshCost() {
        return meshCost;
    }

    public void setMeshCost(int meshCost) {
        this.meshCost = meshCost;
    }

    public String getRecommended() {
        return recommended;
    }

    public void setRecommended(String recommended) {
        this.recommended = recommended;
    }

    public String getRecommendedReason() {
        return recommendedReason;
    }

    public void setRecommendedReason(String recommendedReason) {
        this.recommendedReason = recommendedReason;
    }

    public String getSelectedNote() {
        return selectedNote;
    }

    public void setSelectedNote(String selectedNote) {
        this.selectedNote = selectedNote;
    }

    public Map<String, Integer> getSerialWanByLocation() {
        return serialWanByLocation;
    }

    public void setSerialWanByLocation(Map<String, Integer> serialWanByLocation) {
        this.serialWanByLocation = serialWanByLocation;
    }

    public int getRoutersWith3PlusSerial() {
        return routersWith3PlusSerial;
    }

    public void setRoutersWith3PlusSerial(int routersWith3PlusSerial) {
        this.routersWith3PlusSerial = routersWith3PlusSerial;
    }

    public boolean isFiapCheckpointSerialOk() {
        return fiapCheckpointSerialOk;
    }

    public void setFiapCheckpointSerialOk(boolean fiapCheckpointSerialOk) {
        this.fiapCheckpointSerialOk = fiapCheckpointSerialOk;
    }

    public String getFiapCheckpointNote() {
        return fiapCheckpointNote;
    }

    public void setFiapCheckpointNote(String fiapCheckpointNote) {
        this.fiapCheckpointNote = fiapCheckpointNote;
    }
}
