/**
 * Gabarito do desbloqueio dos níveis (as abas da trilha).
 *
 * PROPÓSITO DE NEGÓCIO: se a conta erra, o aluno que concluiu um nível continua trancado fora do
 *   seguinte — ou pula a ordem sem ter feito nada. O esperado vem da regra pedida por Paulo
 *   (01/10/2026): "as abas só desbloqueiam depois que a pessoa termina uma trilha, e aí ficam
 *   desbloqueadas para essa pessoa" — escrito à mão, caso a caso (A3).
 * INVARIANTES DO DOMÍNIO: primeiro nível sempre liberado; desbloqueio não regride; "em breve" não
 *   quebra a corrente; sem armazenamento, tudo liberado; o requisito aponta um nível que dá para
 *   fazer agora.
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia o caso.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { calcularEstados } = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF',
    'resources', 'academia', 'core', 'js', 'niveis.js'));

const MAPA = [
    { id: 'fundamentos', aberto: true, licoes: ['fundamentos.binario', 'fundamentos.hexadecimal'] },
    { id: 'ipv4', aberto: true, licoes: ['ipv4.mascara', 'ipv4.subredes'] },
    { id: 'transporte', aberto: true, licoes: ['transporte.aperto'] },
    { id: 'enlace', aberto: false, licoes: [] },
];

function rodar(concluidas, desbloqueados = [], guarda = true, mapa = MAPA) {
    const feitas = new Set(concluidas);
    return calcularEstados(mapa, (id) => feitas.has(id), desbloqueados, guarda)
        .map((e) => [e.id, e.estado, e.requisito, e.novo]);
}

test('aluno novo: só o primeiro nível aberto, os outros apontam para ele', () => {
    assert.deepEqual(rodar([]), [
        ['fundamentos', 'liberado', null, false],
        ['ipv4', 'bloqueado', 'fundamentos', false],
        ['transporte', 'bloqueado', 'fundamentos', false],   // aponta o que dá para fazer AGORA, não o ipv4
        ['enlace', 'em-breve', null, false],
    ]);
});

test('A1: metade do nível feita não desbloqueia; o nível inteiro desbloqueia e é registrado', () => {
    assert.deepEqual(rodar(['fundamentos.binario'])[1], ['ipv4', 'bloqueado', 'fundamentos', false]);
    assert.deepEqual(rodar(['fundamentos.binario', 'fundamentos.hexadecimal']).slice(0, 3), [
        ['fundamentos', 'concluido', null, false],
        ['ipv4', 'liberado', null, true],                    // desbloqueou agora: registrar
        ['transporte', 'bloqueado', 'ipv4', false],
    ]);
});

test('lição concluída por link direto num nível bloqueado não fura a ordem', () => {
    // fez o ipv4 inteiro sem fazer fundamentos: ipv4 continua bloqueado e transporte também
    assert.deepEqual(rodar(['ipv4.mascara', 'ipv4.subredes']).slice(1, 3), [
        ['ipv4', 'bloqueado', 'fundamentos', false],
        ['transporte', 'bloqueado', 'fundamentos', false],
    ]);
});

test('desbloqueio não regride: lição nova num nível já concluído não tranca o seguinte', () => {
    // fundamentos ganhou uma lição que o aluno não fez, mas o ipv4 já tinha sido desbloqueado
    assert.deepEqual(rodar(['fundamentos.binario'], ['ipv4']).slice(0, 2), [
        ['fundamentos', 'liberado', null, false],
        ['ipv4', 'liberado', null, false],                   // já registrado: não é novo
    ]);
});

test('"em breve" no meio não quebra a corrente', () => {
    const mapa = [MAPA[0], { id: 'enlace', aberto: false, licoes: [] }, MAPA[1]];
    assert.deepEqual(rodar(['fundamentos.binario', 'fundamentos.hexadecimal'], [], true, mapa), [
        ['fundamentos', 'concluido', null, false],
        ['enlace', 'em-breve', null, false],
        ['ipv4', 'liberado', null, true],
    ]);
});

test('navegador sem armazenamento: tudo liberado, nada registrado (senão nunca desbloquearia)', () => {
    assert.deepEqual(rodar([], [], false).map((e) => e[1]), ['liberado', 'liberado', 'liberado', 'em-breve']);
    assert.ok(rodar([], [], false).every((e) => e[3] === false));
});

test('trilha inteira concluída', () => {
    const tudo = MAPA.flatMap((n) => n.licoes);
    assert.deepEqual(rodar(tudo, ['ipv4', 'transporte']).map((e) => e[1]), ['concluido', 'concluido', 'concluido', 'em-breve']);
});
