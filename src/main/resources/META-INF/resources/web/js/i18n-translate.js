// Internacionalização via Google Translate (client-side).
// IMPORTANTE: este arquivo é servido estaticamente e NÃO passa pelo parser do Qute,
// por isso os objetos literais { ... } do JS são seguros aqui (dentro de um template
// Qute, `{pageLanguage:...}` seria interpretado como expressão e quebraria a página).

// Callback global chamado por translate_a/element.js?cb=googleTranslateElementInit
function googleTranslateElementInit() {
    new google.translate.TranslateElement(
        { pageLanguage: 'pt', autoDisplay: false },
        'google_translate_element'
    );
}

// Troca de idioma acionada pelos botões de bandeira (main_menu.html)
function aedTranslate(lang) {
    var domain = window.location.hostname;
    if (lang === 'pt') {
        document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
        document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=' + domain + ';';
    } else {
        document.cookie = 'googtrans=/pt/' + lang + '; path=/;';
        document.cookie = 'googtrans=/pt/' + lang + '; path=/; domain=' + domain + ';';
    }
    window.location.reload();
}

/**
 * Aviso quando o tradutor do Google não responde.
 *
 * PROPÓSITO DE NEGÓCIO: quem escolhe inglês ou espanhol normalmente não lê português — e, quando o
 *   Google não entrega a tradução, a bandeira parecia não fazer nada (botão morto, sem explicação).
 *   Medido em 01/10/2026: o Google às vezes responde o element.js com um redirecionamento para a
 *   página de captcha (google.com/sorry, limite de uso por endereço), que o CSP bloqueia — com razão,
 *   é uma página HTML e não um script. Este bloco detecta a falha, avisa NO IDIOMA PEDIDO e oferece
 *   caminhos: tentar de novo, abrir a página pelo Google Tradutor (onde o captcha aparece e pode ser
 *   resolvido) ou ficar em português.
 *
 * INVARIANTES DO DOMÍNIO:
 *   - só age quando há tradução pedida (cookie googtrans para um idioma diferente de pt);
 *   - não muda o `lang` do documento (regra de i18n, cicatriz §5b); o aviso leva `lang` próprio;
 *   - o aviso tem translate="no": se a tradução chegar depois, ele some em vez de ser retraduzido;
 *   - "Abrir no Google Tradutor" só para endereço público em https — o proxy do Google não alcança
 *     localhost nem rede interna, e oferecer o que não funciona seria outro botão morto;
 *   - falha detectada pelo erro de carregamento do element.js (inclusive o bloqueio do captcha pelo
 *     CSP) ou por prazo sem tradução; tradução que chega atrasada remove o aviso.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sem cookie, sem DOM ou com qualquer exceção interna, não faz nada
 *   e não lança — tradução é conforto, e o aviso não pode quebrar a página que ele protege.
 */
(function () {
    var PRAZO_MS = 10000;
    var TEXTOS = {
        en: { msg: 'Google Translate did not respond, so this page is still in Portuguese.',
              denovo: 'Try again', abrir: 'Open in Google Translate', voltar: 'Stay in Portuguese', fechar: 'Close this notice' },
        es: { msg: 'El Traductor de Google no respondió; la página sigue en portugués.',
              denovo: 'Intentar de nuevo', abrir: 'Abrir en el Traductor de Google', voltar: 'Seguir en portugués', fechar: 'Cerrar este aviso' },
        pt: { msg: 'O tradutor do Google não respondeu; a página continua em português.',
              denovo: 'Tentar de novo', abrir: 'Abrir no Google Tradutor', voltar: 'Ficar em português', fechar: 'Fechar este aviso' }
    };
    var aviso = null;

    function idiomaPedido() {
        var m = document.cookie.match(/(?:^|;\s*)googtrans=\/[A-Za-z-]+\/([A-Za-z-]+)/);
        var lang = m ? m[1].toLowerCase() : null;
        return lang && lang !== 'pt' ? lang : null;
    }

    function traduzida() {
        var c = document.documentElement.classList;
        return c.contains('translated-ltr') || c.contains('translated-rtl');
    }

    function enderecoPublico() {
        var h = window.location.hostname;
        return window.location.protocol === 'https:' && h.indexOf('.') > 0
            && !/^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(h);
    }

    function botao(rotulo, acao) {
        var b = document.createElement('button');
        b.type = 'button';
        b.textContent = rotulo;
        b.style.cssText = 'font:inherit;font-size:.85rem;padding:.35rem .75rem;border-radius:.5rem;cursor:pointer;'
            + 'border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.06);color:inherit';
        b.addEventListener('click', acao);
        return b;
    }

    function mostrar() {
        var lang = idiomaPedido();
        if (!lang || aviso || traduzida() || !document.body) {
            return;
        }
        var t = TEXTOS[lang] || TEXTOS.pt;
        aviso = document.createElement('div');
        aviso.className = 'aed-tradutor-aviso notranslate';
        aviso.setAttribute('translate', 'no');
        aviso.setAttribute('role', 'status');
        aviso.setAttribute('lang', TEXTOS[lang] ? lang : 'pt');
        aviso.style.cssText = 'position:fixed;left:2%;right:2%;bottom:1rem;z-index:2147483000;display:flex;flex-wrap:wrap;'
            + 'align-items:center;gap:.5rem 1rem;padding:.75rem 1rem;border-radius:.75rem;'
            + 'background:#0f1622;color:#eef2f8;border:1px solid #f59e0b;box-shadow:0 .5rem 1.5rem rgba(0,0,0,.45);'
            + 'font-family:"Inter Tight",system-ui,sans-serif;font-size:.95rem';
        var texto = document.createElement('p');
        texto.textContent = t.msg;
        texto.style.cssText = 'margin:0;flex:1 1 18rem;min-width:0';
        var acoes = document.createElement('div');
        acoes.style.cssText = 'display:flex;flex-wrap:wrap;gap:.5rem';
        acoes.appendChild(botao(t.denovo, function () { window.location.reload(); }));
        if (enderecoPublico()) {
            var abrir = document.createElement('a');
            abrir.textContent = t.abrir;
            abrir.href = 'https://translate.google.com/translate?sl=pt&tl=' + encodeURIComponent(lang)
                + '&u=' + encodeURIComponent(window.location.href);
            abrir.rel = 'noopener';
            abrir.style.cssText = 'font-size:.85rem;padding:.35rem .75rem;border-radius:.5rem;'
                + 'border:1px solid #2dd4bf;color:#2dd4bf;text-decoration:none';
            acoes.appendChild(abrir);
        }
        acoes.appendChild(botao(t.voltar, function () { aedTranslate('pt'); }));
        var fechar = botao('×', function () { remover(); });
        fechar.setAttribute('aria-label', t.fechar);
        fechar.title = t.fechar;
        acoes.appendChild(fechar);
        aviso.appendChild(texto);
        aviso.appendChild(acoes);
        document.body.appendChild(aviso);
    }

    function remover() {
        if (aviso && aviso.parentNode) {
            aviso.parentNode.removeChild(aviso);
        }
        aviso = null;
    }

    try {
        if (!idiomaPedido()) {
            return;
        }
        // Erro de carregamento de <script> não sobe por bolha: só a fase de captura no window o vê.
        window.addEventListener('error', function (ev) {
            var alvo = ev && ev.target;
            if (alvo && alvo.tagName === 'SCRIPT' && /translate_a\/element\.js/.test(alvo.src || '')) {
                if (document.body) { mostrar(); } else { document.addEventListener('DOMContentLoaded', mostrar); }
            }
        }, true);
        window.setTimeout(mostrar, PRAZO_MS);
        // Tradução que chega depois do aviso: o aviso sai, porque deixou de ser verdade.
        new MutationObserver(function () {
            if (traduzida()) {
                remover();
            }
        }).observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    } catch (e) {
        // falha aberta: o aviso é conforto, nunca motivo para quebrar a página
    }
})();

// Ilhas técnicas não se traduzem (regra de i18n do projeto, §4): nome de ícone Material Symbols
// traduzido APAGA o glifo ("delete" → "borrar"), e IP/CLI/hex traduzidos viram lixo. Os templates já
// nascem carimbados; isto carimba o que o JavaScript pinta DEPOIS do carregamento (resultados htmx,
// tabelas dinâmicas), que de outra forma nasceria desprotegido.
(function () {
    var SELETOR = '.material-symbols-outlined, code, pre, kbd, samp';

    function carimbar(el) {
        if (el.getAttribute('translate') !== 'no') {
            el.setAttribute('translate', 'no');
        }
        if (!el.classList.contains('notranslate')) {
            el.classList.add('notranslate');
        }
        if (el.classList.contains('material-symbols-outlined') && !el.hasAttribute('aria-hidden')
                && !el.hasAttribute('aria-label') && !el.hasAttribute('role')) {
            el.setAttribute('aria-hidden', 'true');
        }
    }

    // Valor técnico pintado como texto solto (auditoria FRONT-07): IP, prefixo, máscara, MAC, IPv6,
    // binário, hexadecimal e linha de ACL. O tradutor trocava "permit" por "permitir" e reformatava
    // número; a ilha precisa ser a folha que contém SÓ o valor (texto misto com palavras continua
    // traduzível, de propósito).
    var VALOR_TECNICO = new RegExp('^(?:' + [
        '\\d{1,3}(?:\\.\\d{1,3}){3}(?:\\/\\d{1,2})?',                 // IPv4, máscara, CIDR
        '\\/\\d{1,3}',                                                  // prefixo solto
        '(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}',                          // MAC
        '[0-9A-Fa-f]{0,4}(?::[0-9A-Fa-f]{0,4}){2,7}(?:\\/\\d{1,3})?',     // IPv6 (com prefixo)
        '[01]{4,}(?:[ .][01]{4,})*',                                        // binário
        '0x[0-9A-Fa-f]+',                                                   // hexadecimal
        '(?:permit|deny|access-list|ip access-list)\\b.*'                 // linha de ACL
    ].join('|') + ')$', 'i');

    function ehFolhaTecnica(el) {
        if (el.children.length !== 0 || el.closest('[translate="no"]')) {
            return false;
        }
        var texto = (el.textContent || '').trim();
        return texto.length > 0 && texto.length <= 200 && VALOR_TECNICO.test(texto);
    }

    function varrer(raiz) {
        if (!raiz || raiz.nodeType !== 1) {
            return;
        }
        if (raiz.matches && raiz.matches(SELETOR)) {
            carimbar(raiz);
        }
        raiz.querySelectorAll(SELETOR).forEach(carimbar);
        if (ehFolhaTecnica(raiz)) {
            carimbar(raiz);
        }
        raiz.querySelectorAll('td, th, span, strong, b, dd, li, div, p, output, small').forEach(function (el) {
            if (ehFolhaTecnica(el)) {
                carimbar(el);
            }
        });
    }

    function iniciar() {
        varrer(document.body);
        new MutationObserver(function (mutacoes) {
            mutacoes.forEach(function (m) {
                m.addedNodes.forEach(varrer);
            });
        }).observe(document.body, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();
