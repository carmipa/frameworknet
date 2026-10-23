(function (w) {
    "use strict";

    function getCookie(name) {
        const match = document.cookie.match(new RegExp("(^|;\\s*)" + name + "=([^;]*)"));
        return match ? decodeURIComponent(match[2]) : "";
    }

    // Produção (HTTPS) usa __Host-XSRF-TOKEN (subdomínio irmão não consegue plantar); dev usa o nome comum.
    function tokenCsrf() {
        return getCookie("__Host-XSRF-TOKEN") || getCookie("XSRF-TOKEN");
    }

    function ensureFormToken(form) {
        const token = tokenCsrf();
        if (!token) {
            return;
        }
        let input = form.querySelector('input[name="csrf_token"]');
        if (!input) {
            input = document.createElement("input");
            input.type = "hidden";
            input.name = "csrf_token";
            form.appendChild(input);
        }
        input.value = token;
    }

    function bindForms(root) {
        const scope = root || document;
        scope.querySelectorAll('form[method="POST"], form[method="post"]').forEach(ensureFormToken);
    }

    const nativeFetch = w.fetch.bind(w);
    w.fetch = function (url, options) {
        const opts = options || {};
        const method = (opts.method || "GET").toUpperCase();
        if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
            const token = tokenCsrf();
            if (token) {
                const headers = new Headers(opts.headers || {});
                if (!headers.has("X-CSRF-Token")) {
                    headers.set("X-CSRF-Token", token);
                }
                opts.headers = headers;
            }
        }
        return nativeFetch(url, opts);
    };

    document.addEventListener("submit", (event) => {
        const form = event.target;
        if (form && form.tagName === "FORM") {
            ensureFormToken(form);
        }
    }, true);

    document.addEventListener("DOMContentLoaded", () => bindForms(document));
    w.CsrfClient = { bindForms, getToken: () => tokenCsrf() };
})(window);
