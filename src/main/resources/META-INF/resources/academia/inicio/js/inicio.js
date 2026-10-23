/**
 * Landing da Academia: a demonstração viva de oito bits e o "apagar o progresso deste navegador"
 * (os selos são do selos.js do kernel).
 *
 * PROPÓSITO DE NEGÓCIO: quem chega deve mexer em algo antes de ler qualquer coisa (D5). A
 *   demonstração é a ideia da primeira lição em miniatura. E quem usa computador compartilhado
 *   precisa poder apagar o próprio progresso, senão o próximo aluno começa com as lições marcadas.
 *
 * INVARIANTES DO DOMÍNIO: a demonstração é DOM (nada de texto em canvas), cada bit é botão com
 *   aria-pressed e rótulo; duplicação consciente da fileira de bits da lição — a landing é outra
 *   fatia e não carrega script de fatia alheia. Apagar pergunta antes, dizendo quantas lições e
 *   que é só deste navegador e que os níveis voltam a trancar; sem progresso guardado, não
 *   pergunta nada. Depois de apagar, as abas da trilha são repintadas na hora (trancadas).
 *
 * COMPORTAMENTO EM CASO DE FALHA: sem o container da demonstração nada é desenhado; sem o
 *   AcademiaProgresso o botão de apagar fica sem ação.
 */
(function () {
    'use strict';

    var PESOS = [128, 64, 32, 16, 8, 4, 2, 1];
    var container = document.getElementById('demo-bits');
    var saida = document.getElementById('demo-valor');
    var valor = 0;

    if (container && saida) {
        PESOS.forEach(function (peso) {
            var botao = document.createElement('button');
            botao.type = 'button';
            botao.className = 'acad-bit';
            botao.title = 'Acende ou apaga o bit de peso ' + peso;
            var digito = document.createElement('span');
            digito.className = 'acad-bit-valor';
            digito.setAttribute('translate', 'no');
            digito.setAttribute('aria-hidden', 'true');
            var rotulo = document.createElement('span');
            rotulo.className = 'acad-bit-peso';
            rotulo.setAttribute('translate', 'no');
            rotulo.setAttribute('aria-hidden', 'true');
            rotulo.textContent = String(peso);
            botao.appendChild(digito);
            botao.appendChild(rotulo);
            botao.addEventListener('click', function () {
                valor ^= peso;
                pintar();
            });
            container.appendChild(botao);
        });
    }

    function pintar() {
        if (!container || !saida) {
            return;
        }
        Array.prototype.forEach.call(container.querySelectorAll('.acad-bit'), function (botao, i) {
            var aceso = (valor & PESOS[i]) !== 0;
            botao.setAttribute('aria-pressed', aceso ? 'true' : 'false');
            botao.setAttribute('aria-label', 'peso ' + PESOS[i] + ', ' + (aceso ? 'aceso' : 'apagado'));
            botao.querySelector('.acad-bit-valor').textContent = aceso ? '1' : '0';
        });
        saida.textContent = String(valor);
    }

    pintar();

    // Computador compartilhado: apagar o progresso local, com o escopo e a quantidade na pergunta.
    var botaoApagar = document.getElementById('apagar-progresso');
    var ecoApagar = document.getElementById('apagar-progresso-eco');
    var P = window.AcademiaProgresso;
    if (botaoApagar && P) {
        botaoApagar.addEventListener('click', function () {
            var quantas = P.licoesGuardadas().length;
            if (quantas === 0) {
                ecoApagar.textContent = 'Não há progresso guardado neste navegador.';
                return;
            }
            var pergunta = 'Apagar o progresso de ' + quantas + (quantas === 1 ? ' lição' : ' lições')
                + ' guardado neste navegador? Os níveis desbloqueados voltam a ficar trancados. Isso não pode ser desfeito.';
            if (!window.confirm(pergunta)) {
                return;
            }
            var apagadas = P.apagarTudo();
            ecoApagar.textContent = 'Progresso de ' + apagadas + (apagadas === 1 ? ' lição apagado.' : ' lições apagado.');
            Array.prototype.forEach.call(document.querySelectorAll('[data-acad-selo]'), function (selo) {
                P.pintarSelo(selo, selo.getAttribute('data-acad-selo'));
            });
            if (window.AcademiaAbas) {
                window.AcademiaAbas.repintar();
            }
        });
    }
}());
