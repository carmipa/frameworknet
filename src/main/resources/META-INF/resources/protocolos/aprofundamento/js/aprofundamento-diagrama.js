/*
 * Renderiza os diagramas Mermaid das páginas de aprofundamento genéricas, no
 * tema DARK do projeto (mesma paleta da Documentação).
 *
 * Propósito de negócio: o diagrama de arquitetura/fluxo é o coração didático de
 * cada aprofundamento; sem ele desenhado, a página perde a explicação visual.
 *
 * Invariantes: securityLevel "strict" (o texto do diagrama vem de conteudo.json
 * do próprio projeto, mas nunca renderiza HTML embutido); só processa blocos
 * .aprof-mermaid, sem tocar em outros usos de Mermaid na aplicação.
 *
 * Comportamento em caso de falha: Mermaid ausente (CDN bloqueado) ou definição
 * inválida não quebram a página — o bloco cai no fallback CSS (a definição em
 * texto mono) e o erro fica só no console. A definição é conferida com
 * mermaid.parse ANTES de desenhar: o Mermaid 11 troca o bloco inválido por um
 * desenho "Syntax error in text", e o fallback nunca aparecia (auditoria FRONT-04).
 * Bloco inválido fica em texto, com um aviso visível acima dele.
 */
(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") {
            return;
        }
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
                secondaryColor: "#111a27",
                tertiaryColor: "#0a0e16",
                lineColor: "#5b6679",
                textColor: "#c9d1d9",
                clusterBkg: "#0c1220",
                clusterBorder: "rgba(255,255,255,0.12)",
                nodeBorder: "rgba(255,255,255,0.18)",
                edgeLabelBackground: "#0a0e16",
                fontFamily: "Inter Tight, system-ui, sans-serif",
                fontSize: "14px"
            }
        });
        var blocos = Array.prototype.slice.call(document.querySelectorAll(".aprof-mermaid"));
        var conferidos = blocos.map(function (el) {
            var conferencia;
            try {
                conferencia = Promise.resolve(mermaid.parse(el.textContent, { suppressErrors: true }));
            } catch (e) {
                conferencia = Promise.resolve(false);
            }
            return conferencia
                .then(function (valido) { return valido ? el : manterEmTexto(el); })
                .catch(function () { return manterEmTexto(el); });
        });
        Promise.all(conferidos).then(function (els) {
            var validos = els.filter(Boolean);
            if (!validos.length) {
                return;
            }
            try {
                // mermaid.run é ASSÍNCRONO: um try/catch síncrono não pega a rejeição da
                // promise. O .catch evita "Uncaught (in promise)".
                var resultado = mermaid.run({ nodes: validos });
                if (resultado && typeof resultado.catch === "function") {
                    resultado.catch(function (e) {
                        console.warn("Mermaid não conseguiu renderizar o diagrama", e);
                    });
                }
            } catch (e) {
                console.warn("Mermaid não conseguiu inicializar", e);
            }
        });
    }

    function manterEmTexto(el) {
        console.warn("Diagrama com definição inválida: mostrado em texto", el);
        if (!el.previousElementSibling || !el.previousElementSibling.classList.contains("aprof-mermaid-aviso")) {
            var aviso = document.createElement("p");
            aviso.className = "small text-warning aprof-mermaid-aviso";
            aviso.setAttribute("role", "note");
            var icone = document.createElement("span");
            icone.className = "material-symbols-outlined";
            icone.setAttribute("aria-hidden", "true");
            icone.setAttribute("translate", "no");
            icone.textContent = "warning";
            aviso.appendChild(icone);
            aviso.appendChild(document.createTextNode(" Não foi possível desenhar este diagrama; a definição aparece em texto abaixo."));
            el.parentNode.insertBefore(aviso, el);
        }
        return null;
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initMermaid);
    } else {
        initMermaid();
    }
})();
