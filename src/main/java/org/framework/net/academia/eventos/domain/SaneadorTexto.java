package org.framework.net.academia.eventos.domain;

import java.util.regex.Pattern;

/**
 * Limpa o texto livre que chega do navegador antes de ele ir para log ou telemetria.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a mensagem de um erro de JavaScript pode carregar o que o aluno
 * digitou — um IP, um e-mail, um CPF. A telemetria da Academia não guarda dado pessoal
 * (INV-ACAD-005), então o texto livre perde tudo o que poderia identificar alguém e fica só a
 * forma do erro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o texto é normalizado (NFKC) antes de tudo — "＠" de largura
 * cheia vira "@" e "²" vira "2" (auditoria ACAD-26); todo algarismo vira {@code #} (some IP, CPF,
 * telefone, número digitado); toda palavra com {@code @}, mesmo com espaço em volta, vira
 * {@code [removido]} (some e-mail e usuário); sequência hexadecimal com dois-pontos (pedaço de IPv6)
 * vira {@code [removido]};
 * caracteres de controle viram espaço; o resultado tem no máximo {@link #TETO} caracteres. A
 * ordem importa: e-mail sai antes dos algarismos, senão {@code a1@b.com} sobraria em parte.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada nula vira texto vazio; nunca lança.</p>
 */
public final class SaneadorTexto {

    /** Tamanho máximo do texto saneado. */
    public static final int TETO = 160;

    private static final Pattern COM_ARROBA = Pattern.compile("\\S*\\s?@\\s?\\S*");
    private static final Pattern IPV6 = Pattern.compile("(?i)[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,}");
    private static final Pattern ALGARISMO = Pattern.compile("[\\p{Nd}\\p{No}]");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern ESPACOS = Pattern.compile("\\s{2,}");

    private SaneadorTexto() {
    }

    /** O texto sem algarismos, sem palavras com arroba, sem controle e com teto. */
    public static String sanear(String entrada) {
        if (entrada == null) {
            return "";
        }
        String bruto = entrada.length() > TETO * 4 ? entrada.substring(0, TETO * 4) : entrada;
        bruto = java.text.Normalizer.normalize(bruto, java.text.Normalizer.Form.NFKC);
        String limpo = COM_ARROBA.matcher(bruto).replaceAll("[removido]");
        limpo = IPV6.matcher(limpo).replaceAll("[removido]");
        limpo = ALGARISMO.matcher(limpo).replaceAll("#");
        limpo = CONTROLE.matcher(limpo).replaceAll(" ");
        limpo = ESPACOS.matcher(limpo).replaceAll(" ").strip();
        return limpo.length() > TETO ? limpo.substring(0, TETO) : limpo;
    }
}
