/**
 * Adaptador do tooltip compartilhado para as dicas que o site escreve como atributo title.
 *
 * PROPÓSITO DE NEGÓCIO: o site tem centenas de dicas escritas como title="…" nos templates, nos JSON de
 * conteúdo e no JS que monta tela. Este arquivo converte cada title na marcação do componente compartilhado
 * (web/css/dica.css + web/js/dica.js, copiados sem edição), e só onde a dica é necessária: desde 25/11/2026 (regra
 * de 04/11) botão, link, aba, item de menu e título com texto não levam dica nem "?" (o texto já diz, e o "?" ao
 * lado de cada um sujava a tela); o gatilho "?" (um botão) continua junto do campo, da coluna e do texto que não
 * recebe foco. O comportamento (foco, mouse, toque, Esc, posição) fica todo com o dica.js.
 *
 * INVARIANTES DO DOMÍNIO:
 *  - a .dica ENVOLVE o controle; o texto da dica nunca fica dentro do botão, do link ou do campo (entraria no nome
 *    que o leitor de tela lê) e nunca fica fora de uma .dica;
 *  - controle com texto: habilitado, sem dica e sem "?"; desabilitado, a dica do motivo (o texto que veio no
 *    title, guardado em data-dica-desab), sem "?"; título (h1-h6, legend, caption): sem dica;
 *  - nenhuma marca "?" ao lado de controle; o "?" fica no campo, na coluna e no texto que não recebe foco, e um só
 *    para os itens de um grupo marcado com data-dica-grupo (o contêiner leva data-dica-excecao com o motivo); item
 *    dentro de um contêiner com data-dica-excecao sem grupo segue a exceção registrada naquele contêiner;
 *  - o rótulo visível continua sendo o nome acessível: a dica entra como DESCRIÇÃO; botão só com ícone, cujo único
 *    nome era o title, ganha aria-label com o mesmo texto antes do title sair;
 *  - dica igual ao rótulo não é dica: vira só o nome, sem balão repetindo o que já está escrito;
 *  - menu suspenso do Bootstrap acha o menu pelo irmão do botão: ali a .dica entra logo depois do botão, sem
 *    envolvê-lo; nos demais controles, o envoltório mantém o desenho (bloco quando o controle ocupava a linha, em
 *    linha quando não);
 *  - o title sai depois da conversão (sem balão nativo duplicado), e o texto fica em data-dica-texto para o
 *    balão ser atualizado se o JS da página regravar o title;
 *  - texto copiado pela página (botões "Copiar") não leva o texto das dicas: use FieldTooltips.textoLimpo.
 *
 * COMPORTAMENTO EM CASO DE FALHA: elemento que não dá para converter com segurança (iframe, svg, abbr, option,
 * imagem) fica com o title nativo, que continua funcionando no mouse; conteúdo que chega depois (htmx, JS de
 * tela) é convertido pelo MutationObserver e por FieldTooltips.init(escopo); sem o dica.js carregado, o balão não
 * abre, mas o aria-describedby continua descrevendo o elemento.
 */
(function (w) {
    "use strict";

    const NAO_CONVERTER = "iframe, abbr, svg, svg *, html, head, link, style, script, option, optgroup, img, area, "
        + "input[type=hidden], .dica__texto, .dica__texto *, [data-dica-nativa]";
    const CAMPO = "input:not([type=hidden]), select, textarea";
    const FOCAVEL = "a[href], button, summary, " + CAMPO
        + ", [tabindex]:not([tabindex='-1']), [role=button], [role=tab], [role=menuitem], [role=switch], [role=radio]";
    const SO_GATILHO = "th, td, label, legend, caption, h1, h2, h3, h4, h5, h6, dt, dd, li";
    // Regra de 04/11/2026: botão, link, aba, item de menu e título com texto não levam dica nem "?" (o texto já diz;
    // a explicação útil vai para a tela). Controle aqui é controle de verdade, não um bloco que só ganhou tabindex.
    const CONTROLE = "a[href], button, summary, [role=button], [role=tab], [role=menuitem], [role=switch], "
        + "[role=radio], [role=link], [role=checkbox]";
    const TITULO = "h1, h2, h3, h4, h5, h6, legend, caption";
    const IRMAO = "[data-bs-toggle=dropdown], .dropdown-toggle";
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
            ".material-symbols-outlined, [aria-hidden='true'], .dica__texto, .dica__gatilho, .dica__marca, select, "
            + "option, input, textarea, .visually-hidden-focusable"
        ).forEach((n) => n.remove());
        return limpo(copia.textContent);
    }

    /** Texto do elemento para copiar ou comparar, sem o texto das dicas que este adaptador pôs junto dele. */
    function textoLimpo(no) {
        if (!no) {
            return "";
        }
        const copia = no.cloneNode(true);
        copia.querySelectorAll(".dica__texto, .dica__gatilho, .dica__marca").forEach((n) => n.remove());
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

    /**
     * O texto da dica. Oculto (aria-hidden) quando ele mora ao lado de um controle: o leitor de tela já o lê como
     * descrição do controle (aria-describedby lê alvo oculto), e assim ele não aparece de novo na leitura corrida
     * nem vira filho solto de uma lista de abas.
     */
    function novoBalao(texto, oculto) {
        const balao = document.createElement("span");
        balao.id = "d-auto-" + (++sequencia);
        balao.setAttribute("role", "tooltip");
        balao.className = "dica__texto";
        balao.setAttribute("translate", "yes");
        if (oculto) {
            balao.setAttribute("aria-hidden", "true");
        }
        balao.textContent = texto;
        return balao;
    }

    function novaMarca() {
        const marca = document.createElement("span");
        marca.className = "dica__marca";
        marca.setAttribute("aria-hidden", "true");
        marca.innerHTML = SVG_AJUDA;
        return marca;
    }

    function novoGatilho(rotulo, balao) {
        const gatilho = document.createElement("button");
        gatilho.type = "button";
        gatilho.className = "dica__gatilho";
        gatilho.setAttribute("aria-label", "Ajuda sobre " + rotulo);
        gatilho.innerHTML = SVG_AJUDA;
        gatilho.setAttribute("aria-describedby", balao.id);
        return gatilho;
    }

    function novoEnvoltorio(extra) {
        const envoltorio = document.createElement("span");
        envoltorio.className = extra ? "dica " + extra : "dica";
        envoltorio.setAttribute("data-dica-adaptador", "");
        return envoltorio;
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

    /** Padrão 3: gatilho "?" no fim do elemento que não recebe foco (título, coluna, rótulo, selo, item de ficha). */
    function comGatilho(el, texto) {
        const envoltorio = novoEnvoltorio();
        const balao = novoBalao(texto, false);
        envoltorio.appendChild(novoGatilho(rotuloCurto(el), balao));
        envoltorio.appendChild(balao);
        el.appendChild(envoltorio);
    }

    /**
     * Ícone de ajuda solto (ex.: "info" com title dentro de um rótulo): vira o gatilho "?" — um botão alcançável
     * por Tab e pelo toque, com o MESMO ícone dentro, nomeado "Ajuda sobre <rótulo>".
     */
    function iconeVirandoGatilho(el, texto) {
        const dono = el.parentElement && el.parentElement.closest("label, th, legend, caption, h1, h2, h3, h4, h5, h6, dt");
        const envoltorio = novoEnvoltorio();
        const balao = novoBalao(texto, false);
        const gatilho = novoGatilho(dono ? rotuloCurto(dono) : texto.slice(0, 60), balao);
        gatilho.innerHTML = "";
        el.parentNode.insertBefore(envoltorio, el);
        gatilho.appendChild(el);
        el.setAttribute("aria-hidden", "true");
        el.setAttribute("data-dica-texto", texto);
        envoltorio.appendChild(gatilho);
        envoltorio.appendChild(balao);
    }

    /**
     * O controle ocupava a linha do pai (botão w-100, item que estica)? Então o envoltório também ocupa. Mede pela
     * LARGURA, não pelo display: item de menu e bandeira são flex por dentro e nem por isso ocupam a linha.
     */
    function ocupaALinha(el) {
        const pai = el.parentElement;
        if (!pai) {
            return false;
        }
        const ps = w.getComputedStyle(pai);
        // Célula de grade e item que estica no flex: o envoltório passa a ser a célula/o item, e o controle estica nele.
        if (/grid/.test(ps.display) || (/flex/.test(ps.display) && parseFloat(w.getComputedStyle(el).flexGrow) > 0)) {
            return true;
        }
        const largura = el.getBoundingClientRect().width;
        const util = pai.clientWidth - parseFloat(ps.paddingLeft || 0) - parseFloat(ps.paddingRight || 0);
        return largura > 0 && util > 0 && largura >= util * 0.95;
    }

    /** Padrão 1: a .dica envolve o controle, com a marca "?" (controle com texto) e o texto da dica ao lado. */
    function embrulhar(el, texto, comMarca) {
        const envoltorio = novoEnvoltorio(ocupaALinha(el) ? "dica-bloco" : "");
        el.parentNode.insertBefore(envoltorio, el);
        envoltorio.appendChild(el);
        if (comMarca) {
            envoltorio.appendChild(novaMarca());
        }
        const balao = novoBalao(texto, true);
        envoltorio.appendChild(balao);
        descrever(el, balao.id);
    }

    /** Menu suspenso: a .dica (marca + texto) entra logo depois do botão, sem envolvê-lo. */
    function aoLado(el, texto, comMarca) {
        const envoltorio = novoEnvoltorio("dica-irmao");
        if (comMarca) {
            envoltorio.appendChild(novaMarca());
        }
        const balao = novoBalao(texto, true);
        envoltorio.appendChild(balao);
        el.after(envoltorio);
        el.setAttribute("data-dica-irmao", "");
        descrever(el, balao.id);
    }

    /** Item de um grupo com UM "?" para todos: a dica fica no próprio item (o contêiner é exceção registrada). */
    function noGrupo(el, texto) {
        const antes = w.getComputedStyle(el).display;
        el.classList.add("dica");
        if (antes !== "none" && w.getComputedStyle(el).display !== antes) {
            el.style.display = antes;
        }
        const balao = novoBalao(texto, true);
        el.appendChild(balao);
        descrever(el, balao.id);
        if (!el.matches(FOCAVEL) && !el.hasAttribute("tabindex")) {
            el.setAttribute("tabindex", "0");
        }
    }

    /**
     * Padrão 2: o "?" (gatilho) entra logo depois do rótulo do campo; o bloco do campo vira .campo quando o campo é o
     * único controle dele e não há outra dica ali (assim o foco no campo também mostra a dica).
     */
    function noCampo(el, texto) {
        const rotulo = (el.labels && el.labels[0])
            || (el.parentElement && el.parentElement.querySelector(":scope > label"));
        const nomeRotulo = rotulo ? textoVisivel(rotulo) : (limpo(el.getAttribute("aria-label")) || "este campo");
        if (!(el.labels && el.labels.length) && !el.getAttribute("aria-label") && !el.getAttribute("aria-labelledby")) {
            el.setAttribute("aria-label", nomeRotulo !== "este campo" ? nomeRotulo : texto);
        }
        const envoltorio = novoEnvoltorio();
        const balao = novoBalao(texto, false);
        envoltorio.appendChild(novoGatilho(nomeRotulo, balao));
        envoltorio.appendChild(balao);
        const grupo = el.closest(".input-group");
        if (rotulo) {
            rotulo.after(envoltorio);
        } else {
            (grupo || el).before(envoltorio);
        }
        descrever(el, balao.id);
        const bloco = envoltorio.parentElement;
        const sozinho = bloco && bloco !== document.body && !bloco.matches("form, .campo, .dica")
            && bloco.querySelectorAll(CAMPO).length === 1 && bloco.querySelectorAll(".dica").length === 1;
        if (sozinho) {
            bloco.classList.add("campo");
        }
    }

    /**
     * Botão de ajuda que era popover do Bootstrap ("info" ao lado do rótulo, título + explicação): vira o gatilho
     * com a EXPLICAÇÃO no balão — o título do popover já está no rótulo e no aria-label do botão.
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
        const envoltorio = novoEnvoltorio();
        el.parentNode.insertBefore(envoltorio, el);
        envoltorio.appendChild(el);
        const balao = novoBalao(texto, false);
        envoltorio.appendChild(balao);
        descrever(el, balao.id);
    }

    /** Balão que este adaptador já criou para o elemento (pelo aria-describedby ou pelo "?" dentro dele). */
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

    /** Controle desabilitado: atributo nativo, aria-disabled ou a classe disabled do Bootstrap. */
    function desabilitado(el) {
        return el.getAttribute("aria-disabled") === "true" || el.disabled === true
            || !!(el.classList && el.classList.contains("disabled"));
    }

    function semDescricao(el, id) {
        const resto = (el.getAttribute("aria-describedby") || "").split(/\s+/).filter((i) => i && i !== id);
        if (resto.length) {
            el.setAttribute("aria-describedby", resto.join(" "));
        } else {
            el.removeAttribute("aria-describedby");
        }
    }

    function fecharBootstrap(el) {
        if (w.bootstrap && w.bootstrap.Tooltip && w.bootstrap.Tooltip.getInstance(el)) {
            w.bootstrap.Tooltip.getInstance(el).dispose();
        }
    }

    /** Muda o DOM em volta do controle sem tirar o foco do teclado dele (a .dica nova envolve o próprio controle). */
    function mantendoFoco(el, mudar) {
        const tinhaFoco = document.activeElement === el;
        mudar();
        if (tinhaFoco && document.activeElement !== el) {
            el.focus({ preventScroll: true });
        }
    }

    /** Desfaz a .dica que este adaptador pôs em volta do controle (ou ao lado dele, no menu suspenso). */
    function desembrulhar(el) {
        const pai = el.parentElement;
        if (pai && pai.matches(".dica[data-dica-adaptador]") && pai.firstElementChild === el) {
            const balao = pai.querySelector(":scope > .dica__texto");
            mantendoFoco(el, () => {
                pai.parentNode.insertBefore(el, pai);
                pai.remove();
            });
            if (balao) {
                semDescricao(el, balao.id);
            }
            return;
        }
        const irmao = el.nextElementSibling;
        if (el.hasAttribute("data-dica-irmao") && irmao && irmao.matches(".dica-irmao")) {
            const balao = irmao.querySelector(".dica__texto");
            irmao.remove();
            el.removeAttribute("data-dica-irmao");
            if (balao) {
                semDescricao(el, balao.id);
            }
        }
    }

    /**
     * Botão, link, aba ou item de menu com texto visível.
     *
     * PROPÓSITO DE NEGÓCIO: o texto do controle já diz o que ele faz; a dica e o "?" ao lado só repetiam e sujavam a
     *   tela (25/11/2026, as abas da Análise). Desabilitado, a pessoa precisa saber POR QUE ele não anda.
     * INVARIANTES DO DOMÍNIO: habilitado, sem dica, sem "?" e sem envoltório (o layout fica como o do item sem dica);
     *   desabilitado, a dica do motivo sem "?", envolvendo o controle (ao lado dele no menu suspenso), sem tirar o
     *   foco do teclado; o texto que veio no title fica em data-dica-desab e volta se a página desabilitar o controle
     *   depois (o anterior e o próximo do laboratório trocam de estado a cada etapa); o nome acessível é o texto visível.
     * COMPORTAMENTO EM CASO DE FALHA: desabilitado sem texto guardado fica sem dica (a varredura de dicas acusa).
     */
    function controleComTexto(el, texto) {
        if (texto) {
            fecharBootstrap(el);
            tirarTitle(el);
            el.setAttribute("data-dica-desab", texto);
        }
        const motivo = el.getAttribute("data-dica-desab") || "";
        if (!desabilitado(el) || !motivo) {
            desembrulhar(el);
            return;
        }
        const balao = balaoExistente(el);
        if (balao) {
            balao.textContent = motivo;
        } else if (el.matches(IRMAO)) {
            aoLado(el, motivo, false);
        } else {
            mantendoFoco(el, () => embrulhar(el, motivo, false));
        }
    }

    /** UM "?" antes de cada grupo marcado com data-dica-grupo (bits, campos de uma saída, opções, filtros, idiomas). */
    function gatilhoDoGrupo(grupo) {
        const texto = limpo(grupo.getAttribute("data-dica-grupo"));
        if (!texto) {
            return;
        }
        const antes = grupo.previousElementSibling;
        if (antes && antes.matches(".dica-do-grupo")) {
            // Grupo redesenhado (ex.: a régua trocou de modo): o "?" que já existe só troca de texto.
            const balao = antes.querySelector(".dica__texto");
            if (balao && balao.textContent !== texto) {
                balao.textContent = texto;
            }
            return;
        }
        const envoltorio = novoEnvoltorio("dica-do-grupo");
        const balao = novoBalao(texto, false);
        const rotulo = limpo(grupo.getAttribute("data-dica-grupo-rotulo")) || limpo(grupo.getAttribute("aria-label"))
            || "estes itens";
        envoltorio.appendChild(novoGatilho(rotulo, balao));
        envoltorio.appendChild(balao);
        grupo.before(envoltorio);
    }

    function converter(el) {
        if (el.matches(NAO_CONVERTER) || el.closest(".dica__texto")) {
            return;
        }
        if (el.hasAttribute("data-dica-texto")) {
            // Já convertido: a página regravou o title (ex.: a Academia a cada acerto). Atualiza o balão que existe.
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
        const focavel = el.matches(FOCAVEL);
        if (!visivel && !el.getAttribute("aria-label") && !el.matches(CAMPO) && focavel) {
            // Botão/link só com ícone: o title era o único nome. O nome fica; a dica igual ao nome seria repetida.
            el.setAttribute("aria-label", texto);
            tirarTitle(el);
            return;
        }
        if (visivel && visivel.toLowerCase() === texto.toLowerCase()) {
            tirarTitle(el);
            return;
        }
        if (visivel && el.matches(CONTROLE) && !el.matches(CAMPO)) {
            controleComTexto(el, texto);
            return;
        }
        if (el.matches(TITULO)) {
            // Título não leva dica: o que ela dizia de útil vai em texto logo abaixo dele, no template.
            fecharBootstrap(el);
            tirarTitle(el);
            return;
        }
        fecharBootstrap(el);
        tirarTitle(el);
        el.setAttribute("data-dica-texto", texto);
        // Item de grupo (um "?" para todos) ou de contêiner com exceção registrada (o "?" fica no título dele): a dica
        // fica no próprio item, sem "?" em cada um.
        const grupo = el.closest("[data-dica-grupo]");
        if (grupo || el.closest("[data-dica-excecao]")) {
            if (grupo) {
                gatilhoDoGrupo(grupo);
            }
            noGrupo(el, texto);
            return;
        }
        if (el.matches(CAMPO)) {
            noCampo(el, texto);
            return;
        }
        if (el.matches(SO_GATILHO) || !focavel) {
            if (!focavel && !visivel && el.parentNode) {
                iconeVirandoGatilho(el, texto);
                return;
            }
            comGatilho(el, texto);
            return;
        }
        if (el.matches(IRMAO)) {
            aoLado(el, texto, !!visivel);
            return;
        }
        embrulhar(el, texto, !!visivel);
    }

    /**
     * Converte as dicas de um trecho da página: o documento inteiro, ou o que a página acabou de pôr nela.
     *
     * PROPÓSITO DE NEGÓCIO: toda dica escrita como title vira a dica acessível do kit (teclado, toque, leitor de
     *   tela); e o controle com texto que a página desabilitou enquanto este adaptador ainda não vigiava (o Remover
     *   da Resolução de Problemas, desabilitado pelo script da página no carregamento) mostra o motivo mesmo assim.
     * INVARIANTES DO DOMÍNIO: chamar de novo no mesmo trecho não duplica nada (cada conversão confere o que já
     *   existe); o "?" de cada grupo entra antes das dicas dos itens dele; o controle que guarda o motivo em
     *   data-dica-desab fica com a dica do motivo, sem "?", enquanto está desabilitado, e sem dica quando habilitado.
     * COMPORTAMENTO EM CASO DE FALHA: raiz sem querySelectorAll vale o documento inteiro; elemento sem texto de dica
     *   fica como está.
     */
    function init(raiz) {
        const escopo = raiz && raiz.querySelectorAll ? raiz : document;
        const grupos = [];
        if (escopo.nodeType === 1 && escopo.matches("[data-dica-grupo]")) {
            grupos.push(escopo);
        }
        escopo.querySelectorAll("[data-dica-grupo]").forEach((g) => grupos.push(g));
        grupos.forEach(gatilhoDoGrupo);
        const alvos = [];
        const COM_DICA = "[title], [data-bs-title], [data-bs-original-title], [data-bs-toggle=popover]";
        if (escopo.nodeType === 1 && escopo.matches(COM_DICA)) {
            alvos.push(escopo);
        }
        escopo.querySelectorAll(COM_DICA).forEach((el) => alvos.push(el));
        alvos.forEach(converter);
        // Controle que mudou de estado sem ninguém vigiar (antes do vigiar() começar, ou fora do documento, num nó
        // ainda solto): a dica do motivo aparece ou some conforme o estado de agora.
        const comMotivo = [];
        if (escopo.nodeType === 1 && escopo.matches("[data-dica-desab]")) {
            comMotivo.push(escopo);
        }
        escopo.querySelectorAll("[data-dica-desab]").forEach((el) => comMotivo.push(el));
        comMotivo.forEach((el) => controleComTexto(el, ""));
    }

    function vigiar() {
        if (!w.MutationObserver) {
            return;
        }
        new MutationObserver((mudancas) => {
            for (const m of mudancas) {
                if (m.type === "attributes") {
                    const alvo = m.target;
                    if (alvo.nodeType !== 1) {
                        continue;
                    }
                    if (m.attributeName === "title") {
                        if (alvo.hasAttribute("title")) {
                            converter(alvo);
                        }
                    } else if (alvo.hasAttribute("data-dica-desab")) {
                        // A página habilitou ou desabilitou um controle com texto: a dica do motivo aparece ou some.
                        controleComTexto(alvo, "");
                    }
                    continue;
                }
                m.addedNodes.forEach((n) => {
                    if (n.nodeType === 1 && !n.closest(".dica__texto") && !n.matches(".dica__texto, [data-dica-adaptador]")) {
                        init(n);
                    }
                });
            }
        }).observe(document.body, {
            childList: true, subtree: true, attributes: true, attributeFilter: ["title", "aria-disabled", "disabled"]
        });
    }

    /**
     * Menu suspenso: a .dica mora DEPOIS do botão (não o envolve), então o foco do teclado no botão não abre a dica
     * pelo canônico. Abre aqui, ancorada na marca "?" (o pointerover sintético faz o dica.js posicionar), e fecha ao
     * sair do botão; Esc e o clique fora continuam com o dica.js.
     */
    function focoNoIrmao() {
        const irmaoDe = (el) => {
            const botao = el && el.closest ? el.closest("[data-dica-irmao]") : null;
            const prox = botao && botao.nextElementSibling;
            return prox && prox.matches(".dica-irmao") ? prox : null;
        };
        document.addEventListener("focusin", (e) => {
            const dica = irmaoDe(e.target);
            if (!dica || !e.target.matches(":focus-visible")) {
                return;
            }
            dica.setAttribute("data-aberta", "");
            dica.removeAttribute("data-fechada");
            const alvo = dica.querySelector(".dica__marca") || dica;
            alvo.dispatchEvent(new PointerEvent("pointerover", { bubbles: true }));
        });
        document.addEventListener("focusout", (e) => {
            const dica = irmaoDe(e.target);
            if (dica) {
                dica.removeAttribute("data-aberta");
            }
        });
    }

    /**
     * Dica aberta pela marca "?": o texto passa a receber o ponteiro (data-pela-marca), para o mouse descer da marca
     * ao texto sem a dica sumir (WCAG 1.4.13). Aberta pelo próprio controle, o texto continua deixando o clique passar
     * para o vizinho (dica-camada.css).
     */
    function abertaPelaMarca() {
        document.addEventListener("pointerover", (e) => {
            const marca = e.target.closest && e.target.closest(".dica__marca");
            const dica = marca && marca.closest(".dica");
            if (dica) {
                dica.setAttribute("data-pela-marca", "");
            }
        });
        document.addEventListener("pointerout", (e) => {
            const dica = e.target.closest && e.target.closest(".dica[data-pela-marca]");
            if (dica && !dica.contains(e.relatedTarget)) {
                dica.removeAttribute("data-pela-marca");
            }
        });
    }

    w.FieldTooltips = { init, textoLimpo };
    focoNoIrmao();
    abertaPelaMarca();

    // Converte já o que existe (script de página no fim do <body> roda antes do DOMContentLoaded e não pode
    // achar title sem converter) e de novo no DOMContentLoaded, quando o documento está inteiro.
    if (document.body) {
        init(document);
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => { init(document); vigiar(); });
    } else {
        vigiar();
    }
})(window);
