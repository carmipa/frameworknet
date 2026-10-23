package org.framework.net.resolucaoProblemas.domain.model;

public class LocationInput {

    private String name;
    private String hosts;

    public LocationInput() {
    }

    public LocationInput(String name, String hosts) {
        this.name = name;
        this.hosts = hosts;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }
}
