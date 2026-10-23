package org.framework.net.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Sessão de acesso à Telemetria, carregada num cookie assinado.
 *
 * <p><b>Propósito de negócio:</b> depois que o GitHub confirma quem é o usuário,
 * alguma coisa precisa lembrar disso entre uma página e outra. Este serviço emite
 * e valida esse comprovante. Não há sessão em memória do servidor de propósito: a
 * aplicação roda em container que é recriado a cada deploy, e sessão em memória
 * derrubaria o login a cada publicação.</p>
 *
 * <p><b>Invariantes do domínio:</b> o cookie é <b>assinado</b> com HMAC-SHA256
 * derivado do {@code CSRF_SECRET} — o cliente enxerga o login, mas não consegue
 * forjar outro sem a chave, que nunca sai do servidor. Carrega expiração dentro
 * da assinatura, então adiantar o relógio do cliente não estende a sessão. Nasce
 * {@code HttpOnly} (JavaScript não lê), {@code SameSite=Lax} (não viaja em
 * requisição de outro site) e {@code Secure} conforme a configuração de ambiente.
 * O comprovante identifica; ele não guarda token do GitHub, que é usado uma vez e
 * descartado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> cookie ausente, malformado, expirado
 * ou com assinatura inválida devolve {@link #loginDaSessao(String)} vazio — falha
 * fechada, sem distinguir os casos para quem estiver sondando.</p>
 */
@ApplicationScoped
public class SessaoTelemetriaService {

    public static final String COOKIE_NAME = "TELEMETRIA_SESSAO";

    /** Quatro horas: suficiente para uma sessão de trabalho, curto para um cookie roubado. */
    private static final long TTL_SEGUNDOS = 4 * 60 * 60;

    /** Como a origem do acesso aparece na telemetria. */
    public static final String ORIGEM_GITHUB = "github";
    public static final String ORIGEM_CONTINGENCIA = "contingencia";

    /**
     * Papel do portador da sessão.
     *
     * <p><b>Propósito de negócio:</b> o dono precisa exportar, limpar console e
     * abrir a pasta de logs; um usuário autenticado do sistema pode <em>ver</em> o
     * painel e os gráficos, e nada além disso. Separar os dois no próprio cookie
     * evita que "quem entrou" e "o que pode fazer" virem a mesma coisa — que é
     * como um leitor acaba com poder de apagar dado.</p>
     */
    public static final String PAPEL_DONO = "dono";
    public static final String PAPEL_LEITOR = "leitor";

    @ConfigProperty(name = "framework.security.csrf-secret", defaultValue = "framework-net-dev-csrf-secret")
    String segredo;

    @ConfigProperty(name = "framework.security.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    private final SecureRandom random = new SecureRandom();

    /**
     * Emite o cookie de sessão.
     *
     * @param login identidade confirmada (login do GitHub, ou {@code contingencia})
     * @param origem {@link #ORIGEM_GITHUB} ou {@link #ORIGEM_CONTINGENCIA}
     * @param papel {@link #PAPEL_DONO} ou {@link #PAPEL_LEITOR}
     */
    public NewCookie emitirCookie(String login, String origem, String papel) {
        return new NewCookie.Builder(COOKIE_NAME)
                .value(emitirValor(login, origem, papel))
                .path("/")
                .maxAge((int) TTL_SEGUNDOS)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    /** Os cookies deste ambiente saem marcados como {@code Secure}? (true em produção HTTPS). */
    public boolean cookieSeguro() {
        return cookieSecure;
    }

    /** Cookie de remoção, usado no logout. */
    public NewCookie cookieDeSaida() {
        return new NewCookie.Builder(COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    /** Valor assinado: {@code expiração.origem.papel.loginBase64.nonce.assinatura}. */
    String emitirValor(String login, String origem, String papel) {
        long expiracao = Instant.now().getEpochSecond() + TTL_SEGUNDOS;
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        String corpo = expiracao
                + "." + origem
                + "." + (PAPEL_LEITOR.equals(papel) ? PAPEL_LEITOR : PAPEL_DONO)
                + "." + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(login.getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return corpo + "." + assinar(corpo);
    }

    /**
     * Login válido guardado no cabeçalho de cookies, se houver.
     *
     * <p><b>Comportamento em caso de falha:</b> string vazia para qualquer defeito
     * — ausente, formato errado, expirado ou assinatura que não confere.</p>
     */
    public String loginDaSessao(String cabecalhoCookie) {
        return validar(valorDoCookie(cabecalhoCookie));
    }

    public boolean temSessaoValida(String cabecalhoCookie) {
        return !loginDaSessao(cabecalhoCookie).isEmpty();
    }

    String validar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String[] partes = valor.strip().split("\\.");
        if (partes.length != 6) {
            return "";
        }
        String corpo = partes[0] + "." + partes[1] + "." + partes[2] + "." + partes[3] + "." + partes[4];
        if (!igualdadeEmTempoConstante(assinar(corpo), partes[5])) {
            return "";
        }
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(partes[0])) {
                return "";
            }
            return new String(Base64.getUrlDecoder().decode(partes[3]), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /** Origem registrada na sessão ({@code github} ou {@code contingencia}); vazio se inválida. */
    public String origemDaSessao(String cabecalhoCookie) {
        String valor = valorDoCookie(cabecalhoCookie);
        if (validar(valor).isEmpty()) {
            return "";
        }
        String[] partes = valor.strip().split("\\.");
        return partes.length == 6 ? partes[1].toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Papel do portador: {@link #PAPEL_DONO} ou {@link #PAPEL_LEITOR}.
     *
     * <p><b>Comportamento em caso de falha:</b> sessão inválida devolve string
     * vazia — e quem chama trata ausência de papel como "não pode", nunca como
     * "pode tudo". Falha fechada.</p>
     */
    public String papelDaSessao(String cabecalhoCookie) {
        String valor = valorDoCookie(cabecalhoCookie);
        if (validar(valor).isEmpty()) {
            return "";
        }
        String[] partes = valor.strip().split("\\.");
        return partes.length == 6 ? partes[2].toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Só o dono mexe: exportar, limpar console e abrir a pasta de logs.
     *
     * <p><b>Invariantes do domínio:</b> leitor autenticado enxerga o painel e os
     * gráficos e nada mais. A verificação é por papel, não por origem — amanhã,
     * quando existir a autenticação de usuários do sistema, ela emite sessão de
     * leitor e este método continua valendo sem alteração.</p>
     */
    public boolean ehDono(String cabecalhoCookie) {
        return PAPEL_DONO.equals(papelDaSessao(cabecalhoCookie));
    }

    private String valorDoCookie(String cabecalhoCookie) {
        if (cabecalhoCookie == null || cabecalhoCookie.isBlank()) {
            return "";
        }
        for (String pedaco : cabecalhoCookie.split(";")) {
            String limpo = pedaco.strip();
            if (limpo.startsWith(COOKIE_NAME + "=")) {
                return limpo.substring(COOKIE_NAME.length() + 1);
            }
        }
        return "";
    }

    private String assinar(String corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derivarChave(), "HmacSHA256"));
            byte[] bruto = mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar a sessão da Telemetria.", ex);
        }
    }

    private byte[] derivarChave() {
        try {
            // Namespace próprio: a mesma semente do CSRF, mas chave distinta, para que
            // um token de um mecanismo jamais seja aceito pelo outro.
            return MessageDigest.getInstance("SHA-256")
                    .digest(("telemetria-sessao:" + segredo).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao derivar a chave da sessão.", ex);
        }
    }

    private static boolean igualdadeEmTempoConstante(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] esquerda = a.getBytes(StandardCharsets.UTF_8);
        byte[] direita = b.getBytes(StandardCharsets.UTF_8);
        if (esquerda.length != direita.length) {
            return false;
        }
        int diferenca = 0;
        for (int i = 0; i < esquerda.length; i++) {
            diferenca |= esquerda[i] ^ direita[i];
        }
        return diferenca == 0;
    }
}
