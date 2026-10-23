/**
 * Adaptador do tooltip compartilhado para as dicas que o site escreve como atributo title.
 *
 * PROPÓSITO DE NEGÓCIO: o site tem centenas de dicas escritas como title="…" nos templates, nos JSON de
 * conteúdo e no JS que monta tela. Até 22/10/2026 elas viravam Tooltip do Bootstrap, que só põe o
 * aria-describedby enquanto a dica está aberta (o leitor de tela não ouvia a dica no foco), não tem gatilho
 * para o toque em título e cabeçalho de coluna, e dependia do Bootstrap carregar. Este arquivo converte cada
 * title na marcação do componente canônico (web/css/dica.css + web/js/dica.js, copiados sem edição): um
 * <span role="tooltip" class="dica__texto"> com id, ligado ao elemento por aria-describedby permanente, e o
 * comportamento (foco, hover, toque, Esc, posição) fica todo com o dica.js.
 *
 * INVARIANTES DO DOMÍNIO:
 *  - o rótulo visível continua sendo o nome acessível: a dica entra como DESCRIÇÃO, nunca como nome; botão
 *    só com ícone, cujo único nome era o title, ganha aria-label com o mesmo texto antes do title sair;
 *  - dica igual ao rótulo não é dica: vira só o nome, sem balão repetindo o que já está escrito;
 *  - o title sai depois da conversão (sem balão nativo duplicado), e o texto fica em data-dica-texto para o
 *    balão ser refeito se o JS da página trocar o conteúdo do elemento;
 *  - nada é embrulhado onde o embrulho quebraria o comportamento: menu suspenso (o Bootstrap acha o menu pelo
 *    irmão do botão), grupo de entrada/botões e item de lista recebem a classe .dica no próprio elemento;
 *    cabeçalho de coluna, rótulo e título recebem o gatilho "?" do canônico, porque não recebem foco;
 *  - texto copiado pela página (botões "Copiar") não leva o texto das dicas: use FieldTooltips.textoLimpo.
 *
 * COMPORTAMENTO EM CASO DE FALHA: elemento que não dá para converter com segurança (iframe, svg, abbr,
 * option, imagem) fica com o title nativo, que continua funcionando no mouse; conteúdo que chega depois
 * (htmx, JS de tela) é convertido pelo MutationObserver e por FieldTooltips.init(escopo); sem o dica.js
 * carregado, o balão não abre, mas o aria-describedby continua descrevendo o elemento.
 */
(function (w) {
    "use strict";

    const NAO_CONVERTER = "iframe, abbr, svg, svg *, html, head, link, style, script, option, optgroup, img, area, "
        + "input[type=hidden], .dica__texto, .dica__texto *, [data-dica-nativa]";
    const CAMPO = "input:not([type=hidden]), select, textarea";
    const FOCAVEL = "a[href], button, summary, " + CAMPO
        + ", [tabindex]:not([tabindex='-1']), [role=button], [role=tab], [role=menuitem], [role=switch]";
    const SO_GATILHO = "th, td, label, legend, caption, h1, h2, h3, h4, h5, h6, dt, dd, li";
    const NO_PROPRIO = "[data-bs-toggle=dropdown], .dropdown-toggle, .dropdown-item, .nav-link, .list-group-item, "
        + ".input-group > *, .btn-group > *, .btn-group-vertical > *, li > *, tr > *";
    const SVG_AJUDA = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="10" '
        + 'fill="none" stroke="currentColor" stroke-width="2"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 '
        + '1.7M12 17h.01" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';

    let sequencia = 0;

    function limpo(t) {
        return (t || "").replace(/\s+/g, " ").trim();
    }

    /** Texto que uma pessoa lê no elemento, sem nome de glifo, sem dica e sem os controles de dentro. */
    function textoVisivel(no) {
        const copia = no.cloneNode(true);
        copia.querySelectorAll(
            ".material-symbols-outlined, [aria-hidden='true'], .dica__texto, .dica__gatilho, select, option, input, "
            + "textarea, .visually-hidden-focusable"
        ).forEach((n) => n.remove());
        return limpo(copia.textContent);
    }

    /** Texto do elemento para copiar ou comparar, sem o texto das dicas que este adaptador pôs dentro dele. */
    function textoLimpo(no) {
        if (!no) {
            return "";
        }
        const copia = no.cloneNode(true);
        copia.querySelectorAll(".dica__texto, .dica__gatilho").forEach((n) => n.remove());
        return copia.textContent;
    }

    function textoDaDica(el) {
        return limpo(el.getAttribute("title") || el.getAttribute("data-bs-title")
            || el.getAttribute("data-bs-original-title") || "");
    }

    function tirarTitle(el) {
        el.removeAttribute("title");
        el.removeAttribute("data-bs-title");
        el.removeAttribute("data-bs-original-title");
        if (el.getAttribute("data-bs-toggle") === "tooltip") {
            el.removeAttribute("data-bs-toggle");
            el.removeAttribute("data-bs-placement");
        }
    }

    function novoBalao(texto, oculto) {
        const balao = document.createElement("span");
        balao.id = "d-auto-" + (++sequencia);
        balao.setAttribute("role", "tooltip");
        balao.className = "dica__texto";
        balao.setAttribute("translate", "yes");
        if (oculto) {
            // Dentro do próprio botão/link: fora do nome acessível (o nome é o rótulo), mas ainda lido como
            // descrição — aria-describedby lê alvo com aria-hidden.
            balao.setAttribute("aria-hidden", "true");
        }
        balao.textContent = texto;
        return balao;
    }

    function descrever(el, id) {
        const atuais = (el.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean)
            .filter((i) => !/^d-auto-/.test(i) || document.getElementById(i));
        if (!atuais.includes(id)) {
            atuais.push(id);
        }
        el.setAttribute("aria-describedby", atuais.join(" "));
    }

    function rotuloCurto(el) {
        return textoVisivel(el) || limpo(el.getAttribute("aria-label")) || "este item";
    }

    /** Padrão 3 do canônico: gatilho "?" no fim do elemento que não recebe foco (título, coluna, rótulo). */
    function comGatilho(el, texto) {
        const envoltorio = document.createElement("span");
        envoltorio.className = "dica";
        envoltorio.setAttribute("data-dica-adaptador", "");
        const gatilho = document.createElement("button");
        gatilho.type = "button";
        gatilho.className = "dica__gatilho";
        gatilho.setAttribute("aria-label", "Ajuda sobre " + rotuloCurto(el));
        gatilho.innerHTML = SVG_AJUDA;
        const balao = novoBalao(texto, false);
        gatilho.setAttribute("aria-describedby", balao.id);
        envoltorio.appendChild(gatilho);
        envoltorio.appendChild(balao);
        el.appendChild(envoltorio);
    }

    /**
     * Ícone de ajuda solto (ex.: "info" com title dentro de um rótulo): vira o gatilho "?" do canônico — um
     * botão alcançável por Tab e pelo toque, com o MESMO ícone dentro, nomeado "Ajuda sobre <rótulo>".
     */
    function iconeVirandoGatilho(el, texto) {
        const dono = el.parentElement && el.parentElement.closest("label, th, legend, caption, h1, h2, h3, h4, h5, h6, dt");
        const envoltorio = document.createElement("span");
        envoltorio.className = "dica";
        envoltorio.setAttribute("data-dica-adaptador", "");
        const gatilho = document.createElement("button");
        gatilho.type = "button";
        gatilho.className = "dica__gatilho";
        gatilho.setAttribute("aria-label", "Ajuda sobre " + (dono ? rotuloCurto(dono) : texto.slice(0, 60)));
        el.parentNode.insertBefore(envoltorio, el);
        gatilho.appendChild(el);
        el.setAttribute("aria-hidden", "true");
        el.setAttribute("data-dica-texto", texto);
        const balao = novoBalao(texto, false);
        gatilho.setAttribute("aria-describedby", balao.id);
        envoltorio.appendChild(gatilho);
        envoltorio.appendChild(balao);
    }

    /** Padrão 1 do canônico: o próprio controle dentro de .dica, com o balão ao lado. */
    function embrulhar(el, texto, bloco) {
        const envoltorio = document.createElement("span");
        // Campo de formulário mede pela largura do bloco: o envoltório dele ocupa a linha inteira (dica-bloco).
        envoltorio.className = bloco ? "dica dica-bloco" : "dica";
        envoltorio.setAttribute("data-dica-adaptador", "");
        el.parentNode.insertBefore(envoltorio, el);
        envoltorio.appendChild(el);
        const balao = novoBalao(texto, false);
        envoltorio.appendChild(balao);
        descrever(el, balao.id);
    }

    /** Display do elemento como o site o desenha; "none" (menu fechado, aba oculta) cai no padrão da etiqueta. */
    function exibicao(el) {
        const d = w.getComputedStyle(el).display;
        if (d && d !== "none") {
            return d;
        }
        return /^(A|BUTTON|SPAN|SMALL|STRONG|EM|B|I|CODE|KBD|LABEL|SELECT|INPUT|TEXTAREA)$/.test(el.tagName)
            ? "inline" : "block";
    }

    const emLinha = (d) => /^inline/.test(d);

    /**
     * Onde embrulhar quebraria o componente: a classe .dica vai no próprio elemento e o balão fica dentro.
     * Devolve false quando a classe mudaria o display que o site dá ao elemento (só acontece quando o display
     * vinha da folha do navegador, que perde para a camada do canônico) — aí quem chamou usa o gatilho.
     */
    function noProprio(el, texto) {
        const antes = w.getComputedStyle(el).display;
        el.classList.add("dica");
        if (antes !== "none" && w.getComputedStyle(el).display !== antes) {
            el.classList.remove("dica");
            return false;
        }
        const balao = novoBalao(texto, true);
        el.appendChild(balao);
        descrever(el, balao.id);
        return true;
    }

    /** Padrão 2 do canônico: o bloco do campo vira .campo quando o campo é o único controle dele. */
    function noCampo(el, texto) {
        const bloco = el.closest(".input-group") || el.parentElement;
        // Bloco que já tem um "?" (popover convertido, ícone de ajuda) não vira .campo: o foco no campo mostraria os
        // dois balões e o do campo ficaria preso no canto da tela (revisão de boa-fé, 22/10/2026).
        const sozinho = bloco && bloco !== document.body && !bloco.matches("form, .dica, .campo")
            && bloco.querySelectorAll(CAMPO).length === 1
            && !bloco.querySelector(".dica, .dica__gatilho, [data-bs-toggle=popover]");
        if (!sozinho) {
            embrulhar(el, texto, true);
            return;
        }
        bloco.classList.add("campo");
        const balao = novoBalao(texto, false);
        bloco.appendChild(balao);
        descrever(el, balao.id);
    }

    /**
     * Botão de ajuda que era popover do Bootstrap ("info" ao lado do rótulo, título + explicação): vira o gatilho
     * do canônico com a EXPLICAÇÃO no balão — o título do popover já está no rótulo e no aria-label do botão.
     */
    function popoverViraGatilho(el) {
        const explicacao = limpo(el.getAttribute("data-bs-content"));
        const titulo = textoDaDica(el);
        const texto = explicacao || titulo;
        ["data-bs-toggle", "data-bs-trigger", "data-bs-placement", "data-bs-content", "data-bs-html"]
            .forEach((a) => el.removeAttribute(a));
        tirarTitle(el);
        if (!texto) {
            return;
        }
        if (!el.getAttribute("aria-label")) {
            el.setAttribute("aria-label", "Ajuda sobre " + (titulo || "este campo"));
        }
        el.setAttribute("data-dica-texto", texto);
        el.classList.add("dica__gatilho");
        const envoltorio = document.createElement("span");
        envoltorio.className = "dica";
        envoltorio.setAttribute("data-dica-adaptador", "");
        el.parentNode.insertBefore(envoltorio, el);
        envoltorio.appendChild(el);
        const balao = novoBalao(texto, false);
        envoltorio.appendChild(balao);
        descrever(el, balao.id);
    }

    /**
     * Balão que este adaptador já criou para o elemento (pelo aria-describedby, pelo "?" dentro dele ou pelo
     * gatilho em volta dele). Usado quando a página regrava o title de um elemento já convertido.
     */
    function balaoExistente(el) {
        const ids = (el.getAttribute("aria-describedby") || "").split(/\s+/).filter((i) => /^d-auto-/.test(i));
        for (const id of ids) {
            const b = document.getElementById(id);
            if (b) {
                return b;
            }
        }
        const dentro = el.querySelector(":scope > .dica[data-dica-adaptador] > .dica__texto");
        if (dentro) {
            return dentro;
        }
        const gatilho = el.closest(".dica__gatilho");
        return gatilho ? document.getElementById(gatilho.getAttribute("aria-describedby") || "") : null;
    }

    function converter(el) {
        if (el.matches(NAO_CONVERTER) || el.closest(".dica__texto")) {
            return;
        }
        if (el.hasAttribute("data-dica-texto")) {
            // Já convertido: a página regravou o title (ex.: a Academia a cada acerto). Atualiza o balão que existe
            // — criar outro empilhava balões e ids no aria-describedby (achado da revisão adversarial, 22/10/2026).
            const novo = textoDaDica(el);
            tirarTitle(el);
            const balao = balaoExistente(el);
            if (novo && balao) {
                balao.textContent = novo;
                el.setAttribute("data-dica-texto", novo);
            }
            return;
        }
        if (el.getAttribute("data-bs-toggle") === "popover") {
            if (w.bootstrap && w.bootstrap.Popover && w.bootstrap.Popover.getInstance(el)) {
                w.bootstrap.Popover.getInstance(el).dispose();
            }
            popoverViraGatilho(el);
            return;
        }
        const texto = textoDaDica(el);
        if (!texto) {
            return;
        }
        const visivel = textoVisivel(el);
        if (!visivel && !el.getAttribute("aria-label") && !el.matches(CAMPO) && el.matches(FOCAVEL)) {
            // Botão/link só com ícone: o title era o único nome. O nome fica; a dica igual ao nome seria repetida.
            el.setAttribute("aria-label", texto);
            tirarTitle(el);
            return;
        }
        if (visivel && visivel.toLowerCase() === texto.toLowerCase()) {
            tirarTitle(el);
            return;
        }
        if (w.bootstrap && w.bootstrap.Tooltip && w.bootstrap.Tooltip.getInstance(el)) {
            // Tooltip do Bootstrap criado antes do adaptador (script de página): sai, para não haver dois balões.
            w.bootstrap.Tooltip.getInstance(el).dispose();
        }
        tirarTitle(el);
        el.setAttribute("data-dica-texto", texto);
        if (el.matches(CAMPO)) {
            // O title podia ser o ÚNICO nome do campo (sem <label for> nem aria-label): tirá-lo deixaria o campo sem
            // nome para o leitor de tela. O nome passa a ser o rótulo visível ao lado, ou, sem ele, o próprio texto.
            if (!(el.labels && el.labels.length) && !el.getAttribute("aria-label") && !el.getAttribute("aria-labelledby")) {
                const vizinho = el.parentElement && el.parentElement.querySelector(":scope > label");
                el.setAttribute("aria-label", (vizinho && textoVisivel(vizinho)) || texto);
            }
            noCampo(el, texto);
            return;
        }
        if (el.matches(SO_GATILHO)) {
            comGatilho(el, texto);
            return;
        }
        const focavel = el.matches(FOCAVEL);
        if (!focavel && !visivel && el.parentNode) {
            iconeVirandoGatilho(el, texto);
            return;
        }
        // Controle com texto (link, botão): a classe vai no PRÓPRIO elemento e o balão fica dentro dele. Embrulhar
        // num <span> encolhia o que mede pela largura do pai — w-100, item de grade/flex: "Calcular" ia de 370 para
        // 133 px e o bit da Academia de 70 para 15 px, deixando de receber o clique (revisão de boa-fé, 22/10/2026).
        // Embrulho só para o botão sem texto (bandeira do idioma), cujo "texto" viraria o próprio balão, e para o
        // trecho de texto em linha (selo, sigla), que ganha foco de teclado para a dica não ser só do mouse.
        // "Sem texto" de verdade: nem texto visível nem texto escondido (o bit da Academia tem o valor em span
        // aria-hidden e o nome em aria-label — com a classe no próprio botão ele não encolhe).
        const soImagem = focavel && !(el.textContent || "").trim();
        if (el.parentNode && (soImagem || (!focavel && !el.matches(NO_PROPRIO) && emLinha(exibicao(el))))) {
            if (!focavel && !el.hasAttribute("tabindex")) {
                el.setAttribute("tabindex", "0");
            }
            embrulhar(el, texto);
            return;
        }
        if (noProprio(el, texto)) {
            if (!focavel && !el.hasAttribute("tabindex")) {
                el.setAttribute("tabindex", "0");
            }
            return;
        }
        if (!focavel) {
            comGatilho(el, texto);
            return;
        }
        // Controle focável cujo display viria só do navegador (raro): mantém o display e segue no próprio.
        const d = w.getComputedStyle(el).display;
        el.classList.add("dica");
        el.style.display = d;
        const balao = novoBalao(texto, true);
        el.appendChild(balao);
        descrever(el, balao.id);
    }

    /** O JS da página trocou o conteúdo do elemento (ex.: "Copiado") e levou o balão junto: refaz. */
    function refazerSePerdeu(el) {
        const texto = el.getAttribute("data-dica-texto");
        if (!texto || !el.classList.contains("dica")) {
            return;
        }
        let balao = el.querySelector(":scope > .dica__texto");
        if (!balao) {
            balao = novoBalao(texto, true);
            el.appendChild(balao);
        }
        // A página pode ter devolvido o HTML salvo antes ("Copiado" → rótulo), com o balão ANTIGO dentro: o
        // aria-describedby passa a apontar para o balão que está lá (antes ficava apontando para um id sumido).
        descrever(el, balao.id);
    }

    /**
     * Título da página (h1) sem dica: o "?" ao lado dele explica o que a página contém com a descrição que a
     * própria página já publica (meta description, única por página). Regra 6-A: título diz o que a área contém.
     * Acima de 160 caracteres, corta na última palavra inteira.
     */
    function dicaDoTitulo() {
        const meta = document.querySelector('meta[name="description"]');
        const texto = limpo(meta && meta.getAttribute("content"));
        if (!texto) {
            return;
        }
        document.querySelectorAll("h1").forEach((h1) => {
            if (h1.querySelector(".dica") || h1.hasAttribute("data-dica-texto")) {
                return;
            }
            let curto = texto;
            if (curto.length > 160) {
                curto = curto.slice(0, 159).replace(/\s+\S*$/, "") + "…";
            }
            h1.setAttribute("data-dica-texto", curto);
            comGatilho(h1, curto);
        });
    }

    function init(raiz) {
        const escopo = raiz && raiz.querySelectorAll ? raiz : document;
        const alvos = [];
        const COM_DICA = "[title], [data-bs-title], [data-bs-original-title], [data-bs-toggle=popover]";
        if (escopo.nodeType === 1 && escopo.matches(COM_DICA)) {
            alvos.push(escopo);
        }
        escopo.querySelectorAll(COM_DICA).forEach((el) => alvos.push(el));
        alvos.forEach(converter);
    }

    function vigiar() {
        if (!w.MutationObserver) {
            return;
        }
        new MutationObserver((mudancas) => {
            for (const m of mudancas) {
                if (m.type === "attributes") {
                    if (m.target.nodeType === 1 && m.target.hasAttribute("title")) {
                        converter(m.target);
                    }
                    continue;
                }
                if (m.target.nodeType === 1 && m.target.hasAttribute("data-dica-texto")) {
                    refazerSePerdeu(m.target);
                }
                m.addedNodes.forEach((n) => {
                    if (n.nodeType === 1 && !n.closest(".dica__texto") && !n.matches(".dica__texto, [data-dica-adaptador]")) {
                        init(n);
                    }
                });
            }
        }).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["title"] });
    }

    w.FieldTooltips = { init, textoLimpo };

    // Converte já o que existe (script de página no fim do <body> roda antes do DOMContentLoaded e não pode
    // achar title sem converter) e de novo no DOMContentLoaded, quando o documento está inteiro.
    if (document.body) {
        init(document);
        dicaDoTitulo();
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => { init(document); dicaDoTitulo(); vigiar(); });
    } else {
        vigiar();
    }
})(window);
