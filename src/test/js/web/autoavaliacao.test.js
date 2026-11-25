/**
 * Gabarito do teste rápido dos aprofundamentos (protocolos/aprofundamento/js/autoavaliacao.js, função avaliar).
 *
 * PROPÓSITO DE NEGÓCIO: depois do clique, a tela marca a certa e diz se a pessoa acertou (proposta C03). Se avaliar()
 *   errar a posição da certa, o teste ensina a resposta errada. O esperado é escrito à mão, caso a caso, a partir do
 *   requisito (A3): a certa é a alternativa marcada como correta, e acertou só quando a escolhida é ela.
 * INVARIANTES DO DOMÍNIO: a posição da certa não importa (primeira, meio, última); só o booleano true marca a certa
 *   (o texto "true" não marca); sem certa, ninguém acerta, nem quem "escolhe" -1 (fronteira, A1); com duas marcadas
 *   vale a primeira, como no servidor (ProtocoloAprofundamento.Pergunta.indiceCorreta).
 * COMPORTAMENTO EM CASO DE FALHA: `node --test` sai diferente de zero e nomeia o caso.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { avaliar } = require(path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF',
    'resources', 'protocolos', 'aprofundamento', 'js', 'autoavaliacao.js'));

const certaNa = (posicao, total = 4) =>
    Array.from({ length: total }, (_, i) => ({ correta: i === posicao }));

test('escolher a certa acerta, em qualquer posição', () => {
    assert.deepEqual(avaliar(certaNa(0), 0), { acertou: true, certa: 0 });
    assert.deepEqual(avaliar(certaNa(2), 2), { acertou: true, certa: 2 });
    assert.deepEqual(avaliar(certaNa(3), 3), { acertou: true, certa: 3 });
});

test('escolher outra não acerta, e a certa continua apontada', () => {
    assert.deepEqual(avaliar(certaNa(2), 0), { acertou: false, certa: 2 });
    assert.deepEqual(avaliar(certaNa(0), 3), { acertou: false, certa: 0 });
});

test('escolha fora da lista não acerta', () => {
    assert.deepEqual(avaliar(certaNa(1), 4), { acertou: false, certa: 1 });
    assert.deepEqual(avaliar(certaNa(1), -1), { acertou: false, certa: 1 });
    assert.deepEqual(avaliar(certaNa(1), NaN), { acertou: false, certa: 1 });
});

test('sem alternativa certa, ninguém acerta (nem quem escolhe -1)', () => {
    const nenhuma = [{ correta: false }, { correta: false }];
    assert.deepEqual(avaliar(nenhuma, 0), { acertou: false, certa: -1 });
    assert.deepEqual(avaliar(nenhuma, -1), { acertou: false, certa: -1 });
    assert.deepEqual(avaliar([], -1), { acertou: false, certa: -1 });
});

test('só o booleano true marca a certa; item nulo é pulado', () => {
    assert.deepEqual(avaliar([{ correta: 'true' }, { correta: 1 }], 0), { acertou: false, certa: -1 });
    assert.deepEqual(avaliar([null, { correta: true }], 1), { acertou: true, certa: 1 });
});

test('com duas marcadas vale a primeira, como no servidor', () => {
    const duas = [{ correta: false }, { correta: true }, { correta: true }];
    assert.deepEqual(avaliar(duas, 1), { acertou: true, certa: 1 });
    assert.deepEqual(avaliar(duas, 2), { acertou: false, certa: 1 });
});
