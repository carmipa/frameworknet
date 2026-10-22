/* Documentação: Mermaid em tema DARK (alinhado ao design) + scroll-spy do sumário. */
(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") return;
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
        try {
            mermaid.run({ querySelector: ".markdown-doc .mermaid" });
        } catch (e) {
            /* diagramas inválidos não devem quebrar a página */
        }
    }

    function initScrollSpy() {
        const links = Array.from(document.querySelectorAll(".doc-sidebar a[href^='#']"));
        const sections = links
            .map((link) => document.querySelector(link.getAttribute("href")))
            .filter(Boolean);
        if (!links.length || !sections.length) return;

        const sync = () => {
            const threshold = window.scrollY + 150;
            let activeId = sections[0].id;
            sections.forEach((sec) => {
                if (sec.offsetTop <= threshold) activeId = sec.id;
            });
            links.forEach((link) => {
                link.classList.toggle("active", link.getAttribute("href") === "#" + activeId);
            });
        };

        links.forEach((link) => {
            link.addEventListener("click", () => {
                links.forEach((l) => l.classList.remove("active"));
                link.classList.add("active");
            });
        });
        window.addEventListener("scroll", sync, { passive: true });
        sync();
    }

    function boot() {
        initMermaid();
        initScrollSpy();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }
})();
