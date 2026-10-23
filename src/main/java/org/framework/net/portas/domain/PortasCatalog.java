package org.framework.net.portas.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.portas.exception.PortasException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class PortasCatalog {

    private static final String CATALOGO_RESOURCE = "/portas/catalogo.json";

    @Inject
    ObjectMapper objectMapper;

    private List<PortaItem> catalogo = List.of();

    @PostConstruct
    void carregar() {
        try (InputStream input = PortasCatalog.class.getResourceAsStream(CATALOGO_RESOURCE)) {
            if (input == null) {
                throw new PortasException("Recurso de catálogo não encontrado: " + CATALOGO_RESOURCE);
            }
            List<PortaItem> carregado = objectMapper.readValue(input, new TypeReference<>() {
            });
            if (carregado == null || carregado.isEmpty()) {
                throw new PortasException("Catálogo de portas vazio ou inválido");
            }
            this.catalogo = Collections.unmodifiableList(carregado);
        } catch (IOException e) {
            throw new PortasException("Falha ao carregar catálogo de portas", e);
        }
    }

    public List<PortaItem> getCatalogo() {
        return catalogo;
    }
}
