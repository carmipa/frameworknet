package org.framework.net.web.presentation;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Declara {@code Vary: Accept, HX-Request} em toda resposta de erro.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a mesma URL de erro responde três corpos diferentes conforme quem
 * pede: navegação recebe a página HTML do catálogo, o htmx ({@code HX-Request}) e o cliente de máquina
 * recebem JSON ou fragmento (medido: {@code curl /rota-x} devolve JSON, o navegador devolve a página).
 * Sem {@code Vary}, um cache no caminho (navegador, CDN) guarda o primeiro corpo e entrega o JSON a
 * quem navegou, ou a página inteira dentro de um alvo do htmx.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só toca status 400 ou maior — página pública com status 200 não muda
 * de corpo por esses cabeçalhos e não deve fragmentar o cache; valores de {@code Vary} já presentes são
 * preservados, sem duplicar; {@code Vary: *} existente é respeitado como está.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; resposta abaixo de 400 segue como veio.</p>
 */
@Provider
public class VaryNegociacaoErroResponseFilter implements ContainerResponseFilter {

    static final String CABECALHOS_NEGOCIADOS = "Accept, HX-Request";

    @Override
    public void filter(ContainerRequestContext requisicao, ContainerResponseContext resposta) {
        if (resposta.getStatus() < 400) {
            return;
        }
        Set<String> valores = new LinkedHashSet<>();
        for (Object existente : resposta.getHeaders().getOrDefault(HttpHeaders.VARY, java.util.List.of())) {
            for (String parte : String.valueOf(existente).split(",")) {
                if (!parte.isBlank()) {
                    valores.add(parte.trim());
                }
            }
        }
        if (valores.contains("*")) {
            return;
        }
        Set<String> minusculos = new LinkedHashSet<>();
        valores.forEach(v -> minusculos.add(v.toLowerCase(Locale.ROOT)));
        for (String parte : CABECALHOS_NEGOCIADOS.split(",")) {
            String nome = parte.trim();
            if (!minusculos.contains(nome.toLowerCase(Locale.ROOT))) {
                valores.add(nome);
            }
        }
        resposta.getHeaders().putSingle(HttpHeaders.VARY, String.join(", ", valores));
    }
}
