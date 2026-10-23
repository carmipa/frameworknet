(function () {
    function bindDigitsOnly(input, maxLength) {
        if (!input || input.dataset.digitsBound === "1") {
            return;
        }
        input.dataset.digitsBound = "1";
        if (maxLength && !input.maxLength) {
            input.maxLength = maxLength;
        }
        input.addEventListener("input", () => {
            let digits = input.value.replace(/\D/g, "");
            if (maxLength && digits.length > maxLength) {
                digits = digits.slice(0, maxLength);
            }
            if (input.value !== digits) {
                input.value = digits;
            }
        });
    }

    function enhanceIpv4Input(input) {
        if (!input || input.dataset.ipv4Bound === "1") {
            return;
        }
        input.dataset.ipv4Bound = "1";
        input.setAttribute("autocomplete", "off");
        input.setAttribute("spellcheck", "false");
        if (!input.classList.contains("font-monospace")) {
            input.classList.add("font-monospace");
        }
    }

    function bindIpv4NetworkInput(input) {
        if (!input || input.dataset.ipv4NetworkBound === "1") {
            return;
        }
        input.dataset.ipv4NetworkBound = "1";
        enhanceIpv4Input(input);
        input.addEventListener("input", () => {
            const cleaned = input.value.replace(/[^0-9./]/g, "");
            if (input.value !== cleaned) {
                input.value = cleaned;
            }
        });
    }

    function init(root) {
        const scope = root || document;
        scope.querySelectorAll(".input-ipv4, .input-ip").forEach(enhanceIpv4Input);
        scope.querySelectorAll(".input-ipv4-network").forEach(bindIpv4NetworkInput);
        // Prefixo IPv4 cabe em 2 dígitos, o de IPv6 vai a 128: o campo que declara maxlength manda.
        // Com o 2 fixo, quem digitava /127 na WAN do projeto IPv6 mandava /12 e levava erro.
        scope.querySelectorAll(".input-cidr").forEach((input) => {
            const max = parseInt(input.getAttribute("maxlength") || "0", 10);
            bindDigitsOnly(input, max > 0 ? max : 2);
        });
        scope.querySelectorAll(".input-numeric").forEach((input) => {
            const max = parseInt(input.getAttribute("data-max-length") || input.getAttribute("maxlength") || "0", 10);
            bindDigitsOnly(input, max > 0 ? max : null);
        });
    }

    document.addEventListener("DOMContentLoaded", () => init(document));
    window.FormInputs = { init, bindDigitsOnly, enhanceIpv4Input };
})();
