package org.framework.net.camadas.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro das páginas de aprofundamento do módulo de Camadas (OSI × TCP/IP).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a aba Geral traz a tabela das camadas; os
 * aprofundamentos explicam o modelo OSI, o TCP/IP, o encapsulamento e os
 * dispositivos por camada. Sub-menu FLAT (não há um segundo nível). Fonte única
 * do sub-menu, das rotas, do conteúdo, do sitemap e dos testes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code slug} único e minúsculo, último
 * segmento de {@code /camadas/<slug>}; conteúdo em
 * {@code resources/camadas/<slug>/conteudo.json} carregado no boot (falha
 * fechada). Template, CSS e Mermaid são os compartilhados dos aprofundamentos.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #porSlug(String)} devolve
 * {@link Optional#empty()} para entrada desconhecida, nula ou em branco.</p>
 */
public record CamadaAprofundamento(String slug, String titulo, String rotuloMenu, String icone, String chamada) {

    private static final List<CamadaAprofundamento> DISPONIVEIS = List.of(
            new CamadaAprofundamento("osi", "Modelo OSI (7 camadas)", "OSI", "layers",
                    "As 7 camadas do modelo OSI, o que cada uma faz e por que o modelo existe."),
            new CamadaAprofundamento("tcpip", "Modelo TCP/IP e o mapeamento com OSI", "TCP/IP", "account_tree",
                    "As 4 camadas do TCP/IP e como se mapeiam nas 7 do OSI — o modelo real da Internet."),
            new CamadaAprofundamento("encapsulamento", "Encapsulamento e PDUs", "Encapsulamento", "layers_clear",
                    "Como os dados descem a pilha ganhando cabeçalhos: Dados → Segmento → Pacote → Quadro → Bits."),
            new CamadaAprofundamento("dispositivos", "Dispositivos por camada", "Dispositivos", "router",
                    "Onde cada equipamento opera: hub, switch, roteador, firewall, balanceador."));

    public static List<CamadaAprofundamento> disponiveis() {
        return DISPONIVEIS;
    }

    public static List<String> slugs() {
        return DISPONIVEIS.stream().map(CamadaAprofundamento::slug).toList();
    }

    public static Optional<CamadaAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }

    public String rota() {
        return "/camadas/" + slug;
    }
}
