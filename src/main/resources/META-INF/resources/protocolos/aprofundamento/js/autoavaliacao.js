/**
 * Teste rápido dos aprofundamentos de protocolo (piloto de autoavaliação, proposta C03).
 *
 * PROPÓSITO DE NEGÓCIO: quem leu confere o que entendeu: escolhe uma alternativa, vê na hora se acertou, o porquê da
 *   escolhida e, se errou, o da certa. Depois, cada outra alternativa clicada mostra o próprio porquê. A resposta é
 *   contada no servidor no próprio clique (no D22, medido em 25/11/2026, o resumo mandado na saída da página se perdia
 *   quando a pessoa fechava o navegador).
 * INVARIANTES DO DOMÍNIO: vale a primeira escolha, e só ela é enviada (clique duplo ou clique em outra alternativa não
 *   conta de novo); nenhuma alternativa fica desabilitada, então não há botão que não faz nada; certo e errado aparecem
 *   em texto (o resultado diz qual é a certa), além da cor e do ícone; o resultado vai para a região role=status da
 *   pergunta, e a alternativa com o porquê à mostra passa a ser descrita por ele (aria-describedby); o envio leva só
 *   protocolo, pergunta e alternativa (o servidor confere o gabarito).
 * COMPORTAMENTO EM CASO DE FALHA: falha de rede ou recusa do servidor é ignorada em silêncio: a explicação já apareceu
 *   e a contagem não pode atrapalhar quem está estudando. O gabarito de avaliar() mora em src/test/js/web.
 */
(function (raiz) {
    'use strict';

    var ROTA = '/protocolos/api/autoavaliacao';

    /**
     * Resultado de uma escolha.
     *
     * PROPÓSITO DE NEGÓCIO: decide o que a tela mostra depois do clique.
     * INVARIANTES DO DOMÍNIO: "certa" é a posição da primeira alternativa marcada como correta; acertou só quando a
     *   escolhida é essa posição.
     * COMPORTAMENTO EM CASO DE FALHA: lista sem alternativa correta devolve certa -1 e acertou false; escolha fora da
     *   lista devolve acertou false.
     */
    function avaliar(alternativas, escolhida) {
        var certa = -1;
        for (var i = 0; i < alternativas.length; i++) {
            if (alternativas[i] && alternativas[i].correta === true) {
                certa = i;
                break;
            }
        }
        return { acertou: certa >= 0 && escolhida === certa, certa: certa };
    }

    /**
     * Conta a resposta no servidor.
     *
     * PROPÓSITO DE NEGÓCIO: saber se o teste é usado e onde as pessoas erram, sem depender da saída da página.
     * INVARIANTES DO DOMÍNIO: leva só protocolo, pergunta e alternativa; sai pelo fetch da página, que o
     *   web/js/csrf-client.js embrulha com o cabeçalho X-CSRF-Token (sem ele, o servidor recusa com 403).
     * COMPORTAMENTO EM CASO DE FALHA: erro de rede ou recusa é ignorado; sem fetch, não faz nada.
     */
    function enviar(protocolo, pergunta, alternativa) {
        try {
            raiz.fetch(ROTA, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ protocolo: protocolo, pergunta: pergunta, alternativa: alternativa })
            }).catch(function () { /* ignorado de propósito */ });
        } catch (e) {
            /* fetch indisponível: nada a fazer */
        }
    }

    /**
     * Troca o glifo do ícone de uma alternativa.
     *
     * PROPÓSITO DE NEGÓCIO: a seta da opção vira o sinal de certo ou de errado depois da resposta.
     * INVARIANTES DO DOMÍNIO: só o nome do glifo muda; o span continua aria-hidden e translate="no".
     * COMPORTAMENTO EM CASO DE FALHA: botão sem ícone fica como está.
     */
    function trocarIcone(botao, glifo) {
        var icone = botao.querySelector('.material-symbols-outlined');
        if (icone) {
            icone.textContent = glifo;
        }
    }

    /**
     * Marca a alternativa como certa ou errada.
     *
     * PROPÓSITO DE NEGÓCIO: a pessoa vê de relance qual alternativa é a certa e qual errou.
     * INVARIANTES DO DOMÍNIO: cor pela classe e ícone trocado; o texto do resultado diz o mesmo (nada só por cor).
     * COMPORTAMENTO EM CASO DE FALHA: botão sem ícone fica só com a classe; repetir a marcação não muda nada.
     */
    function marcar(botao, certa) {
        botao.classList.add(certa ? 'aval-certa' : 'aval-errada');
        trocarIcone(botao, certa ? 'check_circle' : 'cancel');
    }

    /**
     * Mostra o porquê de uma alternativa.
     *
     * PROPÓSITO DE NEGÓCIO: a explicação é o que ensina; ela aparece para a escolhida, para a certa e para cada
     *   alternativa clicada depois.
     * INVARIANTES DO DOMÍNIO: o porquê mostrado passa a descrever o botão (aria-describedby), para o leitor de tela
     *   achá-lo no foco; nada é escrito na tela além do texto que veio do servidor.
     * COMPORTAMENTO EM CASO DE FALHA: sem o parágrafo do porquê, não faz nada; repetir não muda nada.
     */
    function revelar(campo, botao, i) {
        var porque = campo.querySelector('.aval-porque[data-porque="' + i + '"]');
        if (porque) {
            porque.hidden = false;
            if (porque.id) {
                botao.setAttribute('aria-describedby', porque.id);
            }
        }
    }

    /**
     * Texto visível de uma alternativa.
     *
     * PROPÓSITO DE NEGÓCIO: o resultado de quem errou diz, por escrito, qual é a certa.
     * INVARIANTES DO DOMÍNIO: lê só o texto da alternativa (sem o nome do ícone).
     * COMPORTAMENTO EM CASO DE FALHA: botão ausente ou sem texto devolve ''.
     */
    function textoDe(botao) {
        var texto = botao ? botao.querySelector('.aval-texto') : null;
        return texto ? texto.textContent.trim() : '';
    }

    /**
     * Trata o clique numa alternativa.
     *
     * PROPÓSITO DE NEGÓCIO: a primeira escolha é a resposta: marca, explica, anuncia e conta. As seguintes só mostram
     *   o porquê da alternativa clicada, para a pessoa entender também as erradas.
     * INVARIANTES DO DOMÍNIO: um envio por pergunta por carregamento da página (clique duplo e cliques seguintes não
     *   contam); o resultado diz em texto se acertou e qual é a certa.
     * COMPORTAMENTO EM CASO DE FALHA: pergunta sem alternativa marcada como certa só diz "Não é essa." e envia mesmo
     *   assim (o servidor conta como erro; a guarda do conteúdo impede esse caso); o envio não bloqueia a tela.
     */
    function responder(protocolo, campo, botoes, escolhido) {
        var escolhida = Number(escolhido.getAttribute('data-alternativa'));
        var r = avaliar(botoes.map(function (b) {
            return { correta: b.getAttribute('data-correta') === 'true' };
        }), escolhida);
        marcar(escolhido, escolhida === r.certa);
        revelar(campo, escolhido, escolhida);
        if (campo.getAttribute('data-respondida') === 'sim') {
            return;   // depois da primeira escolha, o clique só mostra o porquê daquela alternativa
        }
        campo.setAttribute('data-respondida', 'sim');
        if (r.certa >= 0 && r.certa !== escolhida) {
            marcar(botoes[r.certa], true);
            revelar(campo, botoes[r.certa], r.certa);
        }
        var retorno = campo.querySelector('.aval-retorno');
        if (retorno) {
            var resultado = r.acertou ? 'Certo.'
                : (r.certa >= 0 ? 'Não é essa. A certa é: ' + textoDe(botoes[r.certa]) + '.' : 'Não é essa.');
            retorno.textContent = resultado + ' Clique nas outras alternativas para ver o porquê de cada uma.';
        }
        enviar(protocolo, Number(campo.getAttribute('data-pergunta')), escolhida);
    }

    /**
     * Liga o clique de cada alternativa de cada teste rápido da página.
     *
     * PROPÓSITO DE NEGÓCIO: o teste funciona em qualquer aprofundamento que tenha perguntas, sem código por protocolo.
     * INVARIANTES DO DOMÍNIO: o protocolo vem do data-autoavaliacao da seção; os botões nativos já atendem Enter e
     *   Espaço, então o teclado funciona sem tratamento à parte.
     * COMPORTAMENTO EM CASO DE FALHA: página sem a seção não liga nada.
     */
    function iniciar() {
        document.querySelectorAll('[data-autoavaliacao]').forEach(function (secao) {
            var protocolo = secao.getAttribute('data-autoavaliacao');
            secao.querySelectorAll('.aval-pergunta').forEach(function (campo) {
                var botoes = Array.prototype.slice.call(campo.querySelectorAll('.aval-alternativa'));
                botoes.forEach(function (botao) {
                    botao.addEventListener('click', function () { responder(protocolo, campo, botoes, botao); });
                });
            });
        });
    }

    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', iniciar);
    }
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = { avaliar: avaliar };
    }
}(typeof window !== 'undefined' ? window : this));
