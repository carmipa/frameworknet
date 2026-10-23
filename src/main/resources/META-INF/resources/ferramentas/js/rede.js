/*
 * Ferramentas > Rede: Windows x Linux — busca, filtros, copiar, dissecar e quiz.
 *
 * Propósito de negócio: tornar o catálogo comparado navegável (buscar por tarefa,
 * filtrar por categoria), permitir copiar o comando exato, entender a saída campo a
 * campo e testar a interpretação — tudo no cliente, sem executar comando algum.
 *
 * Invariantes: nada aqui contata a rede nem executa comandos; a página só apresenta
 * o catálogo servido. Escopo restrito a .rede-*; sem listeners globais nem handlers
 * inline (compatível com a CSP). A busca ignora acentos e caixa.
 *
 * Comportamento em caso de falha: clipboard indisponível mostra aviso acessível e
 * não quebra a página; elementos ausentes são ignorados.
 */
(function () {
    "use strict";

    function norm(s) {
        return (s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
    }

    var estado = { busca: "", categoria: "todas" };

    function aplicarFiltro() {
        var cards = document.querySelectorAll("#rede-lista .rede-cmd");
        var termo = norm(estado.busca).trim();
        var visiveis = 0;
        cards.forEach(function (card) {
            var casaCat = estado.categoria === "todas" || card.getAttribute("data-categoria") === estado.categoria;
            var casaBusca = termo === "" || norm(card.getAttribute("data-termos")).indexOf(termo) >= 0;
            var mostra = casaCat && casaBusca;
            card.hidden = !mostra;
            if (mostra) { visiveis++; }
        });
        var cont = document.getElementById("rede-visiveis");
        if (cont) { cont.textContent = visiveis; }
        var vazio = document.getElementById("rede-vazio");
        if (vazio) { vazio.hidden = visiveis !== 0; }
    }

    function limparBusca() {
        estado.busca = "";
        var input = document.getElementById("rede-busca");
        if (input) { input.value = ""; input.focus(); }
        aplicarFiltro();
    }

    function limparTudo() {
        estado.categoria = "todas";
        document.querySelectorAll(".rede-chip").forEach(function (chip) {
            var ativa = chip.getAttribute("data-cat") === "todas";
            chip.classList.toggle("is-active", ativa);
            chip.setAttribute("aria-pressed", ativa ? "true" : "false");
        });
        limparBusca();
    }

    function copiar(botao) {
        var texto = botao.getAttribute("data-copiar") || "";
        function feedback(ok) {
            var icone = botao.querySelector(".material-symbols-outlined");
            var original = icone ? icone.textContent : "";
            botao.classList.toggle("ok", ok);
            if (icone) { icone.textContent = ok ? "check" : "error"; }
            botao.setAttribute("aria-label", ok ? "Comando copiado" : "Falha ao copiar");
            setTimeout(function () {
                botao.classList.remove("ok");
                if (icone) { icone.textContent = original; }
                botao.setAttribute("aria-label", "Copiar: " + texto);
            }, 1400);
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(texto).then(function () { feedback(true); }, function () { feedback(false); });
        } else {
            feedback(false);
        }
    }

    function dissecar(botao) {
        var corpo = botao.closest(".rede-saida-corpo");
        if (!corpo) { return; }
        var alvo = corpo.querySelector(".rede-campo-explica");
        corpo.querySelectorAll(".rede-campo").forEach(function (b) { b.classList.remove("sel"); });
        botao.classList.add("sel");
        if (alvo) {
            var rot = botao.querySelector(".rede-campo-rot");
            alvo.innerHTML = "";
            var strong = document.createElement("strong");
            strong.textContent = (rot ? rot.textContent : "") + ": ";
            alvo.appendChild(strong);
            alvo.appendChild(document.createTextNode(botao.getAttribute("data-sig") || ""));
            alvo.hidden = false;
        }
    }

    var QUIZ = [
        {
            texto: "No netstat -ano / ss -tulpn, qual campo identifica o programa dono da conexão?",
            opcoes: [["O estado (LISTENING/ESTABLISHED)", false], ["O PID (e o nome do processo)", true], ["O endereço remoto", false]],
            explica: "O PID liga o socket ao processo. O estado diz a fase da conexão; o endereço remoto diz com quem, não quem abriu."
        },
        {
            texto: "Um ping que não responde prova que o serviço no destino está fora do ar?",
            opcoes: [["Sim, prova", false], ["Não — ICMP pode estar bloqueado", true]],
            explica: "Muitos hosts/firewalls descartam ICMP por política. Não responder ao ping não prova que o serviço (ex.: HTTPS/443) esteja indisponível."
        },
        {
            texto: "Test-NetConnection host -Port 443 com TcpTestSucceeded = True prova o quê?",
            opcoes: [["Que o site é seguro", false], ["Que a porta 443 respondeu ao TCP DESTE ponto agora", true], ["Que o serviço está saudável", false]],
            explica: "O sucesso é só o handshake TCP a partir daqui, neste momento. Não avalia a saúde do serviço nem o acesso de outros pontos."
        }
    ];

    function montarQuiz() {
        var grid = document.getElementById("rede-quiz");
        if (!grid) { return; }
        QUIZ.forEach(function (q) {
            var card = document.createElement("div");
            card.className = "rede-q";
            var p = document.createElement("p");
            p.className = "rede-q-texto";
            p.textContent = q.texto;
            card.appendChild(p);
            var opc = document.createElement("div");
            opc.className = "rede-q-opcoes";
            var fb = document.createElement("p");
            fb.className = "rede-q-fb";
            fb.setAttribute("role", "status");
            fb.setAttribute("aria-live", "polite");
            fb.hidden = true;
            q.opcoes.forEach(function (o) {
                var b = document.createElement("button");
                b.type = "button";
                b.className = "rede-q-opcao";
                b.textContent = o[0];
                b.addEventListener("click", function () {
                    var certa = o[1];
                    fb.hidden = false;
                    fb.className = "rede-q-fb " + (certa ? "acertou" : "errou");
                    fb.textContent = (certa ? "Isso mesmo. " : "Ainda não. ") + q.explica;
                });
                opc.appendChild(b);
            });
            card.appendChild(opc);
            card.appendChild(fb);
            grid.appendChild(card);
        });
    }

    function start() {
        if (!document.getElementById("rede-lista")) { return; }
        var input = document.getElementById("rede-busca");
        if (input) {
            input.addEventListener("input", function () { estado.busca = input.value; aplicarFiltro(); });
        }
        document.getElementById("rede-limpar-busca") && document.getElementById("rede-limpar-busca").addEventListener("click", limparBusca);
        document.getElementById("rede-vazio-limpar") && document.getElementById("rede-vazio-limpar").addEventListener("click", limparTudo);
        document.querySelectorAll(".rede-chip").forEach(function (chip) {
            chip.addEventListener("click", function () {
                estado.categoria = chip.getAttribute("data-cat");
                document.querySelectorAll(".rede-chip").forEach(function (c) {
                    var ativa = c === chip;
                    c.classList.toggle("is-active", ativa);
                    c.setAttribute("aria-pressed", ativa ? "true" : "false");
                });
                aplicarFiltro();
            });
        });
        document.addEventListener("click", function (ev) {
            var copy = ev.target.closest ? ev.target.closest(".rede-copiar") : null;
            if (copy) { copiar(copy); return; }
            var campo = ev.target.closest ? ev.target.closest(".rede-campo") : null;
            if (campo) { dissecar(campo); }
        });
        montarQuiz();
        aplicarFiltro();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
