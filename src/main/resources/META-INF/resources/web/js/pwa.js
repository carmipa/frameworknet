/*
 * Registro do service worker e do convite de instalação.
 *
 * PROPOSITO DE NEGOCIO
 *     Habilita o botao "Instalar" que o Chrome mostra na barra de endereco e o
 *     atalho na area de trabalho. O service worker so e registrado depois do
 *     load, para nao disputar banda com o primeiro render.
 *
 * INVARIANTES DO DOMINIO
 *     - Arquivo separado, nao <script> inline: a CSP em vigor precisaria de
 *       'unsafe-inline' extra, e a decisao do projeto e reduzir inline, nao
 *       aumentar.
 *     - Escopo "/" para o service worker valer no site inteiro.
 *     - Se o navegador nao suportar service worker, nada acontece: o site
 *       funciona igual, so nao oferece instalacao.
 *
 * COMPORTAMENTO EM CASO DE FALHA
 *     Falha de registro e apenas registrada no console. Nenhuma excecao escapa,
 *     e nenhuma funcionalidade da aplicacao depende deste arquivo.
 */
(function (w) {
    "use strict";

    if (!("serviceWorker" in w.navigator)) {
        return;
    }

    w.addEventListener("load", function () {
        w.navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(function (erro) {
            // Instalação é conveniência: falhar aqui não pode afetar o uso normal.
            console.warn("Service worker nao registrado:", erro && erro.message);
        });
    });
})(window);
