/**
 * Gabarito das contas do nível IPv4 (máscara, rede, broadcast, sub-redes).
 *
 * PROPÓSITO DE NEGÓCIO: a lição corrige o aluno com estas contas; se elas erram, o aluno honesto
 *   é marcado errado. O gabarito é uma tabela escrita à mão (A3), com a conta ao lado.
 * INVARIANTES DO DOMÍNIO: fronteiras /0, /31, /32, 0.0.0.0 e 255.255.255.255 cobertas; /31 com
 *   dois hosts (RFC 3021); divisão arredonda para a potência de 2 de cima; nunca número negativo
 *   (o bit 31 aceso é o caso que a aritmética com sinal quebraria).
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia a linha.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const C = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF', 'resources',
    'academia', 'ipv4', 'js', 'contas-ipv4.js'));

test('máscara e hosts por prefixo, tabela à mão', () => {
    const TABELA = [
        // prefixo, máscara, hosts utilizáveis (conta)
        [0, '0.0.0.0', 4294967294],          // 2^32 - 2
        [8, '255.0.0.0', 16777214],          // 2^24 - 2
        [16, '255.255.0.0', 65534],          // 2^16 - 2
        [23, '255.255.254.0', 510],          // 2^9 - 2
        [24, '255.255.255.0', 254],          // 2^8 - 2
        [25, '255.255.255.128', 126],
        [26, '255.255.255.192', 62],
        [27, '255.255.255.224', 30],
        [30, '255.255.255.252', 2],
        [31, '255.255.255.254', 2],          // RFC 3021: os dois endereços
        [32, '255.255.255.255', 1],
    ];
    for (const [p, mascara, hosts] of TABELA) {
        assert.equal(C.mascara(p), mascara, `/${p}`);
        assert.equal(C.hostsUtilizaveis(p), hosts, `/${p}`);
    }
});

test('rede, broadcast, primeiro e último host, tabela à mão', () => {
    const CASOS = [
        // ip, prefixo, rede, broadcast, primeiro, último
        [[192, 168, 10, 77], 27, '192.168.10.64', '192.168.10.95', '192.168.10.65', '192.168.10.94'], // blocos de 32: 64..95
        [[172, 16, 5, 130], 23, '172.16.4.0', '172.16.5.255', '172.16.4.1', '172.16.5.254'],       // terceiro octeto de 2 em 2
        [[10, 1, 2, 3], 8, '10.0.0.0', '10.255.255.255', '10.0.0.1', '10.255.255.254'],
        [[200, 1, 1, 5], 31, '200.1.1.4', '200.1.1.5', '200.1.1.4', '200.1.1.5'],               // /31: os dois
        [[200, 1, 1, 5], 32, '200.1.1.5', '200.1.1.5', '200.1.1.5', '200.1.1.5'],               // /32: ele mesmo
        [[0, 0, 0, 0], 0, '0.0.0.0', '255.255.255.255', '0.0.0.1', '255.255.255.254'],
        [[255, 255, 255, 255], 32, '255.255.255.255', '255.255.255.255', '255.255.255.255', '255.255.255.255'],
        [[200, 9, 9, 9], 1, '128.0.0.0', '255.255.255.255', '128.0.0.1', '255.255.255.254'],      // bit 31 aceso
    ];
    for (const [ip, p, rede, broadcast, primeiro, ultimo] of CASOS) {
        const r = C.analisar(ip, p);
        assert.deepEqual([r.rede, r.broadcast, r.primeiro, r.ultimo], [rede, broadcast, primeiro, ultimo],
            `${ip.join('.')}/${p}`);
    }
});

test('bits da máscara e do endereço', () => {
    assert.equal(C.bitsDaMascara(27).join(''), '1'.repeat(27) + '0'.repeat(5));
    assert.equal(C.bitsDaMascara(0).join(''), '0'.repeat(32));
    assert.equal(C.bitsDoEndereco([192, 168, 10, 77]).join(''),
        '11000000' + '10101000' + '00001010' + '01001101'); // 77 = 64 + 8 + 4 + 1
    assert.equal(C.paraInteiro([255, 255, 255, 255]), 4294967295);
    assert.ok(C.paraInteiro([200, 0, 0, 0]) > 0, 'nunca negativo');
});

test('quantas sub-redes e quantos hosts: arredonda para cima, nunca passa de /32', () => {
    assert.equal(C.prefixoParaSubredes(24, 8), 27);   // 2^3 = 8
    assert.equal(C.prefixoParaSubredes(24, 5), 27);   // 5 arredonda para 8
    assert.equal(C.prefixoParaSubredes(24, 1), 24);
    assert.equal(C.prefixoParaSubredes(30, 4), 32);
    assert.throws(() => C.prefixoParaSubredes(31, 4), RangeError);
    assert.equal(C.prefixoParaHosts(50), 26);          // /27 dá 30, /26 dá 62
    assert.equal(C.prefixoParaHosts(62), 26);
    assert.equal(C.prefixoParaHosts(63), 25);
    assert.equal(C.prefixoParaHosts(2), 30);           // rede local mínima é /30
    assert.equal(C.prefixoParaHosts(1), 30);
});

test('divisão em sub-redes: total, sequência e teto da lista', () => {
    const d = C.dividir([10, 0, 0, 0], 24, 27);
    assert.equal(d.total, 8);
    assert.deepEqual(d.sub.slice(0, 3).map((s) => s.rede), ['10.0.0.0/27', '10.0.0.32/27', '10.0.0.64/27']);
    assert.equal(d.sub[7].broadcast, '10.0.0.255');
    assert.equal(C.dividir([10, 0, 0, 99], 24, 28).total, 16);         // /28 num /24
    const grande = C.dividir([10, 0, 0, 0], 8, 30);
    assert.equal(grande.total, 4194304);                               // 2^22
    assert.equal(grande.sub.length, C.MAX_LISTA);                      // a lista corta, o total não
    assert.equal(C.dividir([10, 0, 0, 0], 24, 26, 2, 3).sub[0].rede, '10.0.0.192/26');
    assert.throws(() => C.dividir([10, 0, 0, 0], 24, 23), RangeError);
});

test('a correção nomeia o engano (tabela à mão)', () => {
    // /26: bloco de 64, 62 utilizáveis
    assert.equal(C.diagnosticarHosts(26, 62), null);
    assert.equal(C.diagnosticarHosts(26, 64), 'CONTOU_REDE_E_BROADCAST');
    assert.equal(C.diagnosticarHosts(26, 63), 'TIROU_SO_UM');
    assert.equal(C.diagnosticarHosts(26, 30), null);
    assert.equal(C.diagnosticarHosts(31, 4), null, 'no /31 não há engano reconhecido');
    // 192.168.10.77/27: rede .64, broadcast .95. Em /29 a rede é .72; o /28 vizinho daria .64
    assert.equal(C.diagnosticarRede([192, 168, 10, 77], 27, [192, 168, 10, 64]), null);
    assert.equal(C.diagnosticarRede([192, 168, 10, 77], 27, [192, 168, 10, 95]), 'E_O_BROADCAST');
    assert.equal(C.diagnosticarRede([192, 168, 10, 77], 27, [192, 168, 10, 77]), 'E_O_PROPRIO_ENDERECO');
    assert.equal(C.diagnosticarRede([192, 168, 10, 77], 29, [192, 168, 10, 64]), 'PREFIXO_VIZINHO'); // .77/29 = .72; /28 = .64
    assert.equal(C.diagnosticarRede([192, 168, 10, 77], 27, [192, 168, 10, 1]), null);
    // 5 sub-redes de um /24: 2^3 = 8 ⇒ /27
    assert.equal(C.diagnosticarPrefixoSubredes(24, 5, 27), null);
    assert.equal(C.diagnosticarPrefixoSubredes(24, 5, 29), 'SOMOU_A_QUANTIDADE');   // 24 + 5
    assert.equal(C.diagnosticarPrefixoSubredes(24, 5, 26), 'ARREDONDOU_PARA_BAIXO'); // 2^2 = 4 < 5
    assert.equal(C.diagnosticarPrefixoSubredes(24, 8, 26), 'ARREDONDOU_PARA_BAIXO'); // 4 < 8
    assert.equal(C.diagnosticarPrefixoSubredes(24, 8, 30), null);
});

test('fora do domínio é defeito de programa', () => {
    assert.throws(() => C.mascara(33), RangeError);
    assert.throws(() => C.analisar([256, 0, 0, 0], 8), RangeError);
    assert.throws(() => C.analisar([1, 2, 3], 8), RangeError);
});
