package org.framework.net.shared;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Leitura de endereço IP escrito por extenso (literal), sem consulta DNS.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> todo texto que chega de fora e deveria ser um IP — campo digitado,
 * cabeçalho {@code X-Forwarded-For} ou {@code X-Real-IP} — passa por aqui antes de virar
 * {@link InetAddress}. O {@code InetAddress.getByName} do Java resolve DNS quando o texto é um nome, e
 * isso transformava o site em oráculo de nomes (a resposta trazia o IP para onde o nome apontava) e
 * prendia a thread da requisição ~2,7 s por nome inexistente (auditoria SEC-01).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum texto aceito aqui gera consulta DNS. IPv4 só na forma
 * a.b.c.d com quatro números de 0 a 255 em dígitos ASCII. IPv6 só com dígitos hexadecimais, dois-pontos
 * e (para o IPv4 embutido) pontos — o primeiro caractere é sempre hexadecimal ou dois-pontos, que é o
 * caminho em que o Java interpreta o texto como literal e nunca como nome. Zona ({@code %eth0}),
 * colchetes, prefixo e espaço ficam de fora.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; texto nulo, vazio, longo demais, nome de host ou
 * literal malformado devolve {@link Optional#empty()}.</p>
 */
public final class IpLiteral {

    /** Maior forma textual de IPv6 com IPv4 embutido ({@code ffff:...:255.255.255.255}). */
    private static final int MAX_CARACTERES = 45;

    private IpLiteral() {
    }

    /** O endereço do literal, ou vazio se o texto não for um IP escrito por extenso. */
    public static Optional<InetAddress> ler(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String t = texto.strip();
        if (t.isEmpty() || t.length() > MAX_CARACTERES) {
            return Optional.empty();
        }
        boolean literal = t.indexOf(':') >= 0 ? caracteresIpv6(t) : ipv4PorExtenso(t);
        if (!literal) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(t));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** O texto é um IP escrito por extenso? */
    public static boolean eh(String texto) {
        return ler(texto).isPresent();
    }

    private static boolean caracteresIpv6(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex && c != ':' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean ipv4PorExtenso(String t) {
        String[] partes = t.split("\\.", -1);
        if (partes.length != 4) {
            return false;
        }
        for (String parte : partes) {
            var n = NumeroAscii.inteiro(parte, 3);
            if (n.isEmpty() || n.getAsInt() > 255) {
                return false;
            }
        }
        return true;
    }
}
