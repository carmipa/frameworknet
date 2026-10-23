/**
 * Gabarito das contas do nível Transporte (seq/ack do TCP e janela deslizante).
 *
 * PROPÓSITO DE NEGÓCIO: a lição corrige o aluno com estas contas; se elas erram, o aluno honesto
 *   é marcado errado. O gabarito é escrito à mão a partir da definição do protocolo (A3): o SYN
 *   consome um número, o ack é o próximo byte esperado, a sequência dá a volta em 2^32 (RFC 9293);
 *   a retransmissão segue o exemplo clássico de Go-Back-N com janela 4 e o segmento 2 perdido
 *   (Kurose & Ross, "Redes de computadores e a Internet", seção 3.4), traçado passo a passo.
 * INVARIANTES DO DOMÍNIO: fronteiras cobertas — ISN 4294967295 (volta para 0), perda no último
 *   segmento (Go-Back-N e seletiva empatam), janela 1 (pare e espere), perda no primeiro.
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia a linha.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const C = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF', 'resources',
    'academia', 'transporte', 'js', 'contas-transporte.js'));

test('aperto de mão e troca de dados, traçado à mão', () => {
    // cliente ISN 100, servidor ISN 300, cliente manda 50 bytes, servidor responde 20
    const r = C.apertoDeMao(100, 300, 50, 20);
    const linhas = r.segmentos.map(s => [s.de, s.flags, s.seq, s.ack, s.bytes]);
    assert.deepEqual(linhas, [
        ['cliente', 'SYN', 100, null, 0],
        ['servidor', 'SYN, ACK', 300, 101, 0],     // confirma o SYN: 100 + 1
        ['cliente', 'ACK', 101, 301, 0],           // o SYN do servidor também consome um
        ['cliente', 'ACK, dados', 101, 301, 50],   // o primeiro byte de dados é o 101
        ['servidor', 'ACK', 301, 151, 0],          // bytes 101..150 recebidos: o próximo é o 151
        ['servidor', 'ACK, dados', 301, 151, 20],
        ['cliente', 'ACK', 151, 321, 0],           // 301..320 recebidos: o próximo é o 321
    ]);
    assert.equal(r.proximoCliente, 151);
    assert.equal(r.proximoServidor, 321);
});

test('sem dados, só os três segmentos do aperto de mão', () => {
    assert.equal(C.apertoDeMao(0, 0, 0, 0).segmentos.length, 3);
});

test('o número de sequência dá a volta em 2^32', () => {
    assert.equal(C.somarSeq(4294967295, 1), 0);
    assert.equal(C.somarSeq(4294967290, 10), 4);
    const r = C.apertoDeMao(4294967295, 4294967295, 10, 0);
    assert.equal(r.segmentos[1].ack, 0);   // 4294967295 + 1 volta a 0
    assert.equal(r.segmentos[2].ack, 0);
    assert.equal(r.segmentos[4].ack, 10);  // dados 0..9, próximo 10
    assert.throws(() => C.somarSeq(4294967296, 1), RangeError);
    assert.throws(() => C.apertoDeMao(-1, 0, 0, 0), RangeError);
});

test('diagnóstico do ack: o engano é nomeado e o legítimo não é acusado', () => {
    // ack do SYN-ACK com cliente ISN 2000 e servidor ISN 7000: certo 2001
    assert.equal(C.diagnosticarAck(2000, 7000, 0, 2001), null);
    assert.equal(C.diagnosticarAck(2000, 7000, 0, 2000), 'ESQUECEU_O_MAIS_UM');
    assert.equal(C.diagnosticarAck(2000, 7000, 0, 7001), 'USOU_O_PROPRIO_ISN');
    // ack dos dados: cliente ISN 2000, 300 bytes ⇒ certo 2301
    assert.equal(C.diagnosticarAck(2000, 7000, 300, 2301), null);
    assert.equal(C.diagnosticarAck(2000, 7000, 300, 2300), 'ESQUECEU_O_MAIS_UM');
    assert.equal(C.diagnosticarAck(2000, 7000, 300, 2001), 'NAO_SOMOU_OS_DADOS');
    assert.equal(C.diagnosticarAck(2000, 7000, 300, 300), 'SO_OS_DADOS');
    assert.equal(C.diagnosticarAck(2000, 7000, 300, 5555), null, 'erro sem padrão não inventa engano');
    // na volta do contador, o certo continua certo
    assert.equal(C.diagnosticarAck(4294967295, 7000, 0, 0), null);
});

test('Go-Back-N do exemplo clássico: janela 4, segmento 2 perdido, traçado à mão', () => {
    const r = C.simular(6, 4, 2, 'gbn');
    const linha = r.eventos.map(e => e.tipo === 'estouro' ? 'estouro' + e.seg : e.seg + ':' + e.resultado);
    assert.deepEqual(linha, ['0:aceito', '1:aceito', '2:perdido', '3:descartado', '4:descartado', '5:descartado',
        'estouro2', '2:aceito', '3:aceito', '4:aceito', '5:aceito']);
    assert.equal(r.total, 10);
    assert.equal(r.reenvios, 4);
});

test('repetição seletiva do mesmo caso: só o perdido volta', () => {
    const r = C.simular(6, 4, 2, 'seletiva');
    const linha = r.eventos.map(e => e.tipo === 'estouro' ? 'estouro' + e.seg : e.seg + ':' + e.resultado);
    assert.deepEqual(linha, ['0:aceito', '1:aceito', '2:perdido', '3:guardado', '4:guardado', '5:guardado',
        'estouro2', '2:aceito']);
    assert.equal(r.total, 7);
});

test('total de transmissões, tabela à mão, e a simulação concorda com a fórmula', () => {
    const TABELA = [
        // N, W, perdido, Go-Back-N, seletiva (conta)
        [6, 4, 2, 10, 7],       // reenvia 2,3,4,5
        [10, 3, 8, 12, 11],     // depois do 8 só saiu o 9: reenvia 8 e 9
        [10, 4, 9, 11, 11],     // perdeu o último: os dois modos empatam
        [10, 1, 4, 11, 11],     // janela 1 é pare e espere: nada sai depois do perdido
        [8, 8, 0, 16, 9],       // perdeu o primeiro com a janela inteira atrás dele
        [5, 3, null, 5, 5],     // sem perda
    ];
    for (const [n, w, k, gbn, sel] of TABELA) {
        assert.equal(C.totalTransmissoes(n, w, k, 'gbn'), gbn, `GBN N=${n} W=${w} k=${k}`);
        assert.equal(C.totalTransmissoes(n, w, k, 'seletiva'), sel, `seletiva N=${n} W=${w} k=${k}`);
        assert.equal(C.simular(n, w, k, 'gbn').total, gbn, `simulação GBN N=${n} W=${w} k=${k}`);
        assert.equal(C.simular(n, w, k, 'seletiva').total, sel, `simulação seletiva N=${n} W=${w} k=${k}`);
    }
});

test('simulação e fórmula concordam em toda combinação pequena', () => {
    for (let n = 1; n <= 12; n++) {
        for (let w = 1; w <= 8; w++) {
            for (let k = -1; k < n; k++) {
                const perdido = k < 0 ? null : k;
                for (const modo of ['gbn', 'seletiva']) {
                    assert.equal(C.simular(n, w, perdido, modo).total, C.totalTransmissoes(n, w, perdido, modo),
                        `${modo} N=${n} W=${w} k=${perdido}`);
                }
            }
        }
    }
});

test('janela: o último que pode sair é base + W − 1', () => {
    assert.equal(C.ultimoDaJanela(0, 4), 3);
    assert.equal(C.ultimoDaJanela(7, 1), 7);
    assert.equal(C.ultimoDaJanela(12, 5), 16);
});

test('diagnóstico do total: o engano é nomeado e o legítimo não é acusado', () => {
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'gbn', 10), null);
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'gbn', 7), 'PENSOU_EM_SELETIVA');
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'gbn', 6), 'ESQUECEU_A_PERDA');
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'gbn', 9), 'NAO_CONTOU_O_PERDIDO');
    assert.equal(C.diagnosticarTotal(10, 3, 8, 'gbn', 13), 'JANELA_ALEM_DO_FIM');   // 10 + 3, mas só o 9 saiu depois
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'seletiva', 7), null);
    assert.equal(C.diagnosticarTotal(6, 4, 2, 'seletiva', 10), 'PENSOU_EM_GO_BACK_N');
    // A1: no empate (perda no último), a resposta certa não pode ser acusada de "pensou no outro modo"
    assert.equal(C.diagnosticarTotal(10, 4, 9, 'gbn', 11), null);
    assert.equal(C.diagnosticarTotal(10, 4, 9, 'seletiva', 11), null);
});

test('entrada fora do domínio é recusada com o motivo', () => {
    assert.throws(() => C.simular(0, 4, null, 'gbn'), RangeError);
    assert.throws(() => C.simular(65, 4, null, 'gbn'), RangeError);
    assert.throws(() => C.simular(6, 0, null, 'gbn'), RangeError);
    assert.throws(() => C.simular(6, 4, 6, 'gbn'), RangeError);
    assert.throws(() => C.simular(6, 4, 2, 'outro'), RangeError);
});
