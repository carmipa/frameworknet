/*
 * Comportamento comum das páginas de aprofundamento por protocolo.
 *
 * Propósito de negócio: as páginas são longas e cheias de bloco de comando;
 * sem índice lateral o leitor se perde, e sem botão de cópia ele digita errado
 * o comando de laboratório. Este arquivo resolve as duas coisas para qualquer
 * página do módulo, sem script inline (o CSP do projeto ainda depende de
 * 'unsafe-inline' e a meta é reduzir essa dependência, não ampliá-la).
 *
 * Invariantes: nenhum estado é persistido e nada é enviado ao servidor — a
 * página é conteúdo estático. O índice só marca seções que existem no DOM.
 *
 * Comportamento em caso de falha: navegador sem clipboard assíncrono (contexto
 * não seguro) recebe aviso visual de falha em vez de silêncio; ausência de
 * IntersectionObserver desliga só o realce do índice, mantendo os links.
 */
(function () {
    "use strict";

    function ligarCopia() {
        document.addEventListener("click", async function (ev) {
            const botao = ev.target.closest("[data-copiar]");
            if (!botao) {
                return;
            }
            const alvo = document.getElementById(botao.getAttribute("data-copiar"));
            const texto = alvo ? (window.FieldTooltips ? window.FieldTooltips.textoLimpo(alvo) : alvo.textContent) : "";
            const original = botao.innerHTML;
            try {
                await navigator.clipboard.writeText(texto);
                botao.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">check</span> Copiado';
            } catch (err) {
                botao.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">error</span> Falhou';
                console.warn("Cópia recusada pelo navegador", err);
            }
            setTimeout(function () {
                botao.innerHTML = original;
            }, 1600);
        });
    }

    function ligarIndice() {
        const links = Array.from(document.querySelectorAll("[data-indice-link]"));
        if (links.length === 0 || typeof IntersectionObserver === "undefined") {
            return;
        }
        const porId = new Map(links.map(function (link) {
            return [link.getAttribute("href").slice(1), link];
        }));
        const secoes = links
            .map(function (link) {
                return document.getElementById(link.getAttribute("href").slice(1));
            })
            .filter(Boolean);

        const observador = new IntersectionObserver(function (entradas) {
            entradas.forEach(function (entrada) {
                const link = porId.get(entrada.target.id);
                if (!link) {
                    return;
                }
                if (entrada.isIntersecting) {
                    links.forEach(function (outro) {
                        outro.classList.remove("is-current");
                    });
                    link.classList.add("is-current");
                }
            });
        }, { rootMargin: "-20% 0px -70% 0px", threshold: 0 });

        secoes.forEach(function (secao) {
            observador.observe(secao);
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        ligarCopia();
        ligarIndice();
    });
})();
