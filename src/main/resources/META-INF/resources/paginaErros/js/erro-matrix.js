/*
 * Chuva Matrix de pacotes de rede — arte das páginas de erro.
 *
 * Propósito de negócio: a página de erro é o momento em que o usuário se sente
 * perdido. A animação existe para que a tela pareça parte do sistema, e não uma
 * falha dele — e usa vocabulário real de redes (SYN, ACK, TTL, /30) em vez de
 * caracteres aleatórios, porque o assunto do site é esse.
 *
 * Invariantes: a cor sai de --accent em tempo de execução, então acompanha
 * automaticamente o estado do erro definido pela classe do <body> — não existe
 * cor escrita aqui. Nada é enviado a lugar nenhum; é puramente decorativo.
 *
 * Comportamento em caso de falha: sem <canvas> na página, sai sem fazer nada.
 * Com prefers-reduced-motion, pinta apenas o fundo e não anima. Sem
 * ResizeObserver, cai para o evento resize da janela.
 */
(function () {
    "use strict";

    var cv = document.getElementById("matrix");
    if (!cv || !cv.getContext) {
        return;
    }

    var ctx = cv.getContext("2d");
    var glyphs = "0123456789ABCDEF/.:".split("");
    var words = ["192.168", "10.0.0", "ACK", "SYN", "RST", "TTL", "DROP", "x1F",
        "FFFE", "::1", "/24", "/30", "MTU", "CRC"];
    var cols = [];
    var cw = 14;
    var W = 0;
    var H = 0;
    var tick = 0;

    function accent() {
        return getComputedStyle(document.body).getPropertyValue("--accent").trim() || "#2dd4bf";
    }

    function resize() {
        var r = cv.getBoundingClientRect();
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        W = Math.max(1, Math.round(r.width));
        H = Math.max(1, Math.round(r.height));
        cv.width = W * dpr;
        cv.height = H * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        var n = Math.max(6, Math.floor(W / cw));
        cols = [];
        for (var i = 0; i < n; i++) {
            cols.push({
                y: Math.random() * -H,
                sp: 0.6 + Math.random() * 1.9,
                len: 6 + Math.floor(Math.random() * 14)
            });
        }
    }

    function draw() {
        var ac = accent();
        // Rastro: em vez de limpar, pinta um véu quase transparente por cima.
        ctx.fillStyle = "rgba(4,7,12,0.16)";
        ctx.fillRect(0, 0, W, H);
        ctx.font = "11px 'JetBrains Mono', monospace";
        tick++;

        for (var i = 0; i < cols.length; i++) {
            var c = cols[i];
            var x = i * cw + 4;
            for (var k = 0; k < c.len; k++) {
                var y = c.y - k * cw;
                if (y < -cw || y > H + cw) {
                    continue;
                }
                var head = (k === 0);
                var usaPalavra = !head && ((i + k + Math.floor(tick / 30)) % 17 === 0);
                var ch = usaPalavra
                    ? words[(i + k) % words.length]
                    : glyphs[Math.floor(Math.random() * glyphs.length)];

                if (head) {
                    ctx.fillStyle = "#eafffb";
                    ctx.shadowColor = ac;
                    ctx.shadowBlur = 10;
                } else {
                    ctx.fillStyle = ac;
                    ctx.globalAlpha = Math.max(0, 0.75 - k / c.len);
                    ctx.shadowBlur = 0;
                }
                ctx.fillText(ch, x, y);
                ctx.globalAlpha = 1;
                ctx.shadowBlur = 0;
            }

            c.y += c.sp * 2.1;
            if (c.y - c.len * cw > H) {
                c.y = Math.random() * -60;
                c.sp = 0.6 + Math.random() * 1.9;
                c.len = 6 + Math.floor(Math.random() * 14);
            }
        }
        requestAnimationFrame(draw);
    }

    resize();
    if (window.ResizeObserver) {
        new ResizeObserver(resize).observe(cv);
    } else {
        window.addEventListener("resize", resize);
    }

    if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        ctx.fillStyle = "#04070c";
        ctx.fillRect(0, 0, W, H);
    } else {
        draw();
    }
})();
