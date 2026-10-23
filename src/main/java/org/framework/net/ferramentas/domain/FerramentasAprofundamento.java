package org.framework.net.ferramentas.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Registro FLAT das páginas de aprofundamento do módulo Ferramentas. Fonte única do sub-menu, rotas, conteúdo, sitemap e testes. */
public record FerramentasAprofundamento(String slug, String titulo, String rotuloMenu, String icone, String chamada) {

    private static final List<FerramentasAprofundamento> DISPONIVEIS = List.of(
            new FerramentasAprofundamento("conectividade", "Conectividade: ping, traceroute e mtr", "Conectividade", "network_ping", "Testar alcance e caminho: ping, traceroute e mtr."),
            new FerramentasAprofundamento("dns-tools", "DNS: dig, nslookup e host", "DNS", "dns", "Consultar DNS: dig, nslookup e host."),
            new FerramentasAprofundamento("captura", "Captura: tcpdump e Wireshark", "Captura", "radar", "Capturar e ler pacotes: tcpdump e Wireshark."),
            new FerramentasAprofundamento("varredura", "Varredura: nmap", "Varredura", "travel_explore", "Descoberta e portas com nmap (uso autorizado)."),
            new FerramentasAprofundamento("http-tls", "HTTP e TLS: curl e openssl", "HTTP/TLS", "http", "Inspecionar HTTP e TLS: curl e openssl."),
            new FerramentasAprofundamento("sockets", "Sockets e conexões: ss, netstat, nc", "Sockets", "settings_ethernet", "Ver conexões e testar portas: ss, netstat, nc."));

    public static List<FerramentasAprofundamento> disponiveis() { return DISPONIVEIS; }
    public static List<String> slugs() { return DISPONIVEIS.stream().map(FerramentasAprofundamento::slug).toList(); }
    public static Optional<FerramentasAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) { return Optional.empty(); }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }
    public String rota() { return "/ferramentas/" + slug; }
}
