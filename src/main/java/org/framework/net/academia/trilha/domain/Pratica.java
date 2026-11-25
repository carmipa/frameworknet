package org.framework.net.academia.trilha.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Onde praticar, no site, o tema de uma lição: o único link "ver a ferramenta completa" do fim da lição.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> quem termina a lição continua no mesmo tema com números reais, na ferramenta ou no
 * aprofundamento que já existe — a lição deixa de ser um beco sem saída.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a rota é interna (começa com "/", sem esquema nem host) e, quando tem consulta,
 * é um único parâmetro de vocabulário fixo (ex.: {@code ?tab=mask}); o rótulo não é vazio; o ícone é um glifo
 * Material Symbols (mesmo conceito, mesmo glifo do menu). Um link por lição (decisão D2 do plano da Academia).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> o construtor lança {@link IllegalArgumentException} para rota, rótulo
 * ou ícone fora do formato — o catálogo inteiro falha na carga da classe, nunca uma página com link quebrado.</p>
 */
public record Pratica(String rota, String rotulo, String icone) {

    private static final Pattern ROTA = Pattern.compile("^/[a-z0-9/-]*(\\?[a-z_]+=[a-z0-9_-]+)?$");
    private static final Pattern GLIFO = Pattern.compile("^[a-z][a-z0-9_]*$");

    public Pratica {
        Objects.requireNonNull(rota, "rota");
        if (!ROTA.matcher(rota).matches()) {
            throw new IllegalArgumentException("rota de prática fora do formato interno: " + rota);
        }
        if (rotulo == null || rotulo.isBlank()) {
            throw new IllegalArgumentException("prática sem rótulo: " + rota);
        }
        if (icone == null || !GLIFO.matcher(icone).matches()) {
            throw new IllegalArgumentException("prática com ícone inválido: " + icone);
        }
    }

    /** A rota sem a consulta (para conferir que a página existe). */
    public String caminho() {
        int q = rota.indexOf('?');
        return q < 0 ? rota : rota.substring(0, q);
    }
}
