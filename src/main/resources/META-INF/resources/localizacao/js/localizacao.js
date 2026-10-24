/* Módulo Localização: mapa Leaflet compartilhado + consulta de CEP (ViaCEP + OSM). */
(function (w) {
    "use strict";

    var map = null;
    var marker = null;

    function initMap() {
        var el = document.getElementById("loc-map");
        if (!el || typeof L === "undefined") {
            return;
        }
        map = L.map("loc-map", { scrollWheelZoom: false }).setView([-14.235, -51.925], 4);
        document.querySelectorAll("#loc-map .leaflet-control-zoom a").forEach(function (a) {
            a.setAttribute("data-icone-excecao", "zoom do mapa (Leaflet): o + e o − desenhados no botão são o ícone");
        });
        // Crédito do Leaflet sem o title de propaganda da biblioteca, que o adaptador viraria dica num link de destino óbvio.
        map.attributionControl.setPrefix('<a href="https://leafletjs.com/">Leaflet</a>');
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(map);
        // Corrige o render parcial (tiles só no canto): o Leaflet precisa recalcular o tamanho
        // do container depois do layout/fontes carregarem.
        var fix = function () { if (map) map.invalidateSize(); };
        setTimeout(fix, 200);
        setTimeout(fix, 600);
        w.addEventListener("resize", fix);
        w.addEventListener("load", fix);
    }

    // Exposto para o painel GeoIP (geo-panel.js) plotar resultados no mesmo mapa.
    function setMarker(lat, lon, label) {
        if (!map || lat === null || lat === undefined || lon === null || lon === undefined) {
            return false;
        }
        var la = parseFloat(lat);
        var lo = parseFloat(lon);
        if (isNaN(la) || isNaN(lo)) {
            return false;
        }
        if (marker) {
            map.removeLayer(marker);
        }
        marker = L.marker([la, lo]).addTo(map);
        if (label) {
            // Texto, nunca HTML: o rótulo vem de provedor externo (cidade do GeoIP, logradouro do ViaCEP) e o
            // Leaflet grava string com innerHTML. Elemento com textContent não interpreta marcação (BACK-03).
            var conteudo = document.createElement("span");
            conteudo.textContent = String(label);
            marker.bindPopup(conteudo).openPopup();
        }
        map.invalidateSize();
        map.setView([la, lo], 13);
        return true;
    }

    function item(label, value) {
        var wrap = document.createElement("div");
        wrap.className = "loc-result-item";
        var k = document.createElement("span");
        k.className = "k";
        k.textContent = label;
        var v = document.createElement("span");
        v.className = "v";
        v.textContent = (value === null || value === undefined || value === "") ? "—" : String(value);
        wrap.appendChild(k);
        wrap.appendChild(v);
        return wrap;
    }

    function painel() {
        var el = document.getElementById("loc-resultado");
        el.classList.remove("d-none");
        el.textContent = "";
        return el;
    }

    function titulo(el, texto, icone) {
        var h = document.createElement("h3");
        h.className = "h5 loc-form-title";
        var ic = document.createElement("span");
        ic.className = "material-symbols-outlined";
        ic.setAttribute("aria-hidden", "true");
        ic.setAttribute("translate", "no");
        ic.textContent = icone;
        h.appendChild(ic);
        h.appendChild(document.createTextNode(" " + texto));
        el.appendChild(h);
    }

    function grid(el) {
        var g = document.createElement("div");
        g.className = "loc-result-grid";
        el.appendChild(g);
        return g;
    }

    function aviso(el, texto, classe) {
        var p = document.createElement("p");
        p.className = classe || "loc-alerta";
        p.textContent = texto;
        el.appendChild(p);
    }

    // Resposta HTTP que não é 2xx (ex.: 429 do limite de taxa) vira {ok:false, mensagem} — sem isto o
    // corpo {"erro":...} era desenhado como "Endereço por CEP" com todos os campos vazios.
    function lerResposta(r) {
        return r.json().catch(function () { return {}; }).then(function (j) {
            if (r.ok) {
                return j;
            }
            return { ok: false, mensagem: (j && (j.erro || j.mensagem)) || ("Falha na consulta (HTTP " + r.status + ").") };
        });
    }

    function renderCep(data) {
        if (!data || data.ok === false) {
            var e = painel();
            titulo(e, "CEP não localizado", "error");
            aviso(e, (data && data.mensagem) || "Verifique o CEP informado.", "loc-alerta loc-erro");
            return;
        }
        var el = painel();
        titulo(el, "Endereço por CEP", "map");
        var g = grid(el);
        g.appendChild(item("CEP", data.cep));
        g.appendChild(item("Logradouro", data.logradouro));
        g.appendChild(item("Bairro", data.bairro));
        g.appendChild(item("Cidade", data.cidade));
        g.appendChild(item("UF", data.uf));
        g.appendChild(item("DDD", data.ddd));
        g.appendChild(item("Coordenadas", (data.lat != null && data.lon != null)
            ? (data.lat + ", " + data.lon) : null));
        g.appendChild(item("Fonte", data.fonte));
        if (data.geocoded) {
            setMarker(data.lat, data.lon, (data.logradouro || data.cidade || "CEP " + (data.cep || "")));
        } else if (data.aviso) {
            aviso(el, data.aviso, "loc-alerta");
        }
    }

    function renderGps(data, coords) {
        var el = painel();
        titulo(el, "Minha localização (GPS)", "my_location");
        var g = grid(el);
        g.appendChild(item("Latitude", coords.latitude.toFixed(6)));
        g.appendChild(item("Longitude", coords.longitude.toFixed(6)));
        g.appendChild(item("Precisão (m)", coords.accuracy ? Math.round(coords.accuracy) : null));
        if (data && data.ok === false && data.mensagem) {
            aviso(el, data.mensagem, "loc-alerta loc-erro");
        }
        if (data && data.ok !== false) {
            g.appendChild(item("Logradouro", data.logradouro));
            g.appendChild(item("Bairro", data.bairro));
            g.appendChild(item("Cidade", data.cidade));
            g.appendChild(item("UF", data.uf));
            g.appendChild(item("CEP", data.cep));
        }
        setMarker(coords.latitude, coords.longitude, "Minha localização (GPS)");
        aviso(el, "Esta é a posição real do aparelho (com sua permissão) — bem diferente do que o IP indica.");
    }

    function usarGps() {
        var status = document.getElementById("loc-gps-status");
        if (!navigator.geolocation) {
            if (status) status.textContent = "Geolocalização não suportada neste navegador.";
            return;
        }
        if (status) status.textContent = "Pedindo permissão…";
        navigator.geolocation.getCurrentPosition(function (pos) {
            if (status) status.textContent = "Localização obtida.";
            var c = pos.coords;
            fetch("/localizacao/api/gps?lat=" + c.latitude + "&lon=" + c.longitude, { headers: { Accept: "application/json" } })
                .then(lerResposta)
                .then(function (data) { renderGps(data, c); })
                .catch(function () { renderGps({ ok: false }, c); });
        }, function (err) {
            if (status) status.textContent = "Permissão negada ou indisponível (" + err.message + ").";
        }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    }

    function buscarCep() {
        var cep = (document.getElementById("loc-cep").value || "").trim();
        fetch("/localizacao/api/cep?cep=" + encodeURIComponent(cep), { headers: { Accept: "application/json" } })
            .then(lerResposta)
            .then(renderCep)
            .catch(function () {
                var e = painel();
                titulo(e, "Falha na consulta", "error");
                aviso(e, "Falha de rede ao consultar o CEP.", "loc-alerta loc-erro");
            });
    }

    // O mapa vive numa sub-aba; ao ficar visível o Leaflet precisa recalcular o tamanho,
    // senão renderiza os tiles só num canto (container tinha 0px quando estava oculto).
    function observarAba() {
        var el = document.getElementById("loc-map");
        var painel = el && el.closest ? el.closest(".tab-panel") : null;
        if (!painel || !w.MutationObserver) {
            return;
        }
        new MutationObserver(function () {
            if (painel.classList.contains("active") && map) {
                setTimeout(function () { map.invalidateSize(); }, 60);
            }
        }).observe(painel, { attributes: true, attributeFilter: ["class"] });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initMap();
        observarAba();
        var formCep = document.getElementById("form-loc-cep");
        if (formCep) {
            formCep.addEventListener("submit", buscarCep);
        }
        var btnGps = document.getElementById("btn-loc-gps");
        if (btnGps) {
            btnGps.addEventListener("click", usarGps);
        }
    });

    /** Tira o marcador (FRONT-13: a consulta sem coordenada deixava o da consulta anterior). */
    function limparMarcador() {
        if (map && marker) {
            map.removeLayer(marker);
            marker = null;
        }
    }

    w.LocMap = { setMarker: setMarker, limparMarcador: limparMarcador,
        invalidate: function () { if (map) map.invalidateSize(); } };
})(window);
