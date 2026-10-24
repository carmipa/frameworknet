/**
 * Relatório GeoIP a partir do JSON da API (/api/informacoes/geo).
 * Mantém o mesmo layout didático do partial Jinja geo_relatorio.html.
 */
(function (w) {
    "use strict";

    function esc(s) {
        if (s == null || s === "") return "";
        return String(s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function renderGeoRelatorio(rootEl, j) {
        if (!rootEl || !j) {
            return;
        }
        rootEl.innerHTML = "";

        if (j.erro) {
            rootEl.innerHTML =
                '<div class="alert alert-danger mt-3"><strong>' + HtmlEscape.icone("warning", "Erro:") + '</strong> ' +
                esc(j.erro) +
                "</div>";
            return;
        }

        if (j.reservado === true || j.motivo === "private_or_local") {
            rootEl.innerHTML =
                '<div class="card geo-card-reserved border-secondary mt-3">' +
                '<div class="card-header bg-secondary text-white d-flex justify-content-between align-items-center">' +
                '<span><strong>🏠 IP reservado / rede local</strong></span>' +
                '<span class="badge bg-dark">' +
                esc(j.tipo) +
                "</span></div>" +
                '<div class="card-body"><div class="row mb-3"><div class="col-12">' +
                '<h3 class="h5 font-monospace geo-ip-readout mb-2">' +
                '<span class="material-symbols-outlined" aria-hidden="true" translate="no">dns</span> ' +
                esc(j.ip) +
                "</h3>" +
                '<span class="badge bg-secondary me-1">' +
                esc(j.reservado_motivo || "Reservado") +
                '</span><span class="badge bg-dark">Sem país no mapa público</span></div></div><hr class="border-secondary opacity-75">' +
                '<p class="geo-report-muted mb-1" style="font-size:.85rem;">' +
                "Endereços reservados não são roteados na Internet pública. " +
                "Geolocalização externa não é aplicável para este tipo de IP.</p>" +
                '<p class="geo-report-muted mb-0" style="font-size:.8rem;">' +
                '<strong class="text-light">Exemplos de uso:</strong> redes internas (RFC 1918), ' +
                "loopback (127.x), link-local (169.254.x), CGNAT (100.64.x).</p>" +
                "</div></div>";
            return;
        }

        if (j.ok) {
            const rawIso = String(j.pais_codigo || j.codigo_pais || "")
                .replace(/\s+/g, "")
                .toUpperCase();
            // FRONT-18: bandeira local (emoji pelo código ISO), nunca imagem de CDN. Sem a fonte de emoji
            // (Windows), o navegador mostra as duas letras do país — texto que ainda informa.
            const bandeiraEmoji =
                j.pais_bandeira && j.pais_bandeira !== "🌐"
                    ? j.pais_bandeira
                    : /^[A-Z]{2}$/.test(rawIso) ? String.fromCodePoint.apply(null, rawIso.split("").map(function (c) { return 127397 + c.charCodeAt(0); })) : "🌐";
            const cc = esc(j.pais_codigo || j.codigo_pais || "");
            const lat = j.latitude != null ? j.latitude : j.lat;
            const lon = j.longitude != null ? j.longitude : j.lon;
            const hasCoords = lat != null && lon != null;
            const rcolor = esc(j.risco_badge_color || "secondary");
            const flagHeaderHtml =
                '<span style="font-size:1.4rem;" aria-hidden="true">' + esc(bandeiraEmoji) + "</span>";
            const flagPaísImg = "";
            const locRows = [];
            locRows.push(
                '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">País</div>' +
                '<div class="fw-bold d-flex align-items-center flex-wrap gap-2">' +
                flagPaísImg +
                "<span>" +
                esc(bandeiraEmoji) + " " + esc(j.pais || "—") +
                "</span></div>"
            );
            if (cc) {
                locRows[locRows.length - 1] +=
                    '<div><span class="badge border text-secondary" style="font-size:.7rem;">' +
                    cc +
                    "</span></div>";
            }
            locRows[locRows.length - 1] += "</div></div>";
            if (j.regiao) {
                locRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Estado / Região</div>' +
                    '<div class="fw-bold">' +
                    esc(j.regiao) +
                    "</div></div></div>"
                );
            }
            if (j.cidade) {
                locRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Cidade</div>' +
                    '<div class="fw-bold">' +
                    esc(j.cidade) +
                    "</div></div></div>"
                );
            }
            if (j.timezone) {
                locRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Fuso Horário</div>' +
                    '<div class="fw-bold">🕐 ' +
                    esc(j.timezone) +
                    "</div></div></div>"
                );
            }
            if (hasCoords) {
                let coordCell =
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Coordenadas</div>' +
                    '<div class="fw-bold" style="font-size:.85rem;">' +
                    esc(lat) +
                    ", " +
                    esc(lon) +
                    "</div>";
                if (j.maps_url) {
                    coordCell +=
                        '<a href="' +
                        esc(j.maps_url) +
                        '" target="_blank" rel="noopener" class="btn btn-outline-info btn-sm mt-1 py-0 px-2" style="font-size:.7rem;">🗺️ Ver no Maps</a>';
                }
                coordCell += "</div></div>";
                locRows.push(coordCell);
            }

            const netRows = [];
            if (j.isp) {
                netRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">ISP (Provedor)</div>' +
                    '<div class="fw-bold">' +
                    esc(j.isp) +
                    "</div></div></div>"
                );
            }
            if (j.org && j.org !== j.isp) {
                netRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Organização</div>' +
                    '<div class="fw-bold">' +
                    esc(j.org) +
                    "</div></div></div>"
                );
            }
            if (j.as_name) {
                let asCell =
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Sistema Autônomo (AS)</div>' +
                    '<div class="fw-bold font-monospace" style="font-size:.85rem;">' +
                    esc(j.as_name) +
                    "</div>";
                if (j.as_cidr) {
                    asCell +=
                        '<div class="text-muted font-monospace" style="font-size:.75rem;">' +
                        esc(j.as_cidr) +
                        "</div>";
                }
                asCell += "</div></div>";
                netRows.push(asCell);
            }
            if (j.cidr) {
                netRows.push(
                    '<div class="col-12 col-md-6"><div class="p-2 rounded" style="background:rgba(255,255,255,0.05);overflow:hidden;">' +
                    '<div class="text-muted" style="font-size:.7rem;text-transform:uppercase;letter-spacing:0.04em;">Bloco IP (CIDR)</div>' +
                    '<div class="fw-bold font-monospace" style="font-size:.85rem;">' +
                    esc(j.cidr) +
                    "</div></div></div>"
                );
            }
            const netSection =
                netRows.length > 0
                    ? '<h3 class="h6 text-primary border-bottom pb-1 mb-2">🌐 Rede / ASN</h3>' +
                      '<div class="row g-2 mb-3" style="align-items:flex-start;">' +
                      netRows.join("") +
                      "</div>"
                    : "";

            const proxyBad = !!j.proxy;
            const connBadges =
                '<span class="badge fs-6 px-3 py-2 ' +
                (proxyBad ? "bg-danger" : "bg-success") +
                '">' +
                HtmlEscape.semaforo(j.proxy_flag || (proxyBad ? "🔴 Proxy/VPN detectado" : "🟢 Conexão direta")) +
                "</span>";
            let extraConn = "";
            if (j.hosting_flag) {
                extraConn +=
                    '<span class="badge bg-warning text-dark fs-6 px-3 py-2">' +
                    esc(j.hosting_flag) +
                    "</span>";
            }
            if (j.mobile_flag) {
                extraConn +=
                    '<span class="badge bg-info text-dark fs-6 px-3 py-2">' +
                    esc(j.mobile_flag) +
                    "</span>";
            }
            let resCorp = "";
            if (!j.hosting_flag && !j.mobile_flag && !j.proxy) {
                resCorp =
                    '<span class="badge bg-secondary fs-6 px-3 py-2">🖥️ Conexão residencial / corporativa</span>';
            }
            const locNote =
                !j.regiao && !j.cidade
                    ? '<p class="small text-secondary mb-3">Região e cidade não constam na base GeoIP local para este IP (comum em CDNs / anycast).</p>'
                    : "";

            rootEl.innerHTML =
                '<div class="card border-primary mt-3">' +
                '<div class="card-header d-flex justify-content-between align-items-center bg-primary bg-opacity-25 flex-wrap gap-2">' +
                '<div class="d-flex align-items-center flex-wrap gap-2">' +
                flagHeaderHtml +
                '<strong class="font-monospace geo-ip-readout">' +
                esc(j.ip) +
                '</strong><span class="badge bg-secondary">' +
                esc(j.tipo) +
                '</span></div><span class="badge bg-' +
                rcolor +
                ' fs-6 px-3">' +
                esc(j.risco_badge || "") +
                "</span></div>" +
                '<div class="card-body">' +
                '<h3 class="h6 text-primary border-bottom pb-1 mb-2">' + HtmlEscape.icone("place", "Localização") + '</h3>' +
                '<div class="row g-2 mb-3" style="align-items:flex-start;">' +
                locRows.join("") +
                "</div>" +
                locNote +
                netSection +
                '<h3 class="h6 text-primary border-bottom pb-1 mb-2">🔍 Tipo de Conexão</h3>' +
                '<div class="d-flex flex-wrap gap-2 mb-3">' +
                connBadges +
                extraConn +
                resCorp +
                "</div>" +
                '<h3 class="h6 text-primary border-bottom pb-1 mb-2">🛡️ Avaliação de Risco (GRC)</h3>' +
                '<div class="alert alert-' +
                rcolor +
                ' py-2 mb-2">' +
                '<div class="d-flex align-items-center gap-2 mb-1"><strong>Nível:</strong>' +
                '<span class="badge bg-' +
                rcolor +
                ' fs-6">' +
                esc(j.risco_badge || "") +
                "</span></div>" +
                '<p class="mb-0" style="font-size:.85rem;">' +
                esc(j.risco_recomendacao || "") +
                "</p></div>" +
                (j.fonte
                    ? '<div class="text-end mt-2"><span class="text-muted" style="font-size:.75rem;">' + HtmlEscape.icone("satellite_alt", "Dados:") + ' <strong>' +
                      esc(j.fonte) +
                      "</strong></span></div>"
                    : "") +
                "</div></div>";
            return;
        }

        // O motivo chega como código ("invalid", "network"…); na tela vai o título em português (FRONT-13).
        var TITULOS = {
            invalid: "Endereço inválido",
            empty: "IP não identificado",
            private_or_local: "Endereço local ou privado",
            network: "Localização indisponível no momento"
        };
        rootEl.innerHTML =
            '<div class="alert alert-secondary mt-3 mb-0" role="alert"><strong>' +
            esc(TITULOS[j.motivo] || "Aviso") +
            ":</strong> " +
            esc(j.mensagem || "Indisponível.") +
            "</div>";
    }

    w.renderGeoRelatorio = renderGeoRelatorio;
})(typeof window !== "undefined" ? window : globalThis);
