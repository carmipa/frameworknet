package org.framework.net.analiseDidatica.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeViewModel {

    private Map<String, Object> res;
    private String erro;
    private String ipPre = "";
    private String cidrPre = "";
    private String maskDecPre = "";
    private String wildcardPre = "";
    private String ipv6Pre = "";
    private String reguaCountPre = "5";
    private String historyLimitPre = "1";
    private String historyPagePre = "1";
    private String cidrOrigem = "";
    private Map<String, Object> ipv6Res;
    private Set<String> invalidFields = new HashSet<>();
    private List<Map<String, Object>> wizardCalculo = List.of();
    private Map<String, Object> timelineBloco;
    private Map<String, String> erroDidatico;
    private String comparadorCidrAPre;
    private String comparadorCidrBPre;
    private List<Map<String, Object>> comparadorCards = List.of();
    private boolean comparadorOnly;
    private String comparadorIp = "";
    private String activeTabPre = "cidr";
    private String activeMainMenu = "analise";
    private Map<String, Object> historicoPaginado = Map.of();

    public Map<String, Object> res() { return res; }
    public void setRes(Map<String, Object> res) { this.res = res; }
    public String erro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }
    public String ipPre() { return ipPre; }
    public void setIpPre(String ipPre) { this.ipPre = ipPre; }
    public String cidrPre() { return cidrPre; }
    public void setCidrPre(String cidrPre) { this.cidrPre = cidrPre; }
    public String maskDecPre() { return maskDecPre; }
    public void setMaskDecPre(String maskDecPre) { this.maskDecPre = maskDecPre; }
    public String wildcardPre() { return wildcardPre; }
    public void setWildcardPre(String wildcardPre) { this.wildcardPre = wildcardPre; }
    public String ipv6Pre() { return ipv6Pre; }
    public void setIpv6Pre(String ipv6Pre) { this.ipv6Pre = ipv6Pre; }
    public String reguaCountPre() { return reguaCountPre; }
    public void setReguaCountPre(String reguaCountPre) { this.reguaCountPre = reguaCountPre; }
    public String historyLimitPre() { return historyLimitPre; }
    public void setHistoryLimitPre(String historyLimitPre) { this.historyLimitPre = historyLimitPre; }
    public String historyPagePre() { return historyPagePre; }
    public void setHistoryPagePre(String historyPagePre) { this.historyPagePre = historyPagePre; }
    public String cidrOrigem() { return cidrOrigem; }
    public void setCidrOrigem(String cidrOrigem) { this.cidrOrigem = cidrOrigem; }
    public Map<String, Object> ipv6Res() { return ipv6Res; }
    public void setIpv6Res(Map<String, Object> ipv6Res) { this.ipv6Res = ipv6Res; }
    public Set<String> invalidFields() { return invalidFields; }
    public void setInvalidFields(Set<String> invalidFields) { this.invalidFields = invalidFields; }
    public List<Map<String, Object>> wizardCalculo() { return wizardCalculo; }
    public void setWizardCalculo(List<Map<String, Object>> wizardCalculo) { this.wizardCalculo = wizardCalculo; }
    public Map<String, Object> timelineBloco() { return timelineBloco; }
    public void setTimelineBloco(Map<String, Object> timelineBloco) { this.timelineBloco = timelineBloco; }
    public Map<String, String> erroDidatico() { return erroDidatico; }
    public void setErroDidatico(Map<String, String> erroDidatico) { this.erroDidatico = erroDidatico; }
    public String comparadorCidrAPre() { return comparadorCidrAPre; }
    public void setComparadorCidrAPre(String comparadorCidrAPre) { this.comparadorCidrAPre = comparadorCidrAPre; }
    public String comparadorCidrBPre() { return comparadorCidrBPre; }
    public void setComparadorCidrBPre(String comparadorCidrBPre) { this.comparadorCidrBPre = comparadorCidrBPre; }
    public List<Map<String, Object>> comparadorCards() { return comparadorCards; }
    public void setComparadorCards(List<Map<String, Object>> comparadorCards) { this.comparadorCards = comparadorCards; }
    public boolean comparadorOnly() { return comparadorOnly; }
    public void setComparadorOnly(boolean comparadorOnly) { this.comparadorOnly = comparadorOnly; }
    public String comparadorIp() { return comparadorIp; }
    public void setComparadorIp(String comparadorIp) { this.comparadorIp = comparadorIp; }
    public String activeTabPre() { return activeTabPre; }
    public void setActiveTabPre(String activeTabPre) { this.activeTabPre = activeTabPre; }
    public String activeMainMenu() { return activeMainMenu; }
    public void setActiveMainMenu(String activeMainMenu) { this.activeMainMenu = activeMainMenu; }
    public Map<String, Object> historicoPaginado() { return historicoPaginado; }
    public void setHistoricoPaginado(Map<String, Object> historicoPaginado) { this.historicoPaginado = historicoPaginado; }

    public boolean invalid(String field) {
        return invalidFields != null && invalidFields.contains(field);
    }
}
