/**
 * Gabarito do normalizador de entrada da Academia (INV-ACAD-006).
 *
 * PROPÓSITO DE NEGÓCIO: provar que resposta certa escrita de outro jeito é aceita e que resposta
 *   errada não passa — o aluno honesto nunca pode ser marcado errado pelo formato.
 * INVARIANTES DO DOMÍNIO: os valores esperados foram escritos à mão a partir da definição de
 *   cada formato (A3), nunca gerados pelo próprio normalizador; cada par aceita/recusa carrega o
 *   mesmo sinal superficial (A1: "/24" × "/33", "255.255.255.000" × "255.255.0.255").
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia a entrada.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const N = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF', 'resources',
    'academia', 'core', 'js', 'normalizador.js'));

function aceita(resultado, valor) {
    assert.equal(resultado.ok, true, JSON.stringify(resultado));
    assert.deepEqual(resultado.valor, valor);
}

function recusa(resultado, classe) {
    assert.equal(resultado.ok, false, JSON.stringify(resultado));
    assert.equal(resultado.classe, classe);
    assert.ok(resultado.mensagem.length > 0);
}

test('inteiro: aceita espaço e zero à esquerda; recusa sinal, ponto e fora da faixa', () => {
    aceita(N.inteiro('  42 '), 42);
    aceita(N.inteiro('007'), 7);
    aceita(N.inteiro('0'), 0);
    aceita(N.inteiro('255'), 255);
    recusa(N.inteiro('256'), 'faixa');
    recusa(N.inteiro('-1'), 'formato');
    recusa(N.inteiro('1.0'), 'formato');
    recusa(N.inteiro(''), 'vazio');
    recusa(N.inteiro(null), 'vazio');
});

test('binário: espaço, sublinhado e 0b; menos de 8 bits é número; 9 bits passa da faixa', () => {
    aceita(N.binario('1010 1010'), 170);
    aceita(N.binario('1010_1010'), 170);
    aceita(N.binario('0b101'), 5);
    aceita(N.binario('101'), 5);
    assert.equal(N.binario('101').eco, 'interpretado como 0000 0101 (5)');
    aceita(N.binario('11111111'), 255);
    recusa(N.binario('111111111'), 'faixa');
    // ACAD-07 (fronteira, A1): 9 dígitos com zero à frente é legítimo; 9 dígitos começando por 1 não cabe.
    aceita(N.binario('000100010'), 34);
    aceita(N.binario('0000000000'), 0);
    recusa(N.binario('100000000'), 'faixa');
    recusa(N.binario('102'), 'formato');
    recusa(N.binario(' '), 'vazio');
});

test('hexadecimal: maiúscula, minúscula, 0x, sufixo h e espaços valem o mesmo', () => {
    for (const entrada of ['2A', '2a', '0x2A', '0X2a', '2Ah', ' 2 A ']) {
        aceita(N.hexadecimal(entrada), 42);
    }
    assert.equal(N.hexadecimal('2a').eco, 'interpretado como 0x2A (42)');
    aceita(N.hexadecimal('FF'), 255);
    recusa(N.hexadecimal('100'), 'faixa');
    recusa(N.hexadecimal('2G'), 'formato');
    recusa(N.hexadecimal('0x'), 'vazio');
});

test('IPv4: vírgula do teclado ABNT2 vira ponto; octeto 000 vale; falta de octeto é incompleto', () => {
    aceita(N.ipv4('192,168,0,1'), [192, 168, 0, 1]);
    aceita(N.ipv4(' 192.168.0.1 '), [192, 168, 0, 1]);
    aceita(N.ipv4('010.000.000.001'), [10, 0, 0, 1]);
    aceita(N.ipv4('0.0.0.0'), [0, 0, 0, 0]);
    aceita(N.ipv4('255.255.255.255'), [255, 255, 255, 255]);
    recusa(N.ipv4('256.1.1.1'), 'faixa');
    recusa(N.ipv4('192.168.0'), 'incompleto');
    recusa(N.ipv4('192.168.0.'), 'incompleto');
    recusa(N.ipv4('192..0.1'), 'formato');
    recusa(N.ipv4('1.2.3.4.5'), 'formato');
    recusa(N.ipv4('1.2.3.a'), 'formato');
});

test('prefixo: /24 e 24 são o mesmo; fronteiras /0, /31 e /32; /33 passa da faixa', () => {
    aceita(N.prefixo('/24'), 24);
    aceita(N.prefixo('24'), 24);
    aceita(N.prefixo('/0'), 0);
    aceita(N.prefixo('/31'), 31);
    aceita(N.prefixo('/32'), 32);
    recusa(N.prefixo('/33'), 'faixa');
    recusa(N.prefixo('/2x'), 'formato');
});

test('máscara: contígua passa (inclusive com 000); bit 1 depois de bit 0 é recusado', () => {
    aceita(N.mascara('255.255.255.000'), [255, 255, 255, 0]);
    assert.equal(N.mascara('255.255.255.0').eco, 'interpretado como 255.255.255.0 (/24)');
    assert.equal(N.mascara('255.255.255.255').eco, 'interpretado como 255.255.255.255 (/32)');
    assert.equal(N.mascara('0.0.0.0').eco, 'interpretado como 0.0.0.0 (/0)');
    assert.equal(N.mascara('255.255.255.192').eco, 'interpretado como 255.255.255.192 (/26)');
    recusa(N.mascara('255.255.0.255'), 'formato');
    recusa(N.mascara('255.0.255.0'), 'formato');
});

test('só valor interpretável ou fora da faixa conta tentativa', () => {
    assert.equal(N.contaTentativa(N.inteiro('')), false);
    assert.equal(N.contaTentativa(N.ipv4('192.168.0')), false);
    assert.equal(N.contaTentativa(N.inteiro('4x')), false);
    assert.equal(N.contaTentativa(N.inteiro('300')), true);
    assert.equal(N.contaTentativa(N.inteiro('30')), true);
});

test('texto absurdo não lança e não passa do teto', () => {
    const enorme = '1'.repeat(10_000);
    assert.doesNotThrow(() => N.binario(enorme));
    recusa(N.binario(enorme), 'faixa');
    recusa(N.inteiro({ toString() { return 'x'; } }), 'formato');
});

test('ACAD-06: a mesma resposta repetida tem a mesma chave, inclusive fora da faixa', () => {
    const faixa = N.inteiro('999', { min: 0, max: 255 });
    assert.equal(faixa.classe, 'faixa');
    const a = N.chaveRepeticao(faixa, null, '999');
    assert.equal(a, N.chaveRepeticao(N.inteiro(' 9 99', { min: 0, max: 255 }), null, ' 9 99'),
        'espaço não faz resposta nova');
    assert.notEqual(a, N.chaveRepeticao(N.inteiro('998', { min: 0, max: 255 }), null, '998'),
        'outra resposta fora da faixa conta');
    const ok = N.inteiro('7', { min: 0, max: 255 });
    assert.equal(N.chaveRepeticao(ok, ok.valor, '7'), N.chaveRepeticao(N.inteiro('007'), 7, '007'),
        'mesmo valor escrito de outro jeito é a mesma resposta');
    assert.equal(N.chaveRepeticao(N.inteiro(''), null, ''), null, 'vazio não conta e não bloqueia');
    assert.equal(N.chaveRepeticao(N.inteiro('1.5'), null, '1.5'), null, 'formato não conta');
});
