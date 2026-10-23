/**
 * Lição Fundamentos · Camadas e encapsulamento — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê a mensagem ganhando um cabeçalho por camada (Ver), escreve a
 *   própria mensagem e troca TCP por UDP vendo os tamanhos mudarem (Mexer), e calcula o quadro
 *   sozinho, com a correção dizendo QUAL camada ele esqueceu (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - tamanho da mensagem medido em bytes UTF-8 (acento conta 2), com teto de MAX_MENSAGEM bytes;
 *   - a correção reconhece os enganos comuns pelo valor exato (só o pacote, sem FCS, sem
 *     preenchimento, transporte trocado) — fora deles, a dica é genérica;
 *   - o texto digitado nunca sai do navegador: só contagens vão para a telemetria;
 *   - entrada pelo normalizador; só tentativa interpretável conta.
 *
 * COMPORTAMENTO EM CASO DE FALHA: mensagem acima do teto mostra o aviso e mantém o último
 *   resultado válido; script de apoio ausente deixa a parte parada e o erro sai pelo AcademiaSinais.
 */
(function () {
    'use strict';

    var pagina = document.querySelector('[data-acad-licao]');
    if (!pagina) {
        return;
    }
    var LICAO = pagina.getAttribute('data-acad-licao');
    var F = window.AcademiaFundamentos;
    var N = window.AcademiaNormalizador;
    var P = window.AcademiaProgresso;
    var S = window.AcademiaSinais;
    var M = window.AcademiaMotor;

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    function icone(nome) {
        var el = document.createElement('span');
        el.className = 'material-symbols-outlined';
        el.setAttribute('aria-hidden', 'true');
        el.setAttribute('translate', 'no');
        el.textContent = nome;
        return el;
    }

    /**
     * Um bloco do desenho do quadro: nome do pedaço e quantos bytes ele ocupa.
     *
     * PROPÓSITO DE NEGÓCIO: mostrar ao aluno de que pedaços o quadro é feito e quanto
     * cada um pesa, legível em qualquer tela e em qualquer idioma do tradutor.
     *
     * INVARIANTES DO DOMÍNIO: o número de bytes nunca passa pelo tradutor; nome de
     * protocolo ("Ethernet", "IPv4", "TCP", "FCS") também não; palavra comum ("dados",
     * "preenchimento") passa, senão quem lê em inglês vê português dentro do desenho.
     * O texto nunca é cortado: o bloco não encolhe abaixo do próprio rótulo, e a linha
     * de blocos quebra quando não cabe (CSS .acad-unidade-blocos).
     *
     * COMPORTAMENTO EM CASO DE FALHA: não lança; devolve o elemento montado.
     */
    function bloco(classe, nome, bytes, tecnico) {
        var el = document.createElement('span');
        el.className = 'acad-bloco ' + classe;
        var n = document.createElement('span');
        n.textContent = nome;
        if (tecnico) { n.setAttribute('translate', 'no'); }
        var b = document.createElement('span');
        b.setAttribute('translate', 'no');
        b.textContent = String(bytes);
        el.appendChild(n);
        el.appendChild(document.createTextNode(' '));
        el.appendChild(b);
        return el;
    }

    /** Desenha as quatro unidades (mensagem, segmento, pacote, quadro) de um encapsulamento. */
    function desenharPilha(container, r) {
        container.textContent = '';
        var t = function () { return bloco('transporte', r.transporte, r.cabecalhoTransporte, true); };
        var carga = function () { return bloco('carga', 'dados', r.carga, false); };
        var ip = function () { return bloco('rede', 'IPv4', 20, true); };
        var linhas = [
            { icone: 'chat', nome: 'Aplicação', total: r.carga, blocos: [carga()] },
            { icone: 'swap_horiz', nome: 'Transporte', total: r.segmento, blocos: [t(), carga()] },
            { icone: 'lan', nome: 'Rede (IPv4)', total: r.pacote, blocos: [ip(), t(), carga()] },
            {
                icone: 'settings_ethernet', nome: 'Enlace (Ethernet)', total: r.quadro,
                blocos: [bloco('enlace', 'Ethernet', 14, true), ip(), t(), carga()]
                    .concat(r.preenchimento > 0 ? [bloco('preenchimento', 'preenchimento', r.preenchimento, false)] : [])
                    .concat([bloco('enlace', 'FCS', 4, true)])
            }
        ];
        linhas.forEach(function (linha) {
            var unidade = document.createElement('div');
            unidade.className = 'acad-unidade';
            var rotulo = document.createElement('div');
            rotulo.className = 'acad-unidade-rotulo';
            rotulo.appendChild(icone(linha.icone));
            var nome = document.createElement('span');
            nome.textContent = linha.nome + ' · ';
            var total = document.createElement('span');
            total.setAttribute('translate', 'no');
            total.textContent = linha.total + ' B';
            rotulo.appendChild(nome);
            rotulo.appendChild(total);
            var blocos = document.createElement('div');
            blocos.className = 'acad-unidade-blocos';
            linha.blocos.forEach(function (b) { blocos.appendChild(b); });
            unidade.appendChild(rotulo);
            unidade.appendChild(blocos);
            container.appendChild(unidade);
        });
        return container.querySelectorAll('.acad-unidade');
    }

    function ativarAte(unidades, nivel) {
        unidades.forEach(function (u, i) {
            u.classList.toggle('ativa', i <= nivel);
        });
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var r = F.encapsular(F.bytesUtf8('Oi'), 'TCP');
        var unidades = desenharPilha(document.getElementById('ver-pilha'), r);
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['A mensagem ', { valor: '"Oi"' }, ' vai descer a pilha por TCP. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () { ativarAte(unidades, -1); },
            passos: [
                {
                    aplicar: function () { ativarAte(unidades, 0); },
                    legenda: ['A aplicação entrega ', { valor: r.carga }, ' bytes: "O" e "i", um byte cada em UTF-8.']
                },
                {
                    aplicar: function () { ativarAte(unidades, 1); },
                    legenda: ['O TCP põe ', { valor: 20 }, ' bytes de cabeçalho: o segmento tem ', { valor: r.segmento }, ' bytes.']
                },
                {
                    aplicar: function () { ativarAte(unidades, 2); },
                    legenda: ['O IPv4 põe mais ', { valor: 20 }, ': o pacote tem ', { valor: r.pacote }, ' bytes.']
                },
                {
                    aplicar: function () { ativarAte(unidades, 3); },
                    legenda: ['O pacote tem menos de ', { valor: 46 }, ' bytes, então a Ethernet completa com ',
                        { valor: r.preenchimento }, ' de preenchimento, põe ', { valor: 14 }, ' na frente e ',
                        { valor: 4 }, ' de FCS: o quadro tem ', { valor: r.quadro }, ' bytes.']
                }
            ]
        });
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var mensagem = document.getElementById('mexer-mensagem');
        var transporte = document.getElementById('mexer-transporte');
        var eco = document.getElementById('mexer-mensagem-eco');
        var pilha = document.getElementById('mexer-pilha');
        var campos = ['carga', 'segmento', 'pacote', 'preenchimento', 'quadro'];

        function atualizar(contarInteracao) {
            var bytes = F.bytesUtf8(mensagem.value);
            if (bytes > F.MAX_MENSAGEM) {
                // ACAD-22: o texto antigo dizia que acima de 1000 bytes entraria fragmentação — falso (o
                // quadro Ethernet leva até 1500 bytes de pacote) — e o quadro da mensagem anterior ficava na
                // tela como se fosse desta.
                eco.textContent = 'Esta lição mostra mensagens de até ' + F.MAX_MENSAGEM + ' bytes. Na rede real o '
                    + 'quadro Ethernet leva até 1500 bytes de pacote: o TCP corta a mensagem em segmentos de até '
                    + '1460 bytes, e só um datagrama UDP acima de 1472 bytes faz o IPv4 fragmentar.';
                pilha.textContent = '';
                campos.forEach(function (c) {
                    document.getElementById('mexer-' + c).textContent = '—';
                });
                return;
            }
            // ACAD-18: contar caracteres, não unidades UTF-16 (um emoji contava 2), e no singular quando é 1.
            var caracteres = Array.from(mensagem.value).length;
            eco.textContent = caracteres + (caracteres === 1 ? ' caractere = ' : ' caracteres = ') + bytes
                + (bytes === 1 ? ' byte' : ' bytes') + ' em UTF-8';
            var r = F.encapsular(bytes, transporte.value === 'UDP' ? 'UDP' : 'TCP');
            ativarAte(desenharPilha(pilha, r), 3);
            campos.forEach(function (c) {
                document.getElementById('mexer-' + c).textContent = String(r[c]);
            });
            if (contarInteracao) {
                S.interagiu();
            }
        }

        mensagem.addEventListener('input', function () { atualizar(true); });
        transporte.addEventListener('change', function () { atualizar(true); });
        atualizar(false);
    }());

    // ------------------------------------------------------------------ Provar
    (function provar() {
        var perguntaEl = document.getElementById('provar-pergunta');
        var campo = document.getElementById('provar-resposta');
        P.ligarRascunho(LICAO, campo);
        var ecoEl = document.getElementById('provar-eco');
        var feedback = document.getElementById('provar-feedback');
        var placar = document.getElementById('provar-placar');
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var aleatorio = F.sorteador(P.semente(LICAO));
            var tipo = aleatorio() < 0.5 ? 'TCP' : 'UDP';
            var pequena = aleatorio() < 0.5;
            var carga = pequena ? F.sortearInteiro(aleatorio, 0, 20) : F.sortearInteiro(aleatorio, 21, F.MAX_MENSAGEM);
            return F.encapsular(carga, tipo);
        }

        function responder(classe, partes) {
            feedback.className = 'acad-feedback ' + classe;
            M.montarTexto(feedback, partes);
        }

        function pintarPlacar() {
            var estado = P.ler(LICAO);
            var faltam = Math.max(0, P.ACERTOS_PARA_CONCLUIR - estado.acertos);
            M.montarTexto(placar, estado.concluidaEm
                ? ['Acertos: ', { valor: estado.acertos }, ' · tentativas: ', { valor: estado.tentativas }, ' · lição concluída.']
                : ['Acertos: ', { valor: estado.acertos }, ' · tentativas: ', { valor: estado.tentativas },
                    ' · faltam ', { valor: faltam }, ' para concluir.']);
            P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);
        }

        function apresentar() {
            atual = sortear();
            errosNestaPergunta = 0;
            ultimaErrada = null;
            campo.value = '';
            ecoEl.textContent = '';
            M.montarTexto(perguntaEl, ['Uma aplicação envia ', { valor: atual.carga }, ' bytes por ', { valor: atual.transporte },
                ' sobre IPv4 e Ethernet. Quantos bytes tem o quadro, do cabeçalho Ethernet ao FCS?']);
        }

        var DIAGNOSTICOS = {
            PACOTE: ['Esse é o tamanho do pacote IPv4. Falta a camada de enlace: ', { valor: 14 }, ' de cabeçalho e ', { valor: 4 }, ' de FCS.'],
            SEGMENTO: ['Esse é o segmento de transporte. Faltam o IPv4 e a Ethernet.'],
            SEM_FCS: ['Faltou o FCS: ', { valor: 4 }, ' bytes no fim do quadro.'],
            SEM_PREENCHIMENTO: ['Faltou o preenchimento: a carga do quadro tem no mínimo ', { valor: 46 }, ' bytes.'],
            TRANSPORTE_TROCADO: ['Confira o cabeçalho do transporte: TCP tem ', { valor: 20 }, ' bytes e UDP tem ', { valor: 8 }, '.']
        };

        /** A mensagem do engano mais provável para o valor dado, ou null. */
        function diagnosticar(v) {
            var codigo = F.diagnosticarQuadro(atual, v);
            return codigo ? DIAGNOSTICOS[codigo] : null;
        }

        campo.addEventListener('input', function () {
            var r = N.inteiro(campo.value, { min: 0, max: 2000 });
            ecoEl.textContent = r.ok ? r.eco : (campo.value.trim() === '' ? '' : r.mensagem);
        });

        document.getElementById('provar-form').addEventListener('submit', function (ev) {
            ev.preventDefault();
            var r = N.inteiro(campo.value, { min: 0, max: 2000 });
            if (!N.contaTentativa(r)) {
                responder('neutro', [r.mensagem, ' Isto não conta como tentativa.']);
                return;
            }
            var repeticao = N.chaveRepeticao(r, r.ok ? r.valor : null, campo.value);
            if (!(r.ok && r.valor === atual.quadro) && repeticao !== null && repeticao === ultimaErrada) {
                responder('neutro', ['Essa é a mesma resposta de antes — não contou de novo.']);
                return;
            }
            S.interagiu();
            var acertou = r.ok && r.valor === atual.quadro;
            var resultado = P.registrarTentativa(LICAO, acertou);
            if (acertou) {
                var partes = ['Certo: o quadro tem ', { valor: atual.quadro }, ' bytes.'];
                if (resultado.acabouDeConcluir) {
                    S.concluiu();
                    partes.push(' Lição concluída!');
                }
                if (!resultado.guardado) {
                    partes.push(' (Este navegador não deixou guardar o progresso.)');
                }
                partes.push(' Nova pergunta abaixo.');
                responder('ok', partes);
                P.avancarSemente(LICAO);
                apresentar();
                pintarPlacar();
                return;
            }
            errosNestaPergunta += 1;
            ultimaErrada = repeticao;
            if (!r.ok) {
                responder('erro', [r.mensagem]);
            } else {
                responder('erro', diagnosticar(r.valor) || ['Some de novo, camada por camada: carga, transporte, IPv4, Ethernet, preenchimento e FCS.']);
            }
            if (errosNestaPergunta >= 2) {
                var a = atual;
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = '14 + 20 + ' + a.cabecalhoTransporte + ' + ' + a.carga
                    + (a.preenchimento > 0 ? ' + ' + a.preenchimento : '') + ' + 4 = ' + a.quadro;
                feedback.appendChild(resposta);
            }
            pintarPlacar();
        });

        document.getElementById('provar-outra').addEventListener('click', function () {
            P.avancarSemente(LICAO);
            apresentar();
            responder('', []);
        });

        apresentar();
        pintarPlacar();
    }());
}());
