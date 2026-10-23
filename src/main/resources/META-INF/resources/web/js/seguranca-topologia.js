/*
 * Aba "Montar topologia" da página de Segurança (P05 fase 2).
 *
 * Propósito de negócio: renderizar o diagrama Mermaid da topologia montada
 * (inclusive quando ele chega via troca de fragmento do htmx) e dar os botões
 * de "quick-add" que inserem linhas-modelo no textarea da topologia.
 *
 * Invariantes: securityLevel "strict" (o texto do diagrama vem do próprio
 * servidor); só toca em .aprof-mermaid e no textarea #topo-texto; nenhum
 * handler inline (compatível com a CSP do projeto).
 *
 * Comportamento em caso de falha: Mermaid ausente/bloqueado ou diagrama inválido
 * não quebram a página — o bloco cai no texto-fonte e o erro fica no console.
 */
(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") {
            return;
        }
        try {
            mermaid.initialize({
                startOnLoad: false,
                // Tamanho natural do desenho (texto legível) dentro de uma caixa com rolagem própria: com useMaxWidth o
                // diagrama encolhia até caber e o texto chegava a 3 px no celular (auditoria de responsividade, 22/10/2026).
                flowchart: { useMaxWidth: false }, sequence: { useMaxWidth: false }, state: { useMaxWidth: false }, class: { useMaxWidth: false }, er: { useMaxWidth: false }, mindmap: { useMaxWidth: false },
                securityLevel: "strict",
                theme: "base",
                themeVariables: {
                    darkMode: true,
                    background: "#0a0e16",
                    mainBkg: "#0f1622",
                    primaryColor: "#0f1622",
                    primaryTextColor: "#eef2f8",
                    primaryBorderColor: "#2dd4bf",
                    lineColor: "#5b6679",
                    fontFamily: "Inter Tight, system-ui, sans-serif",
                    fontSize: "14px"
                }
            });
        } catch (e) {
            console.warn("Mermaid não inicializou", e);
        }
    }

    function renderMermaid(raiz) {
        if (typeof mermaid === "undefined") {
            return;
        }
        var alvo = raiz && raiz.querySelectorAll ? raiz : document;
        var blocos = alvo.querySelectorAll(".aprof-mermaid:not([data-processed])");
        if (!blocos.length) {
            return;
        }
        try {
            var r = mermaid.run({ nodes: blocos });
            if (r && typeof r.catch === "function") {
                r.catch(function (e) { console.warn("Mermaid não renderizou", e); });
            }
        } catch (e) {
            console.warn("Mermaid não renderizou", e);
        }
    }

    function quickAdd(ev) {
        var btn = ev.target.closest ? ev.target.closest("[data-topo-add]") : null;
        if (!btn) {
            return;
        }
        var ta = document.getElementById("topo-texto");
        if (!ta) {
            return;
        }
        var linha = btn.getAttribute("data-topo-add");
        ta.value = (ta.value.replace(/\s*$/, "") + "\n" + linha).replace(/^\n+/, "");
        ta.focus();
    }

    function start() {
        initMermaid();
        renderMermaid(document);
        document.body.addEventListener("htmx:afterSwap", function (e) { renderMermaid(e.target); });
        document.body.addEventListener("click", quickAdd);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
