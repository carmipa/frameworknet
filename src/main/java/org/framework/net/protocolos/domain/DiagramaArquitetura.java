package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Diagrama de arquitetura/fluxo de um protocolo, renderizado por Mermaid no cliente.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> tipo COMPARTILHADO por todas as páginas de
 * aprofundamento — as genéricas ({@link ProtocoloAprofundamento}) e as dedicadas
 * (DNS, BGP, SSH, TLS). Antes cada aprofundamento genérico tinha seu próprio
 * {@code Diagrama}; extrair para um record único evita quatro cópias do mesmo
 * conceito quando as páginas dedicadas passaram a ganhar diagrama.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> record puro de dados. {@code definicao} é o
 * texto Mermaid (ex.: {@code sequenceDiagram ...}); o Qute o escapa e o Mermaid
 * o lê do {@code textContent}, então não precisa de {@code .raw}.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nenhum — a ausência é tratada no
 * template ({@code {#if conteudo.diagrama}}) e no carregador.</p>
 *
 * @param titulo   título da seção do diagrama
 * @param definicao definição Mermaid do diagrama
 * @param legenda  legenda que explica como ler o diagrama
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiagramaArquitetura(String titulo, String definicao, String legenda) {
}
