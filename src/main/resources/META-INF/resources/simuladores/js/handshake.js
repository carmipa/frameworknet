/* Simulador de handshake TCP: busca a sequência e revela os segmentos (animado ou passo a passo). */
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

    function classeFlags(flags) {
        if (flags.indexOf("SYN") >= 0) return "syn";
        if (flags.indexOf("FIN") >= 0) return "fin";
        if (flags.indexOf("PSH") >= 0) return "dados";
        return "ack";
    }

    function montarDiagrama() {
        var box = $("hs-diagrama");
        box.textContent = "";
        passos.forEach(function (p, i) {
            var linha = el("div", "hs-passo " + (p.origem === "cliente" ? "para-servidor" : "para-cliente"));
            linha.dataset.idx = String(i);

            var seta = el("div", "hs-seta " + classeFlags(p.flags));
            var pill = el("span", "hs-flags", p.flags);
            var meta = el("span", "hs-seqack", "seq=" + p.seq + (p.ack ? " ack=" + p.ack : "") + (p.bytes ? " len=" + p.bytes : ""));
            seta.appendChild(pill);
            seta.appendChild(el("span", "hs-linha", ""));
            seta.appendChild(el("span", "hs-ponta", p.origem === "cliente" ? "▶" : "◀"));

            var texto = el("div", "hs-texto");
            texto.appendChild(el("strong", null, p.ordem + ". " + p.titulo + " "));
            texto.appendChild(meta);
            texto.appendChild(el("div", "hs-desc", p.descricao));

            linha.appendChild(seta);
            linha.appendChild(texto);
            box.appendChild(linha);
        });
    }

    function aplicarVisiveis() {
        var linhas = $("hs-diagrama").querySelectorAll(".hs-passo");
        linhas.forEach(function (l, i) { l.classList.toggle("revelado", i < visiveis); });
        var idx = visiveis - 1;
        if (idx >= 0 && idx < passos.length) {
            $("hs-estado-cliente").textContent = passos[idx].estadoCliente;
            $("hs-estado-servidor").textContent = passos[idx].estadoServidor;
        } else {
            $("hs-estado-cliente").textContent = "CLOSED";
            $("hs-estado-servidor").textContent = "LISTEN";
        }
    }

    function carregar() {
        var dados = $("hs-dados").checked;
        var enc = $("hs-encerramento").checked;
        return fetch("/simuladores/api/handshake?dados=" + dados + "&encerramento=" + enc,
            { headers: { Accept: "application/json" } })
            .then(function (r) {
                if (!r.ok) { throw new Error("HTTP " + r.status); }
                return r.json();
            })
            .then(function (d) {
                passos = d.passos || [];
                visiveis = 0;
                montarDiagrama();
                aplicarVisiveis();
            })
            .catch(function (e) {
                // Falha visível (ex.: 429 do limite de taxa) em vez de diagrama vazio e mudo.
                passos = [];
                visiveis = 0;
                $("hs-diagrama").textContent = "Não foi possível carregar o handshake. Tente novamente em instantes. ("
                    + e.message + ")";
            });
    }

    function pararTimer() { if (timer) { clearInterval(timer); timer = null; } }

    function animar() {
        pararTimer();
        carregar().then(function () {
            timer = setInterval(function () {
                if (visiveis >= passos.length) { pararTimer(); return; }
                visiveis++;
                aplicarVisiveis();
            }, 900);
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

    document.addEventListener("DOMContentLoaded", function () {
        if (!$("form-hs")) return; // página sem a aba de handshake
        $("hs-play").addEventListener("click", animar);
        $("hs-passo").addEventListener("click", passo);
        $("hs-reiniciar").addEventListener("click", reiniciar);
        $("hs-dados").addEventListener("change", reiniciarComReload);
        $("hs-encerramento").addEventListener("change", reiniciarComReload);
        carregar();
    });

    function reiniciarComReload() {
        pararTimer();
        passos = [];
        visiveis = 0;
        carregar();
    }
})();
