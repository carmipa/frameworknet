package org.framework.net.calculadora;

import org.framework.net.calculadora.domain.SubnetKernel;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mensagem da Calculadora para bloco sem prefixo (auditoria FRONT-20): com texto que não é IP, a
 * resposta é "IP inválido", nunca "informe xyz!@#/24"; com IP válido sem prefixo, a dica de formato
 * continua (A1, mesmo sinal: falta o prefixo).
 */
class SubnetKernelMensagemTest {

    private final SubnetKernel kernel = new SubnetKernel();

    @Test
    void textoQueNaoEhIpNaoViraExemploDeFormato() {
        CalculadoraException e = assertThrows(CalculadoraException.class,
                () -> kernel.parseRede("xyz!@#", null, "Bloco base"));
        assertFalse(e.getMessage().contains("xyz!@#/24"), e.getMessage());
    }

    @Test
    void ipValidoSemPrefixoContinuaComADicaDeFormato() {
        CalculadoraException e = assertThrows(CalculadoraException.class,
                () -> kernel.parseRede("10.0.0.0", null, "Bloco base"));
        assertTrue(e.getMessage().contains("10.0.0.0/24"), e.getMessage());
        assertEquals(24, kernel.parseRede("10.0.0.0", 24, "Bloco base").prefixo());
    }
}
