package org.framework.net.resolucaoProblemas.domain.model;

import java.util.List;

public class PtRouterTable {

    private String routerName;
    private String locationName;
    private List<PtRouterRow> rows;

    public String getRouterName() {
        return routerName;
    }

    public void setRouterName(String routerName) {
        this.routerName = routerName;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public List<PtRouterRow> getRows() {
        return rows;
    }

    public void setRows(List<PtRouterRow> rows) {
        this.rows = rows;
    }

    public static class PtRouterRow {
        private String interfaceName;
        private String ip;
        private String mask;
        private String cidr;
        private String role;
        private String description;

        public String getInterface() {
            return interfaceName;
        }

        public void setInterface(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getMask() {
            return mask;
        }

        public void setMask(String mask) {
            this.mask = mask;
        }

        public String getCidr() {
            return cidr;
        }

        public void setCidr(String cidr) {
            this.cidr = cidr;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
