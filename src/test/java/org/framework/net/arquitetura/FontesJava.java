package org.framework.net.arquitetura;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Leitor único dos fontes Java para as guardas de arquitetura.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> as guardas ({@link ArquiteturaCamadasTest},
 * {@link FronteiraAcademiaArchTest}) precisam concordar sobre o que é módulo, camada e
 * dependência. Um leitor só evita que uma guarda enxergue camada onde a outra enxerga pasta, e
 * deixa as guardas serem calibradas com fonte sintético passando pelo MESMO parser do real.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a camada é o <b>primeiro segmento conhecido</b> depois do
 * módulo ({@link #CAMADAS_CONHECIDAS}); isso cobre subpacote aninhado
 * ({@code analiseDidatica/application/cidr}) e o segundo nível da Academia
 * ({@code academia/trilha/presentation}). Pacote sem segmento conhecido devolve camada vazia, e a
 * guarda decide se isso reprova. Referência a tipo do projeto conta tanto por {@code import} quanto
 * por nome qualificado no corpo — o nome qualificado não escapa da varredura.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> diretório de fontes ausente (execução fora da raiz)
 * vira {@code Assumptions} — teste ignorado, nunca aprovado nem reprovado por motivo errado. Erro de
 * leitura lança {@link UncheckedIOException} com o arquivo.</p>
 */
final class FontesJava {

    static final Path RAIZ_FONTES = Path.of("src", "main", "java", "org", "framework", "net");

    static final String PREFIXO = "org.framework.net.";

    /** Nomes de pacote que são camada. Qualquer outro é pasta de organização interna. */
    static final Set<String> CAMADAS_CONHECIDAS = Set.of(
            "domain", "application", "infrastructure", "presentation",
            "exception", "support", "config", "filter");

    // Aceita também import com curinga (a.b.*): antes ele escapava da varredura inteira.
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");

    // ACAD-27: import quebrado em linhas ou com espaço em volta do ponto é Java válido e escapava do
    // padrão por linha. Este varre o texto inteiro; o nome sai sem os espaços.
    private static final Pattern IMPORT_TEXTO = Pattern.compile(
            "(?m)^\\s*import\\s+(static\\s+)?([\\w.\\s]+?(?:\\.\\s*\\*)?)\\s*;");

    private static final Pattern NOME_QUALIFICADO = Pattern.compile("\\borg\\.framework\\.net\\.[\\w.]*\\w");

    private FontesJava() {
    }

    /** Todos os fontes de {@link #RAIZ_FONTES}; ignora o teste se a raiz não existir. */
    static List<ArquivoJava> todos() {
        Assumptions.assumeTrue(Files.isDirectory(RAIZ_FONTES),
                "Diretório de fontes não encontrado a partir de " + Path.of("").toAbsolutePath());
        try (Stream<Path> caminhos = Files.walk(RAIZ_FONTES)) {
            return caminhos
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(FontesJava::ler)
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao varrer os fontes do projeto", ex);
        }
    }

    private static ArquivoJava ler(Path caminho) {
        try {
            return analisar(RAIZ_FONTES.relativize(caminho), caminho.toString().replace('\\', '/'),
                    Files.readString(caminho));
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + caminho, ex);
        }
    }

    /**
     * Fonte sintético para calibrar uma guarda pelo mesmo parser do real.
     *
     * @param relativo caminho a partir de {@code org/framework/net}, ex.: {@code academia/x/domain/A.java}
     */
    static ArquivoJava sintetico(String relativo, String conteudo) {
        return analisar(Path.of(relativo), "SINTETICO:" + relativo, conteudo);
    }

    private static ArquivoJava analisar(Path relativo, String exibicao, String conteudo) {
        List<String> imports = new ArrayList<>();
        List<String> referencias = new ArrayList<>();
        Matcher noTexto = IMPORT_TEXTO.matcher(conteudo);
        while (noTexto.find()) {
            imports.add(noTexto.group(2).replaceAll("\\s+", ""));
        }
        for (String linha : conteudo.split("\\R")) {
            Matcher importado = IMPORT.matcher(linha);
            if (importado.find()) {
                continue;
            }
            if (linha.stripLeading().startsWith("package ")) {
                continue;
            }
            Matcher qualificado = NOME_QUALIFICADO.matcher(linha);
            while (qualificado.find()) {
                referencias.add(qualificado.group());
            }
        }
        int segmentos = relativo.getNameCount();
        String modulo = segmentos > 1 ? relativo.getName(0).toString() : "";
        List<String> pacote = new ArrayList<>();
        for (int i = 1; i < segmentos - 1; i++) {
            pacote.add(relativo.getName(i).toString());
        }
        return new ArquivoJava(exibicao, modulo, pacote, primeiraCamada(pacote), imports, referencias, conteudo);
    }

    /** Primeiro segmento que é camada; vazio se nenhum for. */
    static String primeiraCamada(List<String> segmentosDoPacote) {
        for (String segmento : segmentosDoPacote) {
            String normal = segmento.toLowerCase(Locale.ROOT);
            if (CAMADAS_CONHECIDAS.contains(normal)) {
                return normal;
            }
        }
        return "";
    }

    /** Segmento após {@code org.framework.net.} — o módulo do tipo referenciado. */
    static String moduloDoImport(String imp) {
        String resto = imp.substring(PREFIXO.length());
        int ponto = resto.indexOf('.');
        return ponto < 0 ? "" : resto.substring(0, ponto);
    }

    /**
     * Camada do tipo referenciado: primeiro segmento conhecido depois do módulo. Nome de classe
     * começa em maiúscula e nunca coincide com camada, então não precisa ser recortado.
     */
    static String camadaDoImport(String imp) {
        String[] partes = imp.substring(PREFIXO.length()).split("\\.");
        List<String> depoisDoModulo = new ArrayList<>();
        for (int i = 1; i < partes.length; i++) {
            depoisDoModulo.add(partes[i]);
        }
        return primeiraCamada(depoisDoModulo);
    }

    static String mensagem(String regra, List<String> violacoes) {
        return regra + "\nViolações (" + violacoes.size() + "):\n  - " + String.join("\n  - ", violacoes);
    }

    /**
     * Um fonte lido.
     *
     * @param pacote segmentos entre o módulo e o arquivo (vazio na raiz do módulo)
     * @param referencias nomes qualificados {@code org.framework.net.*} citados fora de import/package
     */
    record ArquivoJava(String caminho, String modulo, List<String> pacote, String camada,
                       List<String> imports, List<String> referencias, String conteudo) {

        /** Imports e nomes qualificados do projeto: tudo por onde o arquivo alcança outro tipo. */
        List<String> dependenciasDoProjeto() {
            List<String> todas = new ArrayList<>();
            for (String imp : imports) {
                if (imp.startsWith(PREFIXO)) {
                    todas.add(imp);
                }
            }
            todas.addAll(referencias);
            // O mesmo tipo alcançado por import e por nome qualificado (import quebrado em linhas) conta uma vez.
            return List.copyOf(new java.util.LinkedHashSet<>(todas));
        }
    }
}
