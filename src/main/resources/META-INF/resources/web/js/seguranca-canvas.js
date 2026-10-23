/*
 * Aba "Canvas (arrastar)" da página de Segurança — alternativa visual ao montador em texto.
 *
 * Propósito de negócio: deixar o usuário montar a MESMA topologia arrastando
 * equipamentos e ligando interfaces, em vez de digitar. Na avaliação, o canvas é
 * serializado para o mesmo formato de texto do montador e enviado ao MESMO
 * endpoint (/seguranca/api/topologia) — o motor de alcançabilidade e o diagrama
 * são reaproveitados sem tocar no backend.
 *
 * Invariantes: nenhum handler inline (compatível com a CSP); o POST leva o token
 * CSRF (cookie __Host-XSRF-TOKEN em produção, XSRF-TOKEN em dev) no cabeçalho X-CSRF-Token; a serialização usa a mesma
 * gramática que o parser aceita (host/switchl3/firewall/server/link).
 *
 * Comportamento em caso de falha: erro de rede ou 4xx do servidor não quebram a
 * página — a mensagem retornada é mostrada no bloco de resultado; entrada inválida
 * (recusada pelo parser) chega como texto de erro, não como diagnóstico.
 */
(function () {
    "use strict";

    var W = 140, H = 56, VBW = 900, VBH = 460;
    var CORES = { host: "#38bdf8", switchl3: "#34d399", firewall: "#f87171", server: "#a78bfa" };
    var ROTULO = { host: "host", switchl3: "switch L3", firewall: "firewall", server: "servidor" };
    var PREFIXO = { host: "H", switchl3: "R", firewall: "FW", server: "S" };
    var PADRAO = {
        host: { vlan: "10", gw: "" },
        switchl3: { vlans: "10,20" },
        firewall: { deny: "tcp/23" },
        server: { vlan: "20", porta: "443" }
    };
    var CAMPOS = {
        host: ["vlan", "gw"],
        switchl3: ["vlans"],
        firewall: ["deny"],
        server: ["vlan", "porta"]
    };

    var NS = "http://www.w3.org/2000/svg";
    var nos = [];            // {id, tipo, x, y, attrs}
    var enlaces = [];        // {a, b}
    var selecionado = null;  // id
    var modoLigar = false;
    var ligarPrimeiro = null;
    var arrasto = null;      // {id, p0, x0, y0, moveu}
    var avaliacaoSeq = 0;    // guarda contra resposta assincrona fora de ordem
    var textoAvaliado = null; // texto que gerou o resultado atualmente exibido

    var svg, gLinks, gNodes, dica, props, textoPreview;

    function cookie(nome) {
        var m = document.cookie.match(new RegExp("(^|;\\s*)" + nome + "=([^;]*)"));
        return m ? decodeURIComponent(m[2]) : "";
    }

    function porId(id) {
        for (var i = 0; i < nos.length; i++) {
            if (nos[i].id === id) { return nos[i]; }
        }
        return null;
    }

    function idLivre(tipo) {
        var p = PREFIXO[tipo], n = 1;
        while (porId(p + n)) { n++; }
        return p + n;
    }

    function sanId(v) {
        // Id compativel com o parser (split por espaco), os seletores DOM e o Mermaid.
        return (v || "").replace(/[^A-Za-z0-9_]/g, "");
    }

    function centro(no) {
        return { x: no.x + W / 2, y: no.y + H / 2 };
    }

    function pontoSvg(evt) {
        var pt = svg.createSVGPoint();
        pt.x = evt.clientX;
        pt.y = evt.clientY;
        var ctm = svg.getScreenCTM();
        return ctm ? pt.matrixTransform(ctm.inverse()) : { x: evt.clientX, y: evt.clientY };
    }

    function subtitulo(no) {
        if (no.tipo === "host") {
            return "host · VLAN " + (no.attrs.vlan || "0") + (no.attrs.gw ? " · gw " + no.attrs.gw : "");
        }
        if (no.tipo === "switchl3") {
            return "switch L3 · SVIs " + (no.attrs.vlans || "—");
        }
        if (no.tipo === "firewall") {
            return "firewall" + (no.attrs.deny ? " · deny " + no.attrs.deny : "");
        }
        return "servidor · VLAN " + (no.attrs.vlan || "0") + (no.attrs.porta ? " :" + no.attrs.porta : "");
    }

    function el(nome, attrs) {
        var e = document.createElementNS(NS, nome);
        for (var k in attrs) {
            if (Object.prototype.hasOwnProperty.call(attrs, k)) { e.setAttribute(k, attrs[k]); }
        }
        return e;
    }

    function render() {
        gLinks.textContent = "";
        gNodes.textContent = "";
        enlaces.forEach(function (l) {
            var a = porId(l.a), b = porId(l.b);
            if (!a || !b) { return; }
            var ca = centro(a), cb = centro(b);
            gLinks.appendChild(el("line", {
                "class": "topo-edge", "data-a": l.a, "data-b": l.b,
                x1: ca.x, y1: ca.y, x2: cb.x, y2: cb.y
            }));
        });
        nos.forEach(function (no) {
            var cls = "topo-node";
            if (no.id === selecionado) { cls += " sel"; }
            if (no.id === ligarPrimeiro) { cls += " connect-first"; }
            var g = el("g", { "class": cls, "data-id": no.id, transform: "translate(" + no.x + "," + no.y + ")" });
            g.appendChild(el("rect", { "class": "topo-node-box", width: W, height: H, rx: 10, style: "stroke:" + CORES[no.tipo] }));
            var t1 = el("text", { "class": "topo-node-id", x: W / 2, y: 24, "text-anchor": "middle" });
            t1.textContent = no.id;
            g.appendChild(t1);
            var t2 = el("text", { "class": "topo-node-sub", x: W / 2, y: 42, "text-anchor": "middle" });
            t2.textContent = subtitulo(no);
            g.appendChild(t2);
            gNodes.appendChild(g);
        });
        atualizarDica();
        atualizarTexto();
    }

    function moverArestasDe(id, cx, cy) {
        var linhas = gLinks.querySelectorAll('line[data-a="' + id + '"], line[data-b="' + id + '"]');
        linhas.forEach(function (ln) {
            if (ln.getAttribute("data-a") === id) { ln.setAttribute("x1", cx); ln.setAttribute("y1", cy); }
            if (ln.getAttribute("data-b") === id) { ln.setAttribute("x2", cx); ln.setAttribute("y2", cy); }
        });
    }

    function atualizarDica() {
        if (!dica) { return; }
        if (!nos.length) {
            dica.textContent = "Comece adicionando equipamentos acima.";
            return;
        }
        if (modoLigar) {
            dica.textContent = ligarPrimeiro
                ? 'Ligar: clique no segundo equipamento para criar o enlace (primeiro: ' + ligarPrimeiro + ")."
                : "Ligar ativo: clique em dois equipamentos para criar um enlace.";
            return;
        }
        dica.textContent = "Arraste para posicionar; clique para editar. " + nos.length + " equipamento(s), " + enlaces.length + " enlace(s).";
    }

    function selecionar(id) {
        selecionado = id;
        var no = porId(id);
        if (!no) { props.hidden = true; render(); return; }
        props.hidden = false;
        document.getElementById("cp-id").value = no.id;
        ["vlan", "gw", "vlans", "deny", "porta"].forEach(function (campo) {
            var wrap = props.querySelector('[data-prop="' + campo + '"]');
            var input = document.getElementById("cp-" + campo);
            var usa = CAMPOS[no.tipo].indexOf(campo) >= 0;
            if (wrap) { wrap.hidden = !usa; }
            if (input) { input.value = usa ? (no.attrs[campo] || "") : ""; }
        });
        render();
    }

    function adicionar(tipo) {
        var id = idLivre(tipo);
        var i = nos.length;
        var x = 40 + (i % 5) * 165;
        var y = 40 + Math.floor(i / 5) * 90;
        var attrs = {};
        var padrao = PADRAO[tipo] || {};
        for (var k in padrao) { if (Object.prototype.hasOwnProperty.call(padrao, k)) { attrs[k] = padrao[k]; } }
        nos.push({ id: id, tipo: tipo, x: Math.min(x, VBW - W), y: Math.min(y, VBH - H), attrs: attrs });
        selecionar(id);
    }

    function removerNo(id) {
        nos = nos.filter(function (n) { return n.id !== id; });
        enlaces = enlaces.filter(function (l) { return l.a !== id && l.b !== id; });
        // Referencias de gateway ao no removido nao podem ficar penduradas.
        nos.forEach(function (n) {
            if (n.attrs && n.attrs.gw === id) { n.attrs.gw = ""; }
        });
        if (selecionado === id) { selecionado = null; props.hidden = true; }
        if (ligarPrimeiro === id) { ligarPrimeiro = null; }
        sincronizarFluxo();
        render();
    }

    function ligar(id) {
        if (!ligarPrimeiro) { ligarPrimeiro = id; render(); return; }
        if (ligarPrimeiro === id) { ligarPrimeiro = null; render(); return; }
        var a = ligarPrimeiro, b = id;
        var existe = enlaces.some(function (l) {
            return (l.a === a && l.b === b) || (l.a === b && l.b === a);
        });
        if (!existe) { enlaces.push({ a: a, b: b }); }
        ligarPrimeiro = null;
        render();
    }

    function sincronizarFluxo() {
        // Mantém origem/destino coerentes: preenche defaults úteis quando vazios.
        var origem = document.getElementById("canvas-origem");
        var destino = document.getElementById("canvas-destino");
        var host = nos.find(function (n) { return n.tipo === "host"; });
        var server = nos.find(function (n) { return n.tipo === "server"; });
        if (origem && !origem.value && host) { origem.value = host.id; }
        if (destino && !destino.value && server) { destino.value = server.id; }
        if (origem && origem.value && !porId(origem.value)) { origem.value = host ? host.id : ""; }
        if (destino && destino.value && !porId(destino.value)) { destino.value = server ? server.id : ""; }
    }

    function serializar() {
        var linhas = [];
        nos.forEach(function (no) {
            var partes = [no.tipo, no.id];
            CAMPOS[no.tipo].forEach(function (campo) {
                var v = (no.attrs[campo] || "").trim();
                if (v) { partes.push(campo + "=" + v); }
            });
            linhas.push(partes.join(" "));
        });
        enlaces.forEach(function (l) { linhas.push("link " + l.a + " " + l.b); });
        return linhas.join("\n");
    }

    function atualizarTexto() {
        var texto = serializar();
        if (textoPreview) { textoPreview.textContent = texto || "(canvas vazio)"; }
        // Um resultado de avaliacao anterior nao pode parecer valido para outro desenho.
        if (textoAvaliado !== null && texto !== textoAvaliado) {
            var alvo = document.getElementById("canvasResultado");
            if (alvo && alvo.innerHTML.trim()) {
                alvo.innerHTML = '<div class="alert alert-secondary py-2 mb-0">A topologia mudou — clique em <strong>Testar fluxo</strong> para reavaliar.</div>';
            }
            textoAvaliado = null;
        }
    }

    function renderMermaid(raiz) {
        if (typeof mermaid === "undefined") { return; }
        var blocos = raiz.querySelectorAll(".aprof-mermaid:not([data-processed])");
        if (!blocos.length) { return; }
        try {
            var r = mermaid.run({ nodes: blocos });
            if (r && typeof r.catch === "function") { r.catch(function () {}); }
        } catch (e) { /* silencioso */ }
    }

    function avaliar() {
        var alvo = document.getElementById("canvasResultado");
        var spin = document.getElementById("canvas-spin");
        sincronizarFluxo();
        if (!nos.length) {
            alvo.innerHTML = '<div class="alert alert-secondary py-2 mb-0">Adicione equipamentos e ligue-os antes de avaliar.</div>';
            return;
        }
        var origem = (document.getElementById("canvas-origem").value || "").trim();
        var destino = (document.getElementById("canvas-destino").value || "").trim();
        var porta = (document.getElementById("canvas-porta").value || "").trim();
        if (!origem || !destino) {
            alvo.innerHTML = '<div class="alert alert-secondary py-2 mb-0">Informe a origem (um host) e o destino.</div>';
            return;
        }
        var enviado = serializar();
        var corpo = "topologia=" + encodeURIComponent(enviado)
            + "&origem=" + encodeURIComponent(origem)
            + "&destino=" + encodeURIComponent(destino)
            + "&porta=" + encodeURIComponent(porta);
        if (spin) { spin.hidden = false; }
        var meu = ++avaliacaoSeq;
        fetch("/seguranca/api/topologia", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "X-CSRF-Token": cookie("__Host-XSRF-TOKEN") || cookie("XSRF-TOKEN")
            },
            body: corpo
        }).then(function (resp) {
            return resp.text().then(function (txt) { return { ok: resp.ok, txt: txt }; });
        }).then(function (r) {
            if (meu !== avaliacaoSeq) { return; } // uma avaliacao mais nova ja respondeu
            alvo.innerHTML = r.txt;
            textoAvaliado = enviado;
            renderMermaid(alvo);
        }).catch(function () {
            if (meu !== avaliacaoSeq) { return; }
            alvo.innerHTML = '<div class="alert alert-danger py-2 mb-0">Falha ao avaliar o fluxo (rede). Tente de novo.</div>';
        }).finally(function () {
            if (meu === avaliacaoSeq && spin) { spin.hidden = true; }
        });
    }

    // ------------------------------------------------------------- interações

    function onPointerDown(evt) {
        var g = evt.target.closest ? evt.target.closest(".topo-node") : null;
        if (!g) { return; }
        var id = g.getAttribute("data-id");
        if (modoLigar) { ligar(id); return; }
        var no = porId(id);
        if (!no) { return; }
        var p = pontoSvg(evt);
        arrasto = { id: id, p0: p, x0: no.x, y0: no.y, moveu: false, gEl: g };
        try { g.setPointerCapture(evt.pointerId); } catch (e) { /* ignora */ }
        evt.preventDefault();
    }

    function onPointerMove(evt) {
        if (!arrasto) { return; }
        var p = pontoSvg(evt);
        var dx = p.x - arrasto.p0.x, dy = p.y - arrasto.p0.y;
        if (Math.abs(dx) + Math.abs(dy) > 3) { arrasto.moveu = true; }
        var no = porId(arrasto.id);
        if (!no) { return; }
        no.x = Math.max(0, Math.min(VBW - W, arrasto.x0 + dx));
        no.y = Math.max(0, Math.min(VBH - H, arrasto.y0 + dy));
        arrasto.gEl.setAttribute("transform", "translate(" + no.x + "," + no.y + ")");
        var c = centro(no);
        moverArestasDe(no.id, c.x, c.y);
    }

    function onPointerUp() {
        if (!arrasto) { return; }
        var a = arrasto;
        arrasto = null;
        if (!a.moveu) { selecionar(a.id); }
    }

    function onPropInput() {
        var no = porId(selecionado);
        if (!no) { return; }
        ["vlan", "gw", "vlans", "deny", "porta"].forEach(function (campo) {
            if (CAMPOS[no.tipo].indexOf(campo) >= 0) {
                var input = document.getElementById("cp-" + campo);
                if (input) { no.attrs[campo] = input.value; }
            }
        });
        render();
    }

    function onRenomear() {
        var no = porId(selecionado);
        if (!no) { return; }
        var novo = sanId(document.getElementById("cp-id").value);
        if (!novo || novo === no.id) { return; }
        if (porId(novo)) { document.getElementById("cp-id").value = no.id; return; }
        var antigo = no.id;
        enlaces.forEach(function (l) {
            if (l.a === antigo) { l.a = novo; }
            if (l.b === antigo) { l.b = novo; }
        });
        // Todo host que apontava para este gateway precisa acompanhar o novo id.
        nos.forEach(function (n) {
            if (n.attrs && n.attrs.gw === antigo) { n.attrs.gw = novo; }
        });
        ["canvas-origem", "canvas-destino"].forEach(function (fid) {
            var f = document.getElementById(fid);
            if (f && f.value === antigo) { f.value = novo; }
        });
        no.id = novo;
        selecionado = novo;
        render();
    }

    function toggleLigar() {
        modoLigar = !modoLigar;
        ligarPrimeiro = null;
        var btn = document.getElementById("canvas-ligar");
        btn.setAttribute("aria-pressed", modoLigar ? "true" : "false");
        btn.classList.toggle("active", modoLigar);
        render();
    }

    function limpar() {
        nos = [];
        enlaces = [];
        selecionado = null;
        ligarPrimeiro = null;
        modoLigar = false;
        var btn = document.getElementById("canvas-ligar");
        if (btn) { btn.setAttribute("aria-pressed", "false"); btn.classList.remove("active"); }
        props.hidden = true;
        ["canvas-origem", "canvas-destino"].forEach(function (fid) {
            var f = document.getElementById(fid); if (f) { f.value = ""; }
        });
        render();
    }

    function exemploInicial() {
        // Uma topologia que alcança, para o usuário ver algo de imediato.
        nos = [
            { id: "H1", tipo: "host", x: 60, y: 200, attrs: { vlan: "10", gw: "R1" } },
            { id: "R1", tipo: "switchl3", x: 300, y: 120, attrs: { vlans: "10,20" } },
            { id: "FW", tipo: "firewall", x: 540, y: 120, attrs: { deny: "tcp/23" } },
            { id: "S1", tipo: "server", x: 740, y: 200, attrs: { vlan: "20", porta: "443" } }
        ];
        enlaces = [{ a: "H1", b: "R1" }, { a: "R1", b: "FW" }, { a: "FW", b: "S1" }];
        document.getElementById("canvas-origem").value = "H1";
        document.getElementById("canvas-destino").value = "S1";
        render();
    }

    function start() {
        svg = document.getElementById("topo-canvas");
        if (!svg) { return; }
        gLinks = svg.querySelector("[data-canvas-links]");
        gNodes = svg.querySelector("[data-canvas-nodes]");
        dica = document.getElementById("canvas-dica");
        props = document.getElementById("canvas-props");
        textoPreview = document.getElementById("canvas-texto");

        document.querySelectorAll("[data-canvas-add]").forEach(function (b) {
            b.addEventListener("click", function () { adicionar(b.getAttribute("data-canvas-add")); });
        });
        document.getElementById("canvas-ligar").addEventListener("click", toggleLigar);
        document.getElementById("canvas-limpar").addEventListener("click", limpar);
        document.getElementById("canvas-avaliar").addEventListener("click", avaliar);
        document.getElementById("cp-remover").addEventListener("click", function () {
            if (selecionado) { removerNo(selecionado); }
        });
        document.getElementById("cp-id").addEventListener("change", onRenomear);
        ["cp-vlan", "cp-gw", "cp-vlans", "cp-deny", "cp-porta"].forEach(function (cid) {
            document.getElementById(cid).addEventListener("input", onPropInput);
        });

        svg.addEventListener("pointerdown", onPointerDown);
        svg.addEventListener("pointermove", onPointerMove);
        svg.addEventListener("pointerup", onPointerUp);
        svg.addEventListener("pointercancel", onPointerUp);

        exemploInicial();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
