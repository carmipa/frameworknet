/**
 * Gabarito das contas do nível Fundamentos (binário, hexadecimal, camadas).
 *
 * PROPÓSITO DE NEGÓCIO: a lição corrige o aluno com estas contas; se elas erram, o aluno
 *   honesto é marcado errado. Por isso o gabarito é uma tabela escrita à mão (A3).
 * INVARIANTES DO DOMÍNIO: cada valor esperado tem a conta ao lado, refeita à mão a partir da
 *   definição (pesos 128..1; 16 por dígito hexadecimal; cabeçalhos TCP 20, UDP 8, IPv4 20,
 *   Ethernet 14 + FCS 4; carga mínima do quadro 46). Fronteira do preenchimento nos dois lados:
 *   pacote de 45, 46 e 47 bytes.
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia a linha.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const F = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF', 'resources',
    'academia', 'fundamentos', 'js', 'contas-fundamentos.js'));

const BITS = [
    // [decimal, bits à mão, a conta]
    [0, '00000000', 'nenhum peso'],
    [1, '00000001', '1'],
    [5, '00000101', '4 + 1'],
    [42, '00101010', '32 + 8 + 2'],
    [127, '01111111', '64 + 32 + 16 + 8 + 4 + 2 + 1'],
    [128, '10000000', '128'],
    [170, '10101010', '128 + 32 + 8 + 2'],
    [200, '11001000', '128 + 64 + 8'],
    [255, '11111111', 'todos os pesos'],
];

test('decimal ↔ bits pela tabela à mão', () => {
    for (const [decimal, bits, conta] of BITS) {
        assert.equal(F.paraBits(decimal).join(''), bits, `${decimal} = ${conta}`);
        assert.equal(F.deBits(bits.split('').map(Number)), decimal, `${bits} = ${conta}`);
    }
});

test('a correção aponta exatamente os bits trocados', () => {
    assert.deepEqual(F.bitsDiferentes(42, 42), []);
    // 42 = 00101010, 40 = 00101000: só o peso 2 (posição 6) difere
    assert.deepEqual(F.bitsDiferentes(40, 42), [6]);
    // 0 × 255: as oito posições
    assert.deepEqual(F.bitsDiferentes(0, 255), [0, 1, 2, 3, 4, 5, 6, 7]);
});

test('decimal → hexadecimal e nibbles pela tabela à mão', () => {
    const HEX = [
        [0, '00', 0, 0],
        [10, '0A', 0, 10],
        [42, '2A', 2, 10],    // 2×16 + 10
        [127, '7F', 7, 15],   // 7×16 + 15
        [160, 'A0', 10, 0],   // 10×16
        [171, 'AB', 10, 11],  // 10×16 + 11
        [255, 'FF', 15, 15],  // 15×16 + 15
    ];
    for (const [decimal, hex, alto, baixo] of HEX) {
        assert.equal(F.paraHex(decimal), hex, `${decimal}`);
        assert.deepEqual(F.nibbles(decimal), { alto, baixo }, `${decimal}`);
    }
    assert.deepEqual(F.nibblesDiferentes(0x2B, 0x2A), ['baixo']);
    assert.deepEqual(F.nibblesDiferentes(0x3A, 0x2A), ['alto']);
    assert.deepEqual(F.nibblesDiferentes(0x2A, 0x2A), []);
});

test('fora do domínio é defeito de programa, não do aluno', () => {
    assert.throws(() => F.paraBits(256), RangeError);
    assert.throws(() => F.paraBits(-1), RangeError);
    assert.throws(() => F.paraBits(1.5), RangeError);
    assert.throws(() => F.deBits([1, 0]), RangeError);
    assert.throws(() => F.encapsular(1001, 'TCP'), RangeError);
    assert.throws(() => F.encapsular(10, 'ICMP'), RangeError);
});

test('UTF-8: acento ocupa mais de um byte', () => {
    assert.equal(F.bytesUtf8(''), 0);
    assert.equal(F.bytesUtf8('Oi'), 2);
    assert.equal(F.bytesUtf8('ação'), 6);   // a 1 + ç 2 + ã 2 + o 1
    assert.equal(F.bytesUtf8('€'), 3);
});

test('encapsulamento: tabela à mão, com a fronteira do preenchimento nos dois lados', () => {
    const CASOS = [
        // carga, transporte, segmento, pacote, preenchimento, quadro
        [2, 'TCP', 22, 42, 4, 64],      // 2+20; +20; 46-42; 14+42+4+4
        [2, 'UDP', 10, 30, 16, 64],     // 2+8; +20; 46-30; 14+30+16+4
        [5, 'TCP', 25, 45, 1, 64],      // pacote 45: falta 1 para 46
        [6, 'TCP', 26, 46, 0, 64],      // pacote 46: exatamente o mínimo
        [7, 'TCP', 27, 47, 0, 65],      // pacote 47: passa do mínimo, sem preenchimento
        [18, 'UDP', 26, 46, 0, 64],     // 18+8+20 = 46
        [1000, 'TCP', 1020, 1040, 0, 1058],
    ];
    for (const [carga, transporte, segmento, pacote, preenchimento, quadro] of CASOS) {
        const r = F.encapsular(carga, transporte);
        assert.deepEqual(
            [r.segmento, r.pacote, r.preenchimento, r.quadro],
            [segmento, pacote, preenchimento, quadro],
            `${carga} bytes por ${transporte}`);
    }
});

test('a correção do quadro nomeia a camada esquecida (tabela à mão)', () => {
    // 100 bytes por TCP: segmento 120, pacote 140, sem preenchimento, quadro 14+140+4 = 158;
    // por UDP seria 100+8+20 = 128, quadro 14+128+4 = 146
    const tcp = F.encapsular(100, 'TCP');
    assert.equal(F.diagnosticarQuadro(tcp, 158), null);
    assert.equal(F.diagnosticarQuadro(tcp, 140), 'PACOTE');
    assert.equal(F.diagnosticarQuadro(tcp, 120), 'SEGMENTO');
    assert.equal(F.diagnosticarQuadro(tcp, 154), 'SEM_FCS');
    assert.equal(F.diagnosticarQuadro(tcp, 146), 'TRANSPORTE_TROCADO');
    assert.equal(F.diagnosticarQuadro(tcp, 999), null);
    // 10 bytes por UDP: segmento 18, pacote 38, preenchimento 8, quadro 64;
    // por TCP seria 10+20+20 = 50, sem preenchimento, quadro 68
    const udp = F.encapsular(10, 'UDP');
    assert.equal(F.diagnosticarQuadro(udp, 56), 'SEM_PREENCHIMENTO');
    assert.equal(F.diagnosticarQuadro(udp, 60), 'SEM_FCS');
    assert.equal(F.diagnosticarQuadro(udp, 38), 'PACOTE');
    assert.equal(F.diagnosticarQuadro(udp, 68), 'TRANSPORTE_TROCADO');
});

test('sorteio: mesma semente, mesma pergunta; valores dentro da faixa', () => {
    const a = F.sorteador(1234);
    const b = F.sorteador(1234);
    const c = F.sorteador(4321);
    const sa = Array.from({ length: 20 }, () => F.sortearInteiro(a, 0, 255));
    const sb = Array.from({ length: 20 }, () => F.sortearInteiro(b, 0, 255));
    const sc = Array.from({ length: 20 }, () => F.sortearInteiro(c, 0, 255));
    assert.deepEqual(sa, sb);
    assert.notDeepEqual(sa, sc);
    assert.ok(sa.every((n) => Number.isInteger(n) && n >= 0 && n <= 255));
});
