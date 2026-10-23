/**
 * Normalizador de entrada da Academia.
 *
 * PROPÓSITO DE NEGÓCIO: o aluno digita números, bits, hexadecimais e endereços do jeito que
 *   aprendeu — com espaço, vírgula do teclado numérico ABNT2, prefixo 0x, zeros à esquerda. A
 *   lição precisa entender o que ele quis dizer, mostrar como entendeu ("interpretado como …")
 *   e só então corrigir. Resposta certa escrita de outro jeito nunca pode virar "errado".
 *
 * INVARIANTES DO DOMÍNIO (INV-ACAD-006):
 *   - todo resultado é { ok: true, valor, eco } ou { ok: false, classe, mensagem };
 *   - "vazio", "incompleto" e "formato" NÃO contam tentativa — só "faixa" e o valor válido
 *     chegam à correção, porque digitar pela metade não é errar;
 *   - nenhuma função lança exceção para entrada do aluno; o texto nunca é avaliado como código;
 *   - o que é aceito é decidido aqui, uma vez, e as lições só consomem.
 *
 * COMPORTAMENTO EM CASO DE FALHA: entrada nula, numérica ou objeto vira texto antes de ler; o
 *   que não puder ser interpretado volta como { ok: false } com a classe e uma mensagem em
 *   português para a tela. Script clássico: expõe window.AcademiaNormalizador no navegador e
 *   module.exports no Node (os testes de gabarito rodam com `node --test`).
 */
(function (raiz) {
    'use strict';

    var TETO_TEXTO = 64;

    function texto(entrada) {
        if (entrada === null || entrada === undefined) {
            return '';
        }
        return String(entrada).replace(/ /g, ' ').trim().slice(0, TETO_TEXTO);
    }

    function falha(classe, mensagem) {
        return { ok: false, classe: classe, mensagem: mensagem };
    }

    function sucesso(valor, eco) {
        return { ok: true, valor: valor, eco: eco };
    }

    /**
     * Chave para reconhecer a MESMA resposta repetida (auditoria ACAD-06).
     *
     * PROPÓSITO DE NEGÓCIO: Enter duas vezes na mesma resposta errada não pode contar duas tentativas —
     *   na segunda o gabarito aparece. A lição comparava só valor interpretável; a resposta "fora da
     *   faixa" (que conta tentativa) voltava como null e a repetição contava de novo.
     * INVARIANTES: só existe chave para o que conta tentativa; valor interpretável compara pela chave
     *   do valor; fora da faixa compara pelo texto digitado sem espaços e sem caixa.
     * FALHA: o que não conta tentativa devolve null (nunca bloqueia a próxima resposta).
     */
    function chaveRepeticao(resultado, chaveDoValor, entrada) {
        if (!contaTentativa(resultado)) {
            return null;
        }
        if (resultado.ok) {
            return 'v:' + String(chaveDoValor);
        }
        return 'f:' + texto(entrada).replace(/\s+/g, '').toLowerCase();
    }

    /** Conta tentativa? Só valor interpretável ou fora da faixa — nunca digitação incompleta. */
    function contaTentativa(resultado) {
        return Boolean(resultado) && (resultado.ok || resultado.classe === 'faixa');
    }

    /**
     * Inteiro decimal sem sinal. Espaços somem; zero à esquerda é aceito ("007" = 7).
     * Sinal, ponto e vírgula são formato — em 0..255 não existe separador de milhar.
     */
    function inteiro(entrada, opcoes) {
        var min = opcoes && typeof opcoes.min === 'number' ? opcoes.min : 0;
        var max = opcoes && typeof opcoes.max === 'number' ? opcoes.max : 255;
        var limpo = texto(entrada).replace(/\s+/g, '');
        if (limpo === '') {
            return falha('vazio', 'Digite um número.');
        }
        if (!/^\d+$/.test(limpo)) {
            return falha('formato', 'Use só algarismos de 0 a 9.');
        }
        var valor = parseInt(limpo, 10);
        if (valor < min || valor > max) {
            return falha('faixa', 'O número precisa estar entre ' + min + ' e ' + max + '.');
        }
        return sucesso(valor, 'interpretado como ' + valor);
    }

    function agrupar4(bits) {
        return bits.replace(/(.{4})(?=.)/g, '$1 ');
    }

    /**
     * Binário com até `bits` dígitos. Aceita espaço, sublinhado e o prefixo 0b. Menos dígitos que
     * o tamanho é número válido ("101" = 5) e o eco mostra os zeros à esquerda que ele implica.
     */
    function binario(entrada, opcoes) {
        var tamanho = opcoes && typeof opcoes.bits === 'number' ? opcoes.bits : 8;
        var limpo = texto(entrada).replace(/[\s_]+/g, '');
        if (/^0b/i.test(limpo)) {
            limpo = limpo.slice(2);
        }
        if (limpo === '') {
            return falha('vazio', 'Digite os bits (0 ou 1).');
        }
        if (!/^[01]+$/.test(limpo)) {
            return falha('formato', 'Binário só tem 0 e 1.');
        }
        // ACAD-07: zero à esquerda não muda o valor — "000100010" é 34 e cabe em 8 bits. Só passa da faixa
        // quando os bits que SOBRAM depois de tirar os zeros da frente não cabem.
        var significativos = limpo.replace(/^0+/, '') || '0';
        if (significativos.length > tamanho) {
            return falha('faixa', 'Use no máximo ' + tamanho + ' bits.');
        }
        var completo = significativos.padStart(tamanho, '0');
        var valor = parseInt(completo, 2);
        return sucesso(valor, 'interpretado como ' + agrupar4(completo) + ' (' + valor + ')');
    }

    /**
     * Hexadecimal, maiúsculo ou minúsculo, com prefixo 0x ou sufixo h opcionais e espaços.
     */
    function hexadecimal(entrada, opcoes) {
        var max = opcoes && typeof opcoes.max === 'number' ? opcoes.max : 0xFF;
        var limpo = texto(entrada).replace(/\s+/g, '');
        if (/^0x/i.test(limpo)) {
            limpo = limpo.slice(2);
        } else if (/h$/i.test(limpo) && limpo.length > 1) {
            limpo = limpo.slice(0, -1);
        }
        if (limpo === '') {
            return falha('vazio', 'Digite os dígitos hexadecimais.');
        }
        if (!/^[0-9a-f]+$/i.test(limpo)) {
            return falha('formato', 'Hexadecimal usa 0 a 9 e A a F.');
        }
        var valor = parseInt(limpo, 16);
        if (valor > max) {
            return falha('faixa', 'O valor passa de 0x' + max.toString(16).toUpperCase() + '.');
        }
        var digitos = Math.max(2, max.toString(16).length);
        return sucesso(valor, 'interpretado como 0x' + valor.toString(16).toUpperCase().padStart(digitos, '0')
            + ' (' + valor + ')');
    }

    /**
     * Endereço IPv4. A vírgula vira ponto (teclado numérico ABNT2) e espaços somem; octeto com
     * zeros à esquerda é aceito ("000"). Menos de quatro octetos é "incompleto", não erro.
     */
    function ipv4(entrada) {
        var limpo = texto(entrada).replace(/\s+/g, '').replace(/,/g, '.');
        if (limpo === '') {
            return falha('vazio', 'Digite o endereço, como 192.168.0.1.');
        }
        if (!/^[0-9.]+$/.test(limpo)) {
            return falha('formato', 'Endereço IPv4 só tem algarismos e pontos.');
        }
        var partes = limpo.split('.');
        if (partes.length < 4 || partes[partes.length - 1] === '') {
            if (partes.length <= 4) {
                return falha('incompleto', 'Faltam octetos: o IPv4 tem quatro números.');
            }
        }
        if (partes.length !== 4 || partes.some(function (p) { return p === '' || p.length > 3; })) {
            return falha('formato', 'Use quatro números separados por ponto.');
        }
        var octetos = partes.map(function (p) { return parseInt(p, 10); });
        for (var i = 0; i < 4; i++) {
            if (octetos[i] > 255) {
                return falha('faixa', 'O octeto ' + (i + 1) + ' passa de 255.');
            }
        }
        return sucesso(octetos, 'interpretado como ' + octetos.join('.'));
    }

    /** Prefixo CIDR: "/24" ou "24", de 0 a 32. */
    function prefixo(entrada) {
        var limpo = texto(entrada).replace(/\s+/g, '');
        if (limpo.charAt(0) === '/') {
            limpo = limpo.slice(1);
        }
        if (limpo === '') {
            return falha('vazio', 'Digite o prefixo, como /24.');
        }
        if (!/^\d+$/.test(limpo)) {
            return falha('formato', 'O prefixo é um número de 0 a 32.');
        }
        var valor = parseInt(limpo, 10);
        if (valor > 32) {
            return falha('faixa', 'O prefixo vai de /0 a /32.');
        }
        return sucesso(valor, 'interpretado como /' + valor);
    }

    /**
     * Máscara de sub-rede em notação decimal. Precisa ser contígua: todos os bits 1 antes de
     * qualquer bit 0 ("255.255.0.255" não é máscara).
     */
    function mascara(entrada) {
        var ip = ipv4(entrada);
        if (!ip.ok) {
            return ip;
        }
        var bits = ip.valor.map(function (o) { return o.toString(2).padStart(8, '0'); }).join('');
        if (/01/.test(bits)) {
            return falha('formato', 'Máscara tem todos os bits 1 antes dos bits 0.');
        }
        var prefixoBits = bits.indexOf('0') === -1 ? 32 : bits.indexOf('0');
        return sucesso(ip.valor, 'interpretado como ' + ip.valor.join('.') + ' (/' + prefixoBits + ')');
    }

    var api = {
        inteiro: inteiro,
        binario: binario,
        hexadecimal: hexadecimal,
        ipv4: ipv4,
        prefixo: prefixo,
        mascara: mascara,
        contaTentativa: contaTentativa,
        chaveRepeticao: chaveRepeticao
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        raiz.AcademiaNormalizador = api;
    }
}(typeof window !== 'undefined' ? window : this));
