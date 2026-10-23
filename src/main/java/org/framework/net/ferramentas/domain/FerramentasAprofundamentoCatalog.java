package org.framework.net.ferramentas.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.ferramentas.exception.FerramentasException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Carrega, no boot (falha fechada), a aba Geral (catalogo.json) e os aprofundamentos do módulo Ferramentas. Reaproveita ProtocoloAprofundamento (acoplamento ferramentas -> protocolos registrado). */
@Startup
@ApplicationScoped
public class FerramentasAprofundamentoCatalog {

    public static final String CATALOGO = "catalogo";

    @Inject ObjectMapper objectMapper;
    private Map<String, ProtocoloAprofundamento> porChave;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        carregarUm(mapa, CATALOGO, "/ferramentas/catalogo.json", true);
        for (String slug : FerramentasAprofundamento.slugs()) {
            carregarUm(mapa, slug, "/ferramentas/" + slug + "/conteudo.json", false);
        }
        this.porChave = Map.copyOf(mapa);
    }

    private void carregarUm(Map<String, ProtocoloAprofundamento> mapa, String chave, String recurso, boolean geral) {
        try (InputStream input = FerramentasAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
            if (input == null) { throw new FerramentasException("Conteúdo não encontrado: " + recurso); }
            ProtocoloAprofundamento c = objectMapper.readValue(input, ProtocoloAprofundamento.class);
            if (c == null || c.resumo() == null || c.resumo().isBlank()) {
                throw new FerramentasException("Conteúdo \"" + chave + "\" sem resumo: " + recurso);
            }
            if (geral) {
                if (c.tabelas() == null || c.tabelas().isEmpty()) { throw new FerramentasException("Catálogo Geral sem tabelas: " + recurso); }
            } else if (c.conceitos() == null || c.conceitos().isEmpty()) {
                throw new FerramentasException("Aprofundamento \"" + chave + "\" sem conceitos: " + recurso);
            }
            mapa.put(chave, c);
        } catch (IOException e) { throw new FerramentasException("Falha ao carregar " + recurso, e); }
    }

    public ProtocoloAprofundamento get(String chave) { return chave == null ? null : porChave.get(chave); }
}
