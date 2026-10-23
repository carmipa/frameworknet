package org.framework.net.criptografia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda dos comandos {@code openssl enc} ensinados no conteúdo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o laboratório de criptografia simétrica mandava cifrar com
 * {@code openssl enc -aes-256-gcm}. O {@code openssl enc} recusa todo modo AEAD — medido no OpenSSL
 * 3.5.8: gcm, ccm, ocb, siv e chacha20-poly1305 saem com "enc: AEAD ciphers not supported" — e o aluno
 * ficava com um comando que não roda (auditoria CONT-39).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum comando {@code openssl enc} do conteúdo usa cifra AEAD; texto
 * explicativo que cita GCM sem ser flag de cifra continua permitido, e {@code -chacha20} sem
 * {@code -poly1305} também (é cifra de fluxo, o enc aceita).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e comando; sem nenhum
 * {@code openssl enc} encontrado, ou sem o do laboratório, o teste reprova (alvo vazio não é
 * aprovação).</p>
 */
class OpensslEncSemAeadGuardTest {

    private static final Pattern COMANDO = Pattern.compile("openssl\\s+enc\\b[^\\n]*");
    private static final Pattern CIFRA_AEAD =
            Pattern.compile("(?i)(?<![\\w-])-[a-z0-9-]*(?:gcm|ccm|ocb|siv|poly1305)\\b");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void conteudoNaoMandaCifrarComAeadNoOpensslEnc() throws IOException {
        List<String> violacoes = new ArrayList<>();
        List<String> comandos = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String nome = p.toString();
                List<String> textos = new ArrayList<>();
                if (nome.endsWith(".json")) {
                    coletarTextos(JSON.readTree(p.toFile()), textos);
                } else if (nome.endsWith(".html") || nome.endsWith(".js")) {
                    textos.add(Files.readString(p));
                } else {
                    continue;
                }
                for (String texto : textos) {
                    for (String comando : comandosOpensslEnc(texto)) {
                        comandos.add(comando);
                        if (usaAead(comando)) {
                            violacoes.add(p + ": " + comando);
                        }
                    }
                }
            }
        }
        assertFalse(comandos.isEmpty(), "nenhum 'openssl enc' lido: a guarda não enxergou o conteúdo");
        assertTrue(comandos.stream().anyMatch(c -> c.contains("-aes-256-cbc -pbkdf2")),
                "o laboratório de cifrar arquivo não foi encontrado: a guarda ficou cega ou o lab sumiu");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): o mesmo sinal ("GCM", "chacha20", "openssl enc") nos dois lados da fronteira. */
    @Test
    void regraDiscriminaAFronteira() {
        assertTrue(usaAead("openssl enc -aes-256-gcm -pbkdf2 -iter 200000 -salt"));
        assertTrue(usaAead("openssl enc -d -aes-256-gcm -pbkdf2"));
        assertTrue(usaAead("openssl enc -aes-128-ccm -k x"));
        assertTrue(usaAead("openssl enc -chacha20-poly1305 -in a"));
        assertTrue(usaAead("openssl enc -aes-256-ocb"));
        assertFalse(usaAead("openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt"));
        assertFalse(usaAead("openssl enc -chacha20 -pbkdf2"));
        assertFalse(usaAead("openssl enc -aes-256-ecb -nosalt"));
        assertFalse(usaAead("openssl enc -base64"));
        assertFalse(usaAead("O openssl enc NÃO aceita modos AEAD (GCM/CCM): cifra-se em CBC"));
        assertEquals(List.of(), comandosOpensslEnc("openssl s_client -cipher ECDHE-RSA-AES256-GCM-SHA384"));
        assertEquals(List.of("openssl enc -aes-256-gcm -pbkdf2 -in a -out b"),
                comandosOpensslEnc("openssl enc -aes-256-gcm -pbkdf2 \\\n  -in a -out b"));
        assertTrue(usaAead(comandosOpensslEnc("openssl enc \\\n  -aes-256-gcm -in a").getFirst()));
    }

    private static boolean usaAead(String comando) {
        return CIFRA_AEAD.matcher(comando).find();
    }

    private static List<String> comandosOpensslEnc(String texto) {
        String juntado = texto.replace("\\\r\n", " ").replace("\\\n", " ");
        List<String> achados = new ArrayList<>();
        Matcher m = COMANDO.matcher(juntado);
        while (m.find()) {
            achados.add(m.group().strip().replaceAll("\\s+", " "));
        }
        return achados;
    }

    private static void coletarTextos(JsonNode no, List<String> destino) {
        if (no.isTextual()) {
            destino.add(no.asText());
        } else {
            no.forEach(filho -> coletarTextos(filho, destino));
        }
    }
}
