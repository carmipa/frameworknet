package org.framework.net.paginaErros.domain;

import java.util.List;
import java.util.Locale;

/**
 * Área do site onde o erro aconteceu, deduzida só pelo caminho.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o mesmo 401 significa coisas diferentes para quem administra a
 * Telemetria e para quem está lendo uma lição. Mandar o aluno "autenticar-se em Administração"
 * é instrução errada dada a uma pessoa honesta — ela procura uma tela de administrador que não é
 * para ela. A área escolhe o texto; o status continua o mesmo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a decisão usa só o caminho, nunca sessão, cookie ou módulo
 * importado — assim a Academia e a conta não precisam conhecer {@code paginaErros}, nem o
 * contrário. Os prefixos de administração acompanham as rotas protegidas por
 * {@code AdminApiKeyFilter} (Telemetria, exportações) mais o fluxo de login dela; um teste
 * confere a lista contra {@code AdminApiKeyService.isProtectedPath}. Prefixo casa por segmento
 * inteiro: {@code /exportar} não é {@code /export}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> caminho nulo, vazio ou desconhecido é
 * {@link #SITE} — o texto público nunca expõe detalhe de administração, então errar para ele é
 * o lado seguro.</p>
 */
public enum AreaDoErro {

    /** Telemetria, exportações e o login da Telemetria. */
    ADMINISTRACAO,

    /** Todo o resto: ferramentas, Academia, conta do site. */
    SITE;

    private static final List<String> PREFIXOS_DE_ADMINISTRACAO = List.of(
            "/telemetria", "/export", "/ipv6/export", "/login", "/admin");

    /** Área do caminho tentado. */
    public static AreaDoErro doCaminho(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return SITE;
        }
        String normal = caminho.toLowerCase(Locale.ROOT);
        for (String prefixo : PREFIXOS_DE_ADMINISTRACAO) {
            if (normal.equals(prefixo) || normal.startsWith(prefixo + "/")) {
                return ADMINISTRACAO;
            }
        }
        return SITE;
    }
}
