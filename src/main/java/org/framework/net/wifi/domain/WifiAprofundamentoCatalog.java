package org.framework.net.wifi.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.wifi.exception.WifiException;
import org.framework.net.protocolos.domain.ProtocoloAprofundamento;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Carrega, no boot (falha fechada), a aba Geral (catalogo.json) e os aprofundamentos do módulo Wifi. Reaproveita ProtocoloAprofundamento (acoplamento wifi -> protocolos registrado). */
@Startup
@ApplicationScoped
public class WifiAprofundamentoCatalog {

    public static final String CATALOGO = "catalogo";

    @Inject ObjectMapper objectMapper;
    private Map<String, ProtocoloAprofundamento> porChave;

    @PostConstruct
    void carregar() {
        Map<String, ProtocoloAprofundamento> mapa = new LinkedHashMap<>();
        carregarUm(mapa, CATALOGO, "/wifi/catalogo.json", true);
        for (String slug : WifiAprofundamento.slugs()) {
            carregarUm(mapa, slug, "/wifi/" + slug + "/conteudo.json", false);
        }
        this.porChave = Map.copyOf(mapa);
    }

    private void carregarUm(Map<String, ProtocoloAprofundamento> mapa, String chave, String recurso, boolean geral) {
        try (InputStream input = WifiAprofundamentoCatalog.class.getResourceAsStream(recurso)) {
            if (input == null) { throw new WifiException("Conteúdo não encontrado: " + recurso); }
            ProtocoloAprofundamento c = objectMapper.readValue(input, ProtocoloAprofundamento.class);
            if (c == null || c.resumo() == null || c.resumo().isBlank()) {
                throw new WifiException("Conteúdo \"" + chave + "\" sem resumo: " + recurso);
            }
            if (geral) {
                if (c.tabelas() == null || c.tabelas().isEmpty()) { throw new WifiException("Catálogo Geral sem tabelas: " + recurso); }
            } else if (c.conceitos() == null || c.conceitos().isEmpty()) {
                throw new WifiException("Aprofundamento \"" + chave + "\" sem conceitos: " + recurso);
            }
            mapa.put(chave, c);
        } catch (IOException e) { throw new WifiException("Falha ao carregar " + recurso, e); }
    }

    public ProtocoloAprofundamento get(String chave) { return chave == null ? null : porChave.get(chave); }
}
