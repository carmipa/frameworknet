/**
 * Atalhos de teclado de todas as páginas: F1 abre a documentação; Esc duas vezes volta à Início.
 *
 * PROPÓSITO DE NEGÓCIO: pedido de Paulo (25/11/2026) — chegar à documentação e voltar à Início pelo teclado,
 *   de qualquer tela.
 * INVARIANTES DO DOMÍNIO:
 *   - Esc continua sendo a tecla de FECHAR (dica, menu, diálogo, painel do campo). O atalho só conta o Esc que
 *     ninguém tratou, quando nada estava aberto ANTES da tecla e o foco não está num campo de digitação.
 *   - Voltar à Início exige um SEGUNDO Esc em até JANELA_MS, depois de um aviso anunciado ao leitor de tela:
 *     o Esc dado por reflexo não tira ninguém da página.
 *   - F1 abre a documentação em NOVA aba: o que foi digitado na página atual não se perde.
 *   - Qualquer outra tecla zera a sequência; Home, setas e o resto ficam com o navegador.
 * COMPORTAMENTO EM CASO DE FALHA: nova aba bloqueada vira aviso com o link da documentação; erro dentro do
 *   tratador é engolido para não quebrar a página (o atalho simplesmente não age).
 */
(function (raiz) {
    'use strict';

    var JANELA_MS = 2000;
    var ROTA_DOC = '/documentacao';
    var ROTA_INICIO = '/';

    /**
     * Decide o que fazer com uma tecla. Função pura: o estado entra e sai pelo argumento.
     *
     * @param {{tecla: string, comModificador: boolean, jaTratada: boolean, emCampo: boolean, algoAberto: boolean,
     *          naInicio: boolean, agora: number, ultimoEsc: (number|null)}} c
     * @returns {{acao: ('nada'|'documentacao'|'avisar'|'inicio'), ultimoEsc: (number|null)}}
     */
    function decidir(c) {
        if (c.tecla === 'F1' && !c.comModificador && !c.jaTratada) {
            return { acao: 'documentacao', ultimoEsc: null };
        }
        if (c.tecla !== 'Escape' || c.comModificador) {
            return { acao: 'nada', ultimoEsc: null };
        }
        if (c.jaTratada || c.emCampo || c.algoAberto || c.naInicio) {
            return { acao: 'nada', ultimoEsc: null };
        }
        if (c.ultimoEsc !== null && c.agora - c.ultimoEsc >= 0 && c.agora - c.ultimoEsc <= JANELA_MS) {
            return { acao: 'inicio', ultimoEsc: null };
        }
        return { acao: 'avisar', ultimoEsc: c.agora };
    }

    var api = { decidir: decidir, JANELA_MS: JANELA_MS };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (typeof document === 'undefined') {
        return;
    }
    raiz.FnAtalhos = api;

    var ABERTO = 'dialog[open], .modal.show, .offcanvas.show, .dropdown-menu.show, .aed-nav-drop-menu.show, '
        + 'nav.nav-aberta, [data-aberta], .dica:hover, .dica:focus-within';
    var ultimoEsc = null;
    var abertoAntes = false;
    var aviso = null;
    var escondeAviso = null;

    function emCampo(el) {
        if (!el || el === document.body) {
            return false;
        }
        if (el.isContentEditable) {
            return true;
        }
        var nome = (el.tagName || '').toLowerCase();
        if (nome === 'textarea' || nome === 'select') {
            return true;
        }
        if (nome !== 'input') {
            return false;
        }
        var tipo = (el.getAttribute('type') || 'text').toLowerCase();
        return ['button', 'submit', 'reset', 'checkbox', 'radio', 'range', 'color', 'file', 'image'].indexOf(tipo) < 0;
    }

    function mostrarAviso(texto, link) {
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.className = 'atalho-aviso';
            aviso.setAttribute('role', 'status');
            aviso.setAttribute('aria-live', 'polite');
            document.body.appendChild(aviso);
        }
        aviso.textContent = '';
        var icone = document.createElement('span');
        icone.className = 'material-symbols-outlined';
        icone.setAttribute('aria-hidden', 'true');
        icone.setAttribute('translate', 'no');
        icone.textContent = link ? 'menu_book' : 'home';
        aviso.appendChild(icone);
        var corpo = document.createElement('span');
        corpo.textContent = texto;
        aviso.appendChild(corpo);
        if (link) {
            var a = document.createElement('a');
            a.href = link;
            a.target = '_blank';
            a.rel = 'noopener';
            a.textContent = ' Abrir a documentação';
            aviso.appendChild(a);
        }
        aviso.classList.add('visivel');
        if (escondeAviso) {
            clearTimeout(escondeAviso);
        }
        escondeAviso = setTimeout(function () { aviso.classList.remove('visivel'); }, link ? 6000 : JANELA_MS);
    }

    // Fase de captura: fotografa se havia algo aberto ANTES de os outros tratadores fecharem com este Esc.
    document.addEventListener('keydown', function (ev) {
        if (ev.key === 'Escape') {
            try {
                abertoAntes = document.querySelector(ABERTO) !== null;
            } catch (e) {
                abertoAntes = true;
            }
        }
    }, true);

    document.addEventListener('keydown', function (ev) {
        try {
            var r = decidir({
                tecla: ev.key,
                comModificador: ev.ctrlKey || ev.altKey || ev.metaKey || ev.shiftKey,
                jaTratada: ev.defaultPrevented,
                emCampo: emCampo(document.activeElement),
                algoAberto: abertoAntes,
                naInicio: raiz.location.pathname === ROTA_INICIO,
                agora: Date.now(),
                ultimoEsc: ultimoEsc
            });
            ultimoEsc = r.ultimoEsc;
            abertoAntes = false;
            if (r.acao === 'documentacao') {
                ev.preventDefault();
                var janela = raiz.open(ROTA_DOC, '_blank');
                if (janela) {
                    janela.opener = null;
                } else {
                    mostrarAviso('O navegador bloqueou a nova aba.', ROTA_DOC);
                }
            } else if (r.acao === 'avisar') {
                mostrarAviso('Pressione Esc de novo para voltar à Início.', null);
            } else if (r.acao === 'inicio') {
                raiz.location.assign(ROTA_INICIO);
            }
        } catch (e) {
            ultimoEsc = null;
        }
    });
}(typeof window !== 'undefined' ? window : this));
