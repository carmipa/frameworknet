package org.framework.net.protocolos;

import org.framework.net.protocolos.domain.ProtocoloAprofundamento.Tabela;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dicas de coluna das tabelas de aprofundamento.
 *
 * <p><b>Propósito de negócio:</b> cada cabeçalho de coluna mostra a dica da posição correspondente em
 * {@code dicas_colunas}. Uma dica a mais ou a menos desloca todas as seguintes, e a página passa a
 * explicar a coluna errada sem nenhum erro visível.</p>
 *
 * <p><b>Invariantes do domínio:</b> sem dicas é válido (a tabela só não ganha balão); com dicas, uma
 * por coluna, nem mais nem menos.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a construção com contagem divergente lança
 * {@link IllegalArgumentException}, o que impede a subida do catálogo que leu o JSON.</p>
 */
@DisplayName("Protocolos: dicas de coluna alinhadas com as colunas")
class TabelaDicasColunasTest {

    private static final List<String> COLUNAS = List.of("Porta", "Serviço", "Transporte");
    private static final List<List<String>> LINHAS = List.of(List.of("22", "SSH", "TCP"));

    @Test
    @DisplayName("tabela sem dicas continua válida")
    void semDicas() {
        assertDoesNotThrow(() -> new Tabela("T", COLUNAS, LINHAS, null, null));
    }

    @Test
    @DisplayName("uma dica por coluna é aceita")
    void umaPorColuna() {
        assertDoesNotThrow(() -> new Tabela("T", COLUNAS, LINHAS, "dica",
                List.of("Número da porta", "Protocolo de aplicação", "TCP ou UDP")));
    }

    @Test
    @DisplayName("dica faltando ou sobrando é recusada")
    void contagemDivergente() {
        assertThrows(IllegalArgumentException.class, () -> new Tabela("T", COLUNAS, LINHAS, null,
                List.of("Número da porta", "Protocolo de aplicação")));
        assertThrows(IllegalArgumentException.class, () -> new Tabela("T", COLUNAS, LINHAS, null,
                List.of("a", "b", "c", "d")));
    }
}
