package org.framework.net.certificados;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda das frases que citam o Inspetor TLS.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o Inspetor TLS é uma simulação — o aluno informa nome, validade, emissor,
 * versão e cipher, e o {@code TlsInspectorService} aplica as regras sem abrir conexão (o próprio Javadoc
 * dele diz "É simulação"). O conteúdo dizia, em dezenas de lugares, que ele "mostra a cadeia real de
 * qualquer host", "ao vivo", com o status_request "na prática" (auditoria CONT-43): o aluno ia procurar ali
 * o que só o openssl s_client mostra.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> frase que cita o Inspetor TLS e fala de cadeia/conexão real, ao vivo, na
 * prática, do que o servidor realmente serve, de campos decodificados ou do status_request tem de dizer, na
 * mesma frase, que o Inspetor simula.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e frase; sem nenhuma frase do Inspetor
 * encontrada o teste reprova (alvo vazio não é aprovação).</p>
 */
class InspetorTlsSimulacaoGuardTest {

    private static final Pattern INSPETOR = Pattern.compile("Inspetor (?:TLS|de TLS)");
    private static final Pattern PROMESSA_DE_REAL = Pattern.compile(
            "cadeia real|conex[ãa]o real|ao vivo|na pr[áa]tica|realmente serv|decodificad|status_request"
                    + "|qualquer host|qualquer servidor");
    private static final Pattern DIZ_QUE_SIMULA = Pattern.compile("simula");

    @Test
    void frasesDoInspetorNaoPrometemConexaoReal() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int frases = 0;
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json") || x.toString().endsWith(".html")).toList()) {
                for (String frase : frasesDoInspetor(Files.readString(p))) {
                    frases++;
                    if (prometeReal(frase)) {
                        violacoes.add(p + ": " + frase);
                    }
                }
            }
        }
        assertTrue(frases > 10, "só " + frases + " frases do Inspetor lidas: a guarda ficou cega");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): o mesmo sinal ("real", "Inspetor") nos dois lados da fronteira. */
    @Test
    void regraDiscriminaAFronteira() {
        assertTrue(prometeReal("O Inspetor TLS do site mostra essa cadeia real para qualquer servidor."));
        assertTrue(prometeReal("o Inspetor TLS, onde a extensão status_request aparece na prática."));
        assertTrue(prometeReal("o Inspetor TLS mostram esse certificado sendo negociado ao vivo durante a conexão."));
        assertFalse(prometeReal("Para ver a cadeia real de um servidor, use o openssl s_client -showcerts; "
                + "o Inspetor TLS do site simula a validação com os dados que você informa."));
        assertFalse(prometeReal("Depois, informe esses mesmos campos no Inspetor TLS do site."));
        assertEquals(2, frasesDoInspetor("A cadeia real sai do openssl. O Inspetor TLS simula. "
                + "\"o Inspetor de TLS julga\" e nada mais").size());
    }

    private static boolean prometeReal(String frase) {
        String f = frase.toLowerCase(Locale.ROOT);
        return PROMESSA_DE_REAL.matcher(f).find() && !DIZ_QUE_SIMULA.matcher(f).find();
    }

    private static List<String> frasesDoInspetor(String texto) {
        List<String> frases = new ArrayList<>();
        Matcher m = INSPETOR.matcher(texto);
        while (m.find()) {
            int inicio = Math.max(Math.max(texto.lastIndexOf(". ", m.start()), texto.lastIndexOf('"', m.start())), -1) + 1;
            int fimPonto = texto.indexOf(". ", m.end());
            int fimAspas = texto.indexOf('"', m.end());
            int fim = fimPonto < 0 ? fimAspas : fimAspas < 0 ? fimPonto : Math.min(fimPonto, fimAspas);
            frases.add(texto.substring(inicio, fim < 0 ? texto.length() : fim).strip());
        }
        return frases;
    }
}
