/**
 * Contas do nível Fundamentos da Academia: binário, hexadecimal e encapsulamento em camadas.
 *
 * PROPÓSITO DE NEGÓCIO: são as contas que a lição mostra na animação, refaz a cada tecla no
 *   "Mexer" e usa para corrigir o "Provar". Ficam aqui, puras e sem tela, para serem conferidas
 *   contra um gabarito calculado à mão (A3) — a lição nunca chama o motor das ferramentas do site
 *   (D2/D3) e o nível IPv4 não importa este arquivo (duplicação consciente).
 *
 * INVARIANTES DO DOMÍNIO:
 *   - byte = inteiro de 0 a 255, oito bits, o mais significativo primeiro (pesos 128 … 1);
 *   - hexadecimal sempre em MAIÚSCULAS, dois dígitos por byte;
 *   - encapsulamento Ethernet II + IPv4 + TCP/UDP sem opções: TCP 20, UDP 8, IPv4 20, Ethernet
 *     14 de cabeçalho + 4 de FCS, e a carga do quadro tem no mínimo 46 bytes (o resto é
 *     preenchimento); acima de MAX_MENSAGEM bytes a conta recusa, porque fragmentação não é
 *     assunto deste nível;
 *   - sorteio por semente: a mesma semente dá a mesma pergunta (sobrevive ao reload da tradução).
 *
 * COMPORTAMENTO EM CASO DE FALHA: argumento fora do domínio lança RangeError com o motivo — quem
 *   chama já passou pelo normalizador, então chegar aqui fora da faixa é defeito de programa, não
 *   do aluno. Script clássico: window.AcademiaFundamentos no navegador, module.exports no Node.
 */
(function (raiz) {
    'use strict';

    var PESOS = [128, 64, 32, 16, 8, 4, 2, 1];
    var CABECALHO = { TCP: 20, UDP: 8, IPV4: 20, ETHERNET: 14, FCS: 4 };
    var CARGA_MINIMA_ETHERNET = 46;
    var MAX_MENSAGEM = 1000;

    function exigirByte(n) {
        if (!Number.isInteger(n) || n < 0 || n > 255) {
            throw new RangeError('byte fora de 0..255: ' + n);
        }
    }

    /** Os oito bits do byte, do mais significativo (peso 128) ao menos (peso 1). */
    function paraBits(n) {
        exigirByte(n);
        return PESOS.map(function (peso) { return (n & peso) ? 1 : 0; });
    }

    /** Soma dos pesos dos bits acesos. */
    function deBits(bits) {
        if (!Array.isArray(bits) || bits.length !== 8) {
            throw new RangeError('são oito bits');
        }
        return bits.reduce(function (soma, bit, i) {
            if (bit !== 0 && bit !== 1) {
                throw new RangeError('bit inválido na posição ' + i);
            }
            return soma + bit * PESOS[i];
        }, 0);
    }

    /** Posições (0 = peso 128) em que a resposta difere do gabarito. */
    function bitsDiferentes(resposta, gabarito) {
        var a = paraBits(resposta);
        var b = paraBits(gabarito);
        var diferentes = [];
        for (var i = 0; i < 8; i++) {
            if (a[i] !== b[i]) {
                diferentes.push(i);
            }
        }
        return diferentes;
    }

    /** Byte em dois dígitos hexadecimais maiúsculos. */
    function paraHex(n) {
        exigirByte(n);
        return n.toString(16).toUpperCase().padStart(2, '0');
    }

    /** As duas metades de quatro bits: alto (16 em 16) e baixo (1 em 1). */
    function nibbles(n) {
        exigirByte(n);
        return { alto: n >> 4, baixo: n & 0x0F };
    }

    /** Quais nibbles a resposta errou: 'alto', 'baixo', os dois ou nenhum. */
    function nibblesDiferentes(resposta, gabarito) {
        var a = nibbles(resposta);
        var b = nibbles(gabarito);
        var erros = [];
        if (a.alto !== b.alto) {
            erros.push('alto');
        }
        if (a.baixo !== b.baixo) {
            erros.push('baixo');
        }
        return erros;
    }

    /** Tamanho em bytes do texto codificado em UTF-8 ("ação" = 6). */
    function bytesUtf8(texto) {
        return new TextEncoder().encode(String(texto)).length;
    }

    /**
     * A mensagem descendo a pilha: quanto cada camada acrescenta e o tamanho de cada unidade.
     *
     * @param {number} carga bytes da mensagem da aplicação
     * @param {'TCP'|'UDP'} transporte
     */
    function encapsular(carga, transporte) {
        if (!Number.isInteger(carga) || carga < 0 || carga > MAX_MENSAGEM) {
            throw new RangeError('mensagem fora de 0..' + MAX_MENSAGEM + ' bytes: ' + carga);
        }
        if (transporte !== 'TCP' && transporte !== 'UDP') {
            throw new RangeError('transporte desconhecido: ' + transporte);
        }
        var segmento = carga + CABECALHO[transporte];
        var pacote = segmento + CABECALHO.IPV4;
        var preenchimento = Math.max(0, CARGA_MINIMA_ETHERNET - pacote);
        var quadro = CABECALHO.ETHERNET + pacote + preenchimento + CABECALHO.FCS;
        return {
            carga: carga,
            transporte: transporte,
            cabecalhoTransporte: CABECALHO[transporte],
            segmento: segmento,
            pacote: pacote,
            preenchimento: preenchimento,
            quadro: quadro
        };
    }

    /**
     * O engano mais provável quando o aluno responde `valor` para o tamanho do quadro de `r`:
     * 'PACOTE', 'SEGMENTO', 'SEM_FCS', 'SEM_PREENCHIMENTO', 'TRANSPORTE_TROCADO' ou null.
     * A ordem decide empates (ex.: preenchimento de 4 = FCS de 4 ⇒ 'SEM_FCS').
     */
    function diagnosticarQuadro(r, valor) {
        if (valor === r.quadro) {
            return null;
        }
        if (valor === r.pacote) {
            return 'PACOTE';
        }
        if (valor === r.segmento) {
            return 'SEGMENTO';
        }
        if (valor === r.quadro - CABECALHO.FCS) {
            return 'SEM_FCS';
        }
        if (r.preenchimento > 0 && valor === r.quadro - r.preenchimento) {
            return 'SEM_PREENCHIMENTO';
        }
        var comOutroTransporte = encapsular(r.carga, r.transporte === 'TCP' ? 'UDP' : 'TCP').quadro;
        if (valor === comOutroTransporte) {
            return 'TRANSPORTE_TROCADO';
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

    /** Inteiro sorteado em [min, max], inclusive. */
    function sortearInteiro(aleatorio, min, max) {
        return min + Math.floor(aleatorio() * (max - min + 1));
    }

    var api = {
        PESOS: PESOS.slice(),
        CABECALHO: Object.freeze(Object.assign({}, CABECALHO)),
        CARGA_MINIMA_ETHERNET: CARGA_MINIMA_ETHERNET,
        MAX_MENSAGEM: MAX_MENSAGEM,
        paraBits: paraBits,
        deBits: deBits,
        bitsDiferentes: bitsDiferentes,
        paraHex: paraHex,
        nibbles: nibbles,
        nibblesDiferentes: nibblesDiferentes,
        bytesUtf8: bytesUtf8,
        encapsular: encapsular,
        diagnosticarQuadro: diagnosticarQuadro,
        sorteador: sorteador,
        sortearInteiro: sortearInteiro
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        raiz.AcademiaFundamentos = api;
    }
}(typeof window !== 'undefined' ? window : this));
