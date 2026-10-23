package org.framework.net.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O perfil de produção não confia em host nem prefixo vindos de cabeçalho (auditoria OPS-07). O
 * ProxyEncaminhamentoHttpTest prova o comportamento com um perfil que ESPELHA a produção; esta guarda
 * impede que os dois se separem.
 */
class ProducaoProxyConfigGuardTest {

    @Test
    void producaoDesligaHostEPrefixoEncaminhados() throws IOException {
        List<String> linhas = Files.readAllLines(Path.of("src/main/resources/application-prod.properties"), StandardCharsets.UTF_8);
        assertTrue(linhas.size() > 10, "instrumento cego: perfil de produção não lido");
        assertTrue(linhas.contains("quarkus.http.proxy.enable-forwarded-host=false"), "host do cliente aceito em produção");
        assertTrue(linhas.contains("quarkus.http.proxy.enable-forwarded-prefix=false"), "prefixo do cliente aceito em produção");
        assertTrue(linhas.stream().noneMatch(l -> l.matches("quarkus\\.http\\.proxy\\.enable-forwarded-(host|prefix)=true")),
                "valor contraditório repetido no arquivo");
    }
}
