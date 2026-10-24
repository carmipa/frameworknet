/* Globo 3D (Globe.gl + Three.js). Fábrica que cria globos independentes:
 *  - #geo-globe  → aba GeoIP (controlado por geo-panel.js via window.GeoGlobe)
 *  - #priv-globe → aba Vazamento/Privacidade (controlado por privacidade.js via window.PrivGlobe)
 * Cada globo plota o ponto do IP com marcador + anel. Biblioteca e texturas servidas pelo próprio site
 * (localizacao/vendor, gerado por scripts/vendor-globo): sem esm.sh nem jsdelivr (auditoria SEC-05).
 * Se o WebGL falhar, chama opts.onFail. */
import { THREE, Globe } from "/localizacao/vendor/globo-three.mjs?v=20261002";

(function () {
    "use strict";

    var EARTH_IMG = "/localizacao/vendor/texturas/earth-blue-marble.webp";
    var BUMP_IMG = "/localizacao/vendor/texturas/earth-topology.webp";
    var CLOUDS_IMG = "/localizacao/vendor/texturas/clouds.webp";
    var CLOUDS_ALT = 0.01;
    var CLOUDS_SPEED = -0.006;

    // WCAG 2.2.2 (nível A): o que se move sozinho tem de poder parar, e quem pediu menos movimento no sistema
    // (prefers-reduced-motion) recebe o globo parado, sem nuvens girando nem anéis pulsando (UX-07).
    var MENOS_MOVIMENTO = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

    function criarGlobo(el, opts) {
        opts = opts || {};
        var globe = null;
        var clouds = null;
        var ready = false;
        var pendente = null;
        var parado = MENOS_MOVIMENTO;

        /** Botão "Pausar/Girar" logo depois do globo (o quadro do globo é decorativo, aria-hidden). */
        function criarBotaoDePausa(ctrl) {
            var quadro = el.closest("[aria-hidden='true']") || el;
            var botao = document.createElement("button");
            botao.type = "button";
            botao.className = "aed-btn aed-btn-neutral btn-sm globo-pausa";
            function pintar() {
                botao.setAttribute("aria-pressed", parado ? "true" : "false");
                botao.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">'
                    + (parado ? "play_arrow" : "pause") + "</span> " + (parado ? "Girar o globo" : "Pausar o giro");
            }
            botao.addEventListener("click", function () {
                parado = !parado;
                ctrl.autoRotate = !parado;
                pintar();
            });
            pintar();
            quadro.insertAdjacentElement("afterend", botao);
        }

        function apply(loc) {
            if (!ready || !globe || !loc) return;
            var lat = Number(loc.lat);
            var lon = Number(loc.lon);
            if (!isFinite(lat) || !isFinite(lon)) return;
            var data = [{ lat: lat, lng: lon, label: loc.label || "" }];
            globe.pointsData(data).ringsData(MENOS_MOVIMENTO ? [] : data);
            globe.pointOfView({ lat: lat, lng: lon, altitude: 1.5 }, MENOS_MOVIMENTO ? 0 : 1500);
        }

        function build() {
            if (!el || globe) return;
            try {
                globe = Globe()(el)
                    .backgroundColor("rgba(0,0,0,0)")
                    .globeImageUrl(EARTH_IMG)
                    .bumpImageUrl(BUMP_IMG)
                    .showAtmosphere(true)
                    .atmosphereColor("#4da6ff")
                    .atmosphereAltitude(0.18)
                    .pointsData([])
                    .pointLat("lat")
                    .pointLng("lng")
                    .pointColor(function () { return "#ff3131"; })
                    .pointAltitude(0.08)
                    .pointRadius(0.5)
                    // O rótulo vem do provedor de GeoIP (cidade, IP): escapado antes de virar HTML.
                    .pointLabel(function (p) {
                        return "<strong>" + String(p.label || "").replace(/&/g, "&amp;").replace(/</g, "&lt;")
                            .replace(/>/g, "&gt;").replace(/"/g, "&quot;") + "</strong>";
                    })
                    .ringsData([])
                    .ringLat("lat")
                    .ringLng("lng")
                    .ringColor(function () { return function (t) { return "rgba(255,49,49," + (1 - t) + ")"; }; })
                    .ringMaxRadius(4)
                    .ringPropagationSpeed(2)
                    .ringRepeatPeriod(900);
            } catch (e) {
                if (opts.onFail) opts.onFail();
                return;
            }

            var ctrl = globe.controls();
            ctrl.autoRotate = !parado;
            ctrl.autoRotateSpeed = 0.5;
            ctrl.enableDamping = true;

            new THREE.TextureLoader().load(CLOUDS_IMG, function (tex) {
                clouds = new THREE.Mesh(
                    new THREE.SphereGeometry(globe.getGlobeRadius() * (1 + CLOUDS_ALT), 75, 75),
                    new THREE.MeshPhongMaterial({ map: tex, transparent: true, opacity: 0.55, depthWrite: false })
                );
                globe.scene().add(clouds);
                (function spin() {
                    if (clouds && !parado) clouds.rotation.y += CLOUDS_SPEED * Math.PI / 180;
                    requestAnimationFrame(spin);
                })();
            }, undefined, function () { /* nuvens são opcionais */ });

            function resize() {
                var w = el.clientWidth || 420;
                var h = el.clientHeight || 420;
                globe.width(w).height(h);
            }
            resize();
            if (window.ResizeObserver) new ResizeObserver(resize).observe(el);
            else window.addEventListener("resize", resize);

            ready = true;
            criarBotaoDePausa(ctrl);
            globe.pointOfView({ lat: -14, lng: -52, altitude: 1.9 }, 0); // Brasil, enquanto não há ponto
            if (pendente) apply(pendente);
        }

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", build);
        } else {
            build();
        }

        return {
            setLocation: function (lat, lon, label) {
                pendente = { lat: lat, lon: lon, label: label };
                apply(pendente);
            },
            /** FRONT-13: consulta sem coordenada não pode deixar o ponto da consulta anterior. */
            limpar: function () {
                pendente = null;
                if (!ready || !globe) return;
                globe.pointsData([]).ringsData([]);
                globe.pointOfView({ lat: -14, lng: -52, altitude: 1.9 }, MENOS_MOVIMENTO ? 0 : 800);
            }
        };
    }

    // Globo da aba GeoIP — mantém o contrato usado por geo-panel.js (window.__geoPendingLoc).
    var geo = criarGlobo(document.getElementById("geo-globe"), {
        onFail: function () {
            var wrap = document.querySelector(".geo-globe-wrap");
            if (wrap) wrap.remove();
            var box = document.getElementById("geo-sucesso");
            if (box) box.classList.remove("geo-split");
        }
    });
    window.GeoGlobe = {
        setLocation: function (lat, lon, label) {
            window.__geoPendingLoc = { lat: lat, lon: lon, label: label };
            geo.setLocation(lat, lon, label);
        },
        limpar: function () {
            window.__geoPendingLoc = null;
            geo.limpar();
        }
    };
    if (window.__geoPendingLoc) {
        geo.setLocation(window.__geoPendingLoc.lat, window.__geoPendingLoc.lon, window.__geoPendingLoc.label);
    }

    // Globo da aba Vazamento/Privacidade (opcional — só se o container existir).
    var privEl = document.getElementById("priv-globe");
    if (privEl) {
        var priv = criarGlobo(privEl, {
            onFail: function () {
                var wrap = document.getElementById("priv-globe-wrap");
                if (wrap) wrap.classList.add("d-none");
            }
        });
        window.PrivGlobe = {
            setLocation: function (lat, lon, label) { priv.setLocation(lat, lon, label); }
        };
    }
})();
