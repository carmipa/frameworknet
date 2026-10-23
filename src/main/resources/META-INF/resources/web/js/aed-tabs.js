/*
 * Sub-abas genéricas e escopadas. Reaproveita as classes .tab-trigger/.tab-panel/.active
 * do design system (as mesmas da Análise Didática), mas SEM depender do analise.js.
 *
 * Uso: um contêiner com [data-aed-tabs] contendo botões .tab-trigger[data-tab] e
 * painéis .tab-panel[data-tab-panel]. Opcional: data-active-tab define a aba inicial.
 * Cada contêiner é isolado, então dá para ter vários grupos de abas na mesma página.
 */
(function () {
    "use strict";

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

        var inicial = scope.getAttribute("data-active-tab")
            || (triggers[0] ? triggers[0].dataset.tab : null);
        if (inicial) { show(inicial); }
    }

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("[data-aed-tabs]").forEach(initScope);
    });
})();
