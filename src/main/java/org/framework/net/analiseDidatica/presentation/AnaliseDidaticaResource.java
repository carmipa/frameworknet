package org.framework.net.analiseDidatica.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.framework.net.analiseDidatica.application.HomeAnaliseService;
import org.framework.net.analiseDidatica.application.HomeViewModel;
import org.framework.net.analiseDidatica.support.AnaliseDidaticaUiSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/analise")
public class AnaliseDidaticaResource {

    @Inject
    HomeAnaliseService homeAnaliseService;

    @Inject
    @io.quarkus.qute.Location("analiseDidatica/index.html")
    Template index;

    @Inject
    AnaliseDidaticaUiSupport uiSupport;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(
            @QueryParam("tab") String tab,
            @QueryParam("history_limit") String historyLimit,
            @QueryParam("history_page") String historyPage,
            @QueryParam("replay") String replay) {
        HomeViewModel vm = homeAnaliseService.processarGet(tab, historyLimit, historyPage, replay);
        return render(vm);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance post(
            @QueryParam("tab") String tab,
            @QueryParam("history_limit") String historyLimit,
            @QueryParam("history_page") String historyPage,
            MultivaluedMap<String, String> form) {
        Map<String, String> fields = new HashMap<>();
        form.forEach((k, v) -> fields.put(k, v == null || v.isEmpty() ? "" : v.get(0)));
        if (tab != null) {
            fields.putIfAbsent("tab", tab);
        }
        if (historyLimit != null) {
            fields.putIfAbsent("history_limit", historyLimit);
        }
        if (historyPage != null) {
            fields.putIfAbsent("history_page", historyPage);
        }
        HomeViewModel vm = homeAnaliseService.processarPost(fields);
        return render(vm);
    }

    private TemplateInstance render(HomeViewModel vm) {
        Map<String, Object> pag = vm.historicoPaginado();
        Map<String, Object> res = vm.res();
        List<Map<String, Object>> octetosBits = List.of();
        if (res != null) {
            String binRaw = String.valueOf(res.getOrDefault("bin_raw", ""));
            String mask = String.valueOf(res.getOrDefault("mask", "0.0.0.0"));
            octetosBits = uiSupport.octetosBits(binRaw, mask);
        }
        return index
                .data("activeMainMenu", vm.activeMainMenu())
                .data("res", vm.res())
                .data("ipv6Res", vm.ipv6Res())
                .data("erro", vm.erro())
                .data("ipPre", vm.ipPre())
                .data("ipv6Pre", vm.ipv6Pre())
                .data("cidrPre", vm.cidrPre())
                .data("maskDecPre", vm.maskDecPre())
                .data("wildcardPre", vm.wildcardPre())
                .data("reguaCountPre", vm.reguaCountPre())
                .data("comparadorCidrAPre", vm.comparadorCidrAPre())
                .data("comparadorCidrBPre", vm.comparadorCidrBPre())
                .data("comparadorCards", vm.comparadorCards())
                .data("comparadorOnly", vm.comparadorOnly())
                .data("comparadorIp", vm.comparadorIp())
                .data("activeTabPre", vm.activeTabPre())
                .data("wizardCalculo", vm.wizardCalculo())
                .data("timelineBloco", vm.timelineBloco())
                .data("octetosBits", octetosBits)
                .data("erroDidatico", vm.erroDidatico())
                .data("invalidFields", vm.invalidFields())
                .data("history", pag.getOrDefault("history", java.util.List.of()))
                .data("historyLimitPre", pag.getOrDefault("history_limit_pre", "1"))
                .data("historyLimit", pag.getOrDefault("history_limit", 1))
                .data("historyLimitMax", pag.getOrDefault("history_limit_max", 60))
                .data("historyPage", pag.getOrDefault("history_page", 1))
                .data("totalHistoryPages", pag.getOrDefault("total_history_pages", 1))
                .data("hasPrevHistory", pag.getOrDefault("has_prev_history", false))
                .data("hasNextHistory", pag.getOrDefault("has_next_history", false))
                .data("historyPageItems", pag.getOrDefault("history_page_items", java.util.List.of()))
                .data("textoCopia", res != null ? String.valueOf(res.getOrDefault("texto_copia", "")).strip() : "");
    }
}
