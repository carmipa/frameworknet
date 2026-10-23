package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.exception.ProtocolosException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class ProtocolosCatalog {

    private static final String CATALOGO_RESOURCE = "/protocolos/catalogo.json";

    @Inject
    ObjectMapper objectMapper;

    private List<ProtocoloItem> catalogo = List.of();

    @PostConstruct
    void carregar() {
        try (InputStream input = ProtocolosCatalog.class.getResourceAsStream(CATALOGO_RESOURCE)) {
            if (input == null) {
                throw new ProtocolosException("Recurso de catálogo não encontrado: " + CATALOGO_RESOURCE);
            }
            List<ProtocoloItem> carregado = objectMapper.readValue(input, new TypeReference<>() {
            });
            if (carregado == null || carregado.isEmpty()) {
                throw new ProtocolosException("Catálogo de protocolos vazio ou inválido");
            }
            this.catalogo = Collections.unmodifiableList(carregado);
        } catch (IOException e) {
            throw new ProtocolosException("Falha ao carregar catálogo de protocolos", e);
        }
    }

    public List<ProtocoloItem> getCatalogo() {
        return catalogo;
    }
}
