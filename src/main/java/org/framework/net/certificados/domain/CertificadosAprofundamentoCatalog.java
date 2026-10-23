package org.framework.net.certificados.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.certificados.exception.CertificadosException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carregador do conteúdo dos aprofundamentos de Certificados (um por slug).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler, uma vez no boot, todos os
 * {@code /certificados/&lt;slug&gt;/conteudo.json} num mapa por slug. Reaproveita o
 * record {@link ProtocoloAprofundamento} — o modelo didático é o mesmo dos
 * protocolos e das portas; o módulo consome o modelo compartilhado (acoplamento
 * {@code certificados -> protocolos} registrado em {@code ArquiteturaCamadasTest},
 * como {@code portas -> protocolos}).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> falha fechada no boot ({@code @Startup}) —
 * conteúdo ausente, resumo em branco ou conceitos vazios impedem a subida. Quais
 * slugs carregar vem da fonte única {@link CertificadoAprofundamento#slugs()}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link CertificadosException}
 * nomeando o slug/recurso e a seção ausente.</p>
 */
@Startup
@ApplicationScoped
public class CertificadosAprofundamentoCatalog {

    @Inject
    ObjectMapper objectMapper;

    private Map<String, ProtocoloAprofundamento> porSlug;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        for (String slug : CertificadoAprofundamento.slugs()) {
            String recurso = "/certificados/" + slug + "/conteudo.json";
            try (InputStream input = CertificadosAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
                if (input == null) {
                    throw new CertificadosException("Conteúdo do aprofundamento não encontrado: " + recurso);
                }
                ProtocoloAprofundamento conteudo = objectMapper.readValue(input, ProtocoloAprofundamento.class);
                validar(slug, recurso, conteudo);
                mapa.put(slug, conteudo);
            } catch (IOException e) {
                throw new CertificadosException("Falha ao carregar o conteúdo do aprofundamento " + slug, e);
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
            throw new CertificadosException("Aprofundamento \"" + slug + "\" sem resumo: " + recurso);
        }
        if (c.conceitos() == null || c.conceitos().isEmpty()) {
            throw new CertificadosException("Aprofundamento \"" + slug + "\" sem conceitos: " + recurso
                    + " — não pode subir incompleto.");
        }
    }
}
