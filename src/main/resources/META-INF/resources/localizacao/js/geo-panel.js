/* Painel GeoIP da página Localização (movido da aba "Região Geo" da Análise Didática).
 * Usa o renderer global window.renderGeoRelatorio (geo_report.js), o endpoint /api/informacoes/geo
 * e o histórico /history (modo=geo). Plota coordenadas no mapa compartilhado (window.LocMap). */
(function (w) {
    "use strict";

    var PAGE_SIZE = 5;
    var historico = [];
    var paginaIdx = 1;

    function $(id) {
        return document.getElementById(id);
    }

    function geoUrl(ipOpcional) {
        var u = new URL("/api/informacoes/geo", w.location.origin);
        var input = $("geo-ip-digitar");
        var ip = ipOpcional !== undefined ? ipOpcional : (input ? input.value.trim() : "");
        if (ip) {
            u.searchParams.set("ip", ip);
        }
        return u.toString();
    }

    function setLoading(on) {
        var l = $("geo-loading");
        if (l) l.classList.toggle("d-none", !on);
    }

    function td(text) {
        var c = document.createElement("td");
        c.textContent = (text === null || text === undefined || text === "") ? "—" : String(text);
        return c;
    }

    function renderHistorico() {
        var body = $("geo-history-body");
        var counter = $("geo-history-counter");
        var pageLbl = $("geo-history-page");
        var prev = $("geo-history-prev");
        var next = $("geo-history-next");
        if (!body) return;

        var total = historico.length;
        var totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
        if (paginaIdx > totalPages) paginaIdx = totalPages;
        var start = (paginaIdx - 1) * PAGE_SIZE;
        var pageItems = historico.slice(start, start + PAGE_SIZE);

        body.textContent = "";
        if (pageItems.length === 0) {
            var tr = document.createElement("tr");
            var cell = document.createElement("td");
            cell.colSpan = 7;
            cell.className = "text-secondary";
            cell.textContent = "Sem histórico GeoIP no momento.";
            tr.appendChild(cell);
            body.appendChild(tr);
        } else {
            pageItems.forEach(function (it) {
                var g = (it.geo_consulta && typeof it.geo_consulta === "object") ? it.geo_consulta : {};
                var reservado = g.reservado === true || g.motivo === "private_or_local";
                var regiaoCidade = [g.regiao, g.cidade].filter(Boolean).join(" / ");
                var row = document.createElement("tr");
                row.className = "small";
                row.appendChild(td(it.timestamp_utc || it.timestamp));
                row.appendChild(td(g.ip || it.ip_entrada));
                row.appendChild(td(reservado ? "Reservado" : (g.tipo || "—")));
                row.appendChild(td(reservado ? "—" : g.pais));
                row.appendChild(td(reservado ? "—" : regiaoCidade));
                row.appendChild(td(reservado ? "—" : g.isp));
                row.appendChild(td(reservado ? "N/A" : (g.risco_nivel || g.risco_badge)));
                body.appendChild(row);
            });
        }
        if (counter) counter.textContent = total + " registro(s)";
        if (pageLbl) pageLbl.textContent = "Página " + paginaIdx + "/" + totalPages;
        if (prev) prev.disabled = paginaIdx <= 1;
        if (next) next.disabled = paginaIdx >= totalPages;
    }

    function refreshHistorico() {
        return fetch("/history", { headers: { Accept: "application/json" }, credentials: "same-origin" })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (data) {
                var items = Array.isArray(data) ? data : (data.items || data.history || []);
                historico = items.filter(function (it) { return (it.modo || "").toLowerCase() === "geo"; });
                renderHistorico();
            })
            .catch(function () { historico = []; renderHistorico(); });
    }

    function applyPayload(j) {
        var elIp = $("geo-ip");
        var okBox = $("geo-sucesso");
        var failBox = $("geo-falha");
        var elMsg = $("geo-msg");
        var reportRoot = $("geo-report-root");
        var refBox = $("geo-ref-ligacao");
        var refIpEl = $("geo-ref-ip");

        if (elIp) elIp.textContent = j.consultado || j.ip || "—";
        if (refIpEl && j.cliente_ip) refIpEl.textContent = j.cliente_ip;
        if (refBox) refBox.classList.toggle("d-none", j.modo !== "manual");

        if (typeof w.renderGeoRelatorio === "function" && reportRoot) {
            w.renderGeoRelatorio(reportRoot, j);
            if (okBox) okBox.classList.remove("d-none");
            if (failBox) failBox.classList.add("d-none");
        } else if (failBox && elMsg) {
            if (okBox) okBox.classList.add("d-none");
            failBox.classList.remove("d-none");
            elMsg.textContent = j.mensagem || j.erro || "Indisponível.";
        }

        // Plota no mapa compartilhado e no globo 3D quando houver coordenadas públicas.
        var lat = j.latitude != null ? j.latitude : j.lat;
        var lon = j.longitude != null ? j.longitude : j.lon;
        var label = (j.ip || "") + (j.cidade ? " · " + j.cidade : "");
        if (w.LocMap && lat != null && lon != null) {
            w.LocMap.setMarker(lat, lon, label);
        }
        if (lat != null && lon != null) {
            if (w.GeoGlobe) {
                w.GeoGlobe.setLocation(lat, lon, label);
            } else {
                w.__geoPendingLoc = { lat: lat, lon: lon, label: label }; // globo ainda carregando
            }
        } else {
            // Sem coordenada (IP inválido, privado, serviço fora): o mapa e o globo não podem continuar
            // mostrando o lugar da consulta anterior como se fosse desta (FRONT-13).
            if (w.LocMap && w.LocMap.limparMarcador) w.LocMap.limparMarcador();
            if (w.GeoGlobe && w.GeoGlobe.limpar) w.GeoGlobe.limpar();
            w.__geoPendingLoc = null;
        }
    }

    function fetchGeo(ipForced) {
        setLoading(true);
        fetch(geoUrl(ipForced), { credentials: "same-origin", headers: { Accept: "application/json" } })
            .then(function (r) { return r.text().then(function (t) { return { r: r, t: t }; }); })
            .then(function (res) {
                var j;
                try { j = JSON.parse(res.t); } catch (_) { throw new Error("Resposta JSON inválida."); }
                if (!res.r.ok) throw new Error(j.erro || j.mensagem || ("HTTP " + res.r.status));
                applyPayload(j);
                return refreshHistorico();
            })
            .catch(function (e) {
                var okBox = $("geo-sucesso");
                var failBox = $("geo-falha");
                var elMsg = $("geo-msg");
                var reportRoot = $("geo-report-root");
                if (reportRoot) reportRoot.innerHTML = "";
                if (okBox) okBox.classList.add("d-none");
                if (failBox) failBox.classList.remove("d-none");
                if (elMsg) elMsg.textContent = "Erro ao consultar: " + e;
            })
            .finally(function () { setLoading(false); });
    }

    // Dicas e botões de ajuda pelo tooltip canônico (adaptador field-tooltips.js), sem depender do Bootstrap.
    function initTooltips() {
        var formGeo = document.getElementById("form-geo-ip");
        if (formGeo && window.FieldTooltips) window.FieldTooltips.init(formGeo);
    }

    document.addEventListener("DOMContentLoaded", function () {
        var btnLocalizar = $("btn-geo-localizar");
        var btnLigacao = $("btn-geo-refresh");
        var input = $("geo-ip-digitar");
        if (!btnLocalizar || !input) {
            return; // página sem painel geo
        }
        btnLocalizar.addEventListener("click", function () { fetchGeo(); });
        if (btnLigacao) {
            btnLigacao.addEventListener("click", function () { input.value = ""; fetchGeo(""); });
        }
        input.addEventListener("keydown", function (e) {
            if (e.key === "Enter") { e.preventDefault(); fetchGeo(); }
        });
        var prev = $("geo-history-prev");
        var next = $("geo-history-next");
        if (prev) prev.addEventListener("click", function () {
            if (paginaIdx > 1) { paginaIdx -= 1; renderHistorico(); }
        });
        if (next) next.addEventListener("click", function () {
            var totalPages = Math.max(1, Math.ceil(historico.length / PAGE_SIZE));
            if (paginaIdx < totalPages) { paginaIdx += 1; renderHistorico(); }
        });

        initTooltips();
        refreshHistorico();
        fetchGeo("");
    });
})(window);
