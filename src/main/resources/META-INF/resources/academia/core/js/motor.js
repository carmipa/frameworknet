/**
 * Motor de animação das lições da Academia (a parte "Ver").
 *
 * PROPÓSITO DE NEGÓCIO: a animação mostra o conceito acontecendo passo a passo, e o aluno manda no
 *   ritmo — toca, pausa, avança um passo, muda a velocidade, recomeça. Quem prefere movimento
 *   reduzido vê o estado final na hora, sem perder a explicação.
 *
 * INVARIANTES DO DOMÍNIO (INV-ACAD-007):
 *   - nenhum texto é desenhado em canvas: a legenda é DOM, traduzível, e o valor técnico vai em
 *     <span translate="no">;
 *   - a legenda fala em aria-live "polite" só quando o passo muda, nunca a cada quadro;
 *   - aba escondida pausa a animação (não roda no fundo nem "pula" ao voltar);
 *   - prefers-reduced-motion: Tocar aplica todos os passos de uma vez; Passo continua funcionando.
 *
 * COMPORTAMENTO EM CASO DE FALHA: raiz ausente ou sem passos devolve um motor inerte (os botões
 *   ficam desabilitados) em vez de lançar; um passo que lança é registrado no console e o motor
 *   para nele, com a legenda dizendo que a animação parou.
 */
(function (raiz) {
    'use strict';

    var BASE_MS = 1400;

    function movimentoReduzido() {
        try {
            return raiz.matchMedia && raiz.matchMedia('(prefers-reduced-motion: reduce)').matches;
        } catch (e) {
            return false;
        }
    }

    /**
     * Escreve uma legenda feita de partes: texto comum ou { valor: '...' } protegido da tradução.
     */
    function montarTexto(el, partes) {
        if (!el) {
            return;
        }
        el.textContent = '';
        (partes || []).forEach(function (parte) {
            if (parte && typeof parte === 'object' && 'valor' in parte) {
                var span = document.createElement('span');
                span.className = 'acad-valor';
                span.setAttribute('translate', 'no');
                span.textContent = String(parte.valor);
                el.appendChild(span);
            } else {
                el.appendChild(document.createTextNode(String(parte)));
            }
        });
    }

    function criar(opcoes) {
        var container = opcoes && opcoes.raiz;
        var passos = (opcoes && opcoes.passos) || [];
        var aoReiniciar = (opcoes && opcoes.reiniciar) || function () {};
        var indice = -1;
        var temporizador = null;
        var velocidade = 1;

        var btnTocar = container ? container.querySelector('[data-acad-acao="tocar"]') : null;
        var btnPasso = container ? container.querySelector('[data-acad-acao="passo"]') : null;
        var btnReiniciar = container ? container.querySelector('[data-acad-acao="reiniciar"]') : null;
        var seletorVelocidade = container ? container.querySelector('[data-acad-velocidade]') : null;
        var legenda = container ? container.querySelector('[data-acad-legenda]') : null;

        function rotuloTocar(texto, icone) {
            if (!btnTocar) {
                return;
            }
            var iconeEl = btnTocar.querySelector('.material-symbols-outlined');
            var textoEl = btnTocar.querySelector('[data-acad-rotulo]');
            if (iconeEl) {
                iconeEl.textContent = icone;
            }
            if (textoEl) {
                textoEl.textContent = texto;
            }
        }

        function tocando() {
            return temporizador !== null;
        }

        function pausar() {
            if (temporizador !== null) {
                clearTimeout(temporizador);
                temporizador = null;
            }
            rotuloTocar(indice >= passos.length - 1 ? 'Repetir' : 'Tocar', indice >= passos.length - 1 ? 'replay' : 'play_arrow');
        }

        function aplicar(i) {
            try {
                passos[i].aplicar();
                montarTexto(legenda, passos[i].legenda);
                indice = i;
                return true;
            } catch (falha) {
                if (raiz.console) {
                    raiz.console.error('animação parou no passo ' + (i + 1), falha);
                }
                montarTexto(legenda, ['A animação parou neste passo. Use Recomeçar para tentar de novo.']);
                pausar();
                return false;
            }
        }

        function reiniciar() {
            pausar();
            indice = -1;
            try {
                aoReiniciar();
            } catch (e) {
                if (raiz.console) {
                    raiz.console.error('falha ao recomeçar a animação', e);
                }
            }
            montarTexto(legenda, (opcoes && opcoes.legendaInicial) || ['Toque em Tocar ou avance com Passo.']);
            rotuloTocar('Tocar', 'play_arrow');
        }

        function passo() {
            pausar();
            if (indice >= passos.length - 1) {
                return;
            }
            aplicar(indice + 1);
            if (indice >= passos.length - 1) {
                rotuloTocar('Repetir', 'replay');
            }
        }

        function agendar() {
            temporizador = setTimeout(function () {
                temporizador = null;
                if (!aplicar(indice + 1)) {
                    return;
                }
                if (indice < passos.length - 1) {
                    agendar();
                } else {
                    pausar();
                }
            }, BASE_MS / velocidade);
        }

        function tocar() {
            if (!passos.length) {
                return;
            }
            if (indice >= passos.length - 1) {
                reiniciar();
            }
            if (movimentoReduzido()) {
                for (var i = indice + 1; i < passos.length; i++) {
                    if (!aplicar(i)) {
                        return;
                    }
                }
                pausar();
                return;
            }
            rotuloTocar('Pausar', 'pause');
            if (indice < 0 && !aplicar(0)) {
                return;
            }
            if (indice >= passos.length - 1) {
                pausar();
                return;
            }
            agendar();
        }

        if (!container || !passos.length) {
            [btnTocar, btnPasso, btnReiniciar, seletorVelocidade].forEach(function (el) {
                if (el) {
                    el.disabled = true;
                }
            });
            return { tocar: function () {}, pausar: function () {}, passo: function () {}, reiniciar: function () {} };
        }

        if (btnTocar) {
            btnTocar.addEventListener('click', function () {
                if (tocando()) {
                    pausar();
                } else {
                    tocar();
                }
            });
        }
        if (btnPasso) {
            btnPasso.addEventListener('click', passo);
        }
        if (btnReiniciar) {
            btnReiniciar.addEventListener('click', reiniciar);
        }
        if (seletorVelocidade) {
            seletorVelocidade.addEventListener('change', function () {
                var v = parseFloat(seletorVelocidade.value);
                velocidade = v > 0 && v <= 4 ? v : 1;
            });
        }
        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                pausar();
            }
        });

        reiniciar();
        return { tocar: tocar, pausar: pausar, passo: passo, reiniciar: reiniciar };
    }

    raiz.AcademiaMotor = { criar: criar, montarTexto: montarTexto, movimentoReduzido: movimentoReduzido };
}(window));
