/**
 * Contas do nível Transporte da Academia: números de sequência do TCP e janela deslizante.
 *
 * PROPÓSITO DE NEGÓCIO: são as contas que a animação mostra, que o "Mexer" refaz a cada tecla e
 *   que o "Provar" usa para corrigir — o aperto de mão em três vias com seq/ack, e a retransmissão
 *   de Go-Back-N contra a repetição seletiva. Puras e sem tela, para serem conferidas contra um
 *   gabarito escrito à mão (A3). Não importam contas de outro nível (duplicação consciente).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - número de sequência é inteiro sem sinal de 32 bits e dá a volta: 4294967295 + 1 = 0
 *     (RFC 9293, aritmética módulo 2^32);
 *   - SYN e FIN consomem um número de sequência; ACK puro não consome; dado consome um por byte;
 *   - ack é o PRÓXIMO byte esperado, nunca o último recebido;
 *   - janela: segmentos numerados de 0 a N-1, janela de W segmentos (W >= 1), no máximo uma perda,
 *     na primeira transmissão do segmento k; ack nunca se perde; o emissor mantém a janela cheia e
 *     o temporizador só estoura quando ele não pode mais enviar nada;
 *   - Go-Back-N: o receptor descarta o que chega fora de ordem e o emissor reenvia do perdido em
 *     diante; repetição seletiva: o receptor guarda o fora de ordem e só o perdido volta;
 *   - toda simulação tem teto (MAX_SEGMENTOS) — a tela nunca trava.
 *
 * COMPORTAMENTO EM CASO DE FALHA: argumento fora do domínio lança RangeError com o motivo — quem
 *   chama já passou pelo normalizador. Script clássico: window.AcademiaTransporte no navegador,
 *   module.exports no Node.
 */
(function (raiz) {
    'use strict';

    var MODULO = 4294967296;
    var MAX_SEGMENTOS = 64;
    var MAX_JANELA = 32;

    function exigirSeq(n, nome) {
        if (!Number.isInteger(n) || n < 0 || n >= MODULO) {
            throw new RangeError((nome || 'número de sequência') + ' fora de 0..4294967295: ' + n);
        }
    }

    function exigirBytes(n, nome) {
        if (!Number.isInteger(n) || n < 0 || n > 1000000) {
            throw new RangeError((nome || 'bytes') + ' fora de 0..1000000: ' + n);
        }
    }

    /** Soma módulo 2^32: o número de sequência dá a volta depois de 4294967295. */
    function somarSeq(seq, quanto) {
        exigirSeq(seq);
        return (seq + quanto) % MODULO;
    }

    /**
     * Os segmentos do aperto de mão e de uma troca de dados, na ordem em que viajam.
     * isnCliente/isnServidor: números iniciais; bytesCliente/bytesServidor: dados de cada lado
     * depois do aperto (0 = nenhum segmento de dados daquele lado).
     */
    function apertoDeMao(isnCliente, isnServidor, bytesCliente, bytesServidor) {
        exigirSeq(isnCliente, 'ISN do cliente');
        exigirSeq(isnServidor, 'ISN do servidor');
        exigirBytes(bytesCliente, 'bytes do cliente');
        exigirBytes(bytesServidor, 'bytes do servidor');
        var x1 = somarSeq(isnCliente, 1);
        var y1 = somarSeq(isnServidor, 1);
        var segmentos = [
            { de: 'cliente', flags: 'SYN', seq: isnCliente, ack: null, bytes: 0 },
            { de: 'servidor', flags: 'SYN, ACK', seq: isnServidor, ack: x1, bytes: 0 },
            { de: 'cliente', flags: 'ACK', seq: x1, ack: y1, bytes: 0 }
        ];
        var proximoCliente = x1;
        var proximoServidor = y1;
        if (bytesCliente > 0) {
            segmentos.push({ de: 'cliente', flags: 'ACK, dados', seq: proximoCliente, ack: proximoServidor, bytes: bytesCliente });
            proximoCliente = somarSeq(proximoCliente, bytesCliente);
            segmentos.push({ de: 'servidor', flags: 'ACK', seq: proximoServidor, ack: proximoCliente, bytes: 0 });
        }
        if (bytesServidor > 0) {
            segmentos.push({ de: 'servidor', flags: 'ACK, dados', seq: proximoServidor, ack: proximoCliente, bytes: bytesServidor });
            proximoServidor = somarSeq(proximoServidor, bytesServidor);
            segmentos.push({ de: 'cliente', flags: 'ACK', seq: proximoCliente, ack: proximoServidor, bytes: 0 });
        }
        return { segmentos: segmentos, proximoCliente: proximoCliente, proximoServidor: proximoServidor };
    }

    /**
     * Engano provável num ack do aperto de mão. `isnDoOutro` é o ISN do lado que o ack confirma,
     * `isnDeQuemResponde` o do lado que escreve o ack, `bytes` os dados já recebidos depois do SYN:
     * 'ESQUECEU_O_MAIS_UM' (não contou o SYN, ou confirmou o último byte em vez do próximo),
     * 'NAO_SOMOU_OS_DADOS', 'USOU_O_PROPRIO_ISN', 'SO_OS_DADOS' ou null.
     */
    function diagnosticarAck(isnDoOutro, isnDeQuemResponde, bytes, valor) {
        exigirSeq(isnDoOutro);
        exigirSeq(isnDeQuemResponde);
        exigirBytes(bytes);
        var certo = somarSeq(isnDoOutro, 1 + bytes);
        if (valor === certo) {
            return null;
        }
        if (valor === somarSeq(isnDoOutro, bytes)) {
            return 'ESQUECEU_O_MAIS_UM';
        }
        if (bytes > 0 && valor === somarSeq(isnDoOutro, 1)) {
            return 'NAO_SOMOU_OS_DADOS';
        }
        if (valor === isnDeQuemResponde || valor === somarSeq(isnDeQuemResponde, 1)
                || valor === somarSeq(isnDeQuemResponde, 1 + bytes)) {
            return 'USOU_O_PROPRIO_ISN';
        }
        if (bytes > 0 && (valor === bytes || valor === bytes + 1)) {
            return 'SO_OS_DADOS';
        }
        return null;
    }

    function exigirJanela(n, w, perdido) {
        if (!Number.isInteger(n) || n < 1 || n > MAX_SEGMENTOS) {
            throw new RangeError('quantidade de segmentos fora de 1..' + MAX_SEGMENTOS + ': ' + n);
        }
        if (!Number.isInteger(w) || w < 1 || w > MAX_JANELA) {
            throw new RangeError('janela fora de 1..' + MAX_JANELA + ': ' + w);
        }
        if (perdido !== null && (!Number.isInteger(perdido) || perdido < 0 || perdido >= n)) {
            throw new RangeError('segmento perdido fora de 0..' + (n - 1) + ': ' + perdido);
        }
    }

    /**
     * Simula a transmissão de N segmentos com janela W e o segmento `perdido` (ou null) perdido
     * na primeira vez. modo: 'gbn' (Go-Back-N) ou 'seletiva'. Devolve os eventos na ordem —
     * { tipo: 'envio', seg, vez, resultado: 'aceito'|'perdido'|'descartado'|'guardado' } ou
     * { tipo: 'estouro', seg } — e o total de transmissões.
     */
    function simular(n, w, perdido, modo) {
        exigirJanela(n, w, perdido);
        if (modo !== 'gbn' && modo !== 'seletiva') {
            throw new RangeError('modo desconhecido: ' + modo);
        }
        var eventos = [];
        var vezes = [];
        var recebido = [];
        for (var i = 0; i < n; i++) {
            vezes.push(0);
            recebido.push(false);
        }
        var esperado = 0;
        var base = 0;
        var proximo = 0;
        var jaPerdeu = false;
        var guarda = 0;

        function entregarEmOrdem() {
            while (esperado < n && recebido[esperado]) {
                esperado++;
            }
            base = esperado;
        }

        function enviar(seg) {
            vezes[seg]++;
            var ev = { tipo: 'envio', seg: seg, vez: vezes[seg] };
            if (seg === perdido && !jaPerdeu) {
                jaPerdeu = true;
                ev.resultado = 'perdido';
            } else if (seg === esperado) {
                recebido[seg] = true;
                ev.resultado = 'aceito';
                entregarEmOrdem();
            } else if (modo === 'gbn') {
                ev.resultado = 'descartado';
            } else {
                recebido[seg] = true;
                ev.resultado = 'guardado';
            }
            eventos.push(ev);
        }

        while (esperado < n) {
            if (++guarda > 4 * n * (w + 2)) {
                throw new RangeError('simulação não terminou');
            }
            var limite = Math.min(base + w, n);
            if (proximo < base) {
                proximo = base;
            }
            if (proximo < limite) {
                if (modo === 'seletiva' && recebido[proximo]) {
                    proximo++;
                    continue;
                }
                enviar(proximo);
                proximo++;
                continue;
            }
            // Nada mais cabe na janela e falta confirmação: o temporizador do mais antigo estoura.
            eventos.push({ tipo: 'estouro', seg: base });
            if (modo === 'gbn') {
                proximo = base;
            } else {
                enviar(base);
            }
        }
        var total = eventos.filter(function (e) { return e.tipo === 'envio'; }).length;
        return { eventos: eventos, total: total, reenvios: total - n };
    }

    /** Total de transmissões, pela fórmula: Go-Back-N reenvia o perdido e o que já saiu depois dele. */
    function totalTransmissoes(n, w, perdido, modo) {
        exigirJanela(n, w, perdido);
        if (perdido === null) {
            return n;
        }
        if (modo === 'seletiva') {
            return n + 1;
        }
        return n + 1 + Math.min(w - 1, n - 1 - perdido);
    }

    /** Maior segmento que pode sair sem esperar confirmação, com a janela começando em `base`. */
    function ultimoDaJanela(base, w) {
        if (!Number.isInteger(base) || base < 0 || !Number.isInteger(w) || w < 1) {
            throw new RangeError('base ou janela inválida');
        }
        return base + w - 1;
    }

    /**
     * Engano provável no total de transmissões: 'PENSOU_EM_SELETIVA', 'PENSOU_EM_GO_BACK_N',
     * 'ESQUECEU_A_PERDA', 'NAO_CONTOU_O_PERDIDO', 'JANELA_ALEM_DO_FIM' ou null.
     */
    function diagnosticarTotal(n, w, perdido, modo, valor) {
        var certo = totalTransmissoes(n, w, perdido, modo);
        if (valor === certo) {
            return null;
        }
        if (valor === n) {
            return 'ESQUECEU_A_PERDA';
        }
        if (modo === 'gbn') {
            if (valor === n + 1) {
                return 'PENSOU_EM_SELETIVA';
            }
            if (valor === certo - 1) {
                return 'NAO_CONTOU_O_PERDIDO';
            }
            if (valor === n + w) {
                return 'JANELA_ALEM_DO_FIM';
            }
            return null;
        }
        if (valor === totalTransmissoes(n, w, perdido, 'gbn')) {
            return 'PENSOU_EM_GO_BACK_N';
        }
        return null;
    }

    /** Gerador determinístico (mulberry32): a mesma semente devolve a mesma sequência. */
    function sorteador(semente) {
        var estado = (semente >>> 0) || 1;
        return function () {
            estado = (estado + 0x6D2B79F5) >>> 0;
            var t = estado;
            t = Math.imul(t ^ (t >>> 15), t | 1);
            t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }

    function sortearInteiro(aleatorio, min, max) {
        return min + Math.floor(aleatorio() * (max - min + 1));
    }

    var api = {
        MAX_SEGMENTOS: MAX_SEGMENTOS,
        MAX_JANELA: MAX_JANELA,
        somarSeq: somarSeq,
        apertoDeMao: apertoDeMao,
        diagnosticarAck: diagnosticarAck,
        simular: simular,
        totalTransmissoes: totalTransmissoes,
        ultimoDaJanela: ultimoDaJanela,
        diagnosticarTotal: diagnosticarTotal,
        sorteador: sorteador,
        sortearInteiro: sortearInteiro
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        raiz.AcademiaTransporte = api;
    }
}(typeof window !== 'undefined' ? window : this));
