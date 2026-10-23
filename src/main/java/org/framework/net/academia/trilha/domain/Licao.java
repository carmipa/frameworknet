package org.framework.net.academia.trilha.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Uma lição da Academia, como o catálogo a declara.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> é o que a landing lista, o que a página da lição mostra no
 * cabeçalho e o que a telemetria e o progresso usam como chave. O {@code id} é a identidade que o
 * navegador guarda no progresso local — por isso ele nunca muda de nome depois de publicado
 * (renomear apagaria, em silêncio, o progresso de quem já fez a lição).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code id} no formato {@code nivel.licao} em minúsculas, sem
 * acento ({@code fundamentos.binario}); rota {@code /academia/<nivel>/<licao>}, derivada do id e
 * nunca digitada à parte; título e resumo não vazios; ícone é nome de glifo do Material Symbols
 * (minúsculas e sublinhado).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> qualquer campo fora da regra lança
 * {@link IllegalArgumentException} na construção — o catálogo é verificado no build pelo teste e
 * no arranque pela verificação da trilha, nunca no meio de uma requisição.</p>
 *
 * @param id       identidade estável, {@code nivel.licao}
 * @param titulo   título curto, para cartão e cabeçalho
 * @param resumo   uma frase do que o aluno faz na lição
 * @param icone    glifo do Material Symbols
 * @param minutos  duração estimada, para o aluno planejar
 */
public record Licao(String id, String titulo, String resumo, String icone, int minutos) {

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]*\\.[a-z][a-z0-9-]*$");
    private static final Pattern GLIFO = Pattern.compile("^[a-z][a-z0-9_]*$");

    public Licao {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id de lição fora do formato nivel.licao: " + id);
        }
        if (titulo == null || titulo.isBlank() || resumo == null || resumo.isBlank()) {
            throw new IllegalArgumentException("lição " + id + " sem título ou resumo");
        }
        if (icone == null || !GLIFO.matcher(icone).matches()) {
            throw new IllegalArgumentException("lição " + id + " com ícone inválido: " + icone);
        }
        if (minutos < 1 || minutos > 120) {
            throw new IllegalArgumentException("lição " + id + " com duração fora de 1..120 min");
        }
    }

    /** Nível a que a lição pertence ({@code fundamentos}). */
    public String nivelId() {
        return id.substring(0, id.indexOf('.'));
    }

    /** Parte da lição no endereço ({@code binario}). */
    public String slug() {
        return id.substring(id.indexOf('.') + 1);
    }

    /** Endereço público da lição. */
    public String rota() {
        return "/academia/" + nivelId() + "/" + slug();
    }
}
