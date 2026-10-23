/**
 * Lição Fundamentos · Binário — liga a página às contas, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê os pesos somando o número (Ver), acende bits e digita
 *   números com o resultado na hora (Mexer) e converte sozinho, com correção que aponta o bit
 *   errado (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - toda entrada passa pelo AcademiaNormalizador; só tentativa interpretável conta (INV-ACAD-006);
 *   - a pergunta vem da semente da sessão: recarregar (inclusive pela tradução) mantém a pergunta;
 *   - a resposta só é revelada depois de duas tentativas erradas na mesma pergunta;
 *   - valores mostrados vão em translate="no".
 *
 * COMPORTAMENTO EM CASO DE FALHA: script de apoio ausente (contas, normalizador, motor) deixa a
 *   parte correspondente parada e o erro sai pelo AcademiaSinais; a página continua legível.
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

    function bitsTexto(n) {
        var b = F.paraBits(n).join('');
        return b.slice(0, 4) + ' ' + b.slice(4);
    }

    function escrever(el, partes) {
        M.montarTexto(el, partes);
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var EXEMPLO = 42;
        var fileira = B.montar(document.getElementById('ver-bits'), { interativo: false });
        var somaEl = document.getElementById('ver-soma');
        var passos = [];
        var resto = EXEMPLO;
        var acumulado = 0;
        var estados = [];
        F.PESOS.forEach(function (peso, posicao) {
            var cabe = resto >= peso;
            var antes = resto;
            if (cabe) {
                resto -= peso;
                acumulado += peso;
            }
            estados.push({ posicao: posicao, valor: acumulado, peso: peso, cabe: cabe, antes: antes });
        });
        estados.forEach(function (e) {
            passos.push({
                aplicar: function () {
                    fileira.definir(e.valor);
                    fileira.destacar(e.posicao);
                    somaEl.textContent = String(e.valor);
                },
                legenda: e.cabe
                    ? ['O peso ', { valor: e.peso }, ' cabe em ', { valor: e.antes }, ': o bit acende e a soma vai a ', { valor: e.valor }, '.']
                    : ['O peso ', { valor: e.peso }, ' não cabe em ', { valor: e.antes }, ': o bit fica apagado.']
            });
        });
        passos.push({
            aplicar: function () {
                fileira.destacar(-1);
            },
            legenda: ['Pronto: ', { valor: bitsTexto(EXEMPLO) }, ' em binário vale ', { valor: EXEMPLO }, ' em decimal.']
        });
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            passos: passos,
            legendaInicial: ['Vamos escrever ', { valor: EXEMPLO }, ' em binário. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () {
                fileira.definir(0);
                fileira.destacar(-1);
                somaEl.textContent = '0';
            }
        });
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var campoDecimal = document.getElementById('mexer-decimal');
        var campoBinario = document.getElementById('mexer-binario');
        var ecoDecimal = document.getElementById('mexer-decimal-eco');
        var ecoBinario = document.getElementById('mexer-binario-eco');
        var acesosEl = document.getElementById('mexer-acesos');
        var somaEl = document.getElementById('mexer-soma');

        function mostrar(n, origem) {
            fileira.definir(n);
            if (origem !== 'decimal') {
                campoDecimal.value = String(n);
                ecoDecimal.textContent = '';
            }
            if (origem !== 'binario') {
                campoBinario.value = bitsTexto(n);
                ecoBinario.textContent = '';
            }
            var acesos = F.paraBits(n).reduce(function (s, b) { return s + b; }, 0);
            acesosEl.textContent = String(acesos);
            var parcelas = F.PESOS.filter(function (peso) { return (n & peso) !== 0; });
            somaEl.textContent = parcelas.length ? parcelas.join(' + ') + ' = ' + n : '0';
            if (origem !== null) {
                S.interagiu();
            }
        }

        var fileira = B.montar(document.getElementById('mexer-bits'), {
            interativo: true,
            aoMudar: function (n) { mostrar(n, 'bits'); }
        });

        campoDecimal.addEventListener('input', function () {
            var r = N.inteiro(campoDecimal.value, { min: 0, max: 255 });
            ecoDecimal.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                mostrar(r.valor, 'decimal');
            }
        });
        campoBinario.addEventListener('input', function () {
            var r = N.binario(campoBinario.value, { bits: 8 });
            ecoBinario.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                mostrar(r.valor, 'binario');
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
        var correcao = B.montar(document.getElementById('provar-bits'), { interativo: false });
        var correcaoEl = document.getElementById('provar-bits');
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var aleatorio = F.sorteador(P.semente(LICAO));
            var tipo = aleatorio() < 0.5 ? 'para-binario' : 'para-decimal';
            return { tipo: tipo, numero: F.sortearInteiro(aleatorio, 1, 255) };
        }

        function normalizar(texto) {
            return atual.tipo === 'para-binario' ? N.binario(texto, { bits: 8 }) : N.inteiro(texto, { min: 0, max: 255 });
        }

        function pintarPlacar() {
            var estado = P.ler(LICAO);
            var faltam = Math.max(0, P.ACERTOS_PARA_CONCLUIR - estado.acertos);
            placar.textContent = '';
            escrever(placar, estado.concluidaEm
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
            correcaoEl.hidden = true;
            correcao.limparMarcas();
            if (atual.tipo === 'para-binario') {
                escrever(perguntaEl, ['Escreva ', { valor: atual.numero }, ' em binário, com até oito bits.']);
                rotuloEl.textContent = 'Binário';
                campo.setAttribute('inputmode', 'numeric');
            } else {
                escrever(perguntaEl, ['Quanto vale ', { valor: bitsTexto(atual.numero) }, ' em decimal?']);
                rotuloEl.textContent = 'Decimal';
                campo.setAttribute('inputmode', 'numeric');
            }
        }

        function responder(classe, partes) {
            feedback.className = 'acad-feedback ' + classe;
            escrever(feedback, partes);
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
                var partes = ['Certo: ', { valor: bitsTexto(atual.numero) }, ' = ', { valor: atual.numero }, '.'];
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
            } else if (atual.tipo === 'para-binario') {
                var trocados = F.bitsDiferentes(r.valor, atual.numero);
                correcao.definir(r.valor);
                correcao.marcarErros(trocados);
                correcaoEl.hidden = false;
                var pesos = trocados.map(function (p) { return F.PESOS[p]; }).join(', ');
                responder('erro', ['Os bits marcados estão trocados — pesos ', { valor: pesos }, '.']);
            } else {
                var diferenca = Math.abs(r.valor - atual.numero);
                if (F.PESOS.indexOf(diferenca) >= 0) {
                    responder('erro', ['A sua soma difere em ', { valor: diferenca }, ': confira o bit de peso ', { valor: diferenca }, '.']);
                } else {
                    responder('erro', ['Some de novo os pesos dos bits acesos, da esquerda para a direita.']);
                }
            }
            if (errosNestaPergunta >= 2) {
                var parcelas = F.PESOS.filter(function (peso) { return (atual.numero & peso) !== 0; }).join(' + ');
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = bitsTexto(atual.numero) + ' = ' + parcelas + ' = ' + atual.numero;
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
