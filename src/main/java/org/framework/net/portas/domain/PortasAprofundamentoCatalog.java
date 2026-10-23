package org.framework.net.portas.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.portas.exception.PortasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carregador do conteúdo dos aprofundamentos de Portas (um por slug).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler, uma vez no boot, todos os
 * {@code /portas/&lt;slug&gt;/conteudo.json} (Anatomia + famílias) num mapa único
 * por slug. Reaproveita o record {@link ProtocoloAprofundamento} — o modelo
 * didático (resumo, conceitos, diagrama, cabeçalho, tabelas, ataques, defesas,
 * laboratório, troubleshooting) é o mesmo dos protocolos; o módulo Portas o
 * consome (acoplamento {@code portas -> protocolos} registrado em
 * {@code ArquiteturaCamadasTest}, o mesmo mecanismo de
 * {@code localizacao -> analiseDidatica}).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> falha fechada no boot ({@code @Startup}) —
 * conteúdo ausente, resumo em branco ou conceitos vazios impedem a subida,
 * nomeando o recurso. Quais slugs carregar vem da fonte única
 * {@link PortaAprofundamento#slugs()}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link PortasException}
 * nomeando o slug/recurso e a seção ausente.</p>
 */
@Startup
@ApplicationScoped
public class PortasAprofundamentoCatalog {

    @Inject
    ObjectMapper objectMapper;

    private Map<String, ProtocoloAprofundamento> porSlug;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        for (String slug : PortaAprofundamento.slugs()) {
            String recurso = "/portas/" + slug + "/conteudo.json";
            try (InputStream input = PortasAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
                if (input == null) {
                    throw new PortasException("Conteúdo do aprofundamento não encontrado: " + recurso);
                }
                ProtocoloAprofundamento conteudo = objectMapper.readValue(input, ProtocoloAprofundamento.class);
                validar(slug, recurso, conteudo);
                mapa.put(slug, conteudo);
            } catch (IOException e) {
                throw new PortasException("Falha ao carregar o conteúdo do aprofundamento " + slug, e);
            }
        }
        this.porSlug = Map.copyOf(mapa);
    }

    /**
     * Conteúdo de um slug.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code null} para slug não
     * carregado — quem chama traduz em erro, nunca em conteúdo de outra página.</p>
     */
    public ProtocoloAprofundamento get(String slug) {
        return slug == null ? null : porSlug.get(slug);
    }

    private static void validar(String slug, String recurso, ProtocoloAprofundamento c) {
        if (c == null || c.resumo() == null || c.resumo().isBlank()) {
            throw new PortasException("Aprofundamento \"" + slug + "\" sem resumo: " + recurso);
        }
        if (c.conceitos() == null || c.conceitos().isEmpty()) {
            throw new PortasException("Aprofundamento \"" + slug + "\" sem conceitos: " + recurso
                    + " — não pode subir incompleto.");
        }
    }
}
