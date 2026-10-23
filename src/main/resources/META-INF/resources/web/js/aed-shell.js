(function () {
    "use strict";

    /** Scroll ao topo ao trocar de rota (navegação entre telas). */
    document.querySelectorAll(".aed-nav-link").forEach((link) => {
        link.addEventListener("click", () => {
            window.scrollTo({ top: 0, behavior: "smooth" });
        });
    });

    /**
     * Menu recolhível no celular.
     *
     * PROPÓSITO DE NEGÓCIO: no celular o menu completo (ícone + rótulo, em grade) tem perto de 20rem;
     *   ele recolhe atrás de um botão "Menu" para a página começar na tela, não depois do menu.
     * INVARIANTES: sem este script o botão continua escondido e o menu continua aberto (nada fica
     *   inalcançável); aria-expanded acompanha o estado; Esc fecha e devolve o foco ao botão; em tela
     *   larga o CSS ignora o recolhimento.
     * FALHA: sem o botão ou sem a lista, não faz nada.
     */
    const nav = document.querySelector(".aed-topnav");
    const recolher = nav && nav.querySelector(".aed-nav-recolher");
    const itensMenu = document.getElementById("aed-nav-itens");
    if (nav && recolher && itensMenu) {
        recolher.hidden = false;
        nav.classList.add("nav-recolhivel");
        const definir = (aberto) => {
            nav.classList.toggle("nav-aberta", aberto);
            recolher.setAttribute("aria-expanded", String(aberto));
        };
        recolher.addEventListener("click", () => definir(!nav.classList.contains("nav-aberta")));
        nav.addEventListener("keydown", (ev) => {
            // Com um menu suspenso aberto, o Esc é dele (o Bootstrap o fecha); o menu do site fica.
            if (ev.key === "Escape" && nav.classList.contains("nav-aberta")
                    && !nav.querySelector(".aed-nav-drop-menu.show")) {
                definir(false);
                recolher.focus();
            }
        });
    }

    /**
     * Altura do menu grudado, para quem gruda embaixo dele.
     *
     * PROPÓSITO DE NEGÓCIO: os índices pegajosos dos aprofundamentos (BGP, SSH, DNS, TLS), a barra do
     *   construtor de comando e a barra lateral da documentação grudavam em top:0 — debaixo do menu do
     *   topo, que também gruda. Medido em 01/10/2026: a 1920 px o índice ficava inteiro escondido.
     * INVARIANTES: --aed-topnav-fixo vale a altura do menu SÓ quando ele está grudado (sticky); quando
     *   ele rola com a página (telas menores), vale 0 e os pegajosos voltam ao topo.
     * FALHA: sem o menu, a variável não existe e o CSS usa 0 (comportamento anterior).
     */
    if (nav) {
        const marcarAltura = () => {
            const grudado = getComputedStyle(nav).position === "sticky";
            document.documentElement.style.setProperty("--aed-topnav-fixo",
                grudado ? Math.ceil(nav.getBoundingClientRect().height) + "px" : "0px");
        };
        marcarAltura();
        window.addEventListener("resize", marcarAltura);
        if (window.ResizeObserver) {
            new ResizeObserver(marcarAltura).observe(nav);
        }
    }

    /**
     * Aba selecionada anunciada ao leitor de tela.
     *
     * PROPÓSITO DE NEGÓCIO: 46 abas role="tab" em 8 páginas não diziam qual estava aberta (só a
     *   Calculadora definia aria-selected) — auditoria FRONT-08. As páginas usam três implementações de
     *   abas (aed-tabs.js, analise.js e as próprias), todas marcando a aba aberta com a classe "active".
     * INVARIANTES: um mecanismo só, para as abas de hoje e as de amanhã: todo [role=tab] tem
     *   aria-selected igual a ter a classe "active", na carga e a cada troca de classe.
     * FALHA: sem MutationObserver, vale só o estado da carga.
     */
    function sincronizarAbas(raiz) {
        (raiz.matches && raiz.matches("[role='tab']") ? [raiz] : [])
            .concat(Array.from(raiz.querySelectorAll ? raiz.querySelectorAll("[role='tab']") : []))
            .forEach((aba) => {
                const valor = aba.classList.contains("active") ? "true" : "false";
                if (aba.getAttribute("aria-selected") !== valor) {
                    aba.setAttribute("aria-selected", valor);
                }
            });
    }
    sincronizarAbas(document);
    if (window.MutationObserver) {
        new MutationObserver((mutacoes) => {
            mutacoes.forEach((m) => {
                if (m.type === "attributes" && m.target.getAttribute && m.target.getAttribute("role") === "tab") {
                    sincronizarAbas(m.target);
                }
                m.addedNodes && m.addedNodes.forEach((n) => n.nodeType === 1 && sincronizarAbas(n));
            });
        }).observe(document.body, { attributes: true, attributeFilter: ["class"], childList: true, subtree: true });
    }

    /**
     * Setas dentro dos menus suspensos do topo (auditoria FRONT-24).
     *
     * PROPÓSITO DE NEGÓCIO: o Bootstrap só navega com as setas entre itens .dropdown-item; os do topo são
     *   .aed-nav-link (estilo próprio), então quem usa teclado abria o menu e não entrava nele.
     * INVARIANTES: Seta para baixo no botão abre o menu e foca o primeiro item (para cima, o último); dentro do menu, setas
     *   andam entre os itens (com volta), Home e End vão às pontas; Esc continua com o Bootstrap.
     * FALHA: sem Bootstrap ou sem itens, não faz nada.
     * Escuta na CAPTURA do document: o Bootstrap escuta ali também e dá stopPropagation nas setas, então
     *   um ouvinte na bolha nunca as recebia (medido: o foco ficava no botão). Na mesma fase e no mesmo
     *   alvo os dois rodam, em qualquer ordem: o show() de quem chega depois não faz nada (já aberto).
     */
    document.addEventListener("keydown", (ev) => {
        if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(ev.key)) {
            return;
        }
        const caixa = ev.target && ev.target.closest && ev.target.closest(".aed-nav-drop");
        if (!caixa) {
            return;
        }
        const itens = Array.from(caixa.querySelectorAll(".aed-nav-drop-menu .aed-nav-link"));
        if (!itens.length) {
            return;
        }
        const noBotao = ev.target.classList.contains("aed-nav-drop-toggle");
        if (noBotao) {
            if (ev.key !== "ArrowDown" && ev.key !== "ArrowUp") {
                return;
            }
            ev.preventDefault();
            if (window.bootstrap && window.bootstrap.Dropdown) {
                window.bootstrap.Dropdown.getOrCreateInstance(ev.target).show();
            }
            (ev.key === "ArrowDown" ? itens[0] : itens[itens.length - 1]).focus();
            return;
        }
        const atual = itens.indexOf(document.activeElement);
        if (atual < 0) {
            return;
        }
        ev.preventDefault();
        const destino = ev.key === "Home" ? 0 : ev.key === "End" ? itens.length - 1
            : (atual + (ev.key === "ArrowDown" ? 1 : -1) + itens.length) % itens.length;
        itens[destino].focus();
    }, true);

    /** Desabilita tooltips nativos vazios (regra do prompt). */
    document.querySelectorAll("[title='']").forEach((el) => el.removeAttribute("title"));

    /**
     * Menu suspenso do topo que passaria da borda da tela.
     *
     * PROPÓSITO DE NEGÓCIO: o menu abre ancorado no botão (Bootstrap com display estático); o
     *   botão do lado direito abria o menu para fora da tela — medido a 768 px: "Topologia" ia até
     *   813 px, com itens cortados que não dá para ler nem tocar.
     * INVARIANTES: só mexe na posição horizontal do menu aberto; nunca o esconde; no celular o CSS
     *   já o transforma em painel da largura do menu (left/right com !important vencem este ajuste).
     * FALHA: sem Bootstrap ou sem o menu, não faz nada.
     */
    document.addEventListener("shown.bs.dropdown", (ev) => {
        const caixa = ev.target && ev.target.closest(".aed-nav-drop");
        const menu = caixa && caixa.querySelector(".aed-nav-drop-menu");
        if (!menu) {
            return;
        }
        menu.style.left = "";
        menu.style.right = "";
        const largura = document.documentElement.clientWidth;
        let r = menu.getBoundingClientRect();
        if (r.right > largura - 8) {
            menu.style.left = "auto";
            menu.style.right = "0";
            r = menu.getBoundingClientRect();
        }
        if (r.left < 8) {
            menu.style.right = "auto";
            menu.style.left = (8 - caixa.getBoundingClientRect().left) + "px";
        }
    });
})();
