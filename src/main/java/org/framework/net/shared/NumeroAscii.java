package org.framework.net.shared;

import java.util.OptionalInt;

/**
 * Leitura de números inteiros digitados em formulário.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> todo campo numérico do site (hosts, CIDR, página, limite) passa por
 * aqui antes do {@code Integer.parseInt}. O padrão que se repetia — {@code Character::isDigit} e depois
 * {@code parseInt} — deixava passar dígitos de outros alfabetos e números longos demais, e
 * "99999999999" virava {@code NumberFormatException} e página de erro 500 (auditoria CALC-34).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> só dígitos ASCII de 0 a 9; nada de sinal, espaço ou separador; no
 * máximo 9 dígitos para caber em {@code int} sem estourar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; texto nulo, vazio, com outro caractere ou longo
 * demais devolve {@code false} / {@link OptionalInt#empty()}, e quem chama decide a mensagem.</p>
 */
public final class NumeroAscii {

    /** Maior quantidade de dígitos que cabe em {@code int} sem estouro (999.999.999). */
    public static final int MAX_DIGITOS_INT = 9;

    private NumeroAscii() {
    }

    /** O texto é composto só de dígitos ASCII (e não é vazio)? */
    public static boolean digitosAscii(String texto) {
        return texto != null && !texto.isEmpty() && texto.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * {@link Integer#parseInt} restrito a dígitos ASCII: sinal ("+24", "-1") e dígito de outro alfabeto ("٢٤")
     * viram {@link NumberFormatException}, como o texto não numérico. O {@code parseInt} do Java aceita os dois e
     * a tela exibia "+24" como /24 (BACK-10, auditoria de 22/10/2026).
     */
    public static int parseIntEstrito(String texto) {
        if (!digitosAscii(texto) || texto.length() > MAX_DIGITOS_INT) {
            throw new NumberFormatException("não é inteiro de dígitos ASCII: " + texto);
        }
        return Integer.parseInt(texto);
    }

    /** {@link Long#parseLong} restrito a dígitos ASCII (até 18), pelo mesmo motivo de {@link #parseIntEstrito}. */
    public static long parseLongEstrito(String texto) {
        if (!digitosAscii(texto) || texto.length() > 18) {
            throw new NumberFormatException("não é inteiro de dígitos ASCII: " + texto);
        }
        return Long.parseLong(texto);
    }

    /** Inteiro não negativo com até {@code maxDigitos} dígitos ASCII, ou vazio. */
    public static OptionalInt inteiro(String texto, int maxDigitos) {
        if (!digitosAscii(texto) || texto.length() > Math.min(maxDigitos, MAX_DIGITOS_INT)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(texto));
    }
}
