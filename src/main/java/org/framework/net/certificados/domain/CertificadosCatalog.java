package org.framework.net.certificados.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.certificados.exception.CertificadosException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Carregador do catálogo (3 tabelas) da aba Geral de Certificados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ler, uma vez no boot,
 * {@code /certificados/catalogo.json} com as tabelas de formatos, campos X.509 e
 * tipos.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> falha fechada ({@code @Startup}) — arquivo
 * ausente ou qualquer uma das três listas vazia impede a subida.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> lança {@link CertificadosException}.</p>
 */
@Startup
@ApplicationScoped
public class CertificadosCatalog {

    private static final String RECURSO = "/certificados/catalogo.json";

    @Inject
    ObjectMapper objectMapper;

    private CatalogoCertificados catalogo;

    @PostConstruct
    void carregar() {
        try (InputStream input = CertificadosCatalog.class.getResourceAsStream(RECURSO)) {
            if (input == null) {
                throw new CertificadosException("Catálogo de certificados não encontrado: " + RECURSO);
            }
            CatalogoCertificados lido = objectMapper.readValue(input, CatalogoCertificados.class);
            validar(lido);
            this.catalogo = lido;
        } catch (IOException e) {
            throw new CertificadosException("Falha ao carregar " + RECURSO, e);
        }
    }

    public CatalogoCertificados getCatalogo() {
        return catalogo;
    }

    private static void validar(CatalogoCertificados c) {
        if (c == null || c.formatos() == null || c.formatos().isEmpty()) {
            throw new CertificadosException("Catálogo sem a tabela de formatos: " + RECURSO);
        }
        if (c.campos() == null || c.campos().isEmpty()) {
            throw new CertificadosException("Catálogo sem a tabela de campos X.509: " + RECURSO);
        }
        if (c.tipos() == null || c.tipos().isEmpty()) {
            throw new CertificadosException("Catálogo sem a tabela de tipos: " + RECURSO);
        }
    }
}
