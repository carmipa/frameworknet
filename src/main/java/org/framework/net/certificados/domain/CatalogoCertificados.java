package org.framework.net.certificados.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Conteúdo da aba Geral do módulo de Certificados: três tabelas de referência.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a primeira página do módulo é um catálogo, no
 * mesmo espírito de Portas e Protocolos, mas com três recortes que respondem as
 * perguntas mais frequentes de quem lida com certificados: "que arquivo é esse?"
 * (formatos/extensões), "o que tem dentro?" (campos do X.509) e "qual tipo eu
 * preciso?" (tipos de certificado).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> record puro de dados, carregado de
 * {@code certificados/catalogo.json}. O carregador exige as três listas não
 * vazias — catálogo pela metade não sobe.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nenhum — a validação fica no
 * carregador.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CatalogoCertificados(
        List<Formato> formatos,
        List<CampoX509> campos,
        List<TipoCertificado> tipos) {

    /** Uma linha da tabela de formatos/extensões de arquivo de certificado. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Formato(String extensao, String codificacao, String conteudo, String uso, String ferramenta) {
    }

    /** Uma linha da tabela de campos de um certificado X.509 v3. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CampoX509(String campo, String oque, String exemplo, String nota) {
    }

    /** Uma linha da tabela de tipos de certificado. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TipoCertificado(String tipo, String valida, String uso, String observacao) {
    }
}
