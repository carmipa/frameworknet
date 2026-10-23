/*
 * Botão "Limpar" genérico para os formulários de digitar-e-testar.
 *
 * Propósito de negócio: dar em toda ferramenta de entrada um jeito consistente de
 * zerar o que foi digitado e apagar o resultado anterior — o mesmo gesto que o
 * canvas de topologia já oferecia, agora padronizado nas demais telas.
 *
 * Uso: um <button type="button" data-limpar> dentro do <form>. O escopo a limpar é
 * o form que contém o botão (ou o seletor passado em data-limpar); o resultado a
 * apagar vem de data-limpar-resultado (seletores separados por vírgula) ou, na
 * falta dele, do hx-target do form.
 *
 * Invariantes: nunca toca em campo oculto nem no csrf_token (esvaziar o token
 * quebraria o próximo POST); esvazia texto/número/textarea, volta select para a
 * primeira opção e radio/checkbox para o default declarado — nunca deixa o form
 * num estado inválido só por limpar. O resultado volta ao estado INICIAL da página
 * (o placeholder de "digite e teste"), não a um vazio nu. Nenhum handler inline
 * (compatível com a CSP).
 *
 * Comportamento em caso de falha: escopo/resultado inexistente é ignorado sem erro;
 * a limpeza é sempre local ao form do botão.
 */
(function () {
    "use strict";

    var htmlInicial = new Map(); // elemento de resultado -> innerHTML do carregamento

    function alvosDe(botao) {
        var escopo = escopoDe(botao);
        var sel = botao.getAttribute("data-limpar-resultado");
        if (!sel && escopo && escopo.getAttribute("hx-target")) {
            sel = escopo.getAttribute("hx-target");
        }
        var alvos = [];
        if (sel) {
            sel.split(",").forEach(function (s) {
                var t = s.trim();
                if (t) {
                    document.querySelectorAll(t).forEach(function (el) { alvos.push(el); });
                }
            });
        }
        return alvos;
    }

    function escopoDe(botao) {
        var seletor = botao.getAttribute("data-limpar");
        return seletor ? document.querySelector(seletor) : botao.closest("form");
    }

    function ehEditavel(campo) {
        if (campo.tagName === "TEXTAREA" || campo.tagName === "SELECT") {
            return true;
        }
        var tipo = (campo.getAttribute("type") || "text").toLowerCase();
        return tipo !== "hidden" && tipo !== "submit" && tipo !== "button" && tipo !== "reset";
    }

    function limparCampo(campo) {
        if (campo.name === "csrf_token") {
            return;
        }
        var tipo = (campo.getAttribute("type") || "text").toLowerCase();
        if (campo.tagName === "SELECT") {
            campo.selectedIndex = 0;
        } else if (tipo === "checkbox" || tipo === "radio") {
            campo.checked = campo.defaultChecked; // mantém o default; não quebra o form
        } else {
            campo.value = "";
        }
        campo.dispatchEvent(new Event("input", { bubbles: true }));
        campo.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function limpar(botao) {
        var escopo = escopoDe(botao);
        // Corrida assíncrona: aborta uma requisição htmx em voo deste form, senão a
        // resposta atrasada faria swap DEPOIS da limpeza e repovoaria o resultado.
        if (escopo && window.htmx && typeof window.htmx.trigger === "function") {
            try { window.htmx.trigger(escopo, "htmx:abort"); } catch (e) { /* ignora */ }
        }
        if (escopo) {
            escopo.querySelectorAll("input, textarea, select").forEach(function (campo) {
                if (ehEditavel(campo)) { limparCampo(campo); }
            });
        }

        alvosDe(botao).forEach(function (el) {
            el.innerHTML = htmlInicial.has(el) ? htmlInicial.get(el) : "";
        });

        if (escopo) {
            var primeiro = escopo.querySelector(
                "textarea, input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=radio]):not([type=checkbox])");
            if (primeiro) {
                try { primeiro.focus(); } catch (e) { /* ignora */ }
            }
        }
    }

    function capturarIniciais() {
        document.querySelectorAll("[data-limpar]").forEach(function (botao) {
            alvosDe(botao).forEach(function (el) {
                if (!htmlInicial.has(el)) { htmlInicial.set(el, el.innerHTML); }
            });
        });
    }

    document.addEventListener("click", function (ev) {
        var botao = ev.target.closest ? ev.target.closest("[data-limpar]") : null;
        if (botao) {
            ev.preventDefault();
            limpar(botao);
        }
    });

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", capturarIniciais);
    } else {
        capturarIniciais();
    }
})();
