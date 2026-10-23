(function (w) {
    "use strict";

    const FORM_KEY = "fn-analise-form-cache";
    const GRID_PREFIX = "fn-grid-state:";

    function readJson(key) {
        try {
            const raw = localStorage.getItem(key);
            return raw ? JSON.parse(raw) : null;
        } catch (_) {
            return null;
        }
    }

    function writeJson(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (_) {
            /* quota ou modo privado */
        }
    }

    function collectForm(form) {
        const tab = form.getAttribute("data-tab-form") || form.querySelector('input[name="tab"]')?.value || "default";
        const fields = {};
        form.querySelectorAll("input[name], select[name], textarea[name]").forEach((el) => {
            const name = el.name;
            if (!name || name === "modo" || el.type === "submit" || el.type === "hidden" && name === "tab") {
                return;
            }
            if (el.type === "checkbox") {
                fields[name] = el.checked;
            } else if (el.type === "radio") {
                if (el.checked) {
                    fields[name] = el.value;
                }
            } else {
                fields[name] = el.value;
            }
        });
        return { tab, fields };
    }

    function restoreForm(form, cachedFields) {
        if (!cachedFields) {
            return;
        }
        form.querySelectorAll("input[name], select[name], textarea[name]").forEach((el) => {
            const name = el.name;
            if (!name || !(name in cachedFields) || name === "modo") {
                return;
            }
            const serverValue = (el.value || "").trim();
            if (serverValue) {
                return;
            }
            const cached = cachedFields[name];
            if (el.type === "checkbox") {
                el.checked = !!cached;
            } else if (el.type === "radio") {
                el.checked = String(el.value) === String(cached);
            } else {
                el.value = cached == null ? "" : String(cached);
            }
        });
    }

    function bindAnaliseForms(root) {
        const scope = root || document;
        const wrap = scope.querySelector(".analise-didatica-wrap");
        if (!wrap) {
            return;
        }

        const cache = readJson(FORM_KEY) || {};
        wrap.querySelectorAll("form[data-tab-form]").forEach((form) => {
            const { tab, fields } = collectForm(form);
            if (cache[tab]) {
                restoreForm(form, cache[tab]);
            }
            const persist = () => {
                const snapshot = collectForm(form);
                cache[snapshot.tab] = snapshot.fields;
                writeJson(FORM_KEY, cache);
            };
            form.addEventListener("input", persist);
            form.addEventListener("change", persist);
            form.addEventListener("submit", persist);
        });

        const geoIp = scope.querySelector("#geo-ip-digitar");
        if (geoIp) {
            const geoCache = cache.geo || {};
            if (!geoIp.value.trim() && geoCache.ip) {
                geoIp.value = geoCache.ip;
            }
            const persistGeo = () => {
                cache.geo = { ip: geoIp.value };
                writeJson(FORM_KEY, cache);
            };
            geoIp.addEventListener("input", persistGeo);
            geoIp.addEventListener("change", persistGeo);
        }

        const btnLimpar = scope.querySelector("#btn-limpar-tela");
        if (btnLimpar) {
            btnLimpar.addEventListener("click", () => {
                try {
                    localStorage.removeItem(FORM_KEY);
                } catch (_) {
                    /* ignore */
                }
            });
        }
    }

    function gridKey(gridId) {
        return GRID_PREFIX + gridId;
    }

    function loadGridState(gridId) {
        return readJson(gridKey(gridId));
    }

    function saveGridState(gridId, state) {
        writeJson(gridKey(gridId), state);
    }

    function clearGridState(gridId) {
        try {
            localStorage.removeItem(gridKey(gridId));
        } catch (_) {
            /* ignore */
        }
    }

    w.SearchCache = {
        bindAnaliseForms,
        loadGridState,
        saveGridState,
        clearGridState,
    };

    document.addEventListener("DOMContentLoaded", () => bindAnaliseForms(document));
})(window);
