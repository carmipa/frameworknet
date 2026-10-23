/*
 * Tela de acesso à Telemetria: arte Matrix e o botão de mostrar/ocultar a chave.
 *
 * Propósito de negócio: dar à tela de login a mesma linguagem visual do resto do
 * sistema, com tokens de segurança em vez de caracteres aleatórios, e permitir
 * conferir a chave digitada sem precisar apagá-la e redigitar.
 *
 * Invariantes: NENHUMA decisão de autenticação acontece aqui. Qual tela aparece
 * (GitHub ou contingência) é decidido pelo servidor; a chave nunca é guardada,
 * comparada ou enviada por este arquivo — ela vai no POST do formulário e é
 * validada no backend. Segurança em JavaScript é enfeite, não proteção.
 *
 * Comportamento em caso de falha: sem <canvas>, a arte simplesmente não roda.
 * Com prefers-reduced-motion, pinta o fundo e não anima.
 */
(function () {
    "use strict";

    /* ---------- mostrar / ocultar a chave ---------- */
    document.addEventListener("click", function (ev) {
        var botao = ev.target.closest("[data-olho]");
        if (!botao) {
            return;
        }
        var campo = document.getElementById(botao.getAttribute("data-olho"));
        if (!campo) {
            return;
        }
        var oculto = campo.type === "password";
        campo.type = oculto ? "text" : "password";
        var icone = botao.querySelector("span");
        if (icone) {
            icone.textContent = oculto ? "visibility_off" : "visibility";
        }
    });

    /* ---------- chuva Matrix de tokens de segurança ---------- */
    var cv = document.getElementById("matrix");
    if (!cv || !cv.getContext) {
        return;
    }

    var ctx = cv.getContext("2d");
    var glifos = "0123456789ABCDEF/.:".split("");
    var palavras = ["OAUTH", "TLS1.3", "SHA256", "TOKEN", "HMAC", "JWT", "x509",
        "AES", "RSA", "KEY", "::1", "AUTH"];
    var colunas = [];
    var largura = 14;
    var W = 0;
    var H = 0;
    var tique = 0;

    function redimensionar() {
        var r = cv.getBoundingClientRect();
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        W = Math.max(1, Math.round(r.width));
        H = Math.max(1, Math.round(r.height));
        cv.width = W * dpr;
        cv.height = H * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        var n = Math.max(6, Math.floor(W / largura));
        colunas = [];
        for (var i = 0; i < n; i++) {
            colunas.push({
                y: Math.random() * -H,
                sp: 0.6 + Math.random() * 1.8,
                len: 6 + Math.floor(Math.random() * 13)
            });
        }
    }

    function desenhar() {
        ctx.fillStyle = "rgba(4,7,12,0.16)";
        ctx.fillRect(0, 0, W, H);
        ctx.font = "11px 'JetBrains Mono', monospace";
        tique++;

        for (var i = 0; i < colunas.length; i++) {
            var c = colunas[i];
            var x = i * largura + 4;
            for (var k = 0; k < c.len; k++) {
                var y = c.y - k * largura;
                if (y < -largura || y > H + largura) {
                    continue;
                }
                var cabeca = (k === 0);
                var usaPalavra = !cabeca && ((i + k + Math.floor(tique / 30)) % 15 === 0);
                var ch = usaPalavra
                    ? palavras[(i + k) % palavras.length]
                    : glifos[Math.floor(Math.random() * glifos.length)];

                if (cabeca) {
                    ctx.fillStyle = "#eafffb";
                    ctx.shadowColor = "#2dd4bf";
                    ctx.shadowBlur = 10;
                } else {
                    ctx.fillStyle = "#2dd4bf";
                    ctx.globalAlpha = Math.max(0, 0.72 - k / c.len);
                    ctx.shadowBlur = 0;
                }
                ctx.fillText(ch, x, y);
                ctx.globalAlpha = 1;
                ctx.shadowBlur = 0;
            }

            c.y += c.sp * 2;
            if (c.y - c.len * largura > H) {
                c.y = Math.random() * -60;
                c.sp = 0.6 + Math.random() * 1.8;
                c.len = 6 + Math.floor(Math.random() * 13);
            }
        }
        requestAnimationFrame(desenhar);
    }

    redimensionar();
    if (window.ResizeObserver) {
        new ResizeObserver(redimensionar).observe(cv);
    } else {
        window.addEventListener("resize", redimensionar);
    }

    if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        ctx.fillStyle = "#04070c";
        ctx.fillRect(0, 0, W, H);
    } else {
        desenhar();
    }
})();
