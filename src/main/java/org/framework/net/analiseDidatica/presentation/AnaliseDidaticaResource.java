package org.framework.net.analiseDidatica.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.analiseDidatica.application.HomeAnaliseService;
import org.framework.net.analiseDidatica.application.HomeViewModel;
import org.framework.net.analiseDidatica.support.AnaliseDidaticaUiSupport;
import org.jboss.resteasy.reactive.RestForm;

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

    /**
     * Envio do formulário da Análise.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> calcular o que a pessoa pediu na aba escolhida e devolver a página com o resultado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só os campos que a tela tem são lidos, cada um por {@code @RestForm} — o mesmo
     * leitor de formulário das outras rotas, com o teto de 256 KB por campo do Quarkus. Antes o corpo inteiro virava
     * um {@code MultivaluedMap}: esse leitor não tem teto por campo nem de quantidade de campos, e um corpo de 1 MB com
     * centenas de milhares de chaves montava três mapas inteiros na memória antes de chegar aqui. Campo ausente
     * continua ausente no mapa entregue ao serviço (a query preenche {@code tab} e o histórico só nesse caso).
     * Só formulário {@code application/x-www-form-urlencoded} entra: o multipart tem outro leitor, com outros tetos,
     * e a tela nunca o envia.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança por entrada; o serviço devolve a página com o erro. Corpo
     * de outro tipo recebe 415 antes de qualquer leitura.</p>
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance post(
            @QueryParam("tab") String tab,
            @QueryParam("history_limit") String historyLimit,
            @QueryParam("history_page") String historyPage,
            @RestForm("tab") String tabForm,
            @RestForm("history_limit") String historyLimitForm,
            @RestForm("history_page") String historyPageForm,
            @RestForm("modo") String modo,
            @RestForm("ip") String ip,
            @RestForm("ipv6") String ipv6,
            @RestForm("cidr") String cidr,
            @RestForm("mask_decimal") String maskDecimal,
            @RestForm("wildcard_mask") String wildcardMask,
            @RestForm("regua_count") String reguaCount,
            @RestForm("comparador_cidr_a") String comparadorCidrA,
            @RestForm("comparador_cidr_b") String comparadorCidrB) {
        Map<String, String> fields = new HashMap<>();
        colocar(fields, "tab", tabForm);
        colocar(fields, "history_limit", historyLimitForm);
        colocar(fields, "history_page", historyPageForm);
        colocar(fields, "modo", modo);
        colocar(fields, "ip", ip);
        colocar(fields, "ipv6", ipv6);
        colocar(fields, "cidr", cidr);
        colocar(fields, "mask_decimal", maskDecimal);
        colocar(fields, "wildcard_mask", wildcardMask);
        colocar(fields, "regua_count", reguaCount);
        colocar(fields, "comparador_cidr_a", comparadorCidrA);
        colocar(fields, "comparador_cidr_b", comparadorCidrB);
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

    private static void colocar(Map<String, String> fields, String nome, String valor) {
        if (valor != null) {
            fields.put(nome, valor);
        }
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
