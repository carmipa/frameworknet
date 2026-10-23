/* Módulo Tráfego: atalhos de exemplo e limpeza do decodificador.
   A decodificação em si é feita pelo htmx (ver hx-post no formulário). */
(function () {
    "use strict";

    // Ethernet + IPv4 + TCP (SYN para porta 80) — exemplo didático: MAC de origem unicast e os dois
    // checksums corretos (auditoria CONT-35; guarda em ExemploDecodificadorGuardTest).
    var EXEMPLO =
        "aabb ccdd eeff 0211 2233 4455 0800\n" +
        "4500 0028 1c46 4000 4006 9d36 c0a8 0001 c0a8 0002\n" +
        "d431 0050 0000 0000 0000 0000 5002 7210 e7fc 0000";

    document.addEventListener("DOMContentLoaded", function () {
        var hex = document.getElementById("trafego-hex");
        if (!hex) return; // página sem a aba do decodificador

        var exemplo = document.getElementById("trafego-exemplo");
        var limpar = document.getElementById("trafego-limpar");

        if (exemplo) {
            exemplo.addEventListener("click", function () {
                hex.value = EXEMPLO;
                document.getElementById("trafego-camada").value = "auto";
                document.getElementById("form-trafego").requestSubmit();
            });
        }
        if (limpar) {
            limpar.addEventListener("click", function () {
                hex.value = "";
                document.getElementById("trafego-resultado").replaceChildren();
            });
        }
    });
})();
