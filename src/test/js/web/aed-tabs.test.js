/**
 * Gabarito do link direto para uma aba (web/js/aed-tabs.js, função abaPedida).
 *
 * PROPÓSITO DE NEGÓCIO: o link "Inspetor TLS" de /certificados (e os de encapsulamento para /trafego) abre na
 *   aba certa com ?aba=<id>, em vez de cair na primeira aba (proposta D09). O esperado vem do requisito, escrito à
 *   mão caso a caso (A3): a lista branca é o conjunto de abas do grupo, e nada fora dela é aceito.
 * INVARIANTES DO DOMÍNIO: só sai um id do grupo; maiúsculas e espaços nas pontas não importam e o id devolvido é o
 *   do grupo; prefixo de um id não é o id (fronteira, A1); com `aba` repetida vale a primeira; sem `aba` ou com
 *   valor fora da lista, null (vale a aba inicial).
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia o caso.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { abaPedida } = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF',
    'resources', 'web', 'js', 'aed-tabs.js'));

const SEGURANCA = ['acl', 'tls', 'estado', 'handshake', 'fluxo', 'topologia', 'canvas'];

test('aba da lista abre direto', () => {
    assert.equal(abaPedida(SEGURANCA, '?aba=tls'), 'tls');
    assert.equal(abaPedida(SEGURANCA, '?aba=canvas'), 'canvas');
});

test('maiúsculas e espaços nas pontas não importam; sai o id do grupo', () => {
    assert.equal(abaPedida(SEGURANCA, '?aba=TLS'), 'tls');
    assert.equal(abaPedida(SEGURANCA, '?aba=%20tls%20'), 'tls');
    assert.equal(abaPedida(['Mixed'], '?aba=mixed'), 'Mixed');
});

test('fora da lista vale a aba inicial (null)', () => {
    assert.equal(abaPedida(SEGURANCA, '?aba=inexistente'), null);
    assert.equal(abaPedida(SEGURANCA, '?aba=<script>alert(1)</script>'), null);
    assert.equal(abaPedida(SEGURANCA, '?aba=__proto__'), null);
    assert.equal(abaPedida(SEGURANCA, '?aba=constructor'), null);
});

test('fronteira: prefixo ou extensão de um id não é o id', () => {
    assert.equal(abaPedida(SEGURANCA, '?aba=tl'), null);
    assert.equal(abaPedida(SEGURANCA, '?aba=tlsx'), null);
});

test('sem aba, aba vazia ou busca ausente: null', () => {
    assert.equal(abaPedida(SEGURANCA, ''), null);
    assert.equal(abaPedida(SEGURANCA, '?tab=tls'), null);
    assert.equal(abaPedida(SEGURANCA, '?aba='), null);
    assert.equal(abaPedida(SEGURANCA, undefined), null);
    assert.equal(abaPedida([], '?aba=tls'), null);
});

test('aba repetida: vale a primeira; outros parâmetros não atrapalham', () => {
    assert.equal(abaPedida(SEGURANCA, '?aba=tls&aba=acl'), 'tls');
    assert.equal(abaPedida(SEGURANCA, '?x=1&aba=fluxo&y=2'), 'fluxo');
});
