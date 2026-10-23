package org.framework.net.protocolos.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro das páginas de aprofundamento por protocolo.
 *
 * <p><b>Propósito de negócio:</b> o catálogo (aba Geral) responde "quais
 * protocolos existem e como se comparam"; ele não responde "me explica BGP".
 * Cada aprofundamento é uma página didática dedicada a um único protocolo, e
 * este registro é a fonte única de quais delas existem — alimenta ao mesmo
 * tempo o sub-menu, a rota, o CSS carregado e os testes. Protocolo novo entra
 * com uma linha aqui, um template e um CSS.</p>
 *
 * <p><b>Invariantes do domínio:</b> o {@code slug} é único, minúsculo e é o
 * último segmento da rota ({@code /protocolos/<slug>}); todo item apresentado
 * ao usuário tem template e CSS próprios existentes em disco — a guarda
 * {@code AprofundamentoProtocoloTest} reprova o build quando um dos dois falta,
 * porque item de menu apontando para página inexistente é erro 500 na cara de
 * quem clicou. Os nomes em {@code nomesNoCatalogo} precisam existir tal e qual
 * em {@code protocolos/catalogo.json}: é por eles que a linha do DataGrid ganha
 * o botão "Aprofundar", e um nome desatualizado faria o botão sumir em silêncio
 * — a mesma guarda cobre isso.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@link #porSlug(String)} e
 * {@link #porNomeDoCatalogo(String)} devolvem {@link Optional#empty()} para
 * entrada desconhecida, nula ou em branco — nunca um item arbitrário. Quem
 * chama traduz o vazio em 404 ou em "sem aprofundamento", jamais em página de
 * outro protocolo.</p>
 */
public record AprofundamentoProtocolo(
        String slug,
        String titulo,
        String rotuloMenu,
        String icone,
        String chamada,
        String template,
        String css,
        String camada,
        boolean generico,
        List<String> nomesNoCatalogo) {

    /** Versão dos estáticos; muda junto com o CSS para furar cache de browser. */
    private static final String VERSAO_CSS = "20261022";

    /** Template e CSS únicos dos aprofundamentos genéricos (conteúdo por JSON). */
    private static final String GEN_TEMPLATE = "protocolos/aprofundamento.html";
    private static final String GEN_CSS = "/protocolos/aprofundamento/css/aprofundamento.css";

    /** Camadas na ordem de exibição (aba superior do sub-menu). */
    public static final List<String> CAMADAS = List.of("Aplicação", "Transporte", "Rede");

    /** Cria um aprofundamento GENÉRICO (template e CSS compartilhados, conteúdo por JSON). */
    private static AprofundamentoProtocolo generico(String slug, String titulo, String rotuloMenu,
            String icone, String camada, String chamada, List<String> nomesNoCatalogo) {
        return new AprofundamentoProtocolo(slug, titulo, rotuloMenu, icone, chamada,
                GEN_TEMPLATE, GEN_CSS, camada, true, nomesNoCatalogo);
    }

    private static final List<AprofundamentoProtocolo> DISPONIVEIS = List.of(
            // --- Camada de Aplicação ---
            generico("http", "HTTP / HTTPS", "HTTP/S", "http", "Aplicação",
                    "A web crua: métodos, status, cabeçalhos, cookies e o encapsulamento sobre TLS.",
                    List.of("HTTP", "HTTPS", "HTTP/3")),
            new AprofundamentoProtocolo("dns", "DNS — Domain Name System", "DNS", "dns",
                    "A agenda da Internet: resolução recursiva passo a passo, os ataques de spoofing "
                            + "e cache poisoning, e as defesas (DNSSEC, DoT/DoH).",
                    "protocolos/dns/index.html", "/protocolos/dns/css/dns.css",
                    "Aplicação", false, List.of("DNS")),
            new AprofundamentoProtocolo("ssh", "SSH — Secure Shell", "SSH", "terminal",
                    "Acesso remoto cifrado, autenticação por chave, túneis e o endurecimento do "
                            + "servidor que a maioria esquece.",
                    "protocolos/ssh/index.html", "/protocolos/ssh/css/ssh.css",
                    "Aplicação", false, List.of("SSH")),
            new AprofundamentoProtocolo("tls", "TLS — Transport Layer Security", "TLS", "lock",
                    "O cadeado do HTTPS: handshake, cadeia de certificados, cipher suites e por que "
                            + "criptografia legítima também esconde tráfego malicioso.",
                    "protocolos/tls/index.html", "/protocolos/tls/css/tls.css",
                    "Aplicação", false, List.of("TLS", "HTTPS")),
            generico("ftp", "FTP / TFTP", "FTP", "folder_shared", "Aplicação",
                    "Transferência de arquivos e o perigo clássico da credencial em texto plano (controle × dados).",
                    List.of("FTP", "FTPS")),
            generico("smtp", "SMTP / POP3 / IMAP", "E-mail", "mail", "Aplicação",
                    "O caminho de um e-mail e por que forjar remetente era trivial: SPF, DKIM, DMARC.",
                    List.of("SMTP", "POP3", "IMAP")),
            generico("telnet", "Telnet", "Telnet", "terminal", "Aplicação",
                    "O protocolo inseguro por excelência — tudo em texto plano. O contraste direto com o SSH.",
                    List.of("TELNET")),
            // --- Camada de Transporte ---
            generico("tcp", "TCP — Transmission Control Protocol", "TCP", "swap_horiz", "Transporte",
                    "O transporte confiável: 3-way handshake, números de sequência, janela, "
                            + "controle de fluxo e congestionamento, e o encerramento em quatro tempos.",
                    List.of()),
            generico("udp", "UDP — User Datagram Protocol", "UDP", "bolt", "Transporte",
                    "O transporte sem cerimônia: datagrama, sem conexão nem garantia — rápido, e por isso "
                            + "a base de DNS, DHCP, VoIP, jogos e QUIC.",
                    List.of()),
            generico("handshake", "Handshake — o aperto de mão das conexões", "Handshake", "handshake", "Transporte",
                    "O que é um handshake e por que existe: TCP 3-way, TLS e SSH, passo a passo.",
                    List.of()),
            // --- Camada de Rede ---
            generico("ipv4", "IPv4 — Internet Protocol v4", "IPv4", "lan", "Rede",
                    "O endereço de 32 bits que move o pacote: cabeçalho, TTL, fragmentação, "
                            + "o esgotamento dos endereços e por que o NAT existe.",
                    List.of("IPv4")),
            generico("ipv6", "IPv6 — Internet Protocol v6", "IPv6", "lan", "Rede",
                    "O endereço de 128 bits e o futuro do roteamento: notação, SLAAC, "
                            + "cabeçalho enxuto e a coexistência com o IPv4.",
                    List.of("IPv6")),
            generico("arp", "ARP — Address Resolution Protocol", "ARP", "swap_calls", "Rede",
                    "A ponte entre IP e MAC na rede local, e o ARP spoofing — o ataque clássico de "
                            + "quem já está dentro da LAN. Viaja no quadro do Enlace, a serviço da camada de Rede.",
                    List.of("ARP")),
            generico("icmp", "ICMP / ICMPv6 — o mensageiro da rede", "ICMP", "network_ping", "Rede",
                    "As mensagens de controle e erro que sustentam ping e traceroute, e por que o "
                            + "ICMP também é vetor de varredura e túnel.",
                    List.of("ICMP", "ICMPv6")),
            new AprofundamentoProtocolo("bgp", "BGP-4 — Border Gateway Protocol", "BGP", "hub",
                    "O protocolo que mantém a Internet conectada: política entre Sistemas Autônomos, "
                            + "não menor custo interno. Roda como aplicação sobre TCP 179, a serviço da camada de Rede.",
                    "protocolos/bgp/index.html", "/protocolos/bgp/css/bgp.css",
                    "Rede", false, List.of("BGP-4 / eBGP", "iBGP")));

    /** Itens do sub-menu, na ordem em que aparecem depois da aba Geral. */
    public static List<AprofundamentoProtocolo> disponiveis() {
        return DISPONIVEIS;
    }

    /** Aprofundamentos de uma camada, na ordem de registro. */
    public static List<AprofundamentoProtocolo> porCamada(String camada) {
        return DISPONIVEIS.stream().filter(item -> item.camada().equals(camada)).toList();
    }

    /** Camadas que têm ao menos um aprofundamento, na ordem canônica — alimenta as abas. */
    public static List<String> camadasComItens() {
        return CAMADAS.stream().filter(c -> !porCamada(c).isEmpty()).toList();
    }

    /** Slugs dos aprofundamentos genéricos (conteúdo por JSON) — usados pelo catálogo. */
    public static List<String> slugsGenericos() {
        return DISPONIVEIS.stream().filter(AprofundamentoProtocolo::generico)
                .map(AprofundamentoProtocolo::slug).toList();
    }

    /** Uma camada com seus aprofundamentos, para o sub-menu de dois níveis. */
    public record Camada(String nome, List<AprofundamentoProtocolo> itens) {
    }

    /** Aprofundamentos agrupados por camada, na ordem canônica — alimenta o sub-menu. */
    public static List<Camada> agrupadoPorCamada() {
        return camadasComItens().stream().map(c -> new Camada(c, porCamada(c))).toList();
    }

    /**
     * Resolve o slug vindo da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula, em branco ou
     * desconhecida devolve {@link Optional#empty()}.</p>
     */
    public static Optional<AprofundamentoProtocolo> porSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }

    /**
     * Resolve o aprofundamento a partir do nome exato usado no catálogo.
     *
     * <p><b>Propósito de negócio:</b> é o que permite à linha do DataGrid
     * oferecer o botão "Aprofundar" sem que o template saiba quais protocolos
     * têm página.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> nome nulo, em branco ou sem
     * página devolve {@link Optional#empty()} — a linha simplesmente não ganha
     * o botão.</p>
     */
    public static Optional<AprofundamentoProtocolo> porNomeDoCatalogo(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String alvo = nome.trim();
        return DISPONIVEIS.stream()
                .filter(item -> item.nomesNoCatalogo().stream().anyMatch(alvo::equalsIgnoreCase))
                .findFirst();
    }

    /** Rota HTTP da página, derivada do slug — evita rota escrita à mão divergindo do registro. */
    public String rota() {
        return "/protocolos/" + slug;
    }

    /** URL do CSS com a versão anexada. */
    public String cssVersionado() {
        return css + "?v=" + VERSAO_CSS;
    }
}
