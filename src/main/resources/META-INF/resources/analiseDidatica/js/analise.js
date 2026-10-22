(function (w) {
    "use strict";

    let geoInitialized = false;

    function showTab(tabId) {
        const tabButtons = document.querySelectorAll(".tab-trigger");
        const tabPanels = document.querySelectorAll(".tab-panel");
        tabButtons.forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === tabId));
        tabPanels.forEach((panel) => panel.classList.toggle("active", panel.dataset.tabPanel === tabId));
        const btnLimpar = document.getElementById("btn-limpar-tela");
        if (btnLimpar) {
            // A Análise vive em /analise desde que a landing assumiu "/".
            // Reescrever para "/?tab=" mandava o "Limpar Tela" para a home.
            btnLimpar.setAttribute("href", "/analise?tab=" + encodeURIComponent(tabId));
        }
        if (tabId === "geo") {
            initGeoPanel();
        }
    }

    function initGeoPanel() {
        if (geoInitialized) {
            return;
        }
        const btnLigacao = document.getElementById("btn-geo-refresh");
        const btnLocalizar = document.getElementById("btn-geo-localizar");
        const inputIp = document.getElementById("geo-ip-digitar");
        const loading = document.getElementById("geo-loading");
        const okBox = document.getElementById("geo-sucesso");
        const failBox = document.getElementById("geo-falha");
        const elIp = document.getElementById("geo-ip");
        const elMsg = document.getElementById("geo-msg");
        const reportRoot = document.getElementById("geo-report-root");
        const refBox = document.getElementById("geo-ref-ligacao");
        const refIpEl = document.getElementById("geo-ref-ip");
        const geoHistoryBody = document.getElementById("geo-history-body");
        const geoHistoryCounter = document.getElementById("geo-history-counter");
        const geoHistoryPrev = document.getElementById("geo-history-prev");
        const geoHistoryNext = document.getElementById("geo-history-next");
        const geoHistoryPage = document.getElementById("geo-history-page");
        if (!btnLigacao || !btnLocalizar || !inputIp) {
            return;
        }
        geoInitialized = true;

        let geoHistoryItems = [];
        let geoHistoryPageIdx = 1;
        const geoHistoryPageSize = 5;

        const geoUrl = (ipOpcional) => {
            const u = new URL("/api/informacoes/geo", w.location.origin);
            const v = (ipOpcional ?? inputIp.value ?? "").toString().trim();
            if (v) {
                u.searchParams.set("ip", v);
            }
            return u.toString();
        };

        const escHist = (s) => (window.HtmlEscape && window.HtmlEscape.esc) ? window.HtmlEscape.esc(s) : String(s == null ? "" : s);

        const setLoading = (on) => {
            loading && loading.classList.toggle("d-none", !on);
            btnLigacao.disabled = on;
            btnLocalizar.disabled = on;
            inputIp.disabled = on;
        };

        const formatTs = (ts) => {
            if (!ts) return "—";
            const d = new Date(ts);
            if (Number.isNaN(d.getTime())) return ts;
            return d.toLocaleString("pt-BR");
        };

        const parseGeoTema = (tema) => {
            const txt = (tema || "").trim();
            if (txt.startsWith("GeoIP: ")) {
                const data = txt.replace("GeoIP: ", "");
                const parts = data.split("/");
                return { regiao: parts[0] || "—", pais: parts[1] || "—" };
            }
            return { regiao: "—", pais: "—" };
        };

        const riskBadgeClass = (c) => {
            const x = String(c || "").toLowerCase();
            return ["danger", "warning", "success", "secondary"].includes(x) ? x : "secondary";
        };

        const truncHist = (s, n) => {
            const t = String(s || "");
            return t.length > n ? t.slice(0, n) + "…" : t;
        };

        const geoEhLocal = (g) => g && (g.reservado === true || g.motivo === "private_or_local");

        const codigoPaisParaBandeira = (codigo) => {
            const cc = (codigo || "").toUpperCase();
            if (cc === "LOCAL") return "🏠";
            if (!/^[A-Z]{2}$/.test(cc)) return "";
            return String.fromCodePoint(...[...cc].map((c) => 127397 + c.charCodeAt(0)));
        };

        const renderGeoHistory = () => {
            if (!geoHistoryBody || !geoHistoryCounter || !geoHistoryPage || !geoHistoryPrev || !geoHistoryNext) {
                return;
            }
            const total = geoHistoryItems.length;
            const totalPages = Math.max(1, Math.ceil(total / geoHistoryPageSize));
            if (geoHistoryPageIdx > totalPages) geoHistoryPageIdx = totalPages;
            const start = (geoHistoryPageIdx - 1) * geoHistoryPageSize;
            const pageItems = geoHistoryItems.slice(start, start + geoHistoryPageSize);
            if (!pageItems.length) {
                geoHistoryBody.innerHTML = '<tr><td colspan="8" class="text-secondary">Sem histórico GeoIP no momento.</td></tr>';
            } else {
                geoHistoryBody.innerHTML = pageItems.map((it) => {
                    const g = it.geo_consulta && typeof it.geo_consulta === "object" ? it.geo_consulta : null;
                    const fb = parseGeoTema(it.tema);
                    const ip = escHist((g && g.ip) || it.ip_entrada || "—");
                    const tipo = escHist((g && g.tipo) || "IPv4");
                    const ts = escHist(formatTs(it.timestamp_utc || it.timestamp));
                    let paisCell;
                    if (geoEhLocal(g)) {
                        paisCell = '<span class="text-warning me-1" aria-hidden="true">🏠</span><span class="text-light">Local / reservado</span>';
                    } else if (g) {
                        const ccRaw = String(g.pais_codigo || g.codigo_pais || "").replace(/\s+/g, "").toUpperCase();
                        // FRONT-18: bandeira local (emoji), nunca imagem de CDN.
                        const flagPart = '<span class="me-1" aria-hidden="true">'
                            + escHist(codigoPaisParaBandeira(ccRaw) || g.pais_bandeira || "🌐") + "</span>";
                        const ccLab = /^[A-Z]{2}$/.test(ccRaw) ? '<span class="text-secondary">' + escHist(ccRaw) + "</span> " : "";
                        paisCell = flagPart + ccLab + '<span class="text-light">' + escHist(g.pais || "—") + "</span>";
                    } else {
                        const cc = (it.mask_entrada || "").toUpperCase();
                        const flagEmoji = codigoPaisParaBandeira(cc);
                        const flagHtml =
                            cc && cc !== "LOCAL"
                                ? (flagEmoji ? '<span class="me-1" aria-hidden="true">' + flagEmoji + "</span>" : "")
                                : cc === "LOCAL"
                                  ? '<span class="text-warning me-1" aria-hidden="true">🏠</span><span class="text-light">Local</span>'
                                  : "";
                        paisCell = cc === "LOCAL" ? flagHtml : flagHtml + escHist(fb.pais);
                    }
                    let regCid;
                    if (g) {
                        if (geoEhLocal(g)) {
                            regCid = "Rede local / privado";
                        } else {
                            const c = g.cidade ? escHist(g.cidade) + ", " : "";
                            const r = g.regiao ? escHist(g.regiao) : "—";
                            regCid = c + r;
                        }
                    } else {
                        regCid = escHist(fb.regiao);
                    }
                    let ispCell;
                    const ispRaw = g && g.isp != null ? String(g.isp).trim() : "";
                    const ispMeaningful = ispRaw && ispRaw !== "—" && ispRaw !== "-" && ispRaw !== "N/A";
                    if (geoEhLocal(g)) {
                        ispCell = '<span class="text-secondary" style="font-size:.78rem;">Sem ISP público (rede interna)</span>';
                    } else if (g && ispMeaningful) {
                        const orgT = escHist(g.org || "");
                        const isp0 = escHist(truncHist(g.isp, 30));
                        const as0 = g.as_name ? '<br><span class="text-muted font-monospace" style="font-size:.7rem;">' + escHist(truncHist(g.as_name, 25)) + "</span>" : "";
                        ispCell = '<span title="' + orgT + '">' + isp0 + "</span>" + as0;
                    } else {
                        ispCell = '<span class="text-muted">—</span>';
                    }
                    let connCell;
                    if (geoEhLocal(g)) {
                        connCell = '<span class="badge bg-secondary" style="font-size:.7rem;">Reservado</span>';
                    } else if (g) {
                        if (g.proxy) {
                            connCell = '<span class="badge bg-danger" style="font-size:.7rem;">' + HtmlEscape.icone("vpn_lock", "Proxy/VPN") + '</span>';
                        } else if (g.hosting) {
                            connCell = '<span class="badge bg-warning text-dark" style="font-size:.7rem;">' + HtmlEscape.icone("dns", "Datacenter") + '</span>';
                        } else if (g.mobile) {
                            connCell = '<span class="badge bg-info text-dark" style="font-size:.7rem;">📱 Móvel</span>';
                        } else {
                            connCell = '<span class="badge bg-success" style="font-size:.7rem;">' + HtmlEscape.icone("check_circle", "Direto") + '</span>';
                        }
                    } else {
                        connCell = '<span class="text-muted">—</span>';
                    }
                    let riscoCell;
                    if (g && g.risco_badge_color) {
                        const rc = riskBadgeClass(g.risco_badge_color);
                        const rn = escHist(g.risco_nivel || "—");
                        riscoCell = '<span class="badge bg-' + rc + '" style="font-size:.7rem;">' + rn + "</span>";
                    } else {
                        riscoCell = '<span class="text-muted">—</span>';
                    }
                    return "<tr>"
                        + '<td class="font-monospace" style="font-size:.8rem;white-space:nowrap;">' + ts + "</td>"
                        + '<td class="font-monospace fw-bold">' + ip + "</td>"
                        + '<td><span class="badge bg-secondary" style="font-size:.7rem;">' + tipo + "</span></td>"
                        + "<td>" + paisCell + "</td>"
                        + '<td style="font-size:.85rem;">' + regCid + "</td>"
                        + '<td style="font-size:.8rem;">' + ispCell + "</td>"
                        + "<td>" + connCell + "</td>"
                        + "<td>" + riscoCell + "</td>"
                        + "</tr>";
                }).join("");
            }
            geoHistoryCounter.textContent = total + " registro(s)";
            geoHistoryPage.textContent = "Página " + geoHistoryPageIdx + "/" + totalPages;
            geoHistoryPrev.disabled = geoHistoryPageIdx <= 1;
            geoHistoryNext.disabled = geoHistoryPageIdx >= totalPages;
        };

        const refreshGeoHistory = async () => {
            try {
                const r = await fetch("/history", { credentials: "same-origin" });
                const j = await r.json();
                const items = Array.isArray(j.items) ? j.items : [];
                geoHistoryItems = items.filter((it) => (it.modo || "").toLowerCase() === "geo");
                renderGeoHistory();
            } catch (_) {
                geoHistoryItems = [];
                renderGeoHistory();
            }
        };

        const applyPayload = (j) => {
            const analisado = j.consultado != null ? j.consultado : j.cliente_ip;
            if (elIp) {
                elIp.textContent = analisado || j.ip || "—";
            }
            if (refIpEl && j.cliente_ip) {
                refIpEl.textContent = j.cliente_ip;
            }
            if (refBox) {
                refBox.classList.toggle("d-none", j.modo !== "manual");
            }
            if (typeof w.renderGeoRelatorio === "function" && reportRoot) {
                w.renderGeoRelatorio(reportRoot, j);
                okBox && okBox.classList.remove("d-none");
                failBox && failBox.classList.add("d-none");
            } else if (failBox && elMsg) {
                okBox && okBox.classList.add("d-none");
                failBox.classList.remove("d-none");
                elMsg.textContent = j.mensagem || j.erro || "Indisponível.";
            }
        };

        const fetchGeo = async (ipForced) => {
            setLoading(true);
            try {
                const r = await fetch(geoUrl(ipForced), {
                    credentials: "same-origin",
                    headers: { Accept: "application/json" },
                });
                const contentType = (r.headers.get("content-type") || "").toLowerCase();
                const bodyText = await r.text();
                if (!contentType.includes("json")) {
                    throw new Error("O servidor devolveu HTML em vez de JSON.");
                }
                let j;
                try {
                    j = JSON.parse(bodyText);
                } catch (_) {
                    throw new Error("Resposta JSON inválida do servidor.");
                }
                if (!r.ok) {
                    throw new Error(j.erro || j.mensagem || ("HTTP " + r.status));
                }
                applyPayload(j);
                await refreshGeoHistory();
            } catch (e) {
                if (reportRoot) {
                    reportRoot.innerHTML = "";
                }
                okBox && okBox.classList.add("d-none");
                failBox && failBox.classList.remove("d-none");
                if (elMsg) {
                    elMsg.textContent = "Erro ao consultar: " + e;
                }
            } finally {
                setLoading(false);
            }
        };

        btnLocalizar.addEventListener("click", () => fetchGeo());
        btnLigacao.addEventListener("click", () => {
            inputIp.value = "";
            fetchGeo("");
        });
        geoHistoryPrev && geoHistoryPrev.addEventListener("click", () => {
            if (geoHistoryPageIdx > 1) {
                geoHistoryPageIdx -= 1;
                renderGeoHistory();
            }
        });
        geoHistoryNext && geoHistoryNext.addEventListener("click", () => {
            const totalPages = Math.max(1, Math.ceil(geoHistoryItems.length / geoHistoryPageSize));
            if (geoHistoryPageIdx < totalPages) {
                geoHistoryPageIdx += 1;
                renderGeoHistory();
            }
        });
        inputIp.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                fetchGeo();
            }
        });

        // Dicas pelo tooltip canônico (adaptador field-tooltips.js), que também liga os popovers.
        const formGeo = document.getElementById("form-geo-ip");
        if (formGeo && w.FieldTooltips) {
            w.FieldTooltips.init(formGeo);
        }

        w.refreshGeoHistory = refreshGeoHistory;
        refreshGeoHistory();
        fetchGeo("");
    }

    function boot() {
        const root = document.querySelector(".analise-didatica-wrap");
        if (!root) {
            return;
        }
        if (typeof w.initAnaliseWidgets === "function") {
            w.initAnaliseWidgets();
        }
        const initial = root.getAttribute("data-active-tab") || "cidr";
        document.querySelectorAll(".tab-trigger").forEach((btn) => {
            btn.addEventListener("click", () => showTab(btn.dataset.tab));
        });
        showTab(initial);

        const btnCopiar = document.getElementById("btn-copiar-resultado");
        if (btnCopiar) {
            btnCopiar.addEventListener("click", async () => {
                const el = document.getElementById("texto-copia-oculto");
                const texto = el ? el.value : "";
                if (!texto) {
                    btnCopiar.innerHTML = HtmlEscape.icone("block", "Sem resultado");
                    setTimeout(() => { btnCopiar.innerHTML = HtmlEscape.icone("content_copy", "Copiar resultado"); }, 1500);
                    return;
                }
                try {
                    await navigator.clipboard.writeText(texto);
                    btnCopiar.innerHTML = HtmlEscape.icone("check", "Copiado");
                } catch (_) {
                    btnCopiar.innerHTML = HtmlEscape.icone("error", "Falhou");
                }
                setTimeout(() => { btnCopiar.innerHTML = HtmlEscape.icone("content_copy", "Copiar resultado"); }, 1500);
            });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }

    w.showAnaliseTab = showTab;
})(window);
