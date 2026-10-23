/*
 * Interativos do módulo Criptografia: playground de hash/codificação (WebCrypto,
 * 100% client-side) e comparador de força de algoritmo.
 *
 * Propósito: deixar o aluno VER o efeito avalanche e o veredito de cada algoritmo
 * sem enviar nada ao servidor (respeita o teto da VPS: nenhum cálculo no back-end).
 *
 * Comportamento em caso de falha: navegador sem WebCrypto (contexto inseguro)
 * mostra aviso no lugar do hash, sem quebrar a página.
 */
(function () {
    "use strict";

    var enc = new TextEncoder();

    function hex(buffer) {
        var bytes = new Uint8Array(buffer);
        var out = "";
        for (var i = 0; i < bytes.length; i++) {
            out += bytes[i].toString(16).padStart(2, "0");
        }
        return out;
    }

    function base64Utf8(texto) {
        try {
            return btoa(unescape(encodeURIComponent(texto)));
        } catch (e) {
            return "(não foi possível codificar)";
        }
    }

    function hexUtf8(texto) {
        return hex(enc.encode(texto).buffer);
    }

    async function digest(alg, texto) {
        if (!window.crypto || !window.crypto.subtle) {
            return "(WebCrypto indisponível — abra via HTTPS ou localhost)";
        }
        try {
            var buf = await window.crypto.subtle.digest(alg, enc.encode(texto));
            return hex(buf);
        } catch (e) {
            return "(falha ao calcular)";
        }
    }

    async function atualizarHash() {
        var texto = document.getElementById("cripto-in").value;
        var linhas = document.querySelectorAll("#cripto-out tr");
        if (texto === "") {
            // Entrada vazia (ex.: após "Limpar"): zera as células de forma síncrona,
            // sem disparar digest assíncrono que repovoaria depois da limpeza.
            for (var k = 0; k < linhas.length; k++) {
                var vazia = linhas[k].querySelector(".cripto-val");
                if (vazia) { vazia.textContent = ""; }
            }
            return;
        }
        for (var i = 0; i < linhas.length; i++) {
            var linha = linhas[i];
            var alg = linha.getAttribute("data-alg");
            var celula = linha.querySelector(".cripto-val");
            var valor;
            if (alg === "base64") {
                valor = base64Utf8(texto);
            } else if (alg === "hex") {
                valor = hexUtf8(texto);
            } else {
                valor = await digest(alg, texto);
            }
            celula.textContent = valor;
        }
    }

    var FORCA = {
        aes: ["success", "Seguro", "AES-256-GCM é o padrão AEAD atual: rápido, com aceleração de hardware e integridade embutida."],
        chacha: ["success", "Seguro", "ChaCha20-Poly1305 é AEAD moderno, ótimo em dispositivos sem aceleração AES."],
        "3des": ["warning", "Fraco", "3DES tem só 112 bits efetivos e blocos de 64 bits (ataques Sweet32); descontinuado pelo NIST."],
        des: ["danger", "Quebrado", "DES tem chave de 56 bits, quebrável por força bruta em horas. Nunca use."],
        rc4: ["danger", "Quebrado", "RC4 tem vieses no keystream que permitem recuperar texto claro. Proibido em TLS."],
        rsa2048: ["success", "Seguro", "RSA 2048 é o mínimo aceito hoje; prefira 3072+ para longo prazo ou migre para ECC."],
        rsa1024: ["danger", "Quebrado", "RSA 1024 é considerado inseguro: fatorável por atacantes com recursos. Use 2048+."],
        ecdsa: ["success", "Seguro", "ECDSA P-256 dá segurança equivalente a RSA 3072 com chaves bem menores — cuidado com reuso de nonce."],
        ed25519: ["success", "Seguro", "Ed25519 é assinatura moderna, determinística (imune a falha de nonce) e rápida."],
        sha256: ["success", "Seguro", "SHA-256 (família SHA-2) é seguro para integridade e assinaturas. Para SENHA, use argon2/bcrypt."],
        sha1: ["danger", "Quebrado", "SHA-1 tem colisões práticas (SHAttered, 2017). Impróprio para assinaturas e certificados."],
        md5: ["danger", "Quebrado", "MD5 tem colisões triviais. Só serve para checksum não-adversarial; nunca para segurança."],
        bcrypt: ["success", "Seguro", "bcrypt/Argon2 são hashes de SENHA com custo ajustável (memória/tempo), resistentes a GPU. Nunca use SHA rápido para senha."]
    };

    function atualizarForca() {
        var sel = document.getElementById("forca-alg");
        var out = document.getElementById("forca-out");
        var dados = FORCA[sel.value] || ["info", "—", ""];
        var cor = dados[0];
        out.className = "p-3 rounded border border-" + cor + " text-" + cor + " cripto-forca-box";
        out.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true" translate="no">' +
            (cor === "success" ? "verified" : cor === "warning" ? "warning" : cor === "danger" ? "gpp_bad" : "info") +
            '</span> <strong>' + dados[1] + '</strong> — <span class="text-light">' + dados[2] + '</span>';
    }

    document.addEventListener("DOMContentLoaded", function () {
        var input = document.getElementById("cripto-in");
        if (input) {
            input.addEventListener("input", atualizarHash);
            atualizarHash();
        }
        var sel = document.getElementById("forca-alg");
        if (sel) {
            sel.addEventListener("change", atualizarForca);
            atualizarForca();
        }
    });
})();
