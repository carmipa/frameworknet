/* Simulador de encapsulamento: atalhos de exemplo e limpeza.
   O encapsulamento em si é feito pelo htmx (ver hx-post no formulário). */
(function () {
    "use strict";

    function $(id) { return document.getElementById(id); }

    document.addEventListener("DOMContentLoaded", function () {
        var form = $("form-encap");
        if (!form) return; // página sem a aba de encapsulamento

        $("encap-exemplo-dns").addEventListener("click", function () {
            $("encap-msg").value = "example.com A?";
            $("encap-transporte").value = "UDP";
            $("encap-porta-origem").value = "51000";
            $("encap-porta-destino").value = "53";
            $("encap-ip-destino").value = "8.8.8.8";
            form.requestSubmit();
        });

        // O botão "Limpar" (encap-limpar) usa o mecanismo genérico data-limpar
        // (limpar-form.js): esvazia os campos do form E apaga o resultado. O antigo
        // handler daqui só apagava o resultado — deixava os campos preenchidos.
    });
})();
