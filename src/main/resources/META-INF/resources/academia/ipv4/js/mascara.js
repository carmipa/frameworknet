/**
 * Lição IPv4 · Máscara e prefixo — liga a página às contas, à régua, ao motor e ao progresso.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê a divisória andar e a rede encolher (Ver), mexe em endereço,
 *   prefixo e máscara com tudo recalculado na hora (Mexer) e responde máscara, hosts, rede e
 *   broadcast com a correção dizendo qual foi o engano (Provar).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - toda entrada passa pelo AcademiaNormalizador (vírgula ABNT2, /24 ou 24, máscara com 000);
 *     só tentativa interpretável conta; a mesma resposta errada não conta duas vezes;
 *   - prefixo, máscara e régua mostram sempre a MESMA divisória (um muda, os outros acompanham);
 *   - /31 e /32 têm explicação própria na tela (não são "erro" de 0 hosts);
 *   - pergunta estável pela semente da sessão; resposta revelada só após duas tentativas erradas.
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
    var C = window.AcademiaIpv4;
    var N = window.AcademiaNormalizador;
    var P = window.AcademiaProgresso;
    var S = window.AcademiaSinais;
    var M = window.AcademiaMotor;
    var R = window.AcademiaRegua;

    S.iniciar(LICAO);
    P.pintarSelo(document.querySelector('[data-acad-selo]'), LICAO);

    function prefixoDaMascara(octetos) {
        return octetos.reduce(function (soma, o) {
            return soma + o.toString(2).split('').filter(function (b) { return b === '1'; }).length;
        }, 0);
    }

    function nota(p) {
        if (p === 32) {
            return 'Um /32 é um endereço só: usado em rota para um host e em interface de loopback.';
        }
        if (p === 31) {
            return 'Um /31 é um enlace ponto a ponto: os dois endereços são hosts, sem rede nem broadcast separados (RFC 3021).';
        }
        return '';
    }

    // ------------------------------------------------------------------ Ver
    (function ver() {
        var IP = [192, 168, 10, 77];
        var bits = C.bitsDoEndereco(IP);
        var regua = R.montar(document.getElementById('ver-regua'), { interativa: false });
        var el = {
            mascara: document.getElementById('ver-mascara'),
            rede: document.getElementById('ver-rede'),
            broadcast: document.getElementById('ver-broadcast'),
            hosts: document.getElementById('ver-hosts')
        };
        function passo(p) {
            var a = C.analisar(IP, p);
            return {
                aplicar: function () {
                    regua.mostrar(bits, p);
                    el.mascara.textContent = '/' + p + ' = ' + a.mascara;
                    el.rede.textContent = a.rede;
                    el.broadcast.textContent = a.broadcast;
                    el.hosts.textContent = String(a.hosts);
                },
                legenda: ['Com ', { valor: '/' + p }, ' a máscara é ', { valor: a.mascara }, ': rede ', { valor: a.rede },
                    ', broadcast ', { valor: a.broadcast }, ', ', { valor: a.hosts }, ' hosts utilizáveis.']
            };
        }
        M.criar({
            raiz: document.getElementById('ver-animacao'),
            legendaInicial: ['O endereço ', { valor: '192.168.10.77' }, ' com o prefixo crescendo de ', { valor: '/24' },
                ' a ', { valor: '/27' }, '. Toque em Tocar ou avance com Passo.'],
            reiniciar: function () {
                regua.mostrar(bits, 0);
                Object.keys(el).forEach(function (k) { el[k].textContent = '—'; });
            },
            passos: [passo(24), passo(25), passo(26), passo(27)]
        });
    }());

    // ------------------------------------------------------------------ Mexer
    (function mexer() {
        var campoIp = document.getElementById('mexer-ip');
        var campoPrefixo = document.getElementById('mexer-prefixo');
        var campoMascara = document.getElementById('mexer-mascara');
        var eco = {
            ip: document.getElementById('mexer-ip-eco'),
            prefixo: document.getElementById('mexer-prefixo-eco'),
            mascara: document.getElementById('mexer-mascara-eco')
        };
        var notaEl = document.getElementById('mexer-nota');
        var estado = { ip: [192, 168, 10, 77], p: 27 };
        var regua;

        function mostrar(origem) {
            var a = C.analisar(estado.ip, estado.p);
            regua.mostrar(C.bitsDoEndereco(estado.ip), estado.p);
            if (origem !== 'prefixo') {
                campoPrefixo.value = '/' + estado.p;
                eco.prefixo.textContent = '';
            }
            if (origem !== 'mascara') {
                campoMascara.value = a.mascara;
                eco.mascara.textContent = '';
            }
            ['rede', 'broadcast', 'primeiro', 'ultimo'].forEach(function (k) {
                document.getElementById('mexer-' + k).textContent = a[k];
            });
            // ACAD-20: em /31 e /32 não há endereço de rede nem broadcast separados (a nota explica); mostrar
            // um valor ali contradizia a nota logo abaixo.
            if (estado.p >= 31) {
                document.getElementById('mexer-rede').textContent = '— (ver nota)';
                document.getElementById('mexer-broadcast').textContent = '— (ver nota)';
            }
            document.getElementById('mexer-hosts').textContent = String(a.hosts);
            document.getElementById('mexer-bloco').textContent = String(a.bloco);
            notaEl.textContent = nota(estado.p);
            if (origem !== null) {
                S.interagiu();
            }
        }

        regua = R.montar(document.getElementById('mexer-regua'), {
            interativa: true,
            aoEscolherPrefixo: function (p) {
                estado.p = p;
                mostrar('regua');
            }
        });
        campoIp.addEventListener('input', function () {
            var r = N.ipv4(campoIp.value);
            eco.ip.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.ip = r.valor;
                mostrar('ip');
            }
        });
        campoPrefixo.addEventListener('input', function () {
            var r = N.prefixo(campoPrefixo.value);
            eco.prefixo.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.p = r.valor;
                mostrar('prefixo');
            }
        });
        campoMascara.addEventListener('input', function () {
            var r = N.mascara(campoMascara.value);
            eco.mascara.textContent = r.ok ? r.eco : r.mensagem;
            if (r.ok) {
                estado.p = prefixoDaMascara(r.valor);
                mostrar('mascara');
            }
        });
        mostrar(null);
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
        var TIPOS = ['mascara', 'hosts', 'rede', 'broadcast'];
        var atual = null;
        var errosNestaPergunta = 0;
        var ultimaErrada = null;

        function sortear() {
            var a = C.sorteador(P.semente(LICAO));
            var tipo = TIPOS[C.sortearInteiro(a, 0, TIPOS.length - 1)];
            var ip = [C.sortearInteiro(a, 1, 223), C.sortearInteiro(a, 0, 255), C.sortearInteiro(a, 0, 255), C.sortearInteiro(a, 1, 254)];
            var p = tipo === 'mascara' ? C.sortearInteiro(a, 8, 30)
                : tipo === 'hosts' ? C.sortearInteiro(a, 16, 30) : C.sortearInteiro(a, 20, 30);
            return { tipo: tipo, ip: ip, p: p, analise: C.analisar(ip, p) };
        }

        function normalizar(textoResposta) {
            if (atual.tipo === 'mascara') {
                return N.mascara(textoResposta);
            }
            if (atual.tipo === 'hosts') {
                return N.inteiro(textoResposta, { min: 0, max: 4294967296 });
            }
            return N.ipv4(textoResposta);
        }

        function chave(r) {
            return Array.isArray(r.valor) ? r.valor.join('.') : String(r.valor);
        }

        function gabarito() {
            if (atual.tipo === 'mascara') {
                return atual.analise.mascara;
            }
            if (atual.tipo === 'hosts') {
                return String(atual.analise.hosts);
            }
            return atual.tipo === 'rede' ? atual.analise.rede : atual.analise.broadcast;
        }

        function apresentar() {
            atual = sortear();
            errosNestaPergunta = 0;
            ultimaErrada = null;
            campo.value = '';
            ecoEl.textContent = '';
            var enderecoP = atual.ip.join('.') + '/' + atual.p;
            if (atual.tipo === 'mascara') {
                M.montarTexto(perguntaEl, ['Qual é a máscara do prefixo ', { valor: '/' + atual.p }, ' em notação decimal?']);
                rotuloEl.textContent = 'Máscara';
            } else if (atual.tipo === 'hosts') {
                M.montarTexto(perguntaEl, ['Quantos hosts utilizáveis tem uma rede ', { valor: '/' + atual.p }, '?']);
                rotuloEl.textContent = 'Hosts utilizáveis';
            } else if (atual.tipo === 'rede') {
                M.montarTexto(perguntaEl, ['Qual é o endereço de rede de ', { valor: enderecoP }, '?']);
                rotuloEl.textContent = 'Endereço de rede';
            } else {
                M.montarTexto(perguntaEl, ['Qual é o broadcast de ', { valor: enderecoP }, '?']);
                rotuloEl.textContent = 'Broadcast';
            }
            campo.setAttribute('inputmode', atual.tipo === 'hosts' ? 'numeric' : 'decimal');
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
            var a = atual.analise;
            if (atual.tipo === 'mascara') {
                var dele = prefixoDaMascara(r.valor);
                return ['Essa máscara é ', { valor: '/' + dele }, '. Conte de novo: ', { valor: '/' + atual.p },
                    ' são ', { valor: atual.p }, ' bits 1 seguidos.'];
            }
            if (atual.tipo === 'hosts') {
                var cod = C.diagnosticarHosts(atual.p, r.valor);
                if (cod === 'CONTOU_REDE_E_BROADCAST') {
                    return ['Esse é o tamanho do bloco. Tire o endereço de rede e o de broadcast.'];
                }
                if (cod === 'TIROU_SO_UM') {
                    return ['Faltou tirar um: são dois endereços que não vão para host, a rede e o broadcast.'];
                }
                return ['Hosts utilizáveis = 2 elevado ao número de bits de host, menos 2.'];
            }
            if (atual.tipo === 'rede') {
                var codRede = C.diagnosticarRede(atual.ip, atual.p, r.valor);
                if (codRede === 'E_O_BROADCAST') {
                    return ['Esse é o broadcast (o último endereço do bloco). A rede é o primeiro.'];
                }
                if (codRede === 'E_O_PROPRIO_ENDERECO') {
                    return ['Esse é o próprio endereço. Zere os bits de host para achar a rede.'];
                }
                if (codRede === 'PREFIXO_VIZINHO') {
                    return ['Quase: essa é a rede de um prefixo vizinho. Confira onde fica a divisória do ', { valor: '/' + atual.p }, '.'];
                }
            } else if (r.valor.join('.') === a.rede) {
                return ['Esse é o endereço de rede (o primeiro do bloco). O broadcast é o último.'];
            }
            var resto = atual.p % 8;
            if (resto === 0) {
                return ['A divisória cai entre octetos: depois dela, tudo vira ', { valor: atual.tipo === 'rede' ? '0' : '255' }, '.'];
            }
            return ['No octeto ', { valor: Math.floor(atual.p / 8) + 1 }, ' o bloco anda de ', { valor: Math.pow(2, 8 - resto) },
                ' em ', { valor: Math.pow(2, 8 - resto) }, '.'];
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
            var acertou = r.ok && chave(r) === gabarito();
            var repeticao = N.chaveRepeticao(r, r.ok ? chave(r) : null, campo.value);
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
            responder('erro', r.ok ? dica(r) : [r.mensagem]);
            if (errosNestaPergunta >= 2) {
                feedback.appendChild(document.createTextNode(' Resposta: '));
                var resposta = document.createElement('span');
                resposta.className = 'acad-valor';
                resposta.setAttribute('translate', 'no');
                resposta.textContent = gabarito();
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
