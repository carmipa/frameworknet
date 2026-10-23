/**
 * Lição IPv4 · Sub-redes — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê um /24 virar quatro /26 (Ver), divide um bloco qualquer por
 *   quantidade de sub-redes ou por hosts e vê a lista na hora (Mexer), e responde prefixo,
 *   quantidade e a K-ésima sub-rede com a correção dizendo o engano (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - toda entrada passa pelo AcademiaNormalizador; só tentativa interpretável conta; a mesma
 *     resposta errada não conta duas vezes;
 *   - a rede de partida é alinhada ao prefixo, e a tela diz quando alinhou ("192.168.1.77/24 parte
 *     de 192.168.1.0");
 *   - a lista tem teto (AcademiaIpv4.MAX_LISTA) e diz "mostrando X de Y" — nunca trava a aba;
 *   - "a 1ª sub-rede" é a própria base; a resposta da K-ésima aceita o endereço com ou sem "/prefixo"
 *     (se vier o prefixo, ele tem de ser o certo).
 *
 * COMPORTAMENTO EM CASO DE FALHA: divisão impossível (não cabe) mostra o motivo no lugar da lista;
 *   script de apoio ausente deixa a parte parada e o erro sai pelo AcademiaSinais.
 */
(function () {
    'use strict';

    var pagina = document.querySelector('[data-acad-licao]');
    if (!pagina) {
        return;
    }
    var LICAO = pagina.getAttribute('data-acad-licao');
    var C = window.AcademiaIpv4;
    var N = window.AcademiaNormalizador;
    var P = window.AcademiaProgresso;
    var S = window.AcademiaSinais;
    var M = window.AcademiaMotor;

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    function pintarLista(ul, divisao) {
        ul.textContent = '';
        divisao.sub.forEach(function (s) {
            var li = document.createElement('li');
            li.setAttribute('translate', 'no');
            li.textContent = '#' + (s.indice + 1) + '  ' + s.rede + ' … ' + s.broadcast;
            ul.appendChild(li);
        });
        return ul.querySelectorAll('li');
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var BASE = [192, 168, 1, 0];
        var divisao = C.dividir(BASE, 24, 26);
        var prefixoEl = document.getElementById('ver-prefixo');
        var itens = pintarLista(document.getElementById('ver-lista'), divisao);
        function destacar(i) {
            Array.prototype.forEach.call(itens, function (li, j) {
                li.classList.toggle('destaque', j === i);
                li.style.opacity = i < 0 || j <= i ? '1' : '.35';
            });
        }
        var passos = [{
            aplicar: function () {
                prefixoEl.textContent = '/26';
                destacar(-1);
            },
            legenda: ['Quatro sub-redes pedem ', { valor: 2 }, ' bits a mais (', { valor: '2 × 2 = 4' }, '): de ',
                { valor: '/24' }, ' para ', { valor: '/26' }, ', blocos de ', { valor: 64 }, ' endereços.']
        }];
        divisao.sub.forEach(function (s, i) {
            passos.push({
                aplicar: function () { destacar(i); },
                legenda: ['Sub-rede ', { valor: i + 1 }, ': ', { valor: s.rede }, ' até ', { valor: s.broadcast },
                    ', com ', { valor: 62 }, ' hosts utilizáveis.']
            });
        });
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['Vamos dividir ', { valor: '192.168.1.0/24' }, ' em quatro. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () {
                prefixoEl.textContent = '—';
                Array.prototype.forEach.call(itens, function (li) {
                    li.classList.remove('destaque');
                    li.style.opacity = '.35';
                });
            },
            passos: passos
        });
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var campoIp = document.getElementById('mexer-ip');
        var campoPrefixo = document.getElementById('mexer-prefixo');
        var campoModo = document.getElementById('mexer-modo');
        var campoQtd = document.getElementById('mexer-quantidade');
        var eco = {
            ip: document.getElementById('mexer-ip-eco'),
            prefixo: document.getElementById('mexer-prefixo-eco'),
            qtd: document.getElementById('mexer-quantidade-eco')
        };
        var aviso = document.getElementById('mexer-aviso');
        var lista = document.getElementById('mexer-lista');
        var estado = { ip: [192, 168, 1, 0], p: 24, modo: 'subredes', qtd: 4 };

        function limpar(motivo) {
            ['novo', 'mascara', 'total', 'hosts'].forEach(function (k) {
                document.getElementById('mexer-' + k).textContent = '—';
            });
            lista.textContent = '';
            aviso.textContent = motivo;
        }

        function calcular(contar) {
            var novo;
            try {
                novo = estado.modo === 'hosts' ? C.prefixoParaHosts(estado.qtd) : C.prefixoParaSubredes(estado.p, estado.qtd);
            } catch (e) {
                limpar(e.message ? 'Não dá: ' + e.message + '.' : 'Essa divisão não é possível.');
                return;
            }
            if (novo < estado.p) {
                // ACAD-21: a frase antiga dizia "um /31 tem só 2 hosts" quando o aluno tinha pedido 2 — a conta
                // de sub-rede reserva rede e broadcast (/30 para 2 hosts), e o motivo real é o prefixo pedido.
                var pedido = estado.modo === 'hosts'
                    ? estado.qtd + (estado.qtd === 1 ? ' host' : ' hosts') + ' por sub-rede'
                    : estado.qtd + (estado.qtd === 1 ? ' sub-rede' : ' sub-redes');
                limpar('Não cabe: ' + pedido + ' pede um /' + novo + ', que é maior que o /' + estado.p + ' de partida.');
                return;
            }
            var divisao = C.dividir(estado.ip, estado.p, novo);
            var base = C.analisar(estado.ip, estado.p).rede;
            document.getElementById('mexer-novo').textContent = '/' + novo;
            document.getElementById('mexer-mascara').textContent = C.mascara(novo);
            document.getElementById('mexer-total').textContent = String(divisao.total);
            document.getElementById('mexer-hosts').textContent = String(C.hostsUtilizaveis(novo));
            var partes = [];
            if (base !== estado.ip.join('.')) {
                partes.push(estado.ip.join('.') + '/' + estado.p + ' parte de ' + base + '. ');
            }
            if (divisao.total > divisao.sub.length) {
                partes.push('Mostrando ' + divisao.sub.length + ' de ' + divisao.total + ' sub-redes.');
            }
            aviso.textContent = partes.join('');
            pintarLista(lista, divisao);
            if (contar) {
                S.interagiu();
            }
        }

        campoIp.addEventListener('input', function () {
            var r = N.ipv4(campoIp.value);
            eco.ip.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.ip = r.valor;
                calcular(true);
            }
        });
        campoPrefixo.addEventListener('input', function () {
            var r = N.prefixo(campoPrefixo.value);
            eco.prefixo.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.p = r.valor;
                calcular(true);
            }
        });
        campoModo.addEventListener('change', function () {
            estado.modo = campoModo.value === 'hosts' ? 'hosts' : 'subredes';
            calcular(true);
        });
        campoQtd.addEventListener('input', function () {
            var r = N.inteiro(campoQtd.value, { min: 1, max: 16777216 });
            eco.qtd.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.qtd = r.valor;
                calcular(true);
            }
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
        var TIPOS = ['prefixo', 'quantas', 'qual'];
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var a = C.sorteador(P.semente(LICAO));
            var tipo = TIPOS[C.sortearInteiro(a, 0, TIPOS.length - 1)];
            var p = [16, 20, 22, 24][C.sortearInteiro(a, 0, 3)];
            var base = C.analisar([C.sortearInteiro(a, 10, 200), C.sortearInteiro(a, 0, 255), C.sortearInteiro(a, 0, 255), 0], p);
            var ip = base.rede.split('.').map(Number);
            var q = {
                tipo: tipo, p: p, ip: ip, base: base.rede + '/' + p,
                n: C.sortearInteiro(a, 2, 20),
                novo: Math.min(30, p + C.sortearInteiro(a, 1, 6))
            };
            q.k = C.sortearInteiro(a, 2, Math.min(8, Math.pow(2, q.novo - p)));
            return q;
        }

        function gabarito() {
            if (atual.tipo === 'prefixo') {
                return String(C.prefixoParaSubredes(atual.p, atual.n));
            }
            if (atual.tipo === 'quantas') {
                return String(Math.pow(2, atual.novo - atual.p));
            }
            return C.dividir(atual.ip, atual.p, atual.novo, 1, atual.k - 1).sub[0].rede.split('/')[0];
        }

        /** Normaliza; na K-ésima aceita "a.b.c.d/q" desde que q seja o novo prefixo. */
        function normalizar(texto) {
            if (atual.tipo === 'prefixo') {
                return N.prefixo(texto);
            }
            if (atual.tipo === 'quantas') {
                return N.inteiro(texto, { min: 0, max: 16777216 });
            }
            var partes = String(texto || '').split('/');
            if (partes.length > 2) {
                return { ok: false, classe: 'formato', mensagem: 'Use o endereço, com ou sem /prefixo.' };
            }
            var r = N.ipv4(partes[0]);
            if (r.ok && partes.length === 2) {
                var pr = N.prefixo(partes[1]);
                if (!pr.ok) {
                    return pr;
                }
                if (pr.valor !== atual.novo) {
                    return { ok: true, valor: r.valor, eco: r.eco + ' com /' + pr.valor, prefixoErrado: pr.valor };
                }
            }
            return r;
        }

        function chave(r) {
            return Array.isArray(r.valor) ? r.valor.join('.') + (r.prefixoErrado ? '/' + r.prefixoErrado : '') : String(r.valor);
        }

        function apresentar() {
            atual = sortear();
            errosNestaPergunta = 0;
            ultimaErrada = null;
            campo.value = '';
            ecoEl.textContent = '';
            if (atual.tipo === 'prefixo') {
                M.montarTexto(perguntaEl, ['Você precisa dividir ', { valor: atual.base }, ' em pelo menos ', { valor: atual.n },
                    ' sub-redes iguais. Qual é o prefixo de cada uma?']);
                rotuloEl.textContent = 'Prefixo';
            } else if (atual.tipo === 'quantas') {
                M.montarTexto(perguntaEl, ['Quantas sub-redes ', { valor: '/' + atual.novo }, ' cabem num bloco ', { valor: '/' + atual.p }, '?']);
                rotuloEl.textContent = 'Quantidade';
            } else {
                M.montarTexto(perguntaEl, ['Dividindo ', { valor: atual.base }, ' em sub-redes ', { valor: '/' + atual.novo },
                    ', qual é a ', { valor: atual.k + 'ª' }, '? (A 1ª começa no próprio endereço da base.)']);
                rotuloEl.textContent = 'Endereço da sub-rede';
            }
            campo.setAttribute('inputmode', atual.tipo === 'qual' ? 'decimal' : 'numeric');
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

        function dica(r) {
            if (atual.tipo === 'prefixo') {
                var cod = C.diagnosticarPrefixoSubredes(atual.p, atual.n, r.valor);
                if (cod === 'SOMOU_A_QUANTIDADE') {
                    return ['Você somou a quantidade de sub-redes ao prefixo. Some BITS: quantos bits fazem pelo menos ',
                        { valor: atual.n }, ' combinações?'];
                }
                if (cod === 'ARREDONDOU_PARA_BAIXO') {
                    return ['Bits de menos: com eles não cabem ', { valor: atual.n }, ' sub-redes. Arredonde para cima.'];
                }
                return ['Cada bit a mais dobra as sub-redes: conte quantos bits precisa e some ao ', { valor: '/' + atual.p }, '.'];
            }
            if (atual.tipo === 'quantas') {
                if (r.valor === atual.novo - atual.p) {
                    return ['Essa é a diferença de prefixos. A quantidade é 2 elevado a essa diferença.'];
                }
                return ['São 2 elevado a (', { valor: atual.novo + ' − ' + atual.p }, ').'];
            }
            if (r.prefixoErrado) {
                return ['O endereço pode até estar certo, mas o prefixo das sub-redes é ', { valor: '/' + atual.novo }, '.'];
            }
            return ['Cada sub-rede ', { valor: '/' + atual.novo }, ' tem ', { valor: C.tamanhoDoBloco(atual.novo) },
                ' endereços: a ', { valor: atual.k + 'ª' }, ' começa ', { valor: (atual.k - 1) + ' × ' + C.tamanhoDoBloco(atual.novo) },
                ' endereços depois da base.'];
        }

        campo.addEventListener('input', function () {
            if (!atual) {
                return;
            }
            var r = normalizar(campo.value);
            ecoEl.textContent = r.ok ? r.eco : (campo.value.trim() === '' ? '' : r.mensagem);
        });

        document.getElementById('provar-form').addEventListener('submit', function (ev) {
            ev.preventDefault();
            var r = normalizar(campo.value);
            if (!N.contaTentativa(r)) {
                responder('neutro', [r.mensagem, ' Isto não conta como tentativa.']);
                return;
            }
            var acertou = r.ok && !r.prefixoErrado && chave(r) === gabarito();
            var repeticao = N.chaveRepeticao(r, r.ok ? chave(r) : null, campo.value);
            if (!acertou && repeticao !== null && repeticao === ultimaErrada) {
                responder('neutro', ['Essa é a mesma resposta de antes — não contou de novo.']);
                return;
            }
            S.interagiu();
            var resultado = P.registrarTentativa(LICAO, acertou);
            if (acertou) {
                var partes = ['Certo: ', { valor: atual.tipo === 'prefixo' ? '/' + gabarito() : atual.tipo === 'qual' ? gabarito() + '/' + atual.novo : gabarito() }, '.'];
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
            responder('erro', r.ok ? dica(r) : [r.mensagem]);
            if (errosNestaPergunta >= 2) {
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = atual.tipo === 'prefixo' ? '/' + gabarito()
                    : atual.tipo === 'qual' ? gabarito() + '/' + atual.novo : gabarito();
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
