package org.framework.net.shared;


import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Locale;
import java.util.Set;

/**
 * Bloqueia resolução/consulta a endereços privados, reservados ou hostnames internos (mitiga SSRF).
 */
public final class NetworkAddressGuard {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "instance-data",
            "kubernetes.default",
            "kubernetes.default.svc"
    );

    private NetworkAddressGuard() {
    }

    public static void rejectBlockedHostname(String hostname) {
        String h = hostname == null ? "" : hostname.strip().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) {
            return;
        }
        if (BLOCKED_HOSTNAMES.contains(h)) {
            throw new EnderecoBloqueadoException("Hostname não permitido para resolução DNS: " + hostname);
        }
        if (h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost")) {
            throw new EnderecoBloqueadoException("Hostname interno não permitido: " + hostname);
        }
    }

    /**
     * O endereço é roteável na internet pública?
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a mesma régua de "público" para o bloqueio de SSRF e para a escolha
     * do IP do visitante entre os candidatos que o proxy repassou.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> loopback, link-local, privado (RFC 1918 e ULA), multicast, não
     * especificado, CGNAT, faixas de documentação e reservadas nunca são públicos.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; {@code null} devolve {@code false}.</p>
     */
    public static boolean ehPublico(InetAddress address) {
        if (address == null
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            return false;
        }
        if (address instanceof Inet4Address inet4) {
            return !isIpv4NonPublic(inet4);
        }
        return !(address instanceof Inet6Address inet6 && isIpv6NonPublic(inet6));
    }

    public static void rejectNonPublicAddress(InetAddress address, String context) {
        if (address == null) {
            throw new EnderecoBloqueadoException("Endereço inválido em " + context + ".");
        }
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            throw new EnderecoBloqueadoException(
                    "Endereço privado, reservado ou local não permitido em " + context + ": "
                            + address.getHostAddress());
        }
        if (address instanceof Inet4Address inet4 && isIpv4NonPublic(inet4)) {
            throw new EnderecoBloqueadoException(
                    "Endereço IPv4 não público não permitido em " + context + ": " + address.getHostAddress());
        }
        if (address instanceof Inet6Address inet6 && isIpv6NonPublic(inet6)) {
            throw new EnderecoBloqueadoException(
                    "Endereço IPv6 não público não permitido em " + context + ": " + address.getHostAddress());
        }
    }

    private static boolean isIpv4NonPublic(Inet4Address address) {
        byte[] octets = address.getAddress();
        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;
        int b2 = octets[2] & 0xFF;
        // 0.0.0.0/8, 10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, 169.254.0.0/16, 172.16.0.0/12,
        // 192.0.0.0/24, 192.0.2.0/24, 192.168.0.0/16, 198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24, 240.0.0.0/4
        if (b0 == 0 || b0 == 10 || b0 == 127) {
            return true;
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        // 192.0.0.0/24 (atribuições do IETF) e 192.0.2.0/24 (TEST-NET-1): /24, não /16 — o resto de 192.0.x.x é
        // público (ex.: 192.0.43.7, da ICANN), e testar só os dois primeiros octetos recusava esses endereços.
        if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }
        if (b0 == 198 && b1 == 51 && b2 == 100) {
            return true;
        }
        if (b0 == 203 && b1 == 0 && b2 == 113) {
            return true;
        }
        return b0 >= 240;
    }

    private static boolean isIpv6NonPublic(Inet6Address address) {
        if (address.isIPv4CompatibleAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        // Unique local fc00::/7, link-local fe80::/10, documentação 2001:db8::/32 (RFC 3849)
        return (b0 & 0xFE) == 0xFC || b0 == 0xFE || (b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8);
    }
}
