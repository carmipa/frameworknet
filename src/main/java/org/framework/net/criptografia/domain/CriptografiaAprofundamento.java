package org.framework.net.criptografia.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Registro FLAT das páginas de aprofundamento do módulo Criptografia. Fonte única do sub-menu, rotas, conteúdo, sitemap e testes. */
public record CriptografiaAprofundamento(String slug, String titulo, String rotuloMenu, String icone, String chamada) {

    private static final List<CriptografiaAprofundamento> DISPONIVEIS = List.of(
            new CriptografiaAprofundamento("simetrica", "Criptografia simétrica", "Simétrica", "key", "Uma chave para cifrar e decifrar: AES, ChaCha20."),
            new CriptografiaAprofundamento("assimetrica", "Criptografia assimétrica (RSA e ECC)", "Assimétrica", "vpn_key", "Par público/privado: RSA e curvas elípticas."),
            new CriptografiaAprofundamento("hash", "Funções de hash", "Hash", "tag", "Resumo de mão única: SHA-2/3 e hash de senha."),
            new CriptografiaAprofundamento("troca-de-chaves", "Troca de chaves e forward secrecy", "Troca de chaves", "swap_horiz", "Diffie-Hellman, ECDHE e sigilo futuro."),
            new CriptografiaAprofundamento("assinatura", "Assinatura digital e MAC", "Assinatura", "draw", "Provar origem e integridade: assinatura e HMAC."));

    public static List<CriptografiaAprofundamento> disponiveis() { return DISPONIVEIS; }
    public static List<String> slugs() { return DISPONIVEIS.stream().map(CriptografiaAprofundamento::slug).toList(); }
    public static Optional<CriptografiaAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) { return Optional.empty(); }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }
    public String rota() { return "/criptografia/" + slug; }
}
