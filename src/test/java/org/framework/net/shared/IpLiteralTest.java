package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fronteira do leitor de IP literal (auditoria SEC-01): o IP escrito por extenso passa; nome de host
 * nunca é resolvido. "localhost" é o caso-controle que dispensa rede — o Java o resolve pelo arquivo
 * hosts, então qualquer caminho que chame DNS devolveria 127.0.0.1 em vez de vazio.
 */
class IpLiteralTest {

    @Test
    void aceitaIpv4EIpv6EscritosPorExtenso() {
        assertEquals("203.0.113.7", IpLiteral.ler("203.0.113.7").map(InetAddress::getHostAddress).orElseThrow());
        assertEquals("127.0.0.1", IpLiteral.ler(" 127.0.0.1 ").map(InetAddress::getHostAddress).orElseThrow());
        assertTrue(IpLiteral.eh("2001:db8::1"));
        assertTrue(IpLiteral.eh("::1"));
        assertTrue(IpLiteral.eh("::ffff:8.8.8.8"), "IPv4 embutido no IPv6");
        assertTrue(IpLiteral.eh("FE80::1"), "hexadecimal maiúsculo");
    }

    @Test
    void nomeDeHostNuncaEhResolvido() {
        assertTrue(IpLiteral.ler("localhost").isEmpty(), "localhost resolveria para 127.0.0.1");
        assertTrue(IpLiteral.ler("zz:1").isEmpty(), "começa sem hexadecimal: o Java mandaria para o DNS");
        assertTrue(IpLiteral.ler("g::1").isEmpty());
        assertTrue(IpLiteral.ler("exemplo.com").isEmpty());
        assertTrue(IpLiteral.ler("1.2.3.4.nip.io").isEmpty());
    }

    @Test
    void literalMalformadoOuForaDoFormatoEhRecusado() {
        assertFalse(IpLiteral.eh("1.2.3"), "três partes");
        assertFalse(IpLiteral.eh("256.1.1.1"));
        assertFalse(IpLiteral.eh("1.2.3.4/24"), "prefixo não é endereço");
        assertFalse(IpLiteral.eh("[2001:db8::1]"), "colchetes");
        assertFalse(IpLiteral.eh("fe80::1%eth0"), "zona");
        assertFalse(IpLiteral.eh("1:2"), "IPv6 incompleto");
        assertFalse(IpLiteral.eh("١.٢.٣.٤"), "dígito de outro alfabeto");
        assertFalse(IpLiteral.eh("1".repeat(46)));
        assertFalse(IpLiteral.eh(""));
        assertFalse(IpLiteral.eh(null));
    }
}
