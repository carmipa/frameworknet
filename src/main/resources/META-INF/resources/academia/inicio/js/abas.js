/**
 * Abas da trilha na landing: um nível por aba, desbloqueando conforme o aluno conclui o anterior.
 *
 * PROPÓSITO DE NEGÓCIO: no lugar da lista de todos os níveis, o aluno vê os níveis como abas — o
 *   que ele já concluiu, o que está liberado e o que ainda está trancado, com o motivo escrito e o
 *   nível que falta concluir. Ordem de Paulo (01/10/2026): "as abas só desbloqueiam depois que a
 *   pessoa termina uma trilha, e aí ficam desbloqueadas para essa pessoa".
 *
 * INVARIANTES DO DOMÍNIO:
 *   - o estado vem do AcademiaNiveis (dono único da regra); aqui só se pinta;
 *   - todo estado tem ícone E texto visível (concluído, liberado, bloqueado, em breve) — a cor só
 *     reforça; o motivo do bloqueio fica escrito no painel, nunca só na dica do mouse;
 *   - aba bloqueada pode ser aberta (para ler o motivo), mas os links das lições dela ficam
 *     desligados até o desbloqueio;
 *   - padrão de abas do WAI-ARIA: setas, Home e End trocam de aba; só a aba ativa entra no Tab;
 *   - a aba inicial é o nível em que o aluno está (o primeiro liberado e não concluído);
 *   - outra aba do navegador que concluir uma lição repinta esta (evento storage), e "apagar o
 *     progresso" tranca de volta na hora.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sem o AcademiaNiveis, sem o mapa ou sem abas na página, nada é
 *   transformado: a trilha fica como veio do servidor, todos os níveis visíveis em lista.
 */
(function () {
    'use strict';

    var raiz = document.querySelector('[data-acad-abas]');
    var N = window.AcademiaNiveis;
    if (!raiz || !N) {
        return;
    }
    var lista = raiz.querySelector('[data-acad-abas-lista]');
    var abas = Array.prototype.slice.call(raiz.querySelectorAll('[data-acad-aba]'));
    var paineis = Array.prototype.slice.call(raiz.querySelectorAll('[data-acad-painel]'));
    if (!lista || !abas.length || abas.length !== paineis.length) {
        return;
    }

    var ESTADO = N.ESTADO;
    var selecionada = null;

    function porId(niveis, id) {
        for (var i = 0; i < niveis.length; i++) {
            if (niveis[i].id === id) {
                return niveis[i];
            }
        }
        return null;
    }

    function selecionar(id, focar) {
        selecionada = id;
        abas.forEach(function (aba) {
            var ativa = aba.getAttribute('data-acad-aba') === id;
            aba.setAttribute('aria-selected', ativa ? 'true' : 'false');
            aba.setAttribute('tabindex', ativa ? '0' : '-1');
            aba.classList.toggle('active', ativa);
            if (ativa && focar) {
                aba.focus();
            }
        });
        paineis.forEach(function (painel) {
            painel.hidden = painel.getAttribute('data-acad-painel') !== id;
        });
    }

    function pintarAba(aba, estado, titulo) {
        var info = ESTADO[estado.estado] || ESTADO.liberado;
        N.iconeETexto(aba.querySelector('[data-acad-aba-estado]'), info.icone, info.texto);
        Object.keys(ESTADO).forEach(function (e) { aba.classList.remove('estado-' + e); });
        aba.classList.add('estado-' + estado.estado);
    }

    function pintarPainel(painel, estado, requisito) {
        var bloqueado = estado.estado === 'bloqueado';
        var aviso = painel.querySelector('[data-acad-painel-bloqueio]');
        if (aviso) {
            aviso.hidden = !bloqueado;
            var texto = aviso.querySelector('[data-acad-painel-bloqueio-texto]');
            if (texto && bloqueado && requisito) {
                texto.textContent = 'Desbloqueia quando você concluir todas as lições de ' + requisito.titulo
                    + '. Abra a aba ' + requisito.titulo + ' para continuar.';
            }
        }
        painel.classList.toggle('bloqueado', bloqueado);
        Array.prototype.forEach.call(painel.querySelectorAll('.acad-licao-link'), function (link) {
            if (bloqueado) {
                if (link.hasAttribute('href')) {
                    link.setAttribute('data-acad-href', link.getAttribute('href'));
                    // O adaptador de dicas já trocou o title pelo balão: o texto original fica em data-dica-texto.
                    link.setAttribute('data-acad-title', link.getAttribute('title') || link.getAttribute('data-dica-texto') || '');
                    link.removeAttribute('href');
                }
                link.setAttribute('aria-disabled', 'true');
                link.setAttribute('title', 'Bloqueada: conclua ' + (requisito ? requisito.titulo : 'o nível anterior') + ' para abrir');
                link.setAttribute('tabindex', '0');
                // Texto visível do link, sem o texto do balão que o adaptador pôs dentro dele.
                var visivel = window.FieldTooltips ? window.FieldTooltips.textoLimpo(link) : link.textContent;
                link.setAttribute('aria-label', (visivel || '').replace(/\s+/g, ' ').trim() + ' — ' + link.getAttribute('title'));
            } else {
                if (link.hasAttribute('data-acad-href')) {
                    link.setAttribute('href', link.getAttribute('data-acad-href'));
                    link.setAttribute('title', link.getAttribute('data-acad-title') || '');
                    link.removeAttribute('data-acad-href');
                    link.removeAttribute('data-acad-title');
                }
                link.removeAttribute('aria-disabled');
                link.removeAttribute('tabindex');
                link.removeAttribute('aria-label');
            }
        });
    }

    function avisoSemArmazenamento(mostrar) {
        var aviso = raiz.querySelector('[data-acad-abas-aviso]');
        if (!aviso && mostrar) {
            aviso = document.createElement('p');
            aviso.className = 'acad-aviso-local';
            aviso.setAttribute('data-acad-abas-aviso', '');
            var icone = document.createElement('span');
            icone.className = 'material-symbols-outlined';
            icone.setAttribute('aria-hidden', 'true');
            icone.setAttribute('translate', 'no');
            icone.textContent = 'info';
            var texto = document.createElement('span');
            texto.textContent = 'Este navegador não deixa guardar o progresso, então todos os níveis ficam abertos.';
            aviso.appendChild(icone);
            aviso.appendChild(texto);
            raiz.insertBefore(aviso, lista);
        }
        if (aviso) {
            aviso.hidden = !mostrar;
        }
    }

    /** Recalcula os estados e repinta abas e painéis, mantendo a aba que o aluno escolheu. */
    function repintar() {
        var atual = N.estados();
        if (!atual) {
            return;
        }
        abas.forEach(function (aba, i) {
            var id = aba.getAttribute('data-acad-aba');
            var estado = porId(atual.niveis, id);
            if (!estado) {
                return;
            }
            pintarAba(aba, estado, estado.titulo || id);
            pintarPainel(paineis[i], estado, estado.requisito ? porId(atual.niveis, estado.requisito) : null);
        });
        avisoSemArmazenamento(!atual.guardaProgresso);
        if (window.AcademiaProgresso) {
            Array.prototype.forEach.call(raiz.querySelectorAll('[data-acad-selo]'), function (selo) {
                window.AcademiaProgresso.pintarSelo(selo, selo.getAttribute('data-acad-selo'));
            });
        }
        if (!selecionada) {
            var onde = atual.niveis.filter(function (e) { return e.estado === 'liberado'; })[0]
                || atual.niveis.filter(function (e) { return e.estado === 'concluido'; }).pop()
                || atual.niveis[0];
            selecionar(onde.id, false);
        } else {
            selecionar(selecionada, false);
        }
    }

    abas.forEach(function (aba, i) {
        aba.addEventListener('click', function () {
            selecionar(aba.getAttribute('data-acad-aba'), false);
        });
        aba.addEventListener('keydown', function (ev) {
            var alvo = null;
            if (ev.key === 'ArrowRight' || ev.key === 'ArrowDown') {
                alvo = abas[(i + 1) % abas.length];
            } else if (ev.key === 'ArrowLeft' || ev.key === 'ArrowUp') {
                alvo = abas[(i - 1 + abas.length) % abas.length];
            } else if (ev.key === 'Home') {
                alvo = abas[0];
            } else if (ev.key === 'End') {
                alvo = abas[abas.length - 1];
            }
            if (alvo) {
                ev.preventDefault();
                selecionar(alvo.getAttribute('data-acad-aba'), true);
            }
        });
    });

    lista.hidden = false;
    raiz.classList.add('com-abas');
    repintar();
    window.addEventListener('storage', function (ev) {
        if (window.AcademiaProgresso && window.AcademiaProgresso.eventoDeProgresso(ev)) {
            repintar();
        }
    });
    window.addEventListener('academia:progresso', repintar);
    window.AcademiaAbas = { repintar: repintar };
}());
