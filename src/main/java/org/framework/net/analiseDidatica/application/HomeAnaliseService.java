package org.framework.net.analiseDidatica.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.application.autoCidr.AutoCidrModoService;
import org.framework.net.analiseDidatica.application.cidr.CidrModoService;
import org.framework.net.analiseDidatica.application.comparador.ComparadorModoService;
import org.framework.net.analiseDidatica.application.dominio.DominioModoService;
import org.framework.net.analiseDidatica.application.ipv6.Ipv6ModoService;
import org.framework.net.analiseDidatica.application.mascara.MascaraModoService;
import org.framework.net.analiseDidatica.application.wildcard.WildcardModoService;
import org.framework.net.analiseDidatica.config.AnaliseDidaticaConfig;
import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.framework.net.analiseDidatica.exception.HistoricoPersistenciaException;
import org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver;
import org.framework.net.analiseDidatica.infrastructure.historico.HistoricoStore;
import org.framework.net.analiseDidatica.support.ErroDidaticoService;
import org.framework.net.analiseDidatica.support.GrcService;
import org.framework.net.analiseDidatica.support.AnaliseDidaticaUiSupport;
import org.framework.net.analiseDidatica.support.TimelineBuilder;
import org.framework.net.analiseDidatica.support.WizardBuilder;
import org.framework.net.shared.IpCidrInputNormalizer;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.framework.net.telemetria.TelemetriaContext;

@ApplicationScoped
public class HomeAnaliseService {

    private static final Logger LOG = Logger.getLogger(HomeAnaliseService.class);
    private static final Set<String> VALID_MODES = Set.of(
            "cidr", "mask", "wildcard", "autoip", "dominio", "ipv6", "comparador", "geo");
    private static final Set<Integer> REGUA_OPCOES = Set.of(5, 10, 15, 25, 50, 100);

    /**
     * Maior campo aceito no formulário da Análise. O maior valor legítimo é uma URL colada no modo Domínio, de onde
     * sai só o nome do host: 8192 cobre os 8000 octetos que a RFC 9110 §4.1 recomenda aceitar numa URI (link de
     * proteção de e-mail e URL assinada passam de 2 mil). O POST lê cada campo por {@code @RestForm}, com o teto de
     * 256 KB por campo do Quarkus — largo demais para o que a tela tem: sem este teto, cada campo ia até lá e chegava
     * ao DNS, ao log, à telemetria e ao histórico.
     */
    static final int MAX_CARACTERES_CAMPO = 8192;

    /** Campos do formulário que a tela sabe destacar quando recusados. */
    private static final Set<String> CAMPOS_DA_TELA = Set.of(
            "ip", "ipv6", "cidr", "mask_decimal", "wildcard_mask", "modo",
            "comparador_cidr_a", "comparador_cidr_b", "regua_count");

    @Inject
    AnaliseDidaticaConfig config;

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    CidrModoService cidrModoService;

    @Inject
    MascaraModoService mascaraModoService;

    @Inject
    WildcardModoService wildcardModoService;

    @Inject
    AutoCidrModoService autoCidrModoService;

    @Inject
    ComparadorModoService comparadorModoService;

    @Inject
    DominioModoService dominioModoService;

    @Inject
    Ipv6ModoService ipv6ModoService;

    @Inject
    DnsResolver dnsResolver;

    @Inject
    HistoricoStore historicoStore;

    @Inject
    GrcService grcService;

    @Inject
    ErroDidaticoService erroDidaticoService;

    @Inject
    AnaliseDidaticaUiSupport uiSupport;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public HomeViewModel processarGet(String tab, String historyLimit, String historyPage, String replayId) {
        HomeViewModel vm = estadoPadrao(tab);
        aplicarDefaultsHistorico(vm, historyLimit, historyPage);
        aplicarReplay(vm, replayId);
        finalizarHistorico(vm);
        return vm;
    }

    public HomeViewModel processarPost(Map<String, String> form) {
        return telemetriaLogger.medir("analiseDidatica", "calc_request", () -> processarPostInterno(form));
    }

    @SuppressWarnings("unchecked")
    private HomeViewModel processarPostInterno(Map<String, String> form) {
        HomeViewModel recusa = recusarCampoLongo(form);
        if (recusa != null) {
            return recusa;
        }
        HomeViewModel vm = estadoPadrao(form.getOrDefault("tab", "cidr"));
        aplicarDefaultsHistorico(vm, form.get("history_limit"), form.get("history_page"));

        String ipP = trim(form.get("ip"));
        String ipEntradaOriginal = ipP;
        String ipv6P = trim(form.get("ipv6"));
        String cidrRaw = trim(form.get("cidr"));
        IpCidrInputNormalizer.SplitResult ipCidr = IpCidrInputNormalizer.splitIpAndCidr(ipP, cidrRaw);
        ipP = ipCidr.ip();
        cidrRaw = ipCidr.cidrRaw();
        String avisoPrefixo = ipCidr.aviso();
        String maskDecP = trim(form.get("mask_decimal"));
        String wildcardP = trim(form.get("wildcard_mask"));
        int reguaCount = parseRegua(form.get("regua_count"));
        String comparadorA = defaultIfBlank(form.get("comparador_cidr_a"), config.comparadorCidrPadraoA());
        String comparadorB = defaultIfBlank(form.get("comparador_cidr_b"), config.comparadorCidrPadraoB());
        vm.setReguaCountPre(String.valueOf(reguaCount));
        vm.setComparadorCidrAPre(comparadorA);
        vm.setComparadorCidrBPre(comparadorB);
        vm.setHistoryLimitPre(defaultIfBlank(form.get("history_limit"), vm.historyLimitPre()));
        vm.setHistoryPagePre(defaultIfBlank(form.get("history_page"), vm.historyPagePre()));

        String modo = trim(form.get("modo")).toLowerCase();
        if (!modo.isBlank()) {
            vm.setActiveTabPre(modo);
        }
        modo = resolverModo(modo, cidrRaw, maskDecP, wildcardP, ipv6P, ipP);

        String erro = null;
        Set<String> invalidFields = new HashSet<>();
        if (modo.isBlank()) {
            erro = "Selecione um modo e preencha o campo correspondente.";
            invalidFields.add("modo");
        } else {
            LOG.infof("analysis_use modo=%s reason=%s", modo, erroDidaticoService.motivoAnalise(modo));
        }

        if (erro == null && Set.of("cidr", "autoip", "comparador").contains(modo) && !ipP.isBlank()
                && !IpCidrInputNormalizer.looksLikeIpv4OrCidr(ipEntradaOriginal)) {
            try {
                ipP = dnsResolver.resolverComCache(ipP);
            } catch (DnsResolucaoException ex) {
                erro = "Não foi possível resolver o domínio informado: " + form.get("ip");
            }
        }

        ModoResult modoResult = aplicarModo(modo, erro, ipP, ipv6P, cidrRaw, maskDecP, wildcardP,
                ipEntradaOriginal, comparadorA, comparadorB, invalidFields);

        erro = modoResult.erro();
        ipP = modoResult.ipP().isBlank() ? ipP : modoResult.ipP();
        if (modoResult.ipv6Res() != null) {
            vm.setIpv6Res(modoResult.ipv6Res());
        }
        if (modoResult.comparadorOnly()) {
            vm.setComparadorOnly(true);
            vm.setComparadorIp(modoResult.comparadorIp());
            vm.setComparadorCards(modoResult.comparadorCards());
        }

        String cidrOrigemModo = modoResult.cidrOrigem() == null ? "" : modoResult.cidrOrigem();
        if (avisoPrefixo != null) {
            cidrOrigemModo = (avisoPrefixo + " " + cidrOrigemModo).strip();
        }
        Map<String, Object> ipv4 = processarIpv4(
                erro, modoResult.cidrVal(), ipP, modoResult.forcarSomenteMascara(), cidrOrigemModo,
                reguaCount, modo, ipEntradaOriginal, cidrRaw, maskDecP, wildcardP, invalidFields);

        erro = (String) ipv4.get("erro");
        Map<String, Object> res = (Map<String, Object>) ipv4.get("res");
        if (res != null) {
            vm.setRes(res);
        }
        if (ipv4.get("cidr_p") != null && !String.valueOf(ipv4.get("cidr_p")).isBlank()) {
            vm.setCidrPre(String.valueOf(ipv4.get("cidr_p")));
        }
        vm.setMaskDecPre(String.valueOf(ipv4.getOrDefault("mask_dec_p", maskDecP)));
        vm.setWildcardPre(String.valueOf(ipv4.getOrDefault("wildcard_p", wildcardP)));
        vm.setCidrOrigem(String.valueOf(ipv4.getOrDefault("cidr_origem", modoResult.cidrOrigem())));
        invalidFields.addAll((Set<String>) ipv4.getOrDefault("invalid_fields", Set.of()));

        vm.setIpPre(ipP);
        vm.setIpv6Pre(ipv6P);
        vm.setErro(erro);
        vm.setInvalidFields(invalidFields);
        finalizarPosProcessamento(vm);
        finalizarHistorico(vm);
        Map<String, Object> fields = new HashMap<>();
        fields.put("modo", modoParaTelemetria(vm.activeTabPre()));
        fields.put("statusCalc", vm.erro() == null || vm.erro().isBlank() ? "ok" : "invalid_input");
        telemetriaLogger.logEvent("info", "analiseDidatica", "calc_complete", fields);
        return vm;
    }

    /**
     * Recusa o envio que traz um campo (nome ou valor) acima de {@link #MAX_CARACTERES_CAMPO}.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> nenhum endereço, máscara ou domínio chega perto do teto; o que passa dele é
     * colagem errada ou abuso, e não pode chegar ao DNS, ao log, à telemetria nem ao histórico.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a conferência roda antes de qualquer outro uso do formulário; o valor
     * recusado nunca é ecoado na tela, no log nem no evento (só o nome do campo, e só se for um campo da tela);
     * a recusa conta na telemetria como {@code statusCalc=campo_longo}.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; devolve {@code null} quando está tudo no teto, ou a tela
     * com o erro e o campo marcado.</p>
     */
    private HomeViewModel recusarCampoLongo(Map<String, String> form) {
        String campoLongo = null;
        boolean achou = false;
        for (Map.Entry<String, String> e : form.entrySet()) {
            String chave = e.getKey() == null ? "" : e.getKey();
            String valor = e.getValue() == null ? "" : e.getValue();
            if (chave.length() > MAX_CARACTERES_CAMPO || valor.length() > MAX_CARACTERES_CAMPO) {
                achou = true;
                campoLongo = CAMPOS_DA_TELA.contains(chave) ? chave : null;
                break;
            }
        }
        if (!achou) {
            return null;
        }
        String tab = form.get("tab");
        HomeViewModel vm = estadoPadrao(tab != null && tab.length() <= MAX_CARACTERES_CAMPO ? tab : "cidr");
        // Os campos que cabem voltam para a tela (a pessoa não redigita o resto); o que passou do teto não volta.
        vm.setIpPre(noTetoOuVazio(form.get("ip")));
        vm.setIpv6Pre(noTetoOuVazio(form.get("ipv6")));
        vm.setCidrPre(noTetoOuVazio(form.get("cidr")));
        vm.setMaskDecPre(noTetoOuVazio(form.get("mask_decimal")));
        vm.setWildcardPre(noTetoOuVazio(form.get("wildcard_mask")));
        vm.setErro("Um dos campos passou de " + MAX_CARACTERES_CAMPO + " caracteres — nenhum endereço, máscara, "
                + "domínio ou URL chega perto disso. Confira o que foi colado.");
        Set<String> invalidos = new HashSet<>();
        if (campoLongo != null) {
            invalidos.add(campoLongo);
        }
        vm.setInvalidFields(invalidos);
        finalizarHistorico(vm);
        Map<String, Object> fields = new HashMap<>();
        fields.put("modo", modoParaTelemetria(vm.activeTabPre()));
        fields.put("statusCalc", "campo_longo");
        if (campoLongo != null) {
            fields.put("campo", campoLongo);
        }
        telemetriaLogger.logEvent("info", "analiseDidatica", "calc_complete", fields);
        return vm;
    }

    /**
     * O modo que vai ao evento de telemetria.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> {@code modo} vai ao dataset público como vocabulário fixo; a aba ativa vem do
     * formulário, onde quem envia escolhe o texto (um IP derrubava a geração do dataset; texto livre saía publicado).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só os modos da tela passam; o resto vira {@code "invalido"}.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira {@code "invalido"}.</p>
     */
    static String modoParaTelemetria(String modo) {
        String m = modo == null ? "" : modo.strip().toLowerCase(java.util.Locale.ROOT);
        return VALID_MODES.contains(m) ? m : "invalido";
    }

    private static String noTetoOuVazio(String valor) {
        return valor == null || valor.length() > MAX_CARACTERES_CAMPO ? "" : valor.strip();
    }

    /**
     * O que o histórico guarda como "entrada" da consulta.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o histórico serve para repetir a consulta. No modo Domínio a pessoa pode colar
     * uma URL inteira — que pode carregar token de acesso e passar do teto do histórico, cortada num ponto em que a
     * repetição já não funciona. O que a consulta usou de fato foi só o nome do host.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> no modo Domínio sai o host normalizado (o mesmo que foi ao DNS, até 253
     * caracteres); nos demais modos, o texto digitado como veio.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nulo vira {@code ""}.</p>
     */
    static String entradaParaHistorico(String modo, String digitado) {
        if ("dominio".equals(modo)) {
            return org.framework.net.analiseDidatica.support.HostnameNormalizer.normalizar(digitado);
        }
        return digitado == null ? "" : digitado;
    }

    private void finalizarPosProcessamento(HomeViewModel vm) {
        if (vm.res() != null && !Boolean.TRUE.equals(vm.res().get("somente_mascara"))) {
            vm.setWizardCalculo(WizardBuilder.montar(vm.res()));
            vm.setTimelineBloco(TimelineBuilder.montar(vm.res()));
        }
        if (vm.erro() != null && !vm.erro().isBlank()) {
            vm.setErroDidatico(erroDidaticoService.explicar(vm.erro()));
        }
    }

    private void finalizarHistorico(HomeViewModel vm) {
        Map<String, Object> pag = historicoStore.paginar(vm.historyLimitPre(), vm.historyPagePre());
        vm.setHistoricoPaginado(pag);
        vm.setHistoryLimitPre(String.valueOf(pag.get("history_limit_pre")));
        vm.setHistoryPagePre(String.valueOf(pag.get("history_page")));
        vm.setActiveMainMenu("analise");
    }

    private HomeViewModel estadoPadrao(String tab) {
        HomeViewModel vm = new HomeViewModel();
        vm.setComparadorCidrAPre(config.comparadorCidrPadraoA());
        vm.setComparadorCidrBPre(config.comparadorCidrPadraoB());
        vm.setActiveTabPre(tab == null || tab.isBlank() ? "cidr" : tab.strip().toLowerCase());
        return vm;
    }

    private void aplicarDefaultsHistorico(HomeViewModel vm, String historyLimit, String historyPage) {
        if (org.framework.net.shared.NumeroAscii.inteiro(historyLimit, 4).isPresent()) {
            vm.setHistoryLimitPre(historyLimit);
        }
        if (org.framework.net.shared.NumeroAscii.inteiro(historyPage, 6).isPresent()) {
            vm.setHistoryPagePre(historyPage);
        }
    }

    private void aplicarReplay(HomeViewModel vm, String replayId) {
        if (replayId == null || replayId.isBlank()) {
            return;
        }
        Map<String, Object> selected = historicoStore.buscarReplay(replayId);
        if (selected == null) {
            // O histórico é por navegador (cookie de sessão): link de replay copiado de outra pessoa, ou de
            // uma sessão já encerrada, cairia num formulário vazio sem explicação.
            vm.setErro("Esta consulta do histórico não pertence a este navegador ou já expirou "
                    + "(o histórico fica só no navegador em que foi feito e some ao fechá-lo).");
            return;
        }
        String modoReplay = String.valueOf(selected.getOrDefault("modo", "")).toLowerCase();
        if (VALID_MODES.contains(modoReplay)) {
            vm.setActiveTabPre(modoReplay);
        }
        if ("ipv6".equals(selected.get("modo"))) {
            vm.setIpv6Pre(String.valueOf(selected.getOrDefault("ipv6_entrada",
                    selected.getOrDefault("ip_entrada", ""))));
            vm.setIpPre("");
        } else {
            vm.setIpPre(String.valueOf(selected.getOrDefault("ip_entrada", "")));
            vm.setIpv6Pre(String.valueOf(selected.getOrDefault("ipv6_entrada", "")));
        }
        vm.setCidrPre(String.valueOf(selected.getOrDefault("cidr_entrada", "")));
        vm.setMaskDecPre(String.valueOf(selected.getOrDefault("mask_entrada", "")));
        vm.setWildcardPre(String.valueOf(selected.getOrDefault("wildcard_entrada", "")));
    }

    private ModoResult aplicarModo(String modo, String erro, String ipP, String ipv6P, String cidrRaw,
                                   String maskDecP, String wildcardP, String ipEntradaOriginal,
                                   String comparadorA, String comparadorB, Set<String> invalidFields) {
        ModoResult result = new ModoResult();
        if (erro != null) {
            result.setErro(erro);
            return result;
        }

        switch (modo) {
            case "ipv6" -> {
                ModoResult ipv6 = ipv6ModoService.processar(ipv6P);
                copiarModoResult(result, ipv6);
                if (ipv6.erro() == null && ipv6.ipv6Res() != null) {
                    registrarIpv6(modo, ipv6P, ipv6.ipv6Res());
                }
            }
            case "dominio" -> {
                ModoResult dom = dominioModoService.processar(ipEntradaOriginal, cidrRaw);
                copiarModoResult(result, dom);
            }
            case "cidr" -> copiarModoResult(result, cidrModoService.processar(ipP, cidrRaw));
            case "mask" -> copiarModoResult(result, mascaraModoService.processar(ipP, maskDecP));
            case "wildcard" -> copiarModoResult(result, wildcardModoService.processar(ipP, wildcardP));
            case "autoip" -> copiarModoResult(result, autoCidrModoService.processar(ipP));
            case "comparador" -> {
                result.setComparadorOnly(true);
                copiarModoResult(result, comparadorModoService.processar(ipP, comparadorA, comparadorB));
            }
            default -> result.setErro("Modo de análise não suportado: " + modo);
        }
        invalidFields.addAll(result.invalidFields());
        return result;
    }

    private void registrarIpv6(String modo, String ipv6P, Map<String, Object> ipv6Res) {
        Map<String, String> entrada = Map.of(
                "modo", modo, "ip", "", "ipv6", ipv6P, "cidr", "", "mask_decimal", "", "wildcard_mask", "");
        Map<String, Object> resHist = new LinkedHashMap<>();
        resHist.put("rede", ipv6Res.getOrDefault("primeiros_64", ""));
        resHist.put("broad", "N/A em IPv6");
        resHist.put("mask", ipv6Res.getOrDefault("prefixo_sugerido", ""));
        resHist.put("cidr", "64");
        resHist.put("nivel_tema", "IPv6 didático");
        try {
            historicoStore.registrarConsulta(entrada, resHist);
        } catch (HistoricoPersistenciaException ex) {
            LOG.warnf("history_persist warn modo=%s erro=%s", modo, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processarIpv4(String erro, Integer cidrVal, String ipP, boolean forcarSomenteMascara,
                                              String cidrOrigem, int reguaCount, String mode, String ipEntradaOriginal,
                                              String cidrRaw, String maskDecP, String wildcardP, Set<String> invalidFields) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("erro", erro);
        out.put("res", null);
        out.put("cidr_p", "");
        out.put("mask_dec_p", maskDecP);
        out.put("wildcard_p", wildcardP);
        out.put("cidr_origem", cidrOrigem);
        out.put("invalid_fields", new HashSet<String>());

        if (erro != null || cidrVal == null) {
            return out;
        }
        if (cidrVal < 0 || cidrVal > 32) {
            out.put("erro", "CIDR deve estar entre 0 e 32.");
            ((Set<String>) out.get("invalid_fields")).add("cidr");
            return out;
        }

        // O texto do campo de endereço só vira máscara quando começa com "255." (auditoria CALC-01).
        // 128.0.0.0, 192.0.0.0, 224.0.0.0 e 0.0.0.0 também têm forma de máscara contígua, mas são ENDEREÇOS
        // de estudo (1ª rede classe B, bloco IANA 192.0.0.0/24, bloco de controle multicast) — viravam
        // "/1", "/2", "/3" e o prefixo digitado era descartado. Nenhum exercício usa host 255.x.
        Integer ciComoMascara = !ipP.isBlank() ? ipv4Kernel.mascaraDottedParaCidr(ipP) : null;
        if (ciComoMascara != null && !forcarSomenteMascara && !ipP.strip().startsWith("255.")) {
            cidrOrigem = ((cidrOrigem == null ? "" : cidrOrigem).strip() + " O endereço " + ipP
                    + " também tem forma de máscara contígua (/" + ciComoMascara + "), mas foi analisado como "
                    + "endereço; para estudar a máscara, use a aba Máscara.").strip();
            ciComoMascara = null;
        }
        if (ciComoMascara != null && !forcarSomenteMascara) {
            if (!ciComoMascara.equals(cidrVal)) {
                cidrVal = ciComoMascara;
                cidrOrigem = "O texto no campo de endereço é uma máscara contígua (→ /" + cidrVal + "). "
                        + "O número depois do / foi alinhado a essa máscara.";
            } else {
                cidrOrigem = ((cidrOrigem == null ? "" : cidrOrigem).strip()
                        + " Campo de endereço reconhecido como máscara pontuada.").strip();
            }
            forcarSomenteMascara = true;
        }

        try {
            Map<String, Object> res;
            if (forcarSomenteMascara) {
                res = ipv4Kernel.processarSomenteMascara(cidrVal);
            } else if (!ipP.isBlank()) {
                res = ipv4Kernel.processar(ipP, cidrVal, reguaCount);
            } else {
                res = ipv4Kernel.processarSomenteMascara(cidrVal);
            }
            if (res == null) {
                out.put("erro", "CIDR inválido para cálculo de rede.");
                return out;
            }
            if (Boolean.TRUE.equals(res.get("somente_mascara"))) {
                res.put("regua_count", reguaCount);
            }
            if (maskDecP == null || maskDecP.isBlank()) {
                maskDecP = String.valueOf(res.get("mask"));
            }
            if (wildcardP == null || wildcardP.isBlank()) {
                wildcardP = String.valueOf(res.get("wildcard"));
            }
            res.put("cidr_origem", cidrOrigem == null ? "" : cidrOrigem);
            res.put("grc_resumo", grcService.resumo(res));
            uiSupport.normalizarRes(res);
            out.put("res", res);
            out.put("cidr_p", String.valueOf(cidrVal));
            out.put("mask_dec_p", maskDecP);
            out.put("wildcard_p", wildcardP);
            out.put("cidr_origem", cidrOrigem);

            Map<String, String> entrada = Map.of(
                    "modo", mode,
                    "ip", entradaParaHistorico(mode, ipEntradaOriginal),
                    "cidr", cidrRaw,
                    "mask_decimal", maskDecP,
                    "wildcard_mask", wildcardP);
            try {
                historicoStore.registrarConsulta(entrada, res);
            } catch (HistoricoPersistenciaException ex) {
                LOG.warnf("history_persist warn modo=%s erro=%s", mode, ex.getMessage());
            }
        } catch (EntradaInvalidaException ex) {
            LOG.warnf("calc invalid_input modo=%s erro=%s", mode, TelemetriaContext.paraLog(ex.getMessage(), 512));
            out.put("erro", ex.getMessage());
        } catch (Exception ex) {
            LOG.error("calc error", ex);
            out.put("erro", "Erro interno ao processar os dados. Revise os campos e tente novamente.");
        }
        return out;
    }

    private static void copiarModoResult(ModoResult target, ModoResult source) {
        target.setErro(source.erro());
        target.setCidrVal(source.cidrVal());
        target.setCidrOrigem(source.cidrOrigem());
        target.setForcarSomenteMascara(source.forcarSomenteMascara());
        target.setIpv6Res(source.ipv6Res());
        target.setIpP(source.ipP());
        target.setComparadorCards(source.comparadorCards());
        target.setComparadorIp(source.comparadorIp());
        target.setComparadorOnly(source.comparadorOnly());
        target.invalidFields().addAll(source.invalidFields());
    }

    private static String resolverModo(String modo, String cidrRaw, String maskDecP, String wildcardP,
                                       String ipv6P, String ipP) {
        if (VALID_MODES.contains(modo)) {
            return modo;
        }
        if (!cidrRaw.isBlank()) return "cidr";
        if (!maskDecP.isBlank()) return "mask";
        if (!wildcardP.isBlank()) return "wildcard";
        if (!ipv6P.isBlank()) return "ipv6";
        if (!ipP.isBlank()) return "autoip";
        return "";
    }

    private static int parseRegua(String value) {
        try {
            int n = org.framework.net.shared.NumeroAscii.parseIntEstrito(defaultIfBlank(value, "5").strip());
            return REGUA_OPCOES.contains(n) ? n : 5;
        } catch (NumberFormatException ex) {
            return 5;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }
}
