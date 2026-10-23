package org.framework.net.analiseDidatica.infrastructure.historico;

import jakarta.enterprise.context.RequestScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * PROPÓSITO DE NEGÓCIO: identifica o navegador do visitante para que o histórico da Análise seja só
 * dele (decisão de Paulo, 2026-08-04). Antes, /history era um balde global e público com o IP real e a
 * geolocalização de quem consultou.
 *
 * INVARIANTES DO DOMÍNIO: o cookie leva SÓ um identificador aleatório de 128 bits (sem dado pessoal);
 * valor fora do formato é descartado e substituído (nunca aceito como chave); o histórico guarda o
 * SHA-256 do identificador, nunca o identificador — quem ler o arquivo não consegue se passar pelo
 * visitante. O cookie só é emitido quando a requisição realmente usou a sessão.
 *
 * COMPORTAMENTO EM CASO DE FALHA: não lança; SHA-256 ausente na JVM é impossível no Java 25 e vira
 * {@link IllegalStateException}.
 */
@RequestScoped
public class SessaoHistorico {

    public static final String COOKIE = "fnet_hist";

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{22}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private String valor;
    private boolean nova;

    /** Chamado pelo filtro com o cookie recebido; valor malformado é ignorado. */
    public void receber(String cookie) {
        if (cookie != null && FORMATO.matcher(cookie).matches()) {
            this.valor = cookie;
        }
    }

    /** Chave de partição do histórico (hash do identificador); cria a sessão se ainda não houver. */
    public String chave() {
        if (valor == null) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            nova = true;
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(h, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    public boolean isNova() {
        return nova;
    }

    public String valorCookie() {
        return valor;
    }
}
