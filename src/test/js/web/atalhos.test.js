/**
 * Gabarito dos atalhos de teclado (web/js/atalhos.js).
 *
 * PROPÓSITO DE NEGÓCIO: F1 abre a documentação e Esc duas vezes volta à Início (pedido de Paulo, 25/11/2026),
 *   sem roubar o Esc que fecha dica, menu e diálogo. O esperado vem do requisito e da regra desenhada para ele
 *   (Esc reflexo não navega; a tecla de fechar continua fechando) — escrito à mão, caso a caso (A3).
 * INVARIANTES DO DOMÍNIO: F1 sem modificador sempre abre a documentação (em nova aba, então vale até com foco
 *   num campo); o Esc só conta quando ninguém o tratou, nada estava aberto, o foco não está num campo e não se
 *   está na Início; o segundo Esc em até JANELA_MS navega, e o limite exato ainda vale (A1); outra tecla zera.
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia o caso.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { decidir, JANELA_MS } = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF',
    'resources', 'web', 'js', 'atalhos.js'));

function caso(mudancas) {
    return Object.assign({
        tecla: 'Escape', comModificador: false, jaTratada: false, emCampo: false,
        algoAberto: false, naInicio: false, agora: 10000, ultimoEsc: null
    }, mudancas);
}

test('F1 abre a documentação', () => {
    assert.deepEqual(decidir(caso({ tecla: 'F1' })), { acao: 'documentacao', ultimoEsc: null });
});

test('F1 com foco num campo também abre (nova aba: o que foi digitado não se perde)', () => {
    assert.equal(decidir(caso({ tecla: 'F1', emCampo: true })).acao, 'documentacao');
});

test('F1 com modificador ou já tratado não age', () => {
    assert.equal(decidir(caso({ tecla: 'F1', comModificador: true })).acao, 'nada');
    assert.equal(decidir(caso({ tecla: 'F1', jaTratada: true })).acao, 'nada');
});

test('primeiro Esc só avisa e começa a contar', () => {
    assert.deepEqual(decidir(caso({})), { acao: 'avisar', ultimoEsc: 10000 });
});

test('segundo Esc dentro da janela volta à Início', () => {
    assert.deepEqual(decidir(caso({ ultimoEsc: 9000 })), { acao: 'inicio', ultimoEsc: null });
});

test('fronteira: exatamente JANELA_MS ainda vale; 1 ms depois só avisa de novo', () => {
    assert.equal(decidir(caso({ agora: 10000 + JANELA_MS, ultimoEsc: 10000 })).acao, 'inicio');
    assert.deepEqual(decidir(caso({ agora: 10001 + JANELA_MS, ultimoEsc: 10000 })),
        { acao: 'avisar', ultimoEsc: 10001 + JANELA_MS });
});

test('Esc que fechou algo aberto não conta, nem como primeiro', () => {
    assert.deepEqual(decidir(caso({ algoAberto: true })), { acao: 'nada', ultimoEsc: null });
    assert.deepEqual(decidir(caso({ algoAberto: true, ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('Esc com foco num campo de digitação não conta', () => {
    assert.deepEqual(decidir(caso({ emCampo: true, ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('Esc já tratado por outro componente não conta', () => {
    assert.deepEqual(decidir(caso({ jaTratada: true, ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('na Início, Esc não faz nada', () => {
    assert.deepEqual(decidir(caso({ naInicio: true, ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('outra tecla no meio zera a sequência', () => {
    assert.deepEqual(decidir(caso({ tecla: 'a', ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
    assert.deepEqual(decidir(caso({ tecla: 'Home', ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('Esc com modificador não conta', () => {
    assert.deepEqual(decidir(caso({ comModificador: true, ultimoEsc: 9500 })), { acao: 'nada', ultimoEsc: null });
});

test('relógio que voltou não navega: recomeça a contagem', () => {
    assert.deepEqual(decidir(caso({ agora: 10000, ultimoEsc: 12000 })), { acao: 'avisar', ultimoEsc: 10000 });
});
