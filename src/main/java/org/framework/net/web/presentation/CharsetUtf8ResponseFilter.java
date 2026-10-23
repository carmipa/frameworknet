package org.framework.net.web.presentation;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import java.nio.charset.StandardCharsets;

/**
 * Declara {@code charset=UTF-8} em toda resposta de texto que saiu sem ele.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> onze ExceptionMappers respondiam {@code Content-Type: text/plain} sem
 * charset (medido: {@code curl -D -} no erro do Diagnóstico). O corpo é UTF-8, mas sem o parâmetro o
 * navegador lê como windows-1252 e o aluno vê "inv�lido" no lugar de "inválido" (auditoria FRONT-03).
 * Corrigir no mecanismo vale para os onze e para o próximo mapper que nascer igual.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só toca resposta com corpo e tipo {@code text/*} sem charset declarado;
 * charset já declarado (qualquer um) é respeitado; tipo, subtipo e demais parâmetros são preservados;
 * JSON e binários não mudam (JSON é UTF-8 por definição, RFC 8259 §8.1).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança: resposta sem tipo ou com tipo ilegível segue como
 * veio.</p>
 */
@Provider
public class CharsetUtf8ResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requisicao, ContainerResponseContext resposta) {
        if (!resposta.hasEntity()) {
            return;
        }
        MediaType tipo;
        try {
            tipo = resposta.getMediaType();
        } catch (RuntimeException ilegivel) {
            return;
        }
        if (tipo == null || !"text".equalsIgnoreCase(tipo.getType())
                || tipo.getParameters().containsKey(MediaType.CHARSET_PARAMETER)) {
            return;
        }
        resposta.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE,
                tipo.withCharset(StandardCharsets.UTF_8.name()));
    }
}
