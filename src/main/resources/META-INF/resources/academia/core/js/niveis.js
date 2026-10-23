/**
 * Desbloqueio dos níveis da Academia, um por vez, conforme o aluno conclui o anterior.
 *
 * PROPÓSITO DE NEGÓCIO: a trilha é feita em ordem. O primeiro nível está sempre aberto; os
 *   seguintes ficam trancados até o aluno concluir todas as lições do nível anterior — e, uma vez
 *   desbloqueados, ficam desbloqueados para ele (neste navegador, que é onde o progresso mora).
 *   As abas da landing, a navegação (abas de nível e sub-abas de lição) e o aviso nas páginas dos
 *   níveis leem o estado daqui.
 *
 * INVARIANTES DO DOMÍNIO:
 *   - estados possíveis: 'concluido', 'liberado', 'bloqueado' e 'em-breve' (nível ainda sem lições);
 *   - o primeiro nível com lições nunca é bloqueado;
 *   - um nível desbloqueia quando o nível aberto anterior está liberado E concluído; nível
 *     "em breve" no meio não quebra a corrente (é pulado);
 *   - desbloqueio nunca regride: o que já foi desbloqueado fica registrado no AcademiaProgresso, e
 *     só "apagar o progresso" tranca de novo — lição nova num nível já concluído não tranca nada;
 *   - navegador que não guarda progresso libera tudo e diz isso na tela: sem onde guardar, o aluno
 *     nunca conseguiria desbloquear, e o trancamento é de ensino, não de segurança.
 *
 * COMPORTAMENTO EM CASO DE FALHA: mapa de níveis ausente ou malformado na página ⇒ nada é pintado
 *   nem trancado (as páginas continuam inteiras, como vieram do servidor). Script clássico:
 *   window.AcademiaNiveis no navegador, module.exports (só a conta pura) no Node.
 */
(function (raiz) {
    'use strict';

    /**
     * Conta pura dos estados. `mapa`: [{ id, aberto, licoes: [ids] }] na ordem de estudo;
     * `concluida(licaoId)`: a lição foi concluída?; `desbloqueados`: ids já registrados;
     * `guardaProgresso`: o navegador deixa guardar? Devolve [{ id, estado, requisito, novo }] —
     * `requisito` é o nível liberado que falta concluir para chegar aqui (só no bloqueado), `novo`
     * diz que o desbloqueio aconteceu agora e precisa ser registrado.
     */
    function calcularEstados(mapa, concluida, desbloqueados, guardaProgresso) {
        var ja = {};
        (desbloqueados || []).forEach(function (id) { ja[id] = true; });
        var resultado = [];
        var primeiroAberto = true;
        var anteriorCompleto = false;
        var anterior = null;
        mapa.forEach(function (nivel) {
            if (!nivel.aberto || !nivel.licoes || nivel.licoes.length === 0) {
                resultado.push({ id: nivel.id, estado: 'em-breve', requisito: null, novo: false });
                return;
            }
            var concluido = nivel.licoes.every(function (id) { return concluida(id); });
            var porCorrente = primeiroAberto || anteriorCompleto;
            var liberado = !guardaProgresso || porCorrente || ja[nivel.id] === true;
            // O que falta concluir: o nível anterior, ou — se ele também está bloqueado — o mesmo
            // nível que ele espera. Assim o aviso sempre aponta um nível que dá para fazer agora.
            var requisito = null;
            if (!liberado) {
                requisito = anterior && anterior.estado === 'bloqueado' ? anterior.requisito : anterior.id;
            }
            var item = {
                id: nivel.id,
                estado: !liberado ? 'bloqueado' : (concluido ? 'concluido' : 'liberado'),
                requisito: requisito,
                novo: guardaProgresso && liberado && !primeiroAberto && ja[nivel.id] !== true
            };
            resultado.push(item);
            anteriorCompleto = liberado && concluido;
            anterior = item;
            primeiroAberto = false;
        });
        return resultado;
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = { calcularEstados: calcularEstados };
        return;
    }

    /** Lê o mapa que o servidor escreveu na página (academia/core/mapa-niveis.html). */
    function lerMapa() {
        var el = document.querySelector('[data-acad-mapa]');
        if (!el) {
            return null;
        }
        var mapa = [];
        Array.prototype.forEach.call(el.querySelectorAll('[data-nivel]'), function (n) {
            mapa.push({
                id: n.getAttribute('data-nivel'),
                titulo: n.getAttribute('data-titulo'),
                rota: n.getAttribute('data-rota'),
                aberto: n.getAttribute('data-aberto') === 'true',
                licoes: (n.getAttribute('data-licoes') || '').split(' ').filter(Boolean)
            });
        });
        return mapa.length ? mapa : null;
    }

    /** Estados atuais, já registrando os desbloqueios novos. Null sem mapa ou sem progresso. */
    function estados() {
        var P = raiz.AcademiaProgresso;
        var mapa = lerMapa();
        if (!mapa || !P) {
            return null;
        }
        var guarda = P.disponivel();
        var lista = calcularEstados(mapa, function (id) { return Boolean(P.ler(id).concluidaEm); },
            P.niveisDesbloqueados(), guarda);
        lista.forEach(function (e, i) {
            if (e.novo) {
                P.registrarDesbloqueio(e.id);
            }
            e.titulo = mapa[i].titulo;
            e.rota = mapa[i].rota;
        });
        return { niveis: lista, guardaProgresso: guarda };
    }

    function porId(lista, id) {
        for (var i = 0; i < lista.length; i++) {
            if (lista[i].id === id) {
                return lista[i];
            }
        }
        return null;
    }

    /**
     * Aviso das páginas de nível e de lição ([data-acad-bloqueio="<nivelId>"]): aparece só quando
     * o nível está bloqueado neste navegador, dizendo qual nível concluir e com o caminho até ele.
     * A página continua aberta para leitura — quem chega por um buscador não encontra tela vazia.
     */
    function pintarAvisos() {
        var avisos = document.querySelectorAll('[data-acad-bloqueio]');
        if (!avisos.length) {
            return;
        }
        var atual = estados();
        Array.prototype.forEach.call(avisos, function (aviso) {
            var e = atual ? porId(atual.niveis, aviso.getAttribute('data-acad-bloqueio')) : null;
            if (!e || e.estado !== 'bloqueado') {
                aviso.hidden = true;
                return;
            }
            var req = porId(atual.niveis, e.requisito);
            var texto = aviso.querySelector('[data-acad-bloqueio-texto]');
            var link = aviso.querySelector('[data-acad-bloqueio-link]');
            if (texto && req) {
                texto.textContent = 'Este nível ainda está bloqueado neste navegador: ele desbloqueia quando você concluir '
                    + 'todas as lições de ' + req.titulo + '. Dá para olhar, mas o caminho recomendado começa por lá.';
            }
            if (link && req) {
                link.setAttribute('href', req.rota);
                var rotulo = link.querySelector('[data-acad-bloqueio-rotulo]');
                if (rotulo) {
                    rotulo.textContent = 'Ir para ' + req.titulo;
                }
                link.setAttribute('title', 'Abre o nível ' + req.titulo + ', que desbloqueia este');
            }
            aviso.hidden = false;
        });
    }

    var ESTADO = {
        concluido: { icone: 'task_alt', texto: 'concluído' },
        liberado: { icone: 'lock_open', texto: 'liberado' },
        bloqueado: { icone: 'lock', texto: 'bloqueado' },
        'em-breve': { icone: 'schedule', texto: 'em breve' }
    };

    function iconeETexto(alvo, icone, texto) {
        alvo.textContent = '';
        var i = document.createElement('span');
        i.className = 'material-symbols-outlined';
        i.setAttribute('aria-hidden', 'true');
        i.setAttribute('translate', 'no');
        i.textContent = icone;
        var t = document.createElement('span');
        t.textContent = texto;
        alvo.appendChild(i);
        alvo.appendChild(t);
    }

    /**
     * Navegação das páginas de nível e de lição (academia/core/navegacao.html): cada aba de nível
     * ganha ícone e texto do estado; nível bloqueado (que não seja o aberto agora) perde o link e diz
     * qual nível concluir; cada sub-aba de lição mostra se foi concluída. Ícone sempre com texto.
     */
    function pintarNavegacao() {
        var nav = document.querySelector('[data-acad-nav]');
        var P = raiz.AcademiaProgresso;
        if (!nav || !P) {
            return;
        }
        var atual = estados();
        if (atual) {
            Array.prototype.forEach.call(nav.querySelectorAll('[data-acad-nav-nivel]'), function (link) {
                var e = porId(atual.niveis, link.getAttribute('data-acad-nav-nivel'));
                var alvo = link.querySelector('[data-acad-aba-estado]');
                if (!e || !alvo) {
                    return;
                }
                var info = ESTADO[e.estado] || ESTADO.liberado;
                iconeETexto(alvo, info.icone, info.texto);
                Object.keys(ESTADO).forEach(function (k) { link.classList.remove('estado-' + k); });
                link.classList.add('estado-' + e.estado);
                var aqui = link.hasAttribute('aria-current');
                var req = e.requisito ? porId(atual.niveis, e.requisito) : null;
                if (e.estado === 'bloqueado' && !aqui) {
                    if (link.hasAttribute('href')) {
                        link.setAttribute('data-acad-href', link.getAttribute('href'));
                        link.removeAttribute('href');
                    }
                    link.setAttribute('aria-disabled', 'true');
                    link.setAttribute('title', (e.titulo || '') + ': bloqueado — conclua ' + (req ? req.titulo : 'o nível anterior') + ' para abrir');
                    // ACAD-16: sem href o link sai da ordem do Tab, e o motivo morava só no title.
                    link.setAttribute('tabindex', '0');
                    link.setAttribute('role', 'link');
                    link.setAttribute('aria-label', link.getAttribute('title'));
                } else if (link.hasAttribute('data-acad-href')) {
                    link.setAttribute('href', link.getAttribute('data-acad-href'));
                    link.removeAttribute('data-acad-href');
                    link.removeAttribute('aria-disabled');
                    link.removeAttribute('tabindex');
                    link.removeAttribute('role');
                    link.removeAttribute('aria-label');
                    link.setAttribute('title', 'Abre o nível ' + (e.titulo || ''));
                }
            });
        }
        Array.prototype.forEach.call(nav.querySelectorAll('[data-acad-subaba-selo]'), function (alvo) {
            if (!P.disponivel()) {
                alvo.textContent = '';
                return;
            }
            var estado = P.ler(alvo.getAttribute('data-acad-subaba-selo'));
            if (estado.concluidaEm) {
                iconeETexto(alvo, 'task_alt', 'concluída');
                alvo.className = 'acad-subaba-estado concluida';
            } else if (estado.tentativas > 0) {
                iconeETexto(alvo, 'pending', estado.acertos + ' de ' + P.ACERTOS_PARA_CONCLUIR);
                alvo.className = 'acad-subaba-estado';
            } else {
                alvo.textContent = '';
                alvo.className = 'acad-subaba-estado';
            }
        });
    }

    function pintarTudo() {
        pintarNavegacao();
        pintarAvisos();
    }

    raiz.AcademiaNiveis = {
        calcularEstados: calcularEstados,
        estados: estados,
        pintarAvisos: pintarAvisos,
        pintarNavegacao: pintarNavegacao,
        ESTADO: ESTADO,
        iconeETexto: iconeETexto
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', pintarTudo);
    } else {
        pintarTudo();
    }
    raiz.addEventListener('storage', function (ev) {
        if (raiz.AcademiaProgresso && raiz.AcademiaProgresso.eventoDeProgresso(ev)) {
            pintarTudo();
        }
    });
    raiz.addEventListener('academia:progresso', pintarTudo);
}(typeof window !== 'undefined' ? window : this));
