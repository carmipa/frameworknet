/**
 * Contas do nível IPv4 da Academia: máscara, prefixo CIDR, rede, broadcast e divisão em sub-redes.
 *
 * PROPÓSITO DE NEGÓCIO: são as contas que a régua de 32 bits mostra, que o "Mexer" refaz a cada
 *   tecla e que o "Provar" usa para corrigir. Puras e sem tela, para serem conferidas contra um
 *   gabarito escrito à mão (A3). O nível IPv4 não importa as contas de Fundamentos nem o motor da
 *   Calculadora do site (duplicação consciente — D2/D3).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - endereço é inteiro sem sinal de 32 bits (aritmética com >>> 0, nunca número negativo);
 *   - prefixo de 0 a 32; máscara contígua (só a derivada do prefixo existe aqui);
 *   - hosts utilizáveis: 2^(32-p) - 2, exceto /31 = 2 (enlace ponto a ponto, RFC 3021) e /32 = 1;
 *   - primeiro/último host: no /31 são os dois endereços; no /32, o próprio endereço;
 *   - divisão em sub-redes arredonda para a potência de 2 de cima e nunca passa de /32;
 *   - para "quantos hosts por rede", o menor bloco de rede local é o /30 (o /31 é só para enlace);
 *   - toda lista tem teto (MAX_LISTA) — o total continua informado, a lista é que corta.
 *
 * COMPORTAMENTO EM CASO DE FALHA: argumento fora do domínio lança RangeError com o motivo — quem
 *   chama já passou pelo normalizador. Script clássico: window.AcademiaIpv4 no navegador,
 *   module.exports no Node.
 */
(function (raiz) {
    'use strict';

    var MAX_LISTA = 64;

    function exigirPrefixo(p) {
        if (!Number.isInteger(p) || p < 0 || p > 32) {
            throw new RangeError('prefixo fora de 0..32: ' + p);
        }
    }

    function exigirOctetos(octetos) {
        if (!Array.isArray(octetos) || octetos.length !== 4 || octetos.some(function (o) {
            return !Number.isInteger(o) || o < 0 || o > 255;
        })) {
            throw new RangeError('endereço IPv4 inválido: ' + octetos);
        }
    }

    /** Quatro octetos para inteiro sem sinal. */
    function paraInteiro(octetos) {
        exigirOctetos(octetos);
        return (((octetos[0] << 24) >>> 0) + (octetos[1] << 16) + (octetos[2] << 8) + octetos[3]) >>> 0;
    }

    /** Inteiro sem sinal para quatro octetos. */
    function paraOctetos(n) {
        var v = n >>> 0;
        return [(v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255];
    }

    function texto(n) {
        return paraOctetos(n).join('.');
    }

    /** Máscara do prefixo, como inteiro. */
    function mascaraInteira(p) {
        exigirPrefixo(p);
        return p === 0 ? 0 : (0xFFFFFFFF << (32 - p)) >>> 0;
    }

    /** Máscara do prefixo em notação decimal ("255.255.255.224"). */
    function mascara(p) {
        return texto(mascaraInteira(p));
    }

    /** Os 32 bits da máscara: 1 na parte de rede, 0 na de host. */
    function bitsDaMascara(p) {
        exigirPrefixo(p);
        var bits = [];
        for (var i = 0; i < 32; i++) {
            bits.push(i < p ? 1 : 0);
        }
        return bits;
    }

    /** Os 32 bits do endereço, do mais significativo ao menos. */
    function bitsDoEndereco(octetos) {
        var n = paraInteiro(octetos);
        var bits = [];
        for (var i = 31; i >= 0; i--) {
            bits.push((n >>> i) & 1);
        }
        return bits;
    }

    /** Quantidade total de endereços do bloco. */
    function tamanhoDoBloco(p) {
        exigirPrefixo(p);
        return Math.pow(2, 32 - p);
    }

    /** Hosts utilizáveis (RFC 3021 no /31; o /32 é um host só). */
    function hostsUtilizaveis(p) {
        exigirPrefixo(p);
        if (p === 32) {
            return 1;
        }
        if (p === 31) {
            return 2;
        }
        return tamanhoDoBloco(p) - 2;
    }

    /** Endereço de rede, broadcast, primeiro e último host de octetos/prefixo. */
    function analisar(octetos, p) {
        var ip = paraInteiro(octetos);
        var m = mascaraInteira(p);
        var rede = (ip & m) >>> 0;
        var broadcast = (rede | (~m >>> 0)) >>> 0;
        var primeiro;
        var ultimo;
        if (p === 32) {
            primeiro = rede;
            ultimo = rede;
        } else if (p === 31) {
            primeiro = rede;
            ultimo = broadcast;
        } else {
            primeiro = rede + 1;
            ultimo = broadcast - 1;
        }
        return {
            prefixo: p,
            mascara: texto(m),
            rede: texto(rede),
            broadcast: texto(broadcast),
            primeiro: texto(primeiro),
            ultimo: texto(ultimo),
            hosts: hostsUtilizaveis(p),
            bloco: tamanhoDoBloco(p)
        };
    }

    /** Prefixo que divide um bloco /p em pelo menos `quantas` sub-redes iguais. */
    function prefixoParaSubredes(p, quantas) {
        exigirPrefixo(p);
        if (!Number.isInteger(quantas) || quantas < 1) {
            throw new RangeError('quantidade de sub-redes inválida: ' + quantas);
        }
        var bits = 0;
        while (Math.pow(2, bits) < quantas) {
            bits++;
        }
        if (p + bits > 32) {
            throw new RangeError('não cabem ' + quantas + ' sub-redes num /' + p);
        }
        return p + bits;
    }

    /** Menor bloco de rede local (no máximo /30) com pelo menos `hosts` utilizáveis. */
    function prefixoParaHosts(hosts) {
        if (!Number.isInteger(hosts) || hosts < 1) {
            throw new RangeError('quantidade de hosts inválida: ' + hosts);
        }
        for (var p = 30; p >= 0; p--) {
            if (hostsUtilizaveis(p) >= hosts) {
                return p;
            }
        }
        throw new RangeError('nenhum bloco IPv4 comporta ' + hosts + ' hosts');
    }

    /**
     * Divide a rede de `octetos`/p em sub-redes /novo. Devolve o total e no máximo `limite`
     * sub-redes (rede/novo e broadcast de cada uma), a partir da `inicio`-ésima (0 = primeira).
     */
    function dividir(octetos, p, novo, limite, inicio) {
        exigirPrefixo(p);
        exigirPrefixo(novo);
        if (novo < p) {
            throw new RangeError('o novo prefixo /' + novo + ' é maior que o bloco /' + p);
        }
        var teto = Math.min(Number.isInteger(limite) && limite > 0 ? limite : MAX_LISTA, MAX_LISTA);
        var desde = Number.isInteger(inicio) && inicio > 0 ? inicio : 0;
        var base = (paraInteiro(octetos) & mascaraInteira(p)) >>> 0;
        var total = Math.pow(2, novo - p);
        var passo = tamanhoDoBloco(novo);
        var lista = [];
        for (var i = desde; i < total && lista.length < teto; i++) {
            var rede = base + i * passo;
            lista.push({ indice: i, rede: texto(rede) + '/' + novo, broadcast: texto(rede + passo - 1) });
        }
        return { total: total, prefixo: novo, sub: lista };
    }

    /**
     * Engano provável na contagem de hosts de um /p: 'CONTOU_REDE_E_BROADCAST' (não tirou os 2),
     * 'TIROU_SO_UM' ou null. No /31 e no /32 não há engano reconhecido.
     */
    function diagnosticarHosts(p, valor) {
        exigirPrefixo(p);
        if (p >= 31 || valor === hostsUtilizaveis(p)) {
            return null;
        }
        if (valor === tamanhoDoBloco(p)) {
            return 'CONTOU_REDE_E_BROADCAST';
        }
        if (valor === tamanhoDoBloco(p) - 1) {
            return 'TIROU_SO_UM';
        }
        return null;
    }

    /**
     * Engano provável ao responder o endereço de REDE de `octetos`/p com `resposta` (octetos):
     * 'E_O_BROADCAST', 'E_O_PROPRIO_ENDERECO', 'PREFIXO_VIZINHO' (a rede de /p±1) ou null.
     */
    function diagnosticarRede(octetos, p, resposta) {
        var r = analisar(octetos, p);
        var dada = paraOctetos(paraInteiro(resposta)).join('.');
        if (dada === r.rede) {
            return null;
        }
        if (dada === r.broadcast) {
            return 'E_O_BROADCAST';
        }
        if (dada === octetos.join('.')) {
            return 'E_O_PROPRIO_ENDERECO';
        }
        if ((p > 0 && dada === analisar(octetos, p - 1).rede) || (p < 32 && dada === analisar(octetos, p + 1).rede)) {
            return 'PREFIXO_VIZINHO';
        }
        return null;
    }

    /**
     * Engano provável ao calcular o prefixo de `quantas` sub-redes de um /p:
     * 'SOMOU_A_QUANTIDADE' (p + quantas), 'ARREDONDOU_PARA_BAIXO' (bits de menos) ou null.
     */
    function diagnosticarPrefixoSubredes(p, quantas, valor) {
        var certo = prefixoParaSubredes(p, quantas);
        if (valor === certo) {
            return null;
        }
        if (valor === p + quantas) {
            return 'SOMOU_A_QUANTIDADE';
        }
        if (valor === certo - 1 && Math.pow(2, certo - 1 - p) < quantas) {
            return 'ARREDONDOU_PARA_BAIXO';
        }
        return null;
    }

    /** Gerador determinístico (mulberry32): a mesma semente devolve a mesma sequência. */
    function sorteador(semente) {
        var estado = (semente >>> 0) || 1;
        return function () {
            estado = (estado + 0x6D2B79F5) >>> 0;
            var t = estado;
            t = Math.imul(t ^ (t >>> 15), t | 1);
            t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }

    function sortearInteiro(aleatorio, min, max) {
        return min + Math.floor(aleatorio() * (max - min + 1));
    }

    var api = {
        MAX_LISTA: MAX_LISTA,
        paraInteiro: paraInteiro,
        paraOctetos: paraOctetos,
        mascara: mascara,
        bitsDaMascara: bitsDaMascara,
        bitsDoEndereco: bitsDoEndereco,
        tamanhoDoBloco: tamanhoDoBloco,
        hostsUtilizaveis: hostsUtilizaveis,
        analisar: analisar,
        prefixoParaSubredes: prefixoParaSubredes,
        prefixoParaHosts: prefixoParaHosts,
        dividir: dividir,
        diagnosticarHosts: diagnosticarHosts,
        diagnosticarRede: diagnosticarRede,
        diagnosticarPrefixoSubredes: diagnosticarPrefixoSubredes,
        sorteador: sorteador,
        sortearInteiro: sortearInteiro
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        raiz.AcademiaIpv4 = api;
    }
}(typeof window !== 'undefined' ? window : this));
