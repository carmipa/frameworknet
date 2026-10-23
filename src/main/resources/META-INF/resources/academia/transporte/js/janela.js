/**
 * Lição Transporte · Janela e retransmissão — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê o Go-Back-N com janela 4 perder o segmento 2 e reenviar a partir
 *   dele (Ver), muda segmentos, janela, perda e modo e vê o registro de envios e o total dos dois
 *   modos na hora (Mexer), e responde o total de transmissões ou o limite da janela com a correção
 *   dizendo o engano (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - toda entrada passa pelo AcademiaNormalizador; só tentativa interpretável conta; a mesma
 *     resposta errada não conta duas vezes;
 *   - o modelo das contas é o mesmo da tela e das perguntas: uma perda só, confirmações que não se
 *     perdem (a página diz isso ao aluno);
 *   - segmento perdido vazio quer dizer "nenhum"; fora de 0..N-1 é recusado com o motivo, e a tela
 *     mantém o último registro válido.
 *
 * COMPORTAMENTO EM CASO DE FALHA: combinação inválida mostra o motivo no aviso e não recalcula;
 *   script de apoio ausente deixa a parte parada e o erro sai pelo AcademiaSinais.
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

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    var ROTULO = { aceito: 'aceito', perdido: 'perdido', descartado: 'descartado', guardado: 'guardado' };

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var QTD = 6;
        var JANELA = 4;
        var PERDIDO = 2;
        var sim = C.simular(QTD, JANELA, PERDIDO, 'gbn');
        var seletiva = C.totalTransmissoes(QTD, JANELA, PERDIDO, 'seletiva');
        var lista = document.getElementById('ver-segmentos');
        var totalEl = document.getElementById('ver-total');
        var celulas = [];
        for (var i = 0; i < QTD; i++) {
            var li = document.createElement('li');
            li.className = 'acad-segmento';
            var numero = document.createElement('span');
            numero.className = 'acad-segmento-numero';
            numero.setAttribute('translate', 'no');
            numero.textContent = String(i);
            var estado = document.createElement('span');
            estado.textContent = 'na fila';
            li.appendChild(numero);
            li.appendChild(estado);
            lista.appendChild(li);
            celulas.push({ li: li, estado: estado });
        }

        /** Repinta tudo a partir dos eventos 0..ate: estado de cada segmento, janela e total. */
        function mostrarAte(ate) {
            var estados = [];
            var aceitos = [];
            for (var s = 0; s < QTD; s++) {
                estados.push('na fila');
                aceitos.push(false);
            }
            var total = 0;
            for (var e = 0; e <= ate; e++) {
                var ev = sim.eventos[e];
                if (ev.tipo === 'envio') {
                    total++;
                    estados[ev.seg] = ROTULO[ev.resultado] + (ev.vez > 1 ? ' (' + ev.vez + 'ª vez)' : '');
                    aceitos[ev.seg] = ev.resultado === 'aceito';
                } else {
                    estados[ev.seg] = 'tempo estourou';
                }
            }
            var base = 0;
            while (base < QTD && aceitos[base]) {
                base++;
            }
            celulas.forEach(function (c, s) {
                c.estado.textContent = estados[s];
                c.li.className = 'acad-segmento';
                var classe = estados[s].split(' ')[0];
                if (ROTULO[classe]) {
                    c.li.classList.add(classe);
                }
                if (estados[s] === 'tempo estourou') {
                    c.li.classList.add('perdido');
                }
                if (ate >= 0 && s >= base && s < base + JANELA) {
                    c.li.classList.add('na-janela');
                }
            });
            totalEl.textContent = String(total);
            if (ate >= 0) {
                // o segmento que acabou de mudar pulsa (a leitura do offsetWidth reinicia a animação
                // quando o mesmo segmento muda dois passos seguidos: estouro e reenvio)
                var celula = celulas[sim.eventos[ate].seg].li;
                celula.classList.remove('mudou');
                void celula.offsetWidth;
                celula.classList.add('mudou');
            }
        }

        function legenda(ev, ultimo) {
            var partes;
            if (ev.tipo === 'estouro') {
                partes = ['Nada mais cabe na janela e o ', { valor: ev.seg }, ' não foi confirmado: o tempo dele estoura. ',
                    'O Go-Back-N volta e reenvia a partir do ', { valor: ev.seg }, '.'];
            } else if (ev.resultado === 'perdido') {
                partes = ['O segmento ', { valor: ev.seg }, ' se perde no caminho. O emissor ainda não sabe e continua enviando.'];
            } else if (ev.resultado === 'descartado') {
                partes = ['O ', { valor: ev.seg }, ' chega, mas o receptor esperava o ', { valor: PERDIDO },
                    ': no Go-Back-N o que chega fora de ordem é descartado.'];
            } else if (ev.vez > 1) {
                partes = ['O ', { valor: ev.seg }, ' sai de novo (', { valor: ev.vez + 'ª' }, ' vez) e agora é aceito na ordem.'];
            } else {
                partes = ['O ', { valor: ev.seg }, ' chega na ordem e é confirmado. A janela anda uma casa.'];
            }
            if (ultimo) {
                partes = partes.concat([' Total: ', { valor: sim.total }, ' transmissões. Na repetição seletiva só o ',
                    { valor: PERDIDO }, ' voltaria: ', { valor: seletiva }, '.']);
            }
            return partes;
        }

        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['Seis segmentos, janela de ', { valor: JANELA }, '. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () { mostrarAte(-1); },
            passos: sim.eventos.map(function (ev, i) {
                return {
                    aplicar: function () { mostrarAte(i); },
                    legenda: legenda(ev, i === sim.eventos.length - 1)
                };
            })
        });
        mostrarAte(-1);
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var campoQtd = document.getElementById('mexer-segmentos');
        var campoJanela = document.getElementById('mexer-janela');
        var campoPerdido = document.getElementById('mexer-perdido');
        var campoModo = document.getElementById('mexer-modo');
        var aviso = document.getElementById('mexer-aviso');
        var registro = document.getElementById('mexer-registro');
        var estado = { n: 6, w: 4, perdido: 2, modo: 'gbn' };

        function pintarRegistro(sim) {
            registro.textContent = '';
            sim.eventos.forEach(function (ev) {
                var li = document.createElement('li');
                var numero = document.createElement('span');
                numero.setAttribute('translate', 'no');
                numero.textContent = String(ev.seg);
                if (ev.tipo === 'estouro') {
                    li.className = 'estouro';
                    li.appendChild(document.createTextNode('tempo do '));
                    li.appendChild(numero);
                    li.appendChild(document.createTextNode(' estoura'));
                } else {
                    li.className = ev.resultado;
                    li.appendChild(numero);
                    li.appendChild(document.createTextNode(' ' + ROTULO[ev.resultado] + (ev.vez > 1 ? ' (' + ev.vez + 'ª)' : '')));
                }
                registro.appendChild(li);
            });
        }

        function calcular(contar) {
            if (estado.perdido !== null && estado.perdido >= estado.n) {
                aviso.textContent = 'O segmento perdido precisa estar entre 0 e ' + (estado.n - 1) + ', ou vazio para nenhum.';
                return;
            }
            var sim = C.simular(estado.n, estado.w, estado.perdido, estado.modo);
            document.getElementById('mexer-total-gbn').textContent =
                String(C.totalTransmissoes(estado.n, estado.w, estado.perdido, 'gbn'));
            document.getElementById('mexer-total-seletiva').textContent =
                String(C.totalTransmissoes(estado.n, estado.w, estado.perdido, 'seletiva'));
            if (estado.perdido === null) {
                M.montarTexto(aviso, ['Sem perda, os dois modos fazem ', { valor: estado.n }, ' transmissões.']);
            } else if (estado.modo === 'gbn') {
                M.montarTexto(aviso, ['Go-Back-N: ', { valor: sim.reenvios }, ' reenvios — o ', { valor: estado.perdido },
                    ' e os que já tinham saído depois dele (no máximo a janela menos um).']);
            } else {
                M.montarTexto(aviso, ['Repetição seletiva: só o ', { valor: estado.perdido },
                    ' volta; o que chegou depois dele fica guardado no receptor.']);
            }
            pintarRegistro(sim);
            if (contar) {
                S.interagiu();
            }
        }

        function ligar(campo, opcoes, chave, vazioVira) {
            var eco = document.getElementById(campo.id + '-eco');
            campo.addEventListener('input', function () {
                if (vazioVira !== undefined && campo.value.trim() === '') {
                    eco.textContent = 'nenhum segmento perdido';
                    estado[chave] = vazioVira;
                    calcular(true);
                    return;
                }
                var r = N.inteiro(campo.value, opcoes);
                eco.textContent = r.ok ? r.eco : r.mensagem;
                if (r.ok) {
                    estado[chave] = r.valor;
                    calcular(true);
                }
            });
        }

        ligar(campoQtd, { min: 1, max: C.MAX_SEGMENTOS }, 'n');
        ligar(campoJanela, { min: 1, max: C.MAX_JANELA }, 'w');
        ligar(campoPerdido, { min: 0, max: C.MAX_SEGMENTOS - 1 }, 'perdido', null);
        campoModo.addEventListener('change', function () {
            estado.modo = campoModo.value === 'seletiva' ? 'seletiva' : 'gbn';
            calcular(true);
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
        var TIPOS = ['gbn', 'seletiva', 'janela'];
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var a = C.sorteador(P.semente(LICAO));
            var q = {
                tipo: TIPOS[C.sortearInteiro(a, 0, TIPOS.length - 1)],
                n: C.sortearInteiro(a, 6, 20),
                w: C.sortearInteiro(a, 2, 8),
                base: C.sortearInteiro(a, 0, 40)
            };
            q.k = C.sortearInteiro(a, 0, q.n - 1);
            return q;
        }

        function gabarito() {
            if (atual.tipo === 'janela') {
                return C.ultimoDaJanela(atual.base, atual.w);
            }
            return C.totalTransmissoes(atual.n, atual.w, atual.k, atual.tipo);
        }

        function apresentar() {
            atual = sortear();
            errosNestaPergunta = 0;
            ultimaErrada = null;
            campo.value = '';
            ecoEl.textContent = '';
            if (atual.tipo === 'janela') {
                M.montarTexto(perguntaEl, ['A janela é de ', { valor: atual.w }, ' segmentos e o mais antigo ainda sem confirmação é o ',
                    { valor: atual.base }, '. Até que número de segmento o emissor pode enviar sem esperar?']);
                rotuloEl.textContent = 'Último segmento da janela';
                return;
            }
            M.montarTexto(perguntaEl, ['São ', { valor: atual.n }, ' segmentos (do ', { valor: 0 }, ' ao ', { valor: atual.n - 1 },
                '), janela de ', { valor: atual.w }, ', e o segmento ', { valor: atual.k }, ' se perde uma vez. Com ',
                atual.tipo === 'gbn' ? 'Go-Back-N' : 'repetição seletiva',
                ', quantas transmissões acontecem no total até tudo chegar? (As confirmações não se perdem.)']);
            rotuloEl.textContent = 'Total de transmissões';
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
            if (atual.tipo === 'janela') {
                if (valor === atual.base + atual.w) {
                    return ['Um a mais: a janela começa no próprio ', { valor: atual.base }, ' e tem ', { valor: atual.w },
                        ' segmentos contando com ele.'];
                }
                return ['A janela vai do ', { valor: atual.base }, ' até ', { valor: atual.base + ' + ' + atual.w + ' − 1' }, '.'];
            }
            var cod = C.diagnosticarTotal(atual.n, atual.w, atual.k, atual.tipo, valor);
            if (cod === 'PENSOU_EM_SELETIVA') {
                return ['Isso seria a repetição seletiva. No Go-Back-N o receptor descarta o que chega depois do perdido, ',
                    'e tudo o que já tinha saído volta junto.'];
            }
            if (cod === 'PENSOU_EM_GO_BACK_N') {
                return ['Isso seria o Go-Back-N. Na repetição seletiva o receptor guarda o que chegou fora de ordem: só o ',
                    { valor: atual.k }, ' volta.'];
            }
            if (cod === 'ESQUECEU_A_PERDA') {
                return ['Você contou só os ', { valor: atual.n }, '. O segmento perdido precisa sair de novo.'];
            }
            if (cod === 'NAO_CONTOU_O_PERDIDO') {
                return ['Quase: além dos que tinham saído depois do ', { valor: atual.k }, ', o próprio ', { valor: atual.k },
                    ' é reenviado.'];
            }
            if (cod === 'JANELA_ALEM_DO_FIM') {
                return ['Depois do ', { valor: atual.k }, ' só existem ', { valor: atual.n - 1 - atual.k },
                    ' segmentos: não se reenvia o que não existe.'];
            }
            return atual.tipo === 'gbn'
                ? ['Conte os ', { valor: atual.n }, ', mais o ', { valor: atual.k }, ' de novo, mais os que já tinham saído depois dele ',
                    '(no máximo ', { valor: atual.w - 1 }, ').']
                : ['Na repetição seletiva só o perdido é reenviado.'];
        }

        campo.addEventListener('input', function () {
            if (!atual) {
                return;
            }
            var r = N.inteiro(campo.value, { min: 0, max: 100000 });
            ecoEl.textContent = r.ok ? r.eco : (campo.value.trim() === '' ? '' : r.mensagem);
        });

        document.getElementById('provar-form').addEventListener('submit', function (ev) {
            ev.preventDefault();
            var r = N.inteiro(campo.value, { min: 0, max: 100000 });
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
