/**
 * A fileira de oito bits que as lições de binário e hexadecimal desenham.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê cada bit com o seu peso e, quando a fileira é interativa,
 *   acende e apaga com o mouse, o toque ou o teclado. A mesma fileira serve para a animação
 *   (só exibe), para o "Mexer" (interativa) e para a correção do "Provar" (marca o bit errado).
 *
 * INVARIANTES DO DOMÍNIO (INV-ACAD-007):
 *   - cada bit é um <button> de verdade com aria-pressed — funciona com teclado e leitor de tela;
 *   - o rótulo acessível diz o peso e o estado ("peso 32, aceso");
 *   - valores e pesos vão em translate="no";
 *   - nenhum texto em canvas: tudo é DOM.
 *
 * COMPORTAMENTO EM CASO DE FALHA: container ausente devolve uma fileira inerte (métodos que não
 *   fazem nada) em vez de lançar.
 */
(function (raiz) {
    'use strict';

    var PESOS = [128, 64, 32, 16, 8, 4, 2, 1];

    function montar(container, opcoes) {
        var interativo = Boolean(opcoes && opcoes.interativo);
        var aoMudar = (opcoes && opcoes.aoMudar) || function () {};
        var valorAtual = 0;
        var botoes = [];

        if (!container) {
            return { definir: function () {}, valor: function () { return 0; }, marcarErros: function () {},
                destacar: function () {}, limparMarcas: function () {} };
        }
        container.textContent = '';
        if (opcoes && opcoes.nibbles) {
            container.classList.add('com-nibbles');
        }

        PESOS.forEach(function (peso, posicao) {
            var botao = document.createElement('button');
            botao.type = 'button';
            botao.className = 'acad-bit';
            botao.setAttribute('data-icone-excecao', 'bit do byte: o valor 0 ou 1 é o próprio rótulo');
            botao.setAttribute('aria-pressed', 'false');
            var valor = document.createElement('span');
            valor.className = 'acad-bit-valor';
            valor.setAttribute('translate', 'no');
            valor.setAttribute('aria-hidden', 'true');
            valor.textContent = '0';
            // O peso aparece pelo CSS (::after com data-peso): é anotação do bit, não parte do rótulo que se fala.
            // Como texto no botão, ele entrava no rótulo visível ("0128") e o nome acessível não o continha (WCAG 2.5.3).
            botao.dataset.peso = String(peso);
            botao.appendChild(valor);
            if (interativo) {
                botao.title = 'Acende ou apaga o bit de peso ' + peso;
                botao.addEventListener('click', function () {
                    definir(valorAtual ^ peso);
                    aoMudar(valorAtual, posicao);
                });
            } else {
                // Só exibe: aria-disabled (e não disabled) para a dica explicar o porquê no hover e no leitor de
                // tela — elemento disabled não recebe nem foco nem hover (UX-05). Fora da ordem de Tab.
                botao.setAttribute('aria-disabled', 'true');
                botao.tabIndex = -1;
                botao.title = 'Nesta etapa o bit só mostra o valor. Para acender e apagar você mesmo, vá para a etapa Mexer.';
            }
            botoes.push(botao);
            container.appendChild(botao);
        });

        function definir(n) {
            valorAtual = n & 0xFF;
            botoes.forEach(function (botao, posicao) {
                var aceso = (valorAtual & PESOS[posicao]) !== 0;
                botao.setAttribute('aria-pressed', aceso ? 'true' : 'false');
                botao.querySelector('.acad-bit-valor').textContent = aceso ? '1' : '0';
                botao.setAttribute('aria-label', (aceso ? '1' : '0') + ', peso ' + PESOS[posicao] + ', ' + (aceso ? 'aceso' : 'apagado'));
            });
        }

        function limparMarcas() {
            botoes.forEach(function (b) {
                b.classList.remove('marcado-erro');
                b.classList.remove('destaque');
            });
        }

        function marcarErros(posicoes) {
            limparMarcas();
            (posicoes || []).forEach(function (p) {
                if (botoes[p]) {
                    botoes[p].classList.add('marcado-erro');
                }
            });
        }

        /** Destaca uma posição ou uma lista de posições; -1 ou [] apaga o destaque. */
        function destacar(posicoes) {
            var lista = Array.isArray(posicoes) ? posicoes : [posicoes];
            botoes.forEach(function (b, i) {
                b.classList.toggle('destaque', lista.indexOf(i) >= 0);
            });
        }

        definir(0);
        return {
            definir: definir,
            valor: function () { return valorAtual; },
            marcarErros: marcarErros,
            destacar: destacar,
            limparMarcas: limparMarcas
        };
    }

    raiz.AcademiaBits = { montar: montar, PESOS: PESOS.slice() };
}(window));
