/**
 * Lição Transporte · Aperto de mão e sequência — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê o SYN, o SYN-ACK, o ACK e uma troca de dados com os números de
 *   cada um (Ver), escolhe os números iniciais e os bytes de cada lado e vê seq e ack mudarem na
 *   hora (Mexer), e responde o ack ou o seq de um segmento com a correção dizendo o engano (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - toda entrada passa pelo AcademiaNormalizador; só tentativa interpretável conta; a mesma
 *     resposta errada não conta duas vezes;
 *   - o número de sequência dá a volta em 2^32, e a tela avisa quando isso acontece no Mexer;
 *   - nas perguntas os números dos dois lados ficam em faixas separadas, para o diagnóstico nunca
 *     confundir "usou o próprio ISN" com "esqueceu o mais um".
 *
 * COMPORTAMENTO EM CASO DE FALHA: valor fora da faixa no Mexer mantém o último diagrama válido e
 *   diz o motivo no eco do campo; script de apoio ausente deixa a parte parada e o erro sai pelo
 *   AcademiaSinais.
 */
(function () {
    'use strict';

    var pagina = document.querySelector('[data-acad-licao]');
    if (!pagina) {
        return;
    }
    var LICAO = pagina.getAttribute('data-acad-licao');
    var C = window.AcademiaTransporte;
    var N = window.AcademiaNormalizador;
    var P = window.AcademiaProgresso;
    var S = window.AcademiaSinais;
    var M = window.AcademiaMotor;
    var SEQ_MAX = 4294967295;

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    function span(classe, texto, protegido) {
        var el = document.createElement('span');
        el.className = classe;
        if (protegido) {
            el.setAttribute('translate', 'no');
        }
        el.textContent = texto;
        return el;
    }

    /** Desenha os segmentos como setas: do cliente à esquerda, do servidor à direita. */
    function pintarSetas(ol, segmentos) {
        ol.textContent = '';
        segmentos.forEach(function (s) {
            var li = document.createElement('li');
            li.className = 'acad-seta ' + (s.de === 'cliente' ? 'para-servidor' : 'para-cliente');
            li.appendChild(span('acad-seta-direcao', s.de === 'cliente' ? 'cliente → servidor' : 'servidor → cliente'));
            li.appendChild(span('acad-seta-flags', s.flags, true));
            var campos = 'seq ' + s.seq + (s.ack === null ? '' : ' · ack ' + s.ack) + (s.bytes > 0 ? ' · ' + s.bytes + ' bytes' : '');
            li.appendChild(span('acad-seta-campos', campos, true));
            ol.appendChild(li);
        });
        return ol.querySelectorAll('li');
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var troca = C.apertoDeMao(100, 300, 50, 0);
        var itens = pintarSetas(document.getElementById('ver-setas'), troca.segmentos);
        function mostrarAte(i) {
            Array.prototype.forEach.call(itens, function (li, j) {
                li.classList.toggle('apagada', j > i);
                li.classList.toggle('destaque', j === i);
            });
        }
        var LEGENDAS = [
            ['O cliente pede a conexão: ', { valor: 'SYN' }, ' com o número inicial dele, ', { valor: 'seq 100' }, '.'],
            ['O servidor aceita e manda o próprio número, ', { valor: 'seq 300' }, ', confirmando ', { valor: 'ack 101' },
                ': o SYN gastou o 100, então o próximo esperado é o 101.'],
            ['O cliente confirma o SYN do servidor com ', { valor: 'ack 301' }, '. Pronto: três segmentos e a conexão está aberta.'],
            ['Agora vêm os dados: ', { valor: 50 }, ' bytes, do ', { valor: 101 }, ' ao ', { valor: 150 }, '.'],
            ['O servidor confirma com ', { valor: 'ack 151' }, ' — o PRÓXIMO byte que ele espera, não o último que recebeu.']
        ];
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['Um cliente vai abrir uma conexão TCP com um servidor. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () { mostrarAte(-1); },
            passos: LEGENDAS.map(function (legenda, i) {
                return { aplicar: function () { mostrarAte(i); }, legenda: legenda };
            })
        });
        mostrarAte(-1);
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var nota = document.getElementById('mexer-nota');
        var setas = document.getElementById('mexer-setas');
        var estado = { isnCliente: 100, isnServidor: 300, bytesCliente: 50, bytesServidor: 20 };
        var CAMPOS = [
            { id: 'mexer-isn-cliente', chave: 'isnCliente', max: SEQ_MAX },
            { id: 'mexer-isn-servidor', chave: 'isnServidor', max: SEQ_MAX },
            { id: 'mexer-bytes-cliente', chave: 'bytesCliente', max: 1000000 },
            { id: 'mexer-bytes-servidor', chave: 'bytesServidor', max: 1000000 }
        ];

        function calcular(contar) {
            var troca = C.apertoDeMao(estado.isnCliente, estado.isnServidor, estado.bytesCliente, estado.bytesServidor);
            pintarSetas(setas, troca.segmentos);
            var deuAVolta = estado.isnCliente + 1 + estado.bytesCliente > SEQ_MAX
                || estado.isnServidor + 1 + estado.bytesServidor > SEQ_MAX;
            M.montarTexto(nota, deuAVolta
                ? ['O número deu a volta: depois de ', { valor: SEQ_MAX }, ' vem ', { valor: 0 }, ' (a sequência é contada módulo ', { valor: '2^32' }, ').']
                : ['O próximo byte que o cliente vai mandar é o ', { valor: troca.proximoCliente }, '; o do servidor, o ',
                    { valor: troca.proximoServidor }, '.']);
            if (contar) {
                S.interagiu();
            }
        }

        CAMPOS.forEach(function (c) {
            var campo = document.getElementById(c.id);
            var eco = document.getElementById(c.id + '-eco');
            campo.addEventListener('input', function () {
                var r = N.inteiro(campo.value, { min: 0, max: c.max });
                eco.textContent = r.ok ? r.eco : r.mensagem;
                if (r.ok) {
                    estado[c.chave] = r.valor;
                    calcular(true);
                }
            });
        });
        calcular(false);
    }());

    // ------------------------------------------------------------------ Provar
    (function provar() {
        var perguntaEl = document.getElementById('provar-pergunta');
        var rotuloEl = document.getElementById('provar-rotulo');
        var campo = document.getElementById('provar-resposta');
        P.ligarRascunho(LICAO, campo);
        var ecoEl = document.getElementById('provar-eco');
        var feedback = document.getElementById('provar-feedback');
        var placar = document.getElementById('provar-placar');
        var TIPOS = ['synack', 'ackfinal', 'dados', 'seq'];
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        /** Faixas separadas (cliente 2000..3999, servidor 6000..9999, dados 1..1460). */
        function sortear() {
            var a = C.sorteador(P.semente(LICAO));
            return {
                tipo: TIPOS[C.sortearInteiro(a, 0, TIPOS.length - 1)],
                x: C.sortearInteiro(a, 2000, 3999),
                y: C.sortearInteiro(a, 6000, 9999),
                b: C.sortearInteiro(a, 1, 1460)
            };
        }

        /** O que a pergunta confirma: [isn do outro lado, isn de quem responde, bytes]. */
        function parametros() {
            if (atual.tipo === 'ackfinal') {
                return [atual.y, atual.x, 0];
            }
            if (atual.tipo === 'dados') {
                return [atual.x, atual.y, atual.b];
            }
            return [atual.x, atual.y, 0];
        }

        function gabarito() {
            var p = parametros();
            return C.somarSeq(p[0], 1 + p[2]);
        }

        function apresentar() {
            atual = sortear();
            errosNestaPergunta = 0;
            ultimaErrada = null;
            campo.value = '';
            ecoEl.textContent = '';
            if (atual.tipo === 'synack') {
                M.montarTexto(perguntaEl, ['O cliente abre a conexão com ', { valor: 'SYN seq ' + atual.x },
                    '. Que número de ack o servidor põe no SYN-ACK?']);
                rotuloEl.textContent = 'ack do SYN-ACK';
            } else if (atual.tipo === 'ackfinal') {
                M.montarTexto(perguntaEl, ['O cliente usou ', { valor: 'seq ' + atual.x }, ' e o servidor respondeu ',
                    { valor: 'SYN-ACK seq ' + atual.y }, '. Que ack o cliente põe no ACK que fecha o aperto de mão?']);
                rotuloEl.textContent = 'ack do ACK final';
            } else if (atual.tipo === 'dados') {
                M.montarTexto(perguntaEl, ['Depois do aperto de mão (o cliente começou com ', { valor: 'seq ' + atual.x },
                    '), o cliente envia ', { valor: atual.b }, ' bytes de dados. Que ack o servidor devolve?']);
                rotuloEl.textContent = 'ack dos dados';
            } else {
                M.montarTexto(perguntaEl, ['O cliente abriu a conexão com ', { valor: 'SYN seq ' + atual.x },
                    '. Qual é o número de sequência do primeiro byte de dados que ele envia?']);
                rotuloEl.textContent = 'seq do primeiro byte';
            }
        }

        function responder(classe, partes) {
            feedback.className = 'acad-feedback ' + classe;
            M.montarTexto(feedback, partes);
        }

        function pintarPlacar() {
            var e = P.ler(LICAO);
            var faltam = Math.max(0, P.ACERTOS_PARA_CONCLUIR - e.acertos);
            M.montarTexto(placar, e.concluidaEm
                ? ['Acertos: ', { valor: e.acertos }, ' · tentativas: ', { valor: e.tentativas }, ' · lição concluída.']
                : ['Acertos: ', { valor: e.acertos }, ' · tentativas: ', { valor: e.tentativas }, ' · faltam ', { valor: faltam }, ' para concluir.']);
            P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);
        }

        function dica(valor) {
            var p = parametros();
            var cod = C.diagnosticarAck(p[0], p[1], p[2], valor);
            if (cod === 'ESQUECEU_O_MAIS_UM') {
                return atual.tipo === 'dados'
                    ? ['Faltou um: o ack aponta o PRÓXIMO byte esperado, não o último recebido — e o SYN também gastou um número.']
                    : ['Faltou um: o ', { valor: 'SYN' }, ' gasta um número de sequência, então o próximo é ', { valor: p[0] }, ' + 1.'];
            }
            if (cod === 'NAO_SOMOU_OS_DADOS') {
                return ['Isso confirma só o SYN. Os ', { valor: p[2] }, ' bytes de dados também andam a sequência.'];
            }
            if (cod === 'USOU_O_PROPRIO_ISN') {
                return atual.tipo === 'seq'
                    ? ['Esse número é do servidor. O seq dos dados do cliente continua a partir do ISN do cliente, ', { valor: atual.x }, '.']
                    : ['Esse número é de quem está respondendo. O ack confirma o que veio do OUTRO lado: parta do ISN de quem enviou, ',
                        { valor: p[0] }, '.'];
            }
            if (cod === 'SO_OS_DADOS') {
                return ['A sequência não começa do zero: parta do ISN ', { valor: p[0] }, ', some 1 do SYN e os bytes.'];
            }
            return atual.tipo === 'seq'
                ? ['O primeiro byte de dados vem logo depois do SYN: ISN do cliente + 1.']
                : ['O ack é o ISN de quem enviou + 1 (do SYN) + os bytes de dados já recebidos.'];
        }

        campo.addEventListener('input', function () {
            if (!atual) {
                return;
            }
            var r = N.inteiro(campo.value, { min: 0, max: SEQ_MAX });
            ecoEl.textContent = r.ok ? r.eco : (campo.value.trim() === '' ? '' : r.mensagem);
        });

        document.getElementById('provar-form').addEventListener('submit', function (ev) {
            ev.preventDefault();
            var r = N.inteiro(campo.value, { min: 0, max: SEQ_MAX });
            if (!N.contaTentativa(r)) {
                responder('neutro', [r.mensagem, ' Isto não conta como tentativa.']);
                return;
            }
            var acertou = r.ok && r.valor === gabarito();
            var repeticao = N.chaveRepeticao(r, r.ok ? r.valor : null, campo.value);
            if (!acertou && repeticao !== null && repeticao === ultimaErrada) {
                responder('neutro', ['Essa é a mesma resposta de antes — não contou de novo.']);
                return;
            }
            S.interagiu();
            var resultado = P.registrarTentativa(LICAO, acertou);
            if (acertou) {
                var partes = ['Certo: ', { valor: gabarito() }, '.'];
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
            responder('erro', r.ok ? dica(r.valor) : [r.mensagem]);
            if (errosNestaPergunta >= 2) {
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = String(gabarito());
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
