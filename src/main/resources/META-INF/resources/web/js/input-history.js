(function (w) {
    "use strict";

    const STORE_KEY = "fn-input-history";
    const MAX_ITEMS = 5;

    function readStore() {
        try {
            const raw = localStorage.getItem(STORE_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch (_) {
            return {};
        }
    }

    function writeStore(store) {
        try {
            localStorage.setItem(STORE_KEY, JSON.stringify(store));
        } catch (_) {
            /* quota / modo privado */
        }
    }

    function fieldKey(input) {
        const id = input.name || input.id || input.getAttribute("placeholder") || "campo";
        return location.pathname + "::" + id;
    }

    function getHistory(input) {
        const store = readStore();
        const list = store[fieldKey(input)];
        return Array.isArray(list) ? list : [];
    }

    function pushHistory(input) {
        const value = (input.value || "").trim();
        if (value.length < 2) {
            return;
        }
        const store = readStore();
        const key = fieldKey(input);
        const list = Array.isArray(store[key]) ? store[key] : [];
        const filtered = list.filter((v) => v.toLowerCase() !== value.toLowerCase());
        filtered.unshift(value);
        store[key] = filtered.slice(0, MAX_ITEMS);
        writeStore(store);
    }

    function removeHistoryItem(input, value) {
        const store = readStore();
        const key = fieldKey(input);
        const list = Array.isArray(store[key]) ? store[key] : [];
        store[key] = list.filter((v) => v !== value);
        writeStore(store);
    }

    function clearHistory(input) {
        const store = readStore();
        delete store[fieldKey(input)];
        writeStore(store);
    }

    let activePanel = null;
    let activeInput = null;

    function closePanel() {
        if (activePanel) {
            activePanel.remove();
            activePanel = null;
            activeInput = null;
            window.removeEventListener("scroll", reposition, true);
            window.removeEventListener("resize", reposition, true);
        }
    }

    function reposition() {
        if (!activePanel || !activeInput) {
            return;
        }
        const rect = activeInput.getBoundingClientRect();
        activePanel.style.top = rect.bottom + 4 + "px";
        activePanel.style.left = rect.left + "px";
        activePanel.style.width = Math.max(rect.width, 200) + "px";
    }

    function applyValue(input, value) {
        input.value = value;
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
        input.focus();
    }

    function openPanel(input) {
        closePanel();
        const items = getHistory(input);
        if (items.length === 0) {
            return;
        }

        const panel = document.createElement("div");
        panel.className = "fn-input-history-panel";

        const header = document.createElement("div");
        header.className = "fn-ih-header";
        header.innerHTML =
            '<span class="material-symbols-outlined" translate="no" aria-hidden="true">history</span><span>Últimas pesquisas</span>';
        panel.appendChild(header);

        items.forEach((value) => {
            const row = document.createElement("div");
            row.className = "fn-ih-item";

            const label = document.createElement("button");
            label.type = "button";
            label.className = "fn-ih-label";
            label.textContent = value;
            label.addEventListener("mousedown", (ev) => {
                ev.preventDefault();
                applyValue(input, value);
                closePanel();
            });

            const del = document.createElement("button");
            del.type = "button";
            del.className = "fn-ih-del";
            del.setAttribute("aria-label", "Remover do histórico");
            del.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">close</span>';
            del.addEventListener("mousedown", (ev) => {
                ev.preventDefault();
                ev.stopPropagation();
                removeHistoryItem(input, value);
                openPanel(input);
            });

            row.appendChild(label);
            row.appendChild(del);
            panel.appendChild(row);
        });

        const footer = document.createElement("button");
        footer.type = "button";
        footer.className = "fn-ih-clear";
        footer.textContent = "Limpar histórico";
        footer.addEventListener("mousedown", (ev) => {
            ev.preventDefault();
            clearHistory(input);
            closePanel();
        });
        panel.appendChild(footer);

        document.body.appendChild(panel);
        activePanel = panel;
        activeInput = input;
        reposition();
        window.addEventListener("scroll", reposition, true);
        window.addEventListener("resize", reposition, true);
    }

    function isEligible(input) {
        if (!input || input.tagName !== "INPUT") {
            return false;
        }
        if (input.dataset.history === "off") {
            return false;
        }
        const type = (input.getAttribute("type") || "text").toLowerCase();
        return ["text", "search", "tel", "url", "email"].includes(type);
    }

    function bind(input) {
        if (!isEligible(input) || input.dataset.historyBound === "1") {
            return;
        }
        input.dataset.historyBound = "1";
        input.setAttribute("autocomplete", "off");

        input.addEventListener("focus", () => openPanel(input));
        input.addEventListener("click", () => openPanel(input));
        input.addEventListener("blur", () => {
            pushHistory(input);
            setTimeout(closePanel, 150);
        });
        input.addEventListener("change", () => pushHistory(input));
        input.addEventListener("keydown", (ev) => {
            if (ev.key === "Enter") {
                pushHistory(input);
                closePanel();
            } else if (ev.key === "Escape") {
                closePanel();
            }
        });
    }

    function bindForms() {
        document.querySelectorAll("form").forEach((form) => {
            if (form.dataset.historyFormBound === "1") {
                return;
            }
            form.dataset.historyFormBound = "1";
            form.addEventListener("submit", () => {
                form.querySelectorAll("input").forEach((input) => {
                    if (isEligible(input)) {
                        pushHistory(input);
                    }
                });
            });
        });
    }

    function init(root) {
        const scope = root || document;
        scope.querySelectorAll("input").forEach(bind);
        bindForms();
    }

    document.addEventListener("DOMContentLoaded", () => init(document));
    document.addEventListener("mousedown", (ev) => {
        if (activePanel && !activePanel.contains(ev.target) && ev.target !== activeInput) {
            closePanel();
        }
    });

    w.InputHistory = { init, pushHistory, clearHistory };
})(window);
