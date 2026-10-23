package org.framework.net.academia.trilha.domain;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Um nível da trilha (Fundamentos, IPv4…), com as lições na ordem em que se estudam.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a escola cresce um nível por vez. O nível agrupa as lições e
 * diz se já está aberto — nível anunciado e ainda não aberto aparece na landing como "em breve",
 * sem link, para nenhum aluno cair numa página que não existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code id} em minúsculas sem acento; nível aberto tem pelo
 * menos uma lição e todas pertencem a ele (o prefixo do id da lição é o id do nível); nível
 * fechado não tem lição — lição listada é lição que abre.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link IllegalArgumentException} na construção.</p>
 *
 * @param id       identidade estável ({@code fundamentos})
 * @param titulo   nome do nível
 * @param resumo   o que o aluno aprende nele
 * @param icone    glifo do Material Symbols
 * @param aberto   se as lições já podem ser feitas
 * @param licoes   lições na ordem de estudo
 */
public record Nivel(String id, String titulo, String resumo, String icone, boolean aberto, List<Licao> licoes) {

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]*$");

    public Nivel {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id de nível inválido: " + id);
        }
        if (titulo == null || titulo.isBlank() || resumo == null || resumo.isBlank() || icone == null) {
            throw new IllegalArgumentException("nível " + id + " incompleto");
        }
        licoes = List.copyOf(licoes == null ? List.of() : licoes);
        if (aberto && licoes.isEmpty()) {
            throw new IllegalArgumentException("nível aberto sem lição: " + id);
        }
        if (!aberto && !licoes.isEmpty()) {
            throw new IllegalArgumentException("nível fechado com lição listada: " + id);
        }
        for (Licao licao : licoes) {
            if (!licao.nivelId().equals(id)) {
                throw new IllegalArgumentException("lição " + licao.id() + " fora do nível " + id);
            }
        }
    }

    /** Endereço da página do nível. */
    public String rota() {
        return "/academia/" + id;
    }
}
