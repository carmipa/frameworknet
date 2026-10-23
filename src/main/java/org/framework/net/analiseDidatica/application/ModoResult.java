package org.framework.net.analiseDidatica.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModoResult {

    private String erro;
    private Integer cidrVal;
    private String cidrOrigem = "";
    private boolean forcarSomenteMascara;
    private Map<String, Object> ipv6Res;
    private String ipP = "";
    private List<Map<String, Object>> comparadorCards = new ArrayList<>();
    private String comparadorIp = "";
    private boolean comparadorOnly;
    private final Set<String> invalidFields = new HashSet<>();

    public String erro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }

    public Integer cidrVal() {
        return cidrVal;
    }

    public void setCidrVal(Integer cidrVal) {
        this.cidrVal = cidrVal;
    }

    public String cidrOrigem() {
        return cidrOrigem;
    }

    public void setCidrOrigem(String cidrOrigem) {
        this.cidrOrigem = cidrOrigem;
    }

    public boolean forcarSomenteMascara() {
        return forcarSomenteMascara;
    }

    public void setForcarSomenteMascara(boolean forcarSomenteMascara) {
        this.forcarSomenteMascara = forcarSomenteMascara;
    }

    public Map<String, Object> ipv6Res() {
        return ipv6Res;
    }

    public void setIpv6Res(Map<String, Object> ipv6Res) {
        this.ipv6Res = ipv6Res;
    }

    public String ipP() {
        return ipP;
    }

    public void setIpP(String ipP) {
        this.ipP = ipP;
    }

    public List<Map<String, Object>> comparadorCards() {
        return comparadorCards;
    }

    public void setComparadorCards(List<Map<String, Object>> comparadorCards) {
        this.comparadorCards = comparadorCards;
    }

    public String comparadorIp() {
        return comparadorIp;
    }

    public void setComparadorIp(String comparadorIp) {
        this.comparadorIp = comparadorIp;
    }

    public boolean comparadorOnly() {
        return comparadorOnly;
    }

    public void setComparadorOnly(boolean comparadorOnly) {
        this.comparadorOnly = comparadorOnly;
    }

    public Set<String> invalidFields() {
        return invalidFields;
    }
}
