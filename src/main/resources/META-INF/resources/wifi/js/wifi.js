/*
 * Planejador de canais Wi-Fi 2.4 GHz (client-side).
 *
 * Propósito: mostrar, de forma interativa, por que só canais a 5 de distância (1/6/11;
 * com os 13 canais do Brasil, também 2/7/12 e 3/8/13) não se sobrepõem na banda de 2.4 GHz. Cada canal ocupa ~22 MHz e os centros distam
 * 5 MHz; dois canais se sobrepõem quando a diferença é menor que 5.
 *
 * Comportamento em caso de falha: sem os elementos esperados, não faz nada.
 */
(function () {
    "use strict";

    var CANAIS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13];
    var NAO_SOBREPOEM = [1, 6, 11];
    var selecionados = new Set();

    function sobrepoe(a, b) {
        return a !== b && Math.abs(a - b) < 5;
    }

    function render() {
        var box = document.getElementById("wifi-canais");
        box.innerHTML = "";
        CANAIS.forEach(function (c) {
            var b = document.createElement("button");
            b.type = "button";
            b.className = "wifi-canal" + (selecionados.has(c) ? " is-on" : "") + (NAO_SOBREPOEM.indexOf(c) >= 0 ? " wifi-canal-limpo" : "");
            b.textContent = c;
            b.setAttribute("aria-pressed", selecionados.has(c) ? "true" : "false");
            b.title = "Canal " + c + (NAO_SOBREPOEM.indexOf(c) >= 0 ? " (não sobrepõe 1/6/11)" : "");
            b.addEventListener("click", function () {
                if (selecionados.has(c)) { selecionados.delete(c); } else { selecionados.add(c); }
                render();
            });
            box.appendChild(b);
        });
        resultado();
    }

    function resultado() {
        var out = document.getElementById("wifi-resultado");
        var sel = Array.from(selecionados).sort(function (a, b) { return a - b; });
        if (sel.length === 0) {
            out.innerHTML = '<span class="text-secondary">Marque os canais em uso para ver os conflitos.</span>';
            return;
        }
        var conflitos = [];
        for (var i = 0; i < sel.length; i++) {
            for (var j = i + 1; j < sel.length; j++) {
                if (sobrepoe(sel[i], sel[j])) { conflitos.push(sel[i] + "↔" + sel[j]); }
            }
        }
        var linha1 = "<div><strong>Selecionados:</strong> " + sel.join(", ") + "</div>";
        var linha2;
        if (conflitos.length === 0) {
            linha2 = '<div class="text-success mt-1"><span class="material-symbols-outlined" style="font-size:1rem;vertical-align:-0.15em;" translate="no" aria-hidden="true">check_circle</span> Nenhuma sobreposição — ótima escolha.</div>';
        } else {
            linha2 = '<div class="text-warning mt-1"><span class="material-symbols-outlined" style="font-size:1rem;vertical-align:-0.15em;" translate="no" aria-hidden="true">warning</span> ' + conflitos.length +
                ' par(es) sobrepondo: ' + conflitos.join(", ") +
                '. Prefira um trio a 5 canais de distância, como 1, 6 e 11.</div>';
        }
        out.innerHTML = linha1 + linha2;
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (!document.getElementById("wifi-canais")) { return; }
        var rec = document.getElementById("wifi-recomendar");
        var lim = document.getElementById("wifi-limpar");
        if (rec) { rec.addEventListener("click", function () { selecionados = new Set(NAO_SOBREPOEM); render(); }); }
        if (lim) { lim.addEventListener("click", function () { selecionados.clear(); render(); }); }
        render();
    });
})();
