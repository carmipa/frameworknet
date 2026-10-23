/**
 * Lição Fundamentos · Hexadecimal — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê o byte partido em duas metades de quatro bits (Ver), mexe em
 *   bits, decimal e hexadecimal com tudo recalculado na hora (Mexer) e converte sozinho, com a
 *   correção dizendo qual dos dois dígitos errou (Provar).
 *
 * INVARIANTES DO DOMÍNIO: as mesmas da lição de binário — entrada pelo normalizador, só
 *   tentativa interpretável conta, pergunta estável pela semente da sessão, resposta revelada só
 *   depois de duas tentativas erradas, valores em translate="no". Hexadecimal sempre em
 *   maiúsculas na tela.
 *
 * COMPORTAMENTO EM CASO DE FALHA: script de apoio ausente deixa a parte parada e o erro sai pelo
 *   AcademiaSinais; a página continua legível.
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
    var B = window.AcademiaBits;

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    var DIGITOS = '0123456789ABCDEF';

    function metade(bits, inicio) {
        return bits.slice(inicio, inicio + 4).join('');
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var EXEMPLO = 0x2A;
        var fileira = B.montar(document.getElementById('ver-bits'), { interativo: false, nibbles: true });
        var altoEl = document.getElementById('ver-alto');
        var baixoEl = document.getElementById('ver-baixo');
        var hexEl = document.getElementById('ver-hex');
        var bits = F.paraBits(EXEMPLO);
        var n = F.nibbles(EXEMPLO);
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['Vamos ler o byte ', { valor: metade(bits, 0) + ' ' + metade(bits, 4) }, ' em hexadecimal. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () {
                fileira.definir(0);
                fileira.destacar([]);
                altoEl.textContent = '—';
                baixoEl.textContent = '—';
                hexEl.textContent = '—';
            },
            passos: [
                {
                    aplicar: function () { fileira.definir(EXEMPLO); },
                    legenda: ['O byte tem oito bits: ', { valor: metade(bits, 0) + ' ' + metade(bits, 4) }, '.']
                },
                {
                    aplicar: function () {
                        fileira.destacar([0, 1, 2, 3]);
                        altoEl.textContent = metade(bits, 0) + ' = ' + n.alto + ' = ' + DIGITOS.charAt(n.alto);
                    },
                    legenda: ['A metade da esquerda, ', { valor: metade(bits, 0) }, ', vale ', { valor: n.alto },
                        ': o dígito ', { valor: DIGITOS.charAt(n.alto) }, '.']
                },
                {
                    aplicar: function () {
                        fileira.destacar([4, 5, 6, 7]);
                        baixoEl.textContent = metade(bits, 4) + ' = ' + n.baixo + ' = ' + DIGITOS.charAt(n.baixo);
                    },
                    legenda: ['A metade da direita, ', { valor: metade(bits, 4) }, ', vale ', { valor: n.baixo },
                        ': como passa de 9, vira a letra ', { valor: DIGITOS.charAt(n.baixo) }, '.']
                },
                {
                    aplicar: function () {
                        fileira.destacar([]);
                        hexEl.textContent = '0x' + F.paraHex(EXEMPLO);
                    },
                    legenda: ['Juntando: ', { valor: '0x' + F.paraHex(EXEMPLO) }, '. Conferindo: ',
                        { valor: n.alto + ' × 16 + ' + n.baixo + ' = ' + EXEMPLO }, '.']
                }
            ]
        });
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var campoDecimal = document.getElementById('mexer-decimal');
        var campoHex = document.getElementById('mexer-hex');
        var ecoDecimal = document.getElementById('mexer-decimal-eco');
        var ecoHex = document.getElementById('mexer-hex-eco');
        var altoEl = document.getElementById('mexer-alto');
        var baixoEl = document.getElementById('mexer-baixo');
        var byteEl = document.getElementById('mexer-byte');
        var fileira;

        function mostrar(valor, origem) {
            fileira.definir(valor);
            var n = F.nibbles(valor);
            if (origem !== 'decimal') {
                campoDecimal.value = String(valor);
                ecoDecimal.textContent = '';
            }
            if (origem !== 'hex') {
                campoHex.value = F.paraHex(valor);
                ecoHex.textContent = '';
            }
            // ACAD-19: "A = 10 × 16 = 160" é igualdade encadeada falsa (A não vale 160). O dígito mostra o
            // valor entre parênteses e a conta vem depois.
            altoEl.textContent = DIGITOS.charAt(n.alto) + ' (' + n.alto + ') × 16 = ' + (n.alto * 16);
            baixoEl.textContent = DIGITOS.charAt(n.baixo) + ' (' + n.baixo + ') × 1 = ' + n.baixo;
            byteEl.textContent = '0x' + F.paraHex(valor) + ' = ' + valor;
            if (origem !== null) {
                S.interagiu();
            }
        }

        fileira = B.montar(document.getElementById('mexer-bits'), {
            interativo: true,
            nibbles: true,
            aoMudar: function (valor) { mostrar(valor, 'bits'); }
        });
        campoDecimal.addEventListener('input', function () {
            var r = N.inteiro(campoDecimal.value, { min: 0, max: 255 });
            ecoDecimal.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                mostrar(r.valor, 'decimal');
            }
        });
        campoHex.addEventListener('input', function () {
            var r = N.hexadecimal(campoHex.value, { max: 0xFF });
            ecoHex.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                mostrar(r.valor, 'hex');
            }
        });
        mostrar(0, null);
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
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var aleatorio = F.sorteador(P.semente(LICAO));
            var tipo = aleatorio() < 0.5 ? 'para-hex' : 'para-decimal';
            return { tipo: tipo, numero: F.sortearInteiro(aleatorio, 16, 255) };
        }

        function normalizar(texto) {
            return atual.tipo === 'para-hex' ? N.hexadecimal(texto, { max: 0xFF }) : N.inteiro(texto, { min: 0, max: 255 });
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
            if (atual.tipo === 'para-hex') {
                M.montarTexto(perguntaEl, ['Escreva ', { valor: atual.numero }, ' em hexadecimal.']);
                rotuloEl.textContent = 'Hexadecimal';
                campo.removeAttribute('inputmode');
            } else {
                M.montarTexto(perguntaEl, ['Quanto vale ', { valor: '0x' + F.paraHex(atual.numero) }, ' em decimal?']);
                rotuloEl.textContent = 'Decimal';
                campo.setAttribute('inputmode', 'numeric');
            }
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
            var repeticao = N.chaveRepeticao(r, r.ok ? r.valor : null, campo.value);
            if (!(r.ok && r.valor === atual.numero) && repeticao !== null && repeticao === ultimaErrada) {
                responder('neutro', ['Essa é a mesma resposta de antes — não contou de novo.']);
                return;
            }
            S.interagiu();
            var acertou = r.ok && r.valor === atual.numero;
            var resultado = P.registrarTentativa(LICAO, acertou);
            if (acertou) {
                var partes = ['Certo: ', { valor: '0x' + F.paraHex(atual.numero) }, ' = ', { valor: atual.numero }, '.'];
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
            } else if (atual.tipo === 'para-hex') {
                var errados = F.nibblesDiferentes(r.valor, atual.numero);
                if (errados.length === 2) {
                    responder('erro', ['Os dois dígitos estão diferentes. Separe o byte em duas metades de quatro bits.']);
                } else if (errados[0] === 'alto') {
                    responder('erro', ['O dígito da esquerda (o que conta de 16 em 16) está errado.']);
                } else {
                    responder('erro', ['O dígito da direita (o que conta de 1 em 1) está errado.']);
                }
            } else {
                var n = F.nibbles(atual.numero);
                responder('erro', ['Lembre: o dígito da esquerda vale ', { valor: '× 16' }, ' e o da direita ', { valor: '× 1' }, '.']);
                if (errosNestaPergunta < 2) {
                    feedback.appendChild(document.createTextNode(' A esquerda aqui é '));
                    var dica = document.createElement('span');
                    dica.className = 'acad-valor';
                    dica.setAttribute('translate', 'no');
                    dica.textContent = DIGITOS.charAt(n.alto) + ' = ' + n.alto;
                    feedback.appendChild(dica);
                    feedback.appendChild(document.createTextNode('.'));
                }
            }
            if (errosNestaPergunta >= 2) {
                var m = F.nibbles(atual.numero);
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = '0x' + F.paraHex(atual.numero) + ' = ' + m.alto + ' × 16 + ' + m.baixo + ' = ' + atual.numero;
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
