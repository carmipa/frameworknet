/*
 * Laboratório "Camadas em ação" — máquina de estados da animação.
 *
 * Propósito de negócio: dirigir uma experiência didática determinística em que uma
 * mensagem é encapsulada no Host A (UDP/IP/Ethernet), atravessa um switch L2 e é
 * desencapsulada no Host B. A MESMA mensagem e a MESMA etapa produzem sempre a
 * mesma representação — a animação reflete o estado, não é vídeo decorativo.
 *
 * Invariantes: estado = (mensagem, índice de etapa); render() é função pura desse
 * estado. Trocar a mensagem reinicia para a etapa 0 (o tamanho do datagrama muda).
 * Um único timer de reprodução por vez; reiniciar/pausar/sair cancelam-no. Cliques
 * repetidos só movem o índice (clamp), nunca duplicam etapas. Sem listeners globais;
 * escopo restrito à página do laboratório. Respeita prefers-reduced-motion (o CSS
 * remove as transições; aqui o passo automático fica mais lento e explícito).
 *
 * Real × didático: o tamanho do payload é medido em bytes UTF-8 reais (TextEncoder);
 * os tamanhos de cabeçalho são os mínimos padrão declarados (UDP 8, IP 20, Ethernet
 * 14 + 4 de FCS). Endereços/portas são de demonstração.
 */
(function () {
    "use strict";

    var HDR = { udp: 8, ip: 20 };
    var ETH_CABECALHO = 14; // MAC destino + origem + EtherType
    var ETH_FCS = 4;        // sequência de verificação no fim do quadro
    var ETH_QUADRO_MIN = 64; // mínimo do quadro no fio (IEEE 802.3), padding quando menor
    // Num equipamento o pacote paira ACIMA da caixa (y=58) para não cobrir o rótulo
    // do nó; em trânsito ele desce para o fio (y=130).
    var POS = {
        A:    { x: 130, y: 58 },
        A_SW: { x: 290, y: 130 },
        SW:   { x: 450, y: 58 },
        SW_B: { x: 610, y: 130 },
        B:    { x: 770, y: 58 }
    };

    // nivel: camadas presentes ao redor da mensagem (0=só app, 1=+udp, 2=+ip, 3=+eth)
    var ETAPAS = [
        {
            pos: "A", op: 0, nivel: 0, ativa: "app", acao: "origem", rotulo: "msg",
            disp: "Host A · Aplicação (L7)", titulo: "A aplicação tem uma mensagem",
            desc: "O Host A quer enviar uma mensagem curta ao Host B. Por enquanto é só o texto da aplicação, sem nenhum cabeçalho.",
            porque: "Encapsular é embrulhar: cada camada de baixo acrescenta o cabeçalho de que precisa para fazer seu trabalho.",
            campos: [["Mensagem", "conteúdo", "o texto que você digitou"]],
            proximo: "A camada de transporte vai embrulhar isso num datagrama UDP."
        },
        {
            pos: "A", op: 1, nivel: 1, ativa: "udp", acao: "add", rotulo: "UDP",
            disp: "Host A · Transporte (L4)", titulo: "UDP acrescenta portas",
            desc: "A camada de transporte embrulha a mensagem num datagrama UDP, marcando de qual porta sai e para qual porta vai.",
            porque: "É a porta que diz a qual programa a mensagem pertence — sem ela, o destino não saberia a quem entregar. UDP não abre conexão: manda o datagrama e pronto.",
            campos: [["Porta de origem", "acrescentado", "51000 (efêmera)"], ["Porta de destino", "acrescentado", "7777/udp"], ["Comprimento / checksum", "acrescentado", "cobre o datagrama"]],
            proximo: "A camada de rede vai colocar os endereços IP.",
            pergunta: {
                texto: "O que o UDP acrescenta para identificar a qual programa a mensagem pertence?",
                opcoes: [["Endereços IP", false], ["Portas de origem e destino", true], ["Endereços MAC", false]],
                explica: "São as portas (L4) que identificam o programa. IP (L3) identifica a máquina e MAC (L2) a placa de rede."
            }
        },
        {
            pos: "A", op: 1, nivel: 2, ativa: "ip", acao: "add", rotulo: "IP",
            disp: "Host A · Rede (L3)", titulo: "IP acrescenta os endereços lógicos",
            desc: "A camada de rede embrulha o datagrama num pacote IP, com o IP de origem e o de destino.",
            porque: "O endereço IP diz QUEM é a origem e QUEM é o destino, de ponta a ponta. Como os dois estão na mesma sub-rede, o pacote não precisa de gateway.",
            campos: [["IP de origem", "acrescentado", "192.168.1.10"], ["IP de destino", "acrescentado", "192.168.1.20"], ["TTL", "acrescentado", "64"], ["Protocolo", "acrescentado", "17 (UDP)"]],
            proximo: "A camada de enlace vai montar o quadro Ethernet."
        },
        {
            pos: "A", op: 1, nivel: 3, ativa: "eth", acao: "add", rotulo: "quadro",
            disp: "Host A · Enlace (L2)", titulo: "Ethernet monta o quadro",
            desc: "A camada de enlace embrulha o pacote IP num quadro Ethernet, com o MAC de origem e o MAC de destino, e um FCS no fim para detectar erro.",
            porque: "Na rede local quem entrega o quadro ao próximo equipamento é o endereço MAC. O Host A já conhece o MAC do Host B neste cenário.",
            campos: [["MAC de destino", "acrescentado", "02:00:00:00:00:14 (Host B)"], ["MAC de origem", "acrescentado", "02:00:00:00:00:0A (Host A)"], ["EtherType", "acrescentado", "0x0800 (IPv4)"], ["FCS", "acrescentado", "4 B no fim do quadro"]],
            proximo: "O quadro vira bits e sai pela porta do Host A."
        },
        {
            pos: "A_SW", op: 1, nivel: 3, ativa: "eth", acao: "fio", rotulo: "bits",
            disp: "Host A · Física (L1)", titulo: "O quadro vira sinal no fio",
            desc: "A camada física converte o quadro em bits (sinal elétrico/óptico) e os transmite pelo cabo até o switch.",
            porque: "O meio físico não entende IP nem porta — só transporta bits. O tamanho no fio é a soma de tudo que foi embrulhado.",
            campos: [["Sinal", "transmitido", "bits do quadro completo"]],
            proximo: "O switch vai receber o quadro e decidir para onde mandar."
        },
        {
            pos: "SW", op: 1, nivel: 3, ativa: "eth", acao: "exam", rotulo: "quadro",
            disp: "Switch · Enlace (L2)", titulo: "O switch encaminha pelo MAC",
            desc: "O switch recebe o quadro, lê o MAC de destino e consulta sua tabela de endereços (que já aprendeu em qual porta o Host B está) para encaminhar o quadro por essa porta. Ele NÃO abre o IP nem o UDP.",
            porque: "Um switch opera na camada 2: sua decisão usa só o MAC de destino. IP, portas e conteúdo passam intactos — ele nem os examina. Aqui a tabela já contém o MAC do Host B; se não contivesse, o switch inundaria as demais portas para descobri-lo.",
            campos: [["MAC de destino", "examinado", "02:00:00:00:00:14 → porta do Host B"], ["IP / UDP / mensagem", "não examinado", "passam sem alteração"], ["Quadro", "encaminhado", "sai pela porta certa, inalterado"]],
            proximo: "Os bits chegam ao Host B.",
            pergunta: {
                texto: "Qual informação o switch usa para decidir por onde mandar o quadro?",
                opcoes: [["O IP de destino", false], ["O MAC de destino", true], ["A porta UDP", false]],
                explica: "O switch é L2: decide pelo MAC de destino. Quem decide por IP é o roteador (L3), que não existe neste cenário."
            }
        },
        {
            pos: "SW_B", op: 1, nivel: 3, ativa: "eth", acao: "fio", rotulo: "bits",
            disp: "Host B · Física (L1)", titulo: "Os bits chegam ao Host B",
            desc: "A camada física do Host B recebe o sinal e remonta o quadro na memória.",
            porque: "Agora começa o desencapsulamento: cada camada vai ler o seu cabeçalho e retirá-lo, na ordem inversa.",
            campos: [["Sinal", "recebido", "bits remontados em quadro"]],
            proximo: "A camada de enlace vai conferir e retirar o cabeçalho Ethernet."
        },
        {
            pos: "B", op: 1, nivel: 2, ativa: "eth", acao: "remove", rotulo: "IP",
            disp: "Host B · Enlace (L2)", titulo: "Ethernet confere e sai",
            desc: "O Host B confere o FCS (nenhum erro detectado) e vê que o MAC de destino é o dele. Retira o cabeçalho Ethernet e entrega o pacote IP para cima.",
            porque: "O MAC casou: o quadro era mesmo para este host. O FCS não detectou erro — indica que o quadro provavelmente não se corrompeu (é uma checagem de detecção, não uma garantia; aqui o resultado é ilustrativo).",
            campos: [["FCS", "conferido", "sem erro detectado (ilustrativo)"], ["MAC de destino", "conferido", "é o meu → aceito"], ["Ethernet", "removido", "entrega o pacote IP à camada de rede"]],
            proximo: "A camada de rede vai conferir e retirar o cabeçalho IP."
        },
        {
            pos: "B", op: 1, nivel: 1, ativa: "ip", acao: "remove", rotulo: "UDP",
            disp: "Host B · Rede (L3)", titulo: "IP confere e sai",
            desc: "A camada de rede vê que o IP de destino é o do Host B e que o protocolo interno é UDP. Retira o cabeçalho IP e entrega o datagrama para a camada de transporte.",
            porque: "O IP casou: o pacote era para este host. O campo Protocolo (17) diz que dentro vem um datagrama UDP.",
            campos: [["IP de destino", "conferido", "192.168.1.20 → é o meu"], ["Protocolo", "lido", "17 → entregar ao UDP"], ["IP", "removido", "entrega o datagrama à camada de transporte"]],
            proximo: "A camada de transporte vai conferir a porta e retirar o UDP."
        },
        {
            pos: "B", op: 1, nivel: 0, ativa: "udp", acao: "remove", rotulo: "msg",
            disp: "Host B · Transporte (L4)", titulo: "UDP entrega pela porta",
            desc: "A camada de transporte olha a porta de destino (7777) e sabe a qual programa entregar. Retira o cabeçalho UDP e passa a mensagem para a aplicação.",
            porque: "É a porta que liga o datagrama ao programa certo. Retirado o UDP, sobra exatamente a mensagem original.",
            campos: [["Porta de destino", "lida", "7777 → o programa que escuta"], ["Checksum", "conferido", "sem erro detectado (ilustrativo)"], ["UDP", "removido", "entrega a mensagem à aplicação"]],
            proximo: "A aplicação do Host B recebe a mensagem."
        },
        {
            pos: "B", op: 1, nivel: 0, ativa: "app", acao: "entrega", rotulo: "msg",
            disp: "Host B · Aplicação (L7)", titulo: "A mensagem chega inteira",
            desc: "A aplicação do Host B recebe exatamente a mesma mensagem que o Host A enviou. Cada cabeçalho existiu apenas para levá-la até aqui e foi retirado no caminho de volta.",
            porque: "Esse é o princípio das camadas: cada uma conversa com a sua par no outro lado, e o embrulho de uma camada é transparente para as de cima.",
            campos: [["Mensagem", "entregue", "idêntica à enviada pelo Host A"]],
            proximo: "Fim. Reinicie, mude a mensagem ou volte etapas para revisar."
        }
    ];

    var idx = 0;
    var tocando = false;
    var timer = null;
    var seq = 0; // invalida callbacks de reprodução antigos

    function $(id) { return document.getElementById(id); }

    function bytesDe(texto) {
        try { return new TextEncoder().encode(texto).length; }
        catch (e) { return texto.length; }
    }

    function pararTimer() {
        seq++;
        if (timer) { clearTimeout(timer); timer = null; }
    }

    function setTocando(v) {
        tocando = v;
        var btn = $("lab-play");
        $("lab-play-icone").textContent = v ? "pause" : "play_arrow";
        $("lab-play-texto").textContent = v ? "Pausar" : "Executar";
        btn.setAttribute("aria-pressed", v ? "true" : "false");
        // Indicador fixo e visível de que a reprodução automática está acontecendo.
        var rep = $("lab-reproduzindo");
        if (rep) { rep.hidden = !v; }
    }

    function reduzirMovimento() {
        return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }

    function renderPilha(etapa, payloadBytes) {
        var pilha = $("lab-pilha");
        var ordem = ["eth", "ip", "udp", "app"]; // fora → dentro
        var presentesPorNivel = { 0: ["app"], 1: ["udp", "app"], 2: ["ip", "udp", "app"], 3: ["eth", "ip", "udp", "app"] };
        var presentes = presentesPorNivel[etapa.nivel];
        ordem.forEach(function (c) {
            var el = pilha.querySelector('[data-camada="' + c + '"]');
            var presente = presentes.indexOf(c) >= 0;
            el.classList.toggle("presente", presente);
            el.classList.toggle("ativa", c === etapa.ativa && presente);
            // camada sendo examinada/removida: destaca mesmo quando é a de fora
            el.classList.toggle("saindo", c === etapa.ativa && (etapa.acao === "remove" || etapa.acao === "exam"));
        });
        $("lab-app-bytes").textContent = payloadBytes + " B";
        // Mostra a PDU da etapa ATUAL (não "no fio" o tempo todo), com o tamanho certo.
        var rotulo, bytes, nota = "";
        if (etapa.nivel === 0) {
            rotulo = "Mensagem";
            bytes = payloadBytes;
            if (payloadBytes === 0) { nota = "Mensagem vazia: um datagrama sem dados (0 byte de payload) é válido em UDP."; }
        } else if (etapa.nivel === 1) {
            rotulo = "Datagrama UDP";
            bytes = payloadBytes + HDR.udp;
        } else if (etapa.nivel === 2) {
            rotulo = "Pacote IP";
            bytes = payloadBytes + HDR.udp + HDR.ip;
        } else {
            // Quadro Ethernet no fio: mínimo de 64 B (com FCS). Se a carga L2 for
            // menor que 46 B, entra padding até o mínimo — Cisco/IEEE 802.3.
            rotulo = "Quadro Ethernet (no fio)";
            var cargaMin = ETH_QUADRO_MIN - ETH_CABECALHO - ETH_FCS; // = 46 B mínimos de carga
            var cargaL2 = payloadBytes + HDR.udp + HDR.ip;           // o que vai DENTRO do Ethernet
            var cargaComPad = Math.max(cargaMin, cargaL2);
            bytes = ETH_CABECALHO + cargaComPad + ETH_FCS;           // 14 + carga(≥46) + 4
            var pad = cargaComPad - cargaL2;
            nota = (pad > 0
                ? "Inclui " + pad + " B de padding para o mínimo de " + ETH_QUADRO_MIN + " B do quadro. "
                : "")
                + "Preâmbulo, SFD e o intervalo entre quadros ficam fora desta conta.";
        }
        $("lab-total-rotulo").textContent = rotulo;
        $("lab-total-bytes").textContent = bytes + (bytes === 1 ? " byte" : " bytes");
        var notaEl = $("lab-total-nota");
        notaEl.textContent = nota;
        notaEl.hidden = nota === "";
    }

    function renderPacote(etapa) {
        var g = $("lab-pacote");
        var p = POS[etapa.pos];
        g.setAttribute("transform", "translate(" + p.x + "," + p.y + ")");
        g.setAttribute("opacity", etapa.op ? "1" : "0");
        $("lab-pacote-rotulo").textContent = etapa.rotulo;
    }

    function renderCampos(etapa) {
        var tbody = $("lab-campos");
        tbody.innerHTML = "";
        (etapa.campos || []).forEach(function (linha) {
            var tr = document.createElement("tr");
            ["", "", ""].forEach(function (_, i) {
                var td = document.createElement("td");
                td.textContent = linha[i];
                if (i === 1) { td.className = "lab-acao lab-acao-" + acaoClasse(linha[1]); }
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        $("lab-campos-tabela").hidden = (etapa.campos || []).length === 0;
    }

    function acaoClasse(txt) {
        var t = (txt || "").toLowerCase();
        if (t.indexOf("acrescent") >= 0) { return "add"; }
        if (t.indexOf("remov") >= 0) { return "remove"; }
        if (t.indexOf("não") >= 0) { return "neutro"; }
        return "exam";
    }

    function renderPergunta(etapa) {
        var bloco = $("lab-pergunta-bloco");
        var fb = $("lab-pergunta-feedback");
        fb.hidden = true; fb.textContent = ""; fb.className = "lab-pergunta-feedback";
        var opc = $("lab-pergunta-opcoes");
        opc.innerHTML = "";
        if (!etapa.pergunta) { bloco.hidden = true; return; }
        bloco.hidden = false;
        $("lab-pergunta-texto").textContent = etapa.pergunta.texto;
        etapa.pergunta.opcoes.forEach(function (o) {
            var b = document.createElement("button");
            b.type = "button";
            b.className = "aed-btn aed-btn-neutral btn-sm lab-opcao";
            b.textContent = o[0];
            b.addEventListener("click", function () {
                var certa = o[1];
                fb.hidden = false;
                fb.className = "lab-pergunta-feedback " + (certa ? "acertou" : "errou");
                fb.textContent = (certa ? "Isso mesmo. " : "Ainda não. ") + etapa.pergunta.explica;
                opc.querySelectorAll("button").forEach(function (x) { x.classList.remove("marcada"); });
                b.classList.add("marcada");
            });
            opc.appendChild(b);
        });
    }

    function render() {
        var etapa = ETAPAS[idx];
        var payload = bytesDe($("lab-msg").value);
        $("lab-passo-num").textContent = idx + 1;
        $("lab-passo-total").textContent = ETAPAS.length;
        $("lab-etapa-dispositivo").textContent = etapa.disp;
        $("lab-etapa-titulo").textContent = etapa.titulo;
        $("lab-etapa-desc").textContent = etapa.desc;
        $("lab-etapa-porque").textContent = etapa.porque || "";
        $("lab-proximo").textContent = etapa.proximo ? "Próximo: " + etapa.proximo : "";
        renderPacote(etapa);
        renderPilha(etapa, payload);
        renderCampos(etapa);
        renderPergunta(etapa);
        $("lab-alt-lista").querySelectorAll(".lab-alt-atual").forEach(function (li) { li.classList.remove("lab-alt-atual"); });
        var itemAlt = $("lab-alt-lista").children[idx];
        if (itemAlt) { itemAlt.classList.add("lab-alt-atual"); }
        $("lab-prev").disabled = idx === 0;
        $("lab-next").disabled = idx === ETAPAS.length - 1;
    }

    function irPara(novo) {
        idx = Math.max(0, Math.min(ETAPAS.length - 1, novo));
        render();
    }

    function proximo() { if (idx < ETAPAS.length - 1) { irPara(idx + 1); } }
    function anterior() { if (idx > 0) { irPara(idx - 1); } }

    function reproduzir() {
        pararTimer();
        setTocando(true);
        var meu = seq;
        var intervalo = reduzirMovimento() ? 2600 : 1600;
        function passo() {
            if (meu !== seq) { return; }
            if (idx >= ETAPAS.length - 1) { setTocando(false); timer = null; return; }
            irPara(idx + 1);
            timer = setTimeout(passo, intervalo);
        }
        timer = setTimeout(passo, intervalo);
    }

    function alternarPlay() {
        if (tocando) { pararTimer(); setTocando(false); }
        else if (idx === ETAPAS.length - 1) { irPara(0); reproduzir(); }
        else { reproduzir(); }
    }

    function reiniciar() { pararTimer(); setTocando(false); irPara(0); }

    function montarAlternativa() {
        var ol = $("lab-alt-lista");
        ol.innerHTML = "";
        ETAPAS.forEach(function (e) {
            var li = document.createElement("li");
            li.innerHTML = "<strong></strong> <span></span>";
            li.querySelector("strong").textContent = e.disp + " — " + e.titulo + ".";
            li.querySelector("span").textContent = e.desc;
            ol.appendChild(li);
        });
    }

    var MODELO = {
        tcpip: "<p><strong>Modelo TCP/IP (4 camadas), o que este cenário usa:</strong></p>"
            + "<ul><li><strong>Aplicação</strong> — a mensagem.</li>"
            + "<li><strong>Transporte</strong> — o cabeçalho UDP (portas).</li>"
            + "<li><strong>Internet</strong> — o cabeçalho IP (endereços).</li>"
            + "<li><strong>Acesso à rede</strong> — o quadro Ethernet (MAC) e os bits no fio.</li></ul>",
        osi: "<p><strong>Modelo OSI (7 camadas), o mesmo cenário mapeado:</strong></p>"
            + "<ul><li><strong>7 Aplicação / 6 Apresentação / 5 Sessão</strong> — aqui vivem juntas dentro da aplicação; não há cabeçalho separado no cenário.</li>"
            + "<li><strong>4 Transporte</strong> — UDP.</li>"
            + "<li><strong>3 Rede</strong> — IP.</li>"
            + "<li><strong>2 Enlace</strong> — Ethernet (o switch atua aqui).</li>"
            + "<li><strong>1 Física</strong> — os bits no fio.</li></ul>"
    };

    function mostrarModelo(qual) {
        $("lab-modelo-explica").innerHTML = MODELO[qual];
        var tcpip = qual === "tcpip";
        $("lab-modelo-tcpip").classList.toggle("is-active", tcpip);
        $("lab-modelo-osi").classList.toggle("is-active", !tcpip);
        $("lab-modelo-tcpip").setAttribute("aria-pressed", tcpip ? "true" : "false");
        $("lab-modelo-osi").setAttribute("aria-pressed", tcpip ? "false" : "true");
    }

    function atualizarBytesEntrada() {
        var n = bytesDe($("lab-msg").value);
        $("lab-msg-bytes").textContent = n + (n === 1 ? " byte" : " bytes");
    }

    function start() {
        if (!$("lab-cena")) { return; } // não é a página do laboratório
        montarAlternativa();
        mostrarModelo("tcpip");
        atualizarBytesEntrada();
        render();

        $("lab-next").addEventListener("click", function () { pararTimer(); setTocando(false); proximo(); });
        $("lab-prev").addEventListener("click", function () { pararTimer(); setTocando(false); anterior(); });
        $("lab-play").addEventListener("click", alternarPlay);
        $("lab-reiniciar").addEventListener("click", reiniciar);
        $("lab-modelo-tcpip").addEventListener("click", function () { mostrarModelo("tcpip"); });
        $("lab-modelo-osi").addEventListener("click", function () { mostrarModelo("osi"); });

        // Trocar a mensagem reinicia: o tamanho do datagrama muda, então a execução
        // anterior deixa de valer. Invalidação explícita, sem misturar estados.
        $("lab-msg").addEventListener("input", function () {
            atualizarBytesEntrada();
            reiniciar();
        });

        // Sair da página não deixa timer rodando em segundo plano.
        window.addEventListener("pagehide", pararTimer);
        document.addEventListener("visibilitychange", function () {
            if (document.hidden && tocando) { pararTimer(); setTocando(false); }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
