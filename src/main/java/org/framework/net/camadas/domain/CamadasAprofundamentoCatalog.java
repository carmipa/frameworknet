package org.framework.net.camadas.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.camadas.exception.CamadasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carregador do conteúdo do módulo Camadas: a aba Geral ({@code catalogo.json}) e
 * os aprofundamentos ({@code <slug>/conteudo.json}).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler tudo uma vez no boot num mapa por chave. A
 * Geral é carregada sob a chave {@code "catalogo"} — um {@link ProtocoloAprofundamento}
 * com resumo e as tabelas de referência. Reaproveita o record dos protocolos
 * (acoplamento {@code camadas -> protocolos} registrado em
 * {@code ArquiteturaCamadasTest}).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> falha fechada ({@code @Startup}) — conteúdo
 * ausente ou resumo em branco impedem a subida. Quais aprofundamentos carregar vem
 * de {@link CamadaAprofundamento#slugs()}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link CamadasException}.</p>
 */
@Startup
@ApplicationScoped
public class CamadasAprofundamentoCatalog {

    /** Chave da aba Geral (as tabelas de referência) dentro do mapa. */
    public static final String CATALOGO = "catalogo";

    @Inject
    ObjectMapper objectMapper;

    private Map<String, ProtocoloAprofundamento> porChave;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        carregarUm(mapa, CATALOGO, "/camadas/catalogo.json", true);
        for (String slug : CamadaAprofundamento.slugs()) {
            carregarUm(mapa, slug, "/camadas/" + slug + "/conteudo.json", false);
        }
        this.porChave = Map.copyOf(mapa);
    }

    private void carregarUm(Map<String, ProtocoloAprofundamento> mapa, String chave, String recurso, boolean geral) {
        try (InputStream input = CamadasAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
            if (input == null) {
                throw new CamadasException("Conteúdo não encontrado: " + recurso);
            }
            ProtocoloAprofundamento c = objectMapper.readValue(input, ProtocoloAprofundamento.class);
            if (c == null || c.resumo() == null || c.resumo().isBlank()) {
                throw new CamadasException("Conteúdo \"" + chave + "\" sem resumo: " + recurso);
            }
            if (geral) {
                if (c.tabelas() == null || c.tabelas().isEmpty()) {
                    throw new CamadasException("Catálogo Geral sem tabelas: " + recurso);
                }
            } else if (c.conceitos() == null || c.conceitos().isEmpty()) {
                throw new CamadasException("Aprofundamento \"" + chave + "\" sem conceitos: " + recurso);
            }
            mapa.put(chave, c);
        } catch (IOException e) {
            throw new CamadasException("Falha ao carregar " + recurso, e);
        }
    }

    /** Conteúdo por chave ({@code "catalogo"} ou um slug). {@code null} se ausente. */
    public ProtocoloAprofundamento get(String chave) {
        return chave == null ? null : porChave.get(chave);
    }
}
