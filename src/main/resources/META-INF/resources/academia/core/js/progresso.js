/**
 * Progresso das lições guardado no próprio navegador (antes de existir conta no site).
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê o que já fez — acertos, tentativas e lições concluídas — sem
 *   precisar de login. E vê, sempre, ONDE isso está guardado: só neste navegador. Computador de
 *   laboratório que limpa tudo ao desligar apaga o progresso, e o aluno precisa saber disso antes,
 *   não depois.
 *
 * INVARIANTES DO DOMÍNIO:
 *   - uma chave por lição ("academia.progresso.v1.<licaoId>"), com estado absoluto
 *     { acertos, tentativas, concluidaEm } — nunca incremento cego vindo de fora;
 *   - tentativas >= acertos >= 0, inteiros;
 *   - a lição conclui ao chegar a ACERTOS_PARA_CONCLUIR acertos, e concluidaEm é gravado uma vez;
 *   - nada pessoal é guardado (nem o que foi digitado), só contagens;
 *   - a sonda de armazenamento roda uma vez por página e os ouvintes do evento storage só reagem às
 *     chaves de progresso — senão duas abas abertas se repintam uma à outra sem fim;
 *   - a semente da pergunta vive em sessionStorage, para o reload da tradução não trocar a pergunta;
 *   - os níveis já desbloqueados ficam numa chave só ("academia.niveis.v1.desbloqueados"), para
 *     uma lição nova num nível já concluído nunca trancar de volta o nível seguinte; "apagar o
 *     progresso" apaga também essa chave.
 *
 * COMPORTAMENTO EM CASO DE FALHA: armazenamento bloqueado ou cheio (navegação privada, política
 *   da escola) não quebra a lição: o progresso vive na memória da página enquanto ela estiver aberta
 *   (antes ler devolvia sempre vazio e o placar travava em 0 — a lição nunca concluía, auditoria
 *   ACAD-08), gravar devolve false e o selo diz "o progresso não pode ser guardado neste navegador".
 *   Valor corrompido na chave é ignorado e tratado como estado vazio.
 */
(function (raiz) {
    'use strict';

    var PREFIXO = 'academia.progresso.v1.';
    var PREFIXO_SEMENTE = 'academia.semente.v1.';
    var CHAVE_DESBLOQUEADOS = 'academia.niveis.v1.desbloqueados';
    var ID_NIVEL = /^[a-z][a-z0-9]*$/;
    var ACERTOS_PARA_CONCLUIR = 3;

    /** Progresso da página quando o navegador não deixa guardar (ACAD-08). */
    var memoria = {};

    function copia(estado) {
        return { acertos: estado.acertos, tentativas: estado.tentativas, concluidaEm: estado.concluidaEm };
    }

    function vazio() {
        return { acertos: 0, tentativas: 0, concluidaEm: null };
    }

    /*
     * A sonda de "dá para guardar?" grava e apaga uma chave de teste. Ela roda UMA vez por página:
     * cada gravação dispara o evento storage nas outras abas, e com a sonda a cada leitura duas abas
     * da Academia abertas se repintavam uma à outra sem fim (medido: o navegador travou e caiu).
     */
    var sondados = {};

    function armazenamento(tipo) {
        if (Object.prototype.hasOwnProperty.call(sondados, tipo)) {
            return sondados[tipo];
        }
        var resultado = null;
        try {
            var s = raiz[tipo];
            var teste = '__academia_teste__';
            s.setItem(teste, '1');
            s.removeItem(teste);
            resultado = s;
        } catch (e) {
            resultado = null;
        }
        sondados[tipo] = resultado;
        return resultado;
    }

    /** O evento storage é de progresso da Academia? (null = o armazenamento inteiro foi limpo.) */
    function eventoDeProgresso(ev) {
        return Boolean(ev) && (ev.key === null || ev.key.indexOf(PREFIXO) === 0 || ev.key === CHAVE_DESBLOQUEADOS);
    }

    function valido(estado) {
        return estado && Number.isInteger(estado.acertos) && Number.isInteger(estado.tentativas)
            && estado.acertos >= 0 && estado.tentativas >= estado.acertos
            && (estado.concluidaEm === null || typeof estado.concluidaEm === 'string');
    }

    function ler(licaoId) {
        if (Object.prototype.hasOwnProperty.call(memoria, licaoId)) {
            return copia(memoria[licaoId]);
        }
        var s = armazenamento('localStorage');
        if (!s) {
            return vazio();
        }
        try {
            var bruto = s.getItem(PREFIXO + licaoId);
            var estado = bruto ? JSON.parse(bruto) : null;
            return valido(estado) ? estado : vazio();
        } catch (e) {
            return vazio();
        }
    }

    function gravar(licaoId, estado) {
        if (!valido(estado)) {
            return false;
        }
        var s = armazenamento('localStorage');
        if (s) {
            try {
                s.setItem(PREFIXO + licaoId, JSON.stringify(estado));
                delete memoria[licaoId];
                return true;
            } catch (e) {
                /* cheio ou bloqueado no meio: cai para a memória da página */
            }
        }
        memoria[licaoId] = copia(estado);
        return false;
    }

    /**
     * Conta uma tentativa (já validada pelo normalizador) e devolve o novo estado, com
     * `acabouDeConcluir` true só na tentativa que concluiu a lição.
     */
    function registrarTentativa(licaoId, acertou) {
        var estado = ler(licaoId);
        estado.tentativas += 1;
        if (acertou) {
            estado.acertos += 1;
        }
        var acabouDeConcluir = false;
        if (!estado.concluidaEm && estado.acertos >= ACERTOS_PARA_CONCLUIR) {
            estado.concluidaEm = new Date().toISOString();
            acabouDeConcluir = true;
        }
        var guardado = gravar(licaoId, estado);
        avisarMudanca();
        return { estado: estado, guardado: guardado, acabouDeConcluir: acabouDeConcluir };
    }

    /** Avisa a própria página (navegação, abas) que o progresso mudou; outras abas recebem o storage. */
    function avisarMudanca() {
        try {
            raiz.dispatchEvent(new Event('academia:progresso'));
        } catch (e) { /* navegador antigo sem Event: a tela se acerta no próximo carregamento */ }
    }

    function disponivel() {
        return armazenamento('localStorage') !== null;
    }

    /** Ids das lições com progresso guardado neste navegador. */
    function licoesGuardadas() {
        var s = armazenamento('localStorage');
        var ids = [];
        if (!s) {
            return ids;
        }
        for (var i = 0; i < s.length; i++) {
            var chave = s.key(i);
            if (chave && chave.indexOf(PREFIXO) === 0) {
                ids.push(chave.slice(PREFIXO.length));
            }
        }
        return ids;
    }

    /** Ids dos níveis já desbloqueados neste navegador (valor corrompido vale como nenhum). */
    function niveisDesbloqueados() {
        var s = armazenamento('localStorage');
        if (!s) {
            return [];
        }
        try {
            var lista = JSON.parse(s.getItem(CHAVE_DESBLOQUEADOS) || '[]');
            return Array.isArray(lista) ? lista.filter(function (id) { return typeof id === 'string' && ID_NIVEL.test(id); }) : [];
        } catch (e) {
            return [];
        }
    }

    /** Lembra que o nível foi desbloqueado; devolve false se o navegador não deixou guardar. */
    function registrarDesbloqueio(nivelId) {
        var s = armazenamento('localStorage');
        if (!s || !ID_NIVEL.test(String(nivelId))) {
            return false;
        }
        var lista = niveisDesbloqueados();
        if (lista.indexOf(nivelId) >= 0) {
            return true;
        }
        lista.push(nivelId);
        try {
            s.setItem(CHAVE_DESBLOQUEADOS, JSON.stringify(lista));
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Apaga o progresso de todas as lições deste navegador e os níveis desbloqueados; devolve
     * quantas lições foram apagadas.
     */
    function apagarTudo() {
        var s = armazenamento('localStorage');
        if (!s) {
            return 0;
        }
        var ids = licoesGuardadas();
        ids.forEach(function (id) {
            try { s.removeItem(PREFIXO + id); } catch (e) { /* segue com as outras */ }
        });
        try { s.removeItem(CHAVE_DESBLOQUEADOS); } catch (e) { /* idem */ }
        avisarMudanca();
        return ids.length;
    }

    /** Semente da pergunta atual da lição; nova a cada avanço, estável no reload. */
    function semente(licaoId) {
        var s = armazenamento('sessionStorage');
        var chave = PREFIXO_SEMENTE + licaoId;
        var atual = s ? parseInt(s.getItem(chave) || '', 10) : NaN;
        if (!Number.isInteger(atual) || atual <= 0) {
            atual = Math.floor(Math.random() * 2147483646) + 1;
            if (s) {
                try { s.setItem(chave, String(atual)); } catch (e) { /* sem sessão: semente só desta página */ }
            }
        }
        return atual;
    }

    function avancarSemente(licaoId) {
        var s = armazenamento('sessionStorage');
        var proxima = (semente(licaoId) % 2147483646) + 1;
        if (s) {
            try { s.setItem(PREFIXO_SEMENTE + licaoId, String(proxima)); } catch (e) { /* idem */ }
        }
        return proxima;
    }

    /**
     * Guarda o que está digitado na resposta enquanto a pergunta for a mesma (auditoria ACAD-24).
     *
     * PROPÓSITO DE NEGÓCIO: trocar o idioma recarrega a página; a semente já mantinha a pergunta, mas a
     *   resposta digitada sumia.
     * INVARIANTES: o rascunho vive em sessionStorage, ligado à semente da pergunta — pergunta nova
     *   (semente avançou) ignora o rascunho velho; restaura depois que a lição montou a pergunta.
     * FALHA: sem sessionStorage não há rascunho, e a lição funciona igual.
     */
    function ligarRascunho(licaoId, campo) {
        var s = armazenamento('sessionStorage');
        if (!s || !campo) {
            return;
        }
        var chave = 'academia.rascunho.v1.' + licaoId;
        raiz.setTimeout(function () {
            try {
                var r = JSON.parse(s.getItem(chave) || 'null');
                if (r && r.semente === semente(licaoId) && typeof r.texto === 'string' && campo.value === '') {
                    campo.value = r.texto.slice(0, 64);
                    campo.dispatchEvent(new Event('input'));
                }
            } catch (e) { /* rascunho corrompido: ignora */ }
        }, 0);
        campo.addEventListener('input', function () {
            try {
                s.setItem(chave, JSON.stringify({ semente: semente(licaoId), texto: String(campo.value).slice(0, 64) }));
            } catch (e) { /* sem espaço: segue sem rascunho */ }
        });
    }

    /** Preenche o selo de uma lição: concluída/andamento e onde está guardado. */
    function pintarSelo(el, licaoId) {
        if (!el) {
            return;
        }
        el.textContent = '';
        var icone = document.createElement('span');
        icone.className = 'material-symbols-outlined';
        icone.setAttribute('aria-hidden', 'true');
        icone.setAttribute('translate', 'no');
        var texto = document.createElement('span');
        if (!disponivel()) {
            icone.textContent = 'block';
            texto.textContent = 'o progresso não pode ser guardado neste navegador';
            el.classList.remove('concluida');
        } else {
            var estado = ler(licaoId);
            if (estado.concluidaEm) {
                icone.textContent = 'task_alt';
                texto.textContent = 'concluída — salvo só neste navegador';
                el.classList.add('concluida');
            } else if (estado.tentativas > 0) {
                icone.textContent = 'pending';
                texto.textContent = estado.acertos + ' de ' + ACERTOS_PARA_CONCLUIR + ' acertos — salvo só neste navegador';
                el.classList.remove('concluida');
            } else {
                icone.textContent = 'radio_button_unchecked';
                texto.textContent = 'não iniciada';
                el.classList.remove('concluida');
            }
        }
        el.appendChild(icone);
        el.appendChild(texto);
    }

    raiz.AcademiaProgresso = {
        ACERTOS_PARA_CONCLUIR: ACERTOS_PARA_CONCLUIR,
        ler: ler,
        registrarTentativa: registrarTentativa,
        disponivel: disponivel,
        licoesGuardadas: licoesGuardadas,
        apagarTudo: apagarTudo,
        niveisDesbloqueados: niveisDesbloqueados,
        eventoDeProgresso: eventoDeProgresso,
        registrarDesbloqueio: registrarDesbloqueio,
        semente: semente,
        avancarSemente: avancarSemente,
        pintarSelo: pintarSelo,
        ligarRascunho: ligarRascunho
    };
}(window));
