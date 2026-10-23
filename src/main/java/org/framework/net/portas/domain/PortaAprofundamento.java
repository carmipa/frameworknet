package org.framework.net.portas.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro das páginas de aprofundamento do módulo de Portas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o catálogo (aba Geral) responde "quais portas
 * existem e qual o risco de cada uma"; ele não responde "como as portas funcionam"
 * nem "quais os cuidados de cada família de serviço". Cada aprofundamento é uma
 * página didática — a conceitual (Anatomia das portas) e as por família (Web,
 * E-mail, Acesso remoto, Arquivos, Banco de dados, Infra). Este registro é a fonte
 * única de quais existem: alimenta o sub-menu, a rota, o carregamento do conteúdo,
 * o sitemap e os testes. É o espelho de {@code AprofundamentoProtocolo}, porém
 * FLAT — não há camadas; as famílias são os próprios itens.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code slug} único, minúsculo, é o último
 * segmento da rota ({@code /portas/<slug>}); todo item tem conteúdo em
 * {@code resources/portas/<slug>/conteudo.json}, carregado no boot por
 * {@link PortasAprofundamentoCatalog} (falha fechada). O template e o CSS são os
 * compartilhados dos aprofundamentos, não há um por página.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #porSlug(String)} devolve
 * {@link Optional#empty()} para entrada desconhecida, nula ou em branco — nunca um
 * item arbitrário; quem chama traduz o vazio em 404.</p>
 *
 * @param slug       último segmento da rota
 * @param titulo     título exibido no topo da página
 * @param rotuloMenu rótulo curto no sub-menu
 * @param icone      ícone Material Symbols (identidade e sub-menu)
 * @param chamada    frase-resumo (tooltip do sub-menu)
 */
public record PortaAprofundamento(
        String slug,
        String titulo,
        String rotuloMenu,
        String icone,
        String chamada) {

    private static final List<PortaAprofundamento> DISPONIVEIS = List.of(
            new PortaAprofundamento("anatomia", "Anatomia das portas", "Anatomia", "schema",
                    "O que é uma porta: 16 bits, socket (IP:porta), as 3 faixas, portas efêmeras e NAT/PAT."),
            new PortaAprofundamento("web", "Portas Web (HTTP/HTTPS)", "Web", "language",
                    "80, 443, 8080/8443: texto claro × TLS, painéis e apps de dev expostos."),
            new PortaAprofundamento("email", "Portas de E-mail", "E-mail", "mail",
                    "25/587/465 e 110/995/143/993: submissão × relay, claro × seguro, open relay."),
            new PortaAprofundamento("acesso-remoto", "Portas de Acesso Remoto", "Acesso remoto", "settings_remote",
                    "22 SSH × 23 Telnet; 3389 RDP e 5900 VNC nunca expostos direto (brute force, BlueKeep)."),
            new PortaAprofundamento("arquivos", "Portas de Arquivos e Compartilhamento", "Arquivos", "folder_shared",
                    "20/21 FTP, 69 TFTP, 139/445 SMB: texto claro e o clássico EternalBlue."),
            new PortaAprofundamento("banco-de-dados", "Portas de Banco de Dados", "Banco de dados", "database",
                    "3306, 5432, 1433, 1521, 27017, 6379, 9200: a regra de ouro de nunca expor à internet."),
            new PortaAprofundamento("infra", "Portas de Infraestrutura de Rede", "Infra", "router",
                    "53 DNS, 67/68 DHCP, 123 NTP, 161 SNMP, 389 LDAP: amplificação e serviços de rede."));

    /** Itens do sub-menu, na ordem em que aparecem depois da aba Geral. */
    public static List<PortaAprofundamento> disponiveis() {
        return DISPONIVEIS;
    }

    /** Slugs de todos os aprofundamentos — usados pelo carregador de conteúdo. */
    public static List<String> slugs() {
        return DISPONIVEIS.stream().map(PortaAprofundamento::slug).toList();
    }

    /**
     * Resolve o slug vindo da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula, em branco ou
     * desconhecida devolve {@link Optional#empty()}.</p>
     */
    public static Optional<PortaAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }

    /** Rota HTTP da página, derivada do slug — evita rota escrita à mão divergindo do registro. */
    public String rota() {
        return "/portas/" + slug;
    }
}
