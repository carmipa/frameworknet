package org.framework.net.certificados.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro das páginas de aprofundamento do módulo de Certificados (X.509/PKI).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a aba Geral responde "quais formatos, campos e
 * tipos de certificado existem"; ela não responde "como um X.509 é por dentro" ou
 * "como funciona a revogação". Cada aprofundamento é uma página didática,
 * organizada em GRUPOS (Fundamentos · Formatos · Ciclo de vida · Usos &amp;
 * ataques) — o mesmo padrão de duas camadas dos Protocolos. Este registro é a
 * fonte única: alimenta o sub-menu de dois níveis, a rota, o carregamento do
 * conteúdo, o sitemap e os testes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code slug} único, minúsculo, é o último
 * segmento da rota ({@code /certificados/<slug>}); todo item tem conteúdo em
 * {@code resources/certificados/<slug>/conteudo.json}, carregado no boot por
 * {@code CertificadosAprofundamentoCatalog} (falha fechada). Template, CSS e
 * Mermaid são os compartilhados dos aprofundamentos.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #porSlug(String)} devolve
 * {@link Optional#empty()} para entrada desconhecida, nula ou em branco.</p>
 *
 * @param slug       último segmento da rota
 * @param titulo     título exibido no topo da página
 * @param rotuloMenu rótulo curto no sub-menu
 * @param icone      ícone Material Symbols (identidade e sub-menu)
 * @param grupo      grupo (nível 1 do sub-menu)
 * @param chamada    frase-resumo (tooltip do sub-menu)
 */
public record CertificadoAprofundamento(
        String slug,
        String titulo,
        String rotuloMenu,
        String icone,
        String grupo,
        String chamada) {

    /** Grupos na ordem de exibição (nível 1 do sub-menu). */
    public static final List<String> GRUPOS = List.of("Fundamentos", "Formatos", "Ciclo de vida", "Usos & ataques");

    private static final List<CertificadoAprofundamento> DISPONIVEIS = List.of(
            new CertificadoAprofundamento("x509", "Anatomia do X.509", "Anatomia X.509", "badge", "Fundamentos",
                    "O certificado por dentro: TBSCertificate, extensões v3 e a estrutura binária ASN.1/DER (TLV)."),
            new CertificadoAprofundamento("cadeia", "Cadeia de confiança", "Cadeia", "account_tree", "Fundamentos",
                    "Raiz → intermediária → folha, trust stores, CT e CAA: como o navegador decide confiar."),
            new CertificadoAprofundamento("tipos", "Tipos de certificado", "Tipos", "category", "Fundamentos",
                    "DV/OV/EV, wildcard, SAN, self-signed e por uso (servidor, cliente, code signing, S/MIME)."),
            new CertificadoAprofundamento("formatos", "Formatos e conversões", "Formatos", "description", "Formatos",
                    "PEM × DER × PKCS#12 (PFX) × PKCS#7 (P7B), JKS e as conversões com openssl/keytool."),
            new CertificadoAprofundamento("ciclo-de-vida", "Ciclo de vida: CSR, emissão e renovação", "Ciclo de vida", "autorenew", "Ciclo de vida",
                    "Do par de chaves ao CSR, à emissão pela CA e à renovação automática (ACME)."),
            new CertificadoAprofundamento("revogacao", "Revogação: CRL, OCSP e stapling", "Revogação", "block", "Ciclo de vida",
                    "Como um certificado deixa de valer antes de expirar — e por que soft-fail é um problema."),
            new CertificadoAprofundamento("usos", "Usos: HTTPS, mTLS, code signing, S/MIME", "Usos", "verified_user", "Usos & ataques",
                    "Além do HTTPS: autenticação de cliente (mTLS), assinatura de código e e-mail (S/MIME)."),
            new CertificadoAprofundamento("ataques", "Ataques e erros comuns", "Ataques", "gpp_bad", "Usos & ataques",
                    "MITM com CA falsa, expirado, nome que não bate, algoritmos fracos — e os erros do navegador."));

    /** Itens do sub-menu, na ordem de registro. */
    public static List<CertificadoAprofundamento> disponiveis() {
        return DISPONIVEIS;
    }

    /** Aprofundamentos de um grupo, na ordem de registro. */
    public static List<CertificadoAprofundamento> porGrupo(String grupo) {
        return DISPONIVEIS.stream().filter(item -> item.grupo().equals(grupo)).toList();
    }

    /** Grupos que têm ao menos um item, na ordem canônica — alimenta as abas. */
    public static List<String> gruposComItens() {
        return GRUPOS.stream().filter(g -> !porGrupo(g).isEmpty()).toList();
    }

    /** Slugs de todos os aprofundamentos — usados pelo carregador de conteúdo. */
    public static List<String> slugs() {
        return DISPONIVEIS.stream().map(CertificadoAprofundamento::slug).toList();
    }

    /** Um grupo com seus aprofundamentos, para o sub-menu de dois níveis. */
    public record Grupo(String nome, List<CertificadoAprofundamento> itens) {
    }

    /** Aprofundamentos agrupados, na ordem canônica — alimenta o sub-menu. */
    public static List<Grupo> agrupadoPorGrupo() {
        return gruposComItens().stream().map(g -> new Grupo(g, porGrupo(g))).toList();
    }

    /**
     * Resolve o slug vindo da URL.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula, em branco ou
     * desconhecida devolve {@link Optional#empty()}.</p>
     */
    public static Optional<CertificadoAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }

    /** Rota HTTP da página, derivada do slug. */
    public String rota() {
        return "/certificados/" + slug;
    }
}
