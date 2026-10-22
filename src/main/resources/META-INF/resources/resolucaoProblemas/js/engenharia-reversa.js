/*
 * Comportamento da aba "Engenharia reversa".
 *
 * Propósito de negócio: dois gestos que a tela precisa e que não valem uma ida
 * ao servidor — copiar o script corrigido de um roteador e abrir a impressão do
 * resultado. O botão "Executar" NÃO mora aqui: ele é submit de formulário, porque
 * a interpretação acontece no backend, onde a regra pode ser testada.
 *
 * Invariantes: nada é enviado a lugar nenhum e nenhum estado é guardado. A
 * impressão usa a própria janela do navegador; o recorte do que sai no papel é
 * responsabilidade do @media print do CSS, não deste arquivo.
 *
 * Comportamento em caso de falha: navegador sem clipboard assíncrono recebe aviso
 * visual em vez de silêncio; ausência dos elementos simplesmente não liga nada.
 */
(function () {
    "use strict";

    document.addEventListener("click", async function (ev) {
        const copiar = ev.target.closest("[data-copiar-reversa]");
        if (copiar) {
            const alvo = document.getElementById(copiar.getAttribute("data-copiar-reversa"));
            const original = copiar.innerHTML;
            try {
                await navigator.clipboard.writeText(alvo ? (window.FieldTooltips ? window.FieldTooltips.textoLimpo(alvo) : alvo.textContent) : "");
                copiar.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">check</span> Copiado';
            } catch (err) {
                copiar.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">error</span> Falhou';
                console.warn("Cópia recusada pelo navegador", err);
            }
            setTimeout(function () {
                copiar.innerHTML = original;
            }, 1600);
            return;
        }

        if (ev.target.closest("[data-imprimir-reversa]")) {
            window.print();
        }
    });
})();
