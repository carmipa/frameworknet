/**
 * Sinais que a página de uma lição manda ao servidor: erro de JavaScript e resumo da visita.
 *
 * PROPÓSITO DE NEGÓCIO: descobrir que uma lição quebrou no navegador de alguém (o site não tinha
 *   nenhum jeito de saber — F5) e quanto as lições são usadas, sem identificar ninguém.
 *
 * INVARIANTES DO DOMÍNIO (INV-ACAD-005):
 *   - esquema fechado: { tipo: 'erro', licaoId, tipoErro, mensagem } e
 *     { tipo: 'visita', licaoId, segundos, interacoes, concluiu } — nunca o que o aluno digitou;
 *   - erro de outro domínio ("Script error.", sem detalhe) não é enviado: não diz nada e só gasta
 *     o orçamento;
 *   - no máximo MAX_ERROS erros por página, sem repetir a mesma mensagem;
 *   - o resumo da visita sai UMA vez, com o estado ACUMULADO: ao fechar/sair da página (pagehide)
 *     ou depois de OCULTA_MS seguidos com a aba escondida (visita abandonada). Antes saía no primeiro
 *     "esconder a aba": trocar de aba e voltar para concluir mandava segundos 0 e concluiu false, e
 *     nada mais (auditoria ACAD-03). Voltar para a aba antes do prazo cancela o envio;
 *   - por fetch com keepalive (sendBeacon não leva o cabeçalho de CSRF e receberia 403 — R7);
 *   - o servidor faz o saneamento e as faixas; aqui só se corta o tamanho.
 *
 * COMPORTAMENTO EM CASO DE FALHA: rede fora, 4xx ou 5xx são ignorados em silêncio de propósito —
 *   telemetria não pode atrapalhar a lição, e um erro ao reportar erro viraria laço.
 */
(function (raiz) {
    'use strict';

    var ROTA = '/academia/api/eventos';
    var MAX_ERROS = 3;
    /** Aba escondida por este tempo conta como visita encerrada. */
    var OCULTA_MS = 5 * 60 * 1000;
    var timerOculta = null;

    var licaoAtual = null;
    var inicio = Date.now();
    var interacoes = 0;
    var concluiuNestaVisita = false;
    var errosEnviados = 0;
    var mensagensVistas = {};
    var visitaEnviada = false;

    function enviar(corpo) {
        try {
            raiz.fetch(ROTA, {
                method: 'POST',
                keepalive: true,
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(corpo)
            }).catch(function () { /* ignorado de propósito */ });
        } catch (e) {
            /* fetch indisponível: nada a fazer */
        }
    }

    function reportarErro(tipoErro, mensagem, deOutraOrigem) {
        if (!licaoAtual || deOutraOrigem || errosEnviados >= MAX_ERROS) {
            return;
        }
        var texto = String(mensagem || '').slice(0, 300);
        if (texto === '' || texto === 'Script error.' || mensagensVistas[texto]) {
            return;
        }
        mensagensVistas[texto] = true;
        errosEnviados += 1;
        enviar({ tipo: 'erro', licaoId: licaoAtual, tipoErro: tipoErro, mensagem: texto });
    }

    function enviarVisita() {
        if (!licaoAtual || visitaEnviada) {
            return;
        }
        visitaEnviada = true;
        enviar({
            tipo: 'visita',
            licaoId: licaoAtual,
            segundos: Math.max(0, Math.round((Date.now() - inicio) / 1000)),
            interacoes: interacoes,
            concluiu: concluiuNestaVisita
        });
    }

    function iniciar(licaoId) {
        licaoAtual = licaoId;
        raiz.addEventListener('error', function (ev) {
            var arquivo = ev && ev.filename ? String(ev.filename) : '';
            var deOutraOrigem = arquivo !== '' && arquivo.indexOf(raiz.location.origin) !== 0;
            reportarErro('erro', ev && ev.message, deOutraOrigem);
        });
        raiz.addEventListener('unhandledrejection', function (ev) {
            var motivo = ev && ev.reason;
            reportarErro('promessa', motivo && motivo.message ? motivo.message : String(motivo), false);
        });
        raiz.addEventListener('pagehide', enviarVisita);
        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'hidden') {
                if (timerOculta === null) {
                    timerOculta = raiz.setTimeout(enviarVisita, OCULTA_MS);
                }
            } else if (timerOculta !== null) {
                raiz.clearTimeout(timerOculta);
                timerOculta = null;
            }
        });
    }

    raiz.AcademiaSinais = {
        /** Só para teste: troca o prazo da aba oculta. */
        _definirPrazoOculta: function (ms) { OCULTA_MS = ms; },
        iniciar: iniciar,
        interagiu: function () { interacoes += 1; },
        concluiu: function () { concluiuNestaVisita = true; }
    };
}(window));
