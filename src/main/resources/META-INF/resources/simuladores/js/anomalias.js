/*
 * Simulador de anomalias TCP (sub-aba "Anomalias" da página Tráfego).
 *
 * Propósito: buscar o cenário didático (SYN flood ou previsão de sequência) da
 * API e revelá-lo passo a passo, animado ou manual. Espelha o handshake.js.
 *
 * Invariantes: nada é persistido e nenhum pacote é enviado — a API devolve um
 * cenário determinístico. O texto vem por textContent (escape natural), nunca
 * innerHTML com dado do servidor.
 *
 * Falha: navegador sem a aba simplesmente não liga nada (guarda no DOMContent).
 */
(function () {
    "use strict";

    function $(id) { return document.getElementById(id); }

    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined && text !== null) e.textContent = String(text);
        return e;
    }

    var passos = [];
    var visiveis = 0;
    var timer = null;
    var cenario = "syn-flood";

    function reinitTooltips(scope) {
        if (window.FieldTooltips && typeof window.FieldTooltips.init === "function") {
            window.FieldTooltips.init(scope || document);
        }
    }

    function renderKpis(kpis) {
        var box = $("anom-kpis");
        box.textContent = "";
        (kpis || []).forEach(function (k) {
            var chip = el("div", "anom-kpi anom-kpi-" + (k.cor || "info"));
            if (k.dica) { chip.setAttribute("title", k.dica); }
            chip.appendChild(el("span", "anom-kpi-valor", k.valor));
            chip.appendChild(el("span", "anom-kpi-rotulo", k.rotulo));
            box.appendChild(chip);
        });
    }

    function montarPassos() {
        var box = $("anom-passos");
        box.textContent = "";
        passos.forEach(function (p, i) {
            var linha = el("div", "anom-passo anom-passo-" + (p.nivel || "normal"));
            linha.dataset.idx = String(i);

            var num = el("span", "anom-passo-num", p.ordem);
            var corpo = el("div", "anom-passo-corpo");

            var cabeca = el("div", "anom-passo-cabeca");
            cabeca.appendChild(el("span", "anom-passo-ator", p.ator));
            cabeca.appendChild(el("span", "anom-passo-acao", p.acao));
            if (p.estado) { cabeca.appendChild(el("span", "anom-passo-estado", p.estado)); }

            corpo.appendChild(cabeca);
            corpo.appendChild(el("p", "anom-passo-detalhe", p.detalhe));

            linha.appendChild(num);
            linha.appendChild(corpo);
            box.appendChild(linha);
        });
    }

    function renderMitigacoes(mits) {
        var lista = $("anom-mitigacoes");
        lista.textContent = "";
        (mits || []).forEach(function (m) {
            var li = el("li", "anom-mitigacao");
            li.appendChild(el("strong", null, m.nome + " "));
            li.appendChild(el("span", "text-secondary", m.comoFunciona));
            lista.appendChild(li);
        });
    }

    function aplicarVisiveis() {
        var linhas = $("anom-passos").querySelectorAll(".anom-passo");
        linhas.forEach(function (l, i) { l.classList.toggle("revelado", i < visiveis); });
    }

    function render(d) {
        $("anom-titulo").textContent = d.titulo || "";
        $("anom-resumo").textContent = d.resumo || "";
        renderKpis(d.kpis);
        passos = d.passos || [];
        visiveis = 0;
        montarPassos();
        aplicarVisiveis();
        renderMitigacoes(d.mitigacoes);
        var licao = $("anom-licao");
        licao.textContent = "";
        if (d.licao) {
            licao.appendChild(el("span", "material-symbols-outlined", "lightbulb"));
            licao.appendChild(el("span", null, d.licao));
        }
        reinitTooltips($("form-anom") ? $("form-anom").parentNode : document);
    }

    function carregar() {
        return fetch("/simuladores/api/anomalia-tcp?tipo=" + encodeURIComponent(cenario),
            { headers: { Accept: "application/json" } })
            .then(function (r) {
                if (!r.ok) { throw new Error("HTTP " + r.status); }
                return r.json();
            })
            .then(render)
            .catch(function (e) {
                // Falha visível em vez de aba muda (o defeito que a guarda de log pega).
                pararTimer();
                if ($("anom-titulo")) { $("anom-titulo").textContent = "Não foi possível carregar o cenário"; }
                if ($("anom-resumo")) {
                    $("anom-resumo").textContent = "Tente novamente em instantes. (" + e.message + ")";
                }
            });
    }

    function pararTimer() { if (timer) { clearInterval(timer); timer = null; } }

    function animar() {
        pararTimer();
        var p = (passos.length === 0) ? carregar() : Promise.resolve();
        p.then(function () {
            visiveis = 0;
            aplicarVisiveis();
            timer = setInterval(function () {
                if (visiveis >= passos.length) { pararTimer(); return; }
                visiveis++;
                aplicarVisiveis();
            }, 1100);
        });
    }

    function passo() {
        pararTimer();
        var p = (passos.length === 0) ? carregar() : Promise.resolve();
        p.then(function () {
            if (visiveis >= passos.length) { visiveis = 0; }
            visiveis++;
            aplicarVisiveis();
        });
    }

    function reiniciar() {
        pararTimer();
        visiveis = 0;
        if (passos.length === 0) { carregar(); } else { aplicarVisiveis(); }
    }

    function trocarCenario(botao) {
        pararTimer();
        document.querySelectorAll(".anom-cenario").forEach(function (b) {
            b.classList.toggle("active", b === botao);
        });
        cenario = botao.dataset.anom || "syn-flood";
        passos = [];
        visiveis = 0;
        carregar();
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (!$("form-anom")) return; // página sem a aba de anomalias
        $("anom-play").addEventListener("click", animar);
        $("anom-passo").addEventListener("click", passo);
        $("anom-reiniciar").addEventListener("click", reiniciar);
        document.querySelectorAll(".anom-cenario").forEach(function (b) {
            b.addEventListener("click", function () { trocarCenario(b); });
        });
        carregar();
    });
})();
