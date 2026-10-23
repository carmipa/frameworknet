package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;

public class ClassRosterRow {

    private String studentName;
    private String folderSlug;
    private String baseNetwork;
    private List<LocationInput> locations;

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getFolderSlug() {
        return folderSlug;
    }

    public void setFolderSlug(String folderSlug) {
        this.folderSlug = folderSlug;
    }

    public String getBaseNetwork() {
        return baseNetwork;
    }

    public void setBaseNetwork(String baseNetwork) {
        this.baseNetwork = baseNetwork;
    }

    public List<LocationInput> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationInput> locations) {
        this.locations = locations;
    }
}
