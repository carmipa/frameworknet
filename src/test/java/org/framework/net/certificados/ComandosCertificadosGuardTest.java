package org.framework.net.certificados;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda dos comandos de certificado que o conteúdo manda rodar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> dois conselhos do conteúdo não faziam o que prometiam (auditoria
 * CONT-44). "Para chaves EC, troque openssl rsa por openssl pkey" com {@code -modulus}: o pkey não tem
 * essa opção (medido no OpenSSL 3.5.8: "Unknown option or cipher: modulus"). E {@code -Docsp.enable=true}
 * como system property: o OCSP do JDK lê a SECURITY property (medido no JDK 25: com o -D,
 * {@code Security.getProperty("ocsp.enable")} continua null) — a revogação ficava desligada em silêncio.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> nenhum campo {@code comando}/{@code comandos} do conteúdo usa
 * {@code openssl pkey ... -modulus} nem passa {@code -Docsp.enable} como opção da JVM. Comentário que
 * explica por que o -D não funciona, {@code -modulus} no x509/rsa e o {@code -Dcom.sun.security.enableCRLDP}
 * continuam permitidos.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e linha; sem nenhum campo de comando
 * lido, ou sem os dois laboratórios corrigidos à vista, o teste reprova (alvo vazio não é aprovação).</p>
 */
class ComandosCertificadosGuardTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PKEY_MODULUS = Pattern.compile("openssl\\s+pkey\\b.*\\s-modulus\\b");
    private static final Pattern OCSP_COMO_OPCAO_DA_JVM =
            Pattern.compile("^(?:#\\s*)?(?:java\\b.*\\s)?-Docsp\\.enable=");

    @Test
    void comandosDeCertificadoUsamOpcoesQueExistem() throws IOException {
        List<String> violacoes = new ArrayList<>();
        List<String> linhas = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json")).toList()) {
                List<String> comandos = new ArrayList<>();
                coletarComandos(JSON.readTree(p.toFile()), comandos);
                for (String comando : comandos) {
                    for (String linha : linhasDeComando(comando)) {
                        linhas.add(linha);
                        if (viola(linha)) {
                            violacoes.add(p + ": " + linha);
                        }
                    }
                }
            }
        }
        assertFalse(linhas.isEmpty(), "nenhum campo comando/comandos lido: a guarda não enxergou o conteúdo");
        assertEquals(List.of(), violacoes);
        assertTrue(linhas.stream().anyMatch(l -> l.matches("openssl pkey -in \\S+ -pubout.*")),
                "nenhuma comparação de par pela chave pública: a guarda ficou cega ou o conteúdo sumiu");
        assertTrue(linhas.stream().anyMatch(l -> l.contains("ocsp.enable=true")),
                "o laboratório de revogação na JVM sumiu ou a guarda ficou cega");
    }

    /** Calibração (A1): o mesmo sinal ("-modulus", "ocsp.enable", "-D") nos dois lados da fronteira. */
    @Test
    void regraDiscriminaAFronteira() {
        assertTrue(viola("openssl pkey -in ec.key -noout -modulus | openssl md5"));
        assertTrue(viola("#   -Docsp.enable=true                        (habilita OCSP)"));
        assertTrue(viola("java -Docsp.enable=true -jar app.jar"));
        assertFalse(viola("openssl x509 -in servidor.crt -noout -modulus | openssl md5"));
        assertFalse(viola("openssl rsa  -in servidor.key -noout -modulus | openssl md5"));
        assertFalse(viola("openssl pkey -in servidor.key -pubout       | openssl md5"));
        assertFalse(viola("#   -Dcom.sun.security.enableCRLDP=true      (habilita uso do CRL Distribution Point)"));
        assertFalse(viola("# O OCSP é SECURITY property: -Docsp.enable não tem efeito."));
        assertFalse(viola("echo \"ocsp.enable=true\" > revogacao.security"));
        assertEquals(List.of("openssl pkey -in k -noout -modulus"),
                linhasDeComando("openssl pkey -in k \\\n  -noout -modulus"));
    }

    private static boolean viola(String linha) {
        return PKEY_MODULUS.matcher(linha).find() || OCSP_COMO_OPCAO_DA_JVM.matcher(linha).find();
    }

    private static List<String> linhasDeComando(String texto) {
        String juntado = texto.replace("\\\r\n", " ").replace("\\\n", " ");
        List<String> linhas = new ArrayList<>();
        for (String linha : juntado.split("\\R")) {
            String limpa = linha.strip().replaceAll("\\s+", " ");
            if (!limpa.isEmpty()) {
                linhas.add(limpa);
            }
        }
        return linhas;
    }

    private static void coletarComandos(JsonNode no, List<String> destino) {
        if (no.isObject()) {
            for (Map.Entry<String, JsonNode> campo : no.properties()) {
                boolean ehComando = campo.getKey().equals("comando") || campo.getKey().equals("comandos");
                if (ehComando && campo.getValue().isTextual()) {
                    destino.add(campo.getValue().asText());
                } else {
                    coletarComandos(campo.getValue(), destino);
                }
            }
        } else if (no.isArray()) {
            no.forEach(filho -> coletarComandos(filho, destino));
        }
    }
}
