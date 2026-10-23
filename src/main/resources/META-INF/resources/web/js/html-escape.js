(function (w) {
    "use strict";

    function esc(s) {
        if (s == null || s === "") {
            return "";
        }
        return String(s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    /**
     * Ícone local + texto, no lugar de emoji fazendo papel de ícone (auditoria FRONT-18).
     * Propósito: emoji varia por sistema (no Windows a bandeira vira letras, o semáforo muda de cor) e não
     *   segue o sistema de ícones do site. O glifo é o Material Symbols local.
     * Invariante: o glifo sai com aria-hidden e translate="no"; o texto é escapado e continua sendo o sinal.
     * Falha: nome de ícone vazio devolve só o texto.
     */
    function icone(nome, texto) {
        var glifo = nome ? '<span class="material-symbols-outlined" translate="no" aria-hidden="true">' + esc(nome) + "</span> " : "";
        return glifo + esc(texto);
    }

    /** Emoji de semáforo no começo de um texto vindo do servidor vira o glifo equivalente. */
    var SEMAFORO = { "\uD83D\uDD34": "error", "\uD83D\uDFE1": "warning", "\uD83D\uDFE2": "check_circle", "\uD83C\uDFE0": "home" };
    function semaforo(texto) {
        var t = String(texto == null ? "" : texto).trim();
        for (var k in SEMAFORO) {
            if (t.indexOf(k) === 0) {
                return icone(SEMAFORO[k], t.slice(k.length).trim());
            }
        }
        return esc(t);
    }

    w.HtmlEscape = { esc: esc, icone: icone, semaforo: semaforo };
})(window);
