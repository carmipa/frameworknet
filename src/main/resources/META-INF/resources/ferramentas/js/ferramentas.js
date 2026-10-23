/*
 * Construtor de comando do módulo Ferramentas (client-side).
 *
 * Propósito: montar comandos reais (dig, nmap, tcpdump, curl, ping) a partir de
 * opções, explicando cada flag. Nada é executado nem enviado ao servidor — é um
 * gerador didático de linha de comando.
 *
 * Invariantes: o alvo padrão de cada ferramenta é um nome que pode ser usado sem
 * atingir terceiro — example.com (reservado, RFC 2606) para consulta e conexão
 * única, scanme.nmap.org (o host que o projeto Nmap autoriza varrer) para o nmap.
 * Trocar de ferramenta só troca o alvo se ele ainda for um desses padrões: o que a
 * pessoa digitou nunca é sobrescrito.
 *
 * Comportamento em caso de falha: ausência dos elementos esperados não faz nada;
 * navegador sem clipboard mostra aviso no botão copiar.
 */
(function () {
    "use strict";

    var TOOLS = {
        dig: {
            base: "dig", alvoPos: "fim", alvoPadrao: "example.com",
            opcoes: [
                { flag: "+short", label: "Resposta curta", exp: "+short mostra só a resposta, sem a seção completa." },
                { flag: "MX", label: "Registro MX", exp: "Consulta os servidores de e-mail do domínio." },
                { flag: "+trace", label: "Rastrear da raiz", exp: "+trace resolve passo a passo, da raiz ao autoritativo." },
                { flag: "@1.1.1.1", label: "Usar 1.1.1.1", exp: "@servidor força qual resolvedor DNS consultar." }
            ]
        },
        nmap: {
            base: "nmap", alvoPos: "fim", alvoPadrao: "scanme.nmap.org",
            opcoes: [
                { flag: "-sS", label: "SYN scan", exp: "-sS é o scan SYN (meio-aberto), rápido e discreto (requer root)." },
                { flag: "-sV", label: "Versão do serviço", exp: "-sV tenta identificar o software e a versão de cada porta." },
                { flag: "-p 1-1000", label: "Portas 1-1000", exp: "-p define quais portas varrer." },
                { flag: "-Pn", label: "Sem ping", exp: "-Pn pula a descoberta por ping e trata o host como ativo." }
            ]
        },
        tcpdump: {
            base: "tcpdump", alvoPos: "filtro", alvoPadrao: "example.com",
            opcoes: [
                { flag: "-i eth0", label: "Interface eth0", exp: "-i escolhe a interface de captura." },
                { flag: "-nn", label: "Sem resolver nomes", exp: "-nn não resolve hosts nem portas (mais rápido, IPs crus)." },
                { flag: "-c 50", label: "Parar em 50", exp: "-c limita a quantidade de pacotes capturados." },
                { flag: "-w captura.pcap", label: "Salvar em arquivo", exp: "-w grava a captura para abrir depois no Wireshark." }
            ]
        },
        curl: {
            base: "curl", alvoPos: "fim", alvoPrefixo: "https://", alvoPadrao: "example.com",
            opcoes: [
                { flag: "-v", label: "Verboso", exp: "-v mostra o handshake, os cabeçalhos e o passo a passo." },
                { flag: "-I", label: "Só cabeçalhos", exp: "-I faz um HEAD: só os cabeçalhos de resposta." },
                { flag: "-L", label: "Seguir redirects", exp: "-L segue redirecionamentos 3xx até o destino final." },
                { flag: "-k", label: "Ignorar TLS (perigoso)", exp: "-k aceita certificado inválido — só para teste, nunca em produção." }
            ]
        },
        ping: {
            base: "ping", alvoPos: "fim", alvoPadrao: "example.com",
            opcoes: [
                { flag: "-c 4", label: "4 pacotes", exp: "-c limita quantos pacotes enviar (senão roda sem parar no Linux)." },
                { flag: "-M do -s 1472", label: "Testar MTU", exp: "Não fragmentar com payload 1472 testa a MTU do caminho." }
            ]
        }
    };

    var estado = { tool: "dig", flags: {} };
    var PADROES = Object.keys(TOOLS).map(function (t) { return TOOLS[t].alvoPadrao; });

    function atualizarDica() {
        var caixa = document.getElementById("cmd-alvo-dica-texto");
        if (!caixa) { return; }
        var code = document.createElement("code");
        code.setAttribute("translate", "no");
        code.textContent = TOOLS[estado.tool].alvoPadrao;
        var texto = estado.tool === "nmap"
            ? " é o host que o projeto Nmap autoriza varrer (sem exagero). Fora dele, só rede sua ou com autorização por escrito."
            : " é reservado para exemplos (RFC 2606): troque pelo seu host.";
        caixa.textContent = "";
        caixa.appendChild(code);
        caixa.appendChild(document.createTextNode(texto));
    }

    function montar() {
        var spec = TOOLS[estado.tool];
        var alvo = document.getElementById("cmd-alvo").value.trim() || "alvo";
        var ativos = spec.opcoes.filter(function (o) { return estado.flags[o.flag]; });
        var partes = [spec.base];
        ativos.forEach(function (o) { partes.push(o.flag); });
        var alvoTxt = (spec.alvoPrefixo && alvo.indexOf("://") < 0 ? spec.alvoPrefixo : "") + alvo;
        if (spec.alvoPos === "filtro") {
            partes.push("'host " + alvo + "'");
        } else {
            partes.push(alvoTxt);
        }
        document.getElementById("cmd-saida").textContent = partes.join(" ");

        var flagsBox = document.getElementById("cmd-flags");
        flagsBox.innerHTML = "";
        if (ativos.length) {
            var ul = document.createElement("ul");
            ul.className = "small text-light mb-0 cmd-flags-list";
            ativos.forEach(function (o) {
                var li = document.createElement("li");
                li.innerHTML = '<code class="aprof-inline" translate="no">' + o.flag + "</code> — " + o.exp;
                ul.appendChild(li);
            });
            flagsBox.appendChild(ul);
        }
    }

    function renderOpcoes() {
        var spec = TOOLS[estado.tool];
        var box = document.getElementById("cmd-opcoes");
        box.innerHTML = "";
        estado.flags = {};
        spec.opcoes.forEach(function (o) {
            var id = "opt-" + o.flag.replace(/[^a-z0-9]/gi, "");
            var wrap = document.createElement("div");
            wrap.className = "form-check";
            var input = document.createElement("input");
            input.className = "form-check-input";
            input.type = "checkbox";
            input.id = id;
            input.addEventListener("change", function () { estado.flags[o.flag] = input.checked; montar(); });
            var label = document.createElement("label");
            label.className = "form-check-label small";
            label.htmlFor = id;
            label.textContent = o.label;
            label.title = o.exp;
            input.title = "Marque para acrescentar " + o.flag + " ao comando; desmarque para retirar";
            wrap.appendChild(input);
            wrap.appendChild(label);
            box.appendChild(wrap);
        });
        montar();
    }

    document.addEventListener("DOMContentLoaded", function () {
        var sel = document.getElementById("cmd-tool");
        if (!sel) { return; }
        Object.keys(TOOLS).forEach(function (t) {
            var op = document.createElement("option");
            op.value = t; op.textContent = t;
            sel.appendChild(op);
        });
        sel.addEventListener("change", function () {
            var alvo = document.getElementById("cmd-alvo");
            estado.tool = sel.value;
            if (PADROES.indexOf(alvo.value.trim()) >= 0) {
                alvo.value = TOOLS[estado.tool].alvoPadrao;
            }
            atualizarDica();
            renderOpcoes();
        });
        document.getElementById("cmd-alvo").addEventListener("input", montar);
        var limpar = document.getElementById("cmd-limpar");
        if (limpar) {
            limpar.addEventListener("click", function () {
                // Limpar = esvaziar o alvo digitado e desmarcar as opções; mantém a
                // ferramenta escolhida. Remonta o comando base (sem repopular nada).
                var alvo = document.getElementById("cmd-alvo");
                alvo.value = "";
                estado.flags = {};
                document.querySelectorAll("#cmd-opcoes input[type=checkbox]").forEach(function (c) { c.checked = false; });
                montar();
                alvo.focus();
            });
        }
        var copiar = document.getElementById("cmd-copiar");
        copiar.addEventListener("click", async function () {
            try {
                const saida = document.getElementById("cmd-saida");
                await navigator.clipboard.writeText((window.FieldTooltips ? window.FieldTooltips.textoLimpo(saida) : saida.textContent));
                var original = copiar.innerHTML;
                copiar.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">check</span> Copiado';
                setTimeout(function () { copiar.innerHTML = original; }, 1500);
            } catch (e) { copiar.textContent = "Falhou"; }
        });
        atualizarDica();
        renderOpcoes();
    });
})();
