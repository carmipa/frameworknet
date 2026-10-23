package org.framework.net.web;

import org.framework.net.web.support.IconesColuna;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mapa de ícone por coluna: garante ícone semântico para os cabeçalhos conhecidos
 * e um ícone neutro (nunca vazio) para o desconhecido/nulo — é função total.
 */
@DisplayName("Web: ícone por coluna de tabela")
class IconesColunaTest {

    @Test
    @DisplayName("colunas conhecidas ganham ícone semântico (ignorando caixa e espaços)")
    void conhecidas() {
        assertEquals("settings_ethernet", IconesColuna.iconeColuna("Porta"));
        assertEquals("settings_ethernet", IconesColuna.iconeColuna("  PORTA  "));
        assertEquals("layers", IconesColuna.iconeColuna("Camada"));
        assertEquals("shield", IconesColuna.iconeColuna("Seguro"));
        assertEquals("swap_horiz", IconesColuna.iconeColuna("Transporte"));
        assertEquals("key", IconesColuna.iconeColuna("Chave"));
    }

    @Test
    @DisplayName("desconhecido, nulo ou vazio caem num ícone neutro — nunca vazio")
    void fallback() {
        assertEquals("view_column", IconesColuna.iconeColuna("Coluna Totalmente Nova"));
        assertEquals("view_column", IconesColuna.iconeColuna(null));
        assertEquals("view_column", IconesColuna.iconeColuna("   "));
    }
}
