package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;

public class GrowthForecast {

    private String locationName;
    private int currentPrefix;
    private List<GrowthScenario> scenarios;

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public int getCurrentPrefix() {
        return currentPrefix;
    }

    public void setCurrentPrefix(int currentPrefix) {
        this.currentPrefix = currentPrefix;
    }

    public List<GrowthScenario> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<GrowthScenario> scenarios) {
        this.scenarios = scenarios;
    }

    public static class GrowthScenario {
        private String factorLabel;
        private int futureHosts;
        private int requiredPrefix;
        private boolean fitsCurrent;

        public String getFactorLabel() {
            return factorLabel;
        }

        public void setFactorLabel(String factorLabel) {
            this.factorLabel = factorLabel;
        }

        public int getFutureHosts() {
            return futureHosts;
        }

        public void setFutureHosts(int futureHosts) {
            this.futureHosts = futureHosts;
        }

        public int getRequiredPrefix() {
            return requiredPrefix;
        }

        public void setRequiredPrefix(int requiredPrefix) {
            this.requiredPrefix = requiredPrefix;
        }

        public boolean isFitsCurrent() {
            return fitsCurrent;
        }

        public void setFitsCurrent(boolean fitsCurrent) {
            this.fitsCurrent = fitsCurrent;
        }
    }
}
