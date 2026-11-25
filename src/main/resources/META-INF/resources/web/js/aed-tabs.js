/*
 * Sub-abas genéricas e escopadas. Reaproveita as classes .tab-trigger/.tab-panel/.active
 * do design system (as mesmas da Análise Didática), mas SEM depender do analise.js.
 *
 * Uso: um contêiner com [data-aed-tabs] contendo botões .tab-trigger[data-tab] e
 * painéis .tab-panel[data-tab-panel]. Opcional: data-active-tab define a aba inicial.
 * Cada contêiner é isolado, então dá para ter vários grupos de abas na mesma página.
 *
 * Link direto para uma aba: ?aba=<id> na URL (ex.: /seguranca?aba=tls abre o Inspetor TLS).
 * A lista branca é o próprio conjunto de abas do grupo: id que não é aba dele é ignorado e
 * vale a aba inicial. O gabarito de abaPedida() mora em src/test/js/web.
 */
(function () {
    "use strict";

    /**
     * Aba pedida na URL, aceita só se for uma das abas do grupo.
     *
     * PROPÓSITO DE NEGÓCIO: o link de outra página (ex.: "Inspetor TLS" em /certificados) abre direto
     *   na aba certa, em vez de cair na primeira aba da página.
     * INVARIANTES DO DOMÍNIO: só devolve um id que está em `ids` (o menu de abas do grupo é a lista
     *   branca); compara sem diferenciar maiúsculas e sem os espaços das pontas, e devolve o id do grupo,
     *   nunca o texto que veio na URL; com `aba` repetida vale a primeira.
     * COMPORTAMENTO EM CASO DE FALHA: busca vazia, sem `aba`, com valor fora da lista ou ilegível
     *   devolve null, e o grupo abre na aba inicial (data-active-tab ou a primeira).
     */
    function abaPedida(ids, busca) {
        var pedida;
        try {
            pedida = new URLSearchParams(busca || "").get("aba");
        } catch (e) {
            return null;
        }
        if (pedida === null) { return null; }
        pedida = pedida.trim().toLowerCase();
        for (var i = 0; i < ids.length; i++) {
            if (typeof ids[i] === "string" && ids[i].toLowerCase() === pedida) { return ids[i]; }
        }
        return null;
    }

    function initScope(scope) {
        var triggers = scope.querySelectorAll(".tab-trigger");
        var panels = scope.querySelectorAll(".tab-panel");

        function show(id) {
            triggers.forEach(function (b) { b.classList.toggle("active", b.dataset.tab === id); });
            panels.forEach(function (p) { p.classList.toggle("active", p.dataset.tabPanel === id); });
            scope.setAttribute("data-active-tab", id);
        }

        triggers.forEach(function (b) {
            b.addEventListener("click", function () { show(b.dataset.tab); });
        });

        var ids = Array.prototype.map.call(triggers, function (b) { return b.dataset.tab; });
        var inicial = abaPedida(ids, window.location.search)
            || scope.getAttribute("data-active-tab")
            || (triggers[0] ? triggers[0].dataset.tab : null);
        if (inicial) { show(inicial); }
    }

    if (typeof document !== "undefined") {
        document.addEventListener("DOMContentLoaded", function () {
            document.querySelectorAll("[data-aed-tabs]").forEach(initScope);
        });
    }
    if (typeof module !== "undefined" && module.exports) {
        module.exports = { abaPedida: abaPedida };
    }
})();
