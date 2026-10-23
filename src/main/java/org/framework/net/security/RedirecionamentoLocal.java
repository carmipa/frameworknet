package org.framework.net.security;

import java.net.URI;

/**
 * O destino do "voltar para onde eu estava" depois do login e do logout de administrador.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> quem clicou em "Exportar" caía no login e, depois de entrar, voltava ao
 * export SEM a consulta ({@code ?endereco=2001:db8::1}) — o IPv6 analisado se perdia e o export respondia
 * erro (auditoria FRONT-03). E havia duas cópias da regra "o destino é local", com padrões diferentes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só passa caminho local: começa com uma barra, nunca com duas, sem barra
 * invertida nem caractere de controle (navegadores tratam {@code /\} como {@code //}), e que vira um URI
 * relativo sem esquema nem autoridade; a consulta, quando houver, vai junto; qualquer outra coisa vira o
 * padrão dado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nunca lança — entrada nula, vazia, externa ou que o
 * {@link URI} recusa devolve o padrão (antes, {@code /%5Cx} no logout dava HTTP 500).</p>
 */
public final class RedirecionamentoLocal {

    private RedirecionamentoLocal() {
    }

    /** O destino local, ou {@code padrao} se não for um caminho local seguro. */
    public static String destino(String pedido, String padrao) {
        if (pedido == null) {
            return padrao;
        }
        String d = pedido.strip();
        if (d.isEmpty() || !d.startsWith("/") || d.startsWith("//") || d.indexOf('\\') >= 0) {
            return padrao;
        }
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                return padrao;
            }
        }
        try {
            URI uri = URI.create(d);
            if (uri.getScheme() != null || uri.getRawAuthority() != null) {
                return padrao;
            }
        } catch (IllegalArgumentException invalido) {
            return padrao;
        }
        return d;
    }

    /** Caminho mais a consulta crua da requisição, para voltar ao mesmo lugar depois do login. */
    public static String comConsulta(String caminho, String consultaCrua) {
        return consultaCrua == null || consultaCrua.isEmpty() ? caminho : caminho + "?" + consultaCrua;
    }
}
