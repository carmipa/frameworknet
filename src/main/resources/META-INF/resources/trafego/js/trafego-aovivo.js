/* Tráfego ao vivo: polling do snapshot + gráficos Chart.js (estilo Wireshark) + Wi-Fi/Bluetooth. */
(function (w) {
    "use strict";

    var PROTO_CORES = {
        TCP: "#2dd4bf", UDP: "#6366f1", TLS: "#34d399", DNS: "#f59e0b",
        ICMP: "#f43f5e", ARP: "#8b97ad"
    };

    var timer = null;
    // Ciclo de consulta. Era 1 s e não parava com a aba oculta: uma aba esquecida fazia 86.400
    // requisições por dia (auditoria OPS-06). Agora 2 s, e a aba oculta pausa sem perder o "ligado".
    var INTERVALO_MS = 2000;
    var ligado = false;
    var chartProto = null;
    var chartPps = null;
    var chartHosts = null;

    function $(id) { return document.getElementById(id); }

    function corProto(p) { return PROTO_CORES[p] || "#60a5fa"; }

    function initCharts() {
        if (typeof Chart === "undefined") { return; }
        Chart.defaults.color = "#8b97ad";
        Chart.defaults.font.family = "Inter Tight, system-ui, sans-serif";
        var grid = "rgba(255,255,255,0.05)";

        chartProto = new Chart($("chart-proto"), {
            type: "doughnut",
            data: { labels: [], datasets: [{ data: [], backgroundColor: [], borderColor: "#0a0e16", borderWidth: 2 }] },
            options: { plugins: { legend: { position: "right", labels: { boxWidth: 12 } } }, cutout: "62%" }
        });
        chartPps = new Chart($("chart-pps"), {
            type: "line",
            data: { labels: [], datasets: [{ label: "pkts/s", data: [], borderColor: "#2dd4bf", backgroundColor: "rgba(45,212,191,0.15)", fill: true, tension: 0.35, pointRadius: 0 }] },
            options: { plugins: { legend: { display: false } }, scales: { x: { grid: { color: grid } }, y: { grid: { color: grid }, beginAtZero: true } } }
        });
        chartHosts = new Chart($("chart-hosts"), {
            type: "bar",
            data: { labels: [], datasets: [{ label: "pacotes", data: [], backgroundColor: "#6366f1" }] },
            options: { indexAxis: "y", plugins: { legend: { display: false } }, scales: { x: { grid: { color: grid }, beginAtZero: true }, y: { grid: { display: false } } } }
        });
    }

    function td(text, cls) {
        var c = document.createElement("td");
        if (cls) c.className = cls;
        c.textContent = (text === null || text === undefined || text === "") ? "—" : String(text);
        return c;
    }

    function badge(text, cls) {
        var c = document.createElement("td");
        var s = document.createElement("span");
        s.className = "trafego-badge " + cls;
        s.textContent = text;
        c.appendChild(s);
        return c;
    }

    function fillTable(bodyId, rowsBuilder, vazio, cols) {
        var body = $(bodyId);
        body.textContent = "";
        if (!rowsBuilder || rowsBuilder.length === 0) {
            var tr = document.createElement("tr");
            var c = document.createElement("td");
            c.colSpan = cols; c.className = "text-secondary"; c.textContent = vazio;
            tr.appendChild(c); body.appendChild(tr);
            return;
        }
        rowsBuilder.forEach(function (tr) { body.appendChild(tr); });
    }

    function updateProto(porProtocolo) {
        var labels = Object.keys(porProtocolo || {});
        chartProto.data.labels = labels;
        chartProto.data.datasets[0].data = labels.map(function (k) { return porProtocolo[k]; });
        chartProto.data.datasets[0].backgroundColor = labels.map(corProto);
        chartProto.update("none");
    }

    function updatePps(serie) {
        chartPps.data.labels = (serie || []).map(function (p) { return p.t; });
        chartPps.data.datasets[0].data = (serie || []).map(function (p) { return p.valor; });
        chartPps.update("none");
    }

    function updateHosts(topHosts) {
        chartHosts.data.labels = (topHosts || []).map(function (h) { return h.host; });
        chartHosts.data.datasets[0].data = (topHosts || []).map(function (h) { return h.pacotes; });
        chartHosts.update("none");
    }

    function render(d) {
        $("kpi-total").textContent = d.totalPacotes;
        $("kpi-pps").textContent = d.pacotesPorSegundo;
        $("kpi-kbps").textContent = (d.throughputKbps || 0) + " kbps";
        $("kpi-abertas").textContent = d.redesAbertas;
        $("kpi-abertas").parentElement.classList.toggle("ativo", (d.redesAbertas || 0) > 0);

        if (chartProto) updateProto(d.porProtocolo);
        if (chartPps) updatePps(d.serie);
        if (chartHosts) updateHosts(d.topHosts);

        // Pacotes
        var pRows = (d.ultimosPacotes || []).map(function (p) {
            var tr = document.createElement("tr"); tr.className = "small";
            tr.appendChild(td(p.seq));
            tr.appendChild(td(p.timestamp));
            var tp = td(p.protocolo); tp.style.color = corProto(p.protocolo); tp.style.fontWeight = "600"; tr.appendChild(tp);
            tr.appendChild(td(p.origem));
            tr.appendChild(td(p.destino));
            tr.appendChild(td((p.portaOrigem || "—") + " → " + (p.portaDestino || "—")));
            tr.appendChild(td(p.tamanho));
            tr.appendChild(td(p.info));
            return tr;
        });
        fillTable("pacotes-body", pRows, "Sem pacotes.", 8);

        // Wi-Fi
        var wRows = (d.wifi || []).map(function (rede) {
            var tr = document.createElement("tr"); tr.className = "small";
            tr.appendChild(td(rede.ssid));
            tr.appendChild(td(rede.seguranca));
            tr.appendChild(td((rede.sinal || 0) + " dBm"));
            if (rede.aberta) tr.appendChild(badge("⚠ Aberta (insegura)", "risco"));
            else if (/^WEP/i.test(rede.seguranca || "")) tr.appendChild(badge("⚠ WEP (cifra quebrável)", "risco"));
            else tr.appendChild(badge("Protegida", "ok"));
            return tr;
        });
        fillTable("wifi-body", wRows, "Sem redes.", 4);

        // Bluetooth
        var bRows = (d.bluetooth || []).map(function (dev) {
            var tr = document.createElement("tr"); tr.className = "small";
            tr.appendChild(td(dev.nome));
            tr.appendChild(td(dev.endereco));
            tr.appendChild(td(dev.tipo));
            tr.appendChild(td(dev.pareado ? "Pareado" : "Visível"));
            return tr;
        });
        fillTable("bt-body", bRows, "Sem dispositivos.", 4);

        var st = $("live-status");
        if (st) {
            st.textContent = "ao vivo · simulação · atualizado " + d.atualizadoEm;
            st.className = "small text-secondary";
        }
    }

    function tick() {
        fetch("/trafego/api/aovivo", { headers: { Accept: "application/json" } })
            .then(function (r) {
                if (!r.ok) { throw new Error(r.status === 429 ? "muitas requisições desta rede, aguarde" : "HTTP " + r.status); }
                return r.json();
            })
            .then(render)
            .catch(function (e) {
                var st = $("live-status"); if (st) { st.textContent = "erro ao consultar o servidor (" + e.message + ")."; st.className = "small text-danger"; }
            });
    }

    function start() {
        if (timer) return;
        if (!chartProto) initCharts();
        $("live-start").disabled = true;
        $("live-stop").disabled = false;
        ligado = true;
        retomar();
    }

    function retomar() {
        if (timer || !ligado || document.hidden) return;
        tick();
        timer = setInterval(tick, INTERVALO_MS);
    }

    function pausar() {
        if (timer) { clearInterval(timer); timer = null; }
    }

    function stop() {
        ligado = false;
        pausar();
        $("live-start").disabled = false;
        $("live-stop").disabled = true;
        var st = $("live-status"); if (st) { st.textContent = "parado"; st.className = "small text-secondary"; }
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (!$("chart-proto")) return; // página sem o painel ao vivo
        $("live-start").addEventListener("click", start);
        $("live-stop").addEventListener("click", stop);
        document.addEventListener("visibilitychange", function () {
            if (document.hidden) { pausar(); } else { retomar(); }
        });
    });
})(window);
