package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.exception.ProtocolosException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carregador do conteúdo dos aprofundamentos GENÉRICOS (um por slug).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler, uma vez no boot, todos os
 * {@code /protocolos/&lt;slug&gt;/conteudo.json} dos protocolos genéricos
 * (HTTP, FTP, SMTP, Telnet, Handshake…). Um mapa único por slug substitui um
 * catálogo por protocolo — o custo de um protocolo novo cai para um JSON.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> falha fechada no boot ({@code @Startup}) —
 * conteúdo ausente, resumo em branco ou seção de conceitos vazia impede a subida,
 * nomeando o recurso. Quais slugs carregar vem da fonte única
 * {@link AprofundamentoProtocolo#slugsGenericos()}: registro e conteúdo não
 * divergem.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link ProtocolosException}
 * nomeando o slug/recurso e a seção ausente.</p>
 */
@Startup
@ApplicationScoped
public class ProtocolosAprofundamentoCatalog {

    @Inject
    ObjectMapper objectMapper;

    private Map<String, ProtocoloAprofundamento> porSlug;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        for (String slug : AprofundamentoProtocolo.slugsGenericos()) {
            String recurso = "/protocolos/" + slug + "/conteudo.json";
            try (InputStream input = ProtocolosAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
                if (input == null) {
                    throw new ProtocolosException("Conteúdo do aprofundamento não encontrado: " + recurso);
                }
                ProtocoloAprofundamento conteudo = objectMapper.readValue(input, ProtocoloAprofundamento.class);
                validar(slug, recurso, conteudo);
                mapa.put(slug, conteudo);
            } catch (IOException e) {
                throw new ProtocolosException("Falha ao carregar o conteúdo do aprofundamento " + slug, e);
            }
        }
        this.porSlug = Map.copyOf(mapa);
    }

    /**
     * Conteúdo de um slug genérico.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code null} para slug não
     * carregado — quem chama traduz em erro, nunca em conteúdo de outro protocolo.</p>
     */
    public ProtocoloAprofundamento get(String slug) {
        return slug == null ? null : porSlug.get(slug);
    }

    private static void validar(String slug, String recurso, ProtocoloAprofundamento c) {
        if (c == null || c.resumo() == null || c.resumo().isBlank()) {
            throw new ProtocolosException("Aprofundamento \"" + slug + "\" sem resumo: " + recurso);
        }
        if (c.conceitos() == null || c.conceitos().isEmpty()) {
            throw new ProtocolosException("Aprofundamento \"" + slug + "\" sem conceitos: " + recurso
                    + " — não pode subir incompleto.");
        }
    }
}
