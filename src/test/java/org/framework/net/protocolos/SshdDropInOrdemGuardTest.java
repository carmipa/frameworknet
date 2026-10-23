package org.framework.net.protocolos;

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
 * Guarda dos arquivos de {@code sshd_config.d} ensinados no conteúdo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o laboratório de endurecimento do SSH mandava gravar
 * {@code 99-hardening.conf}. No sshd_config vale o PRIMEIRO valor lido, e os arquivos entram em ordem
 * alfabética: o {@code 50-cloud-init.conf} das imagens de nuvem, com {@code PasswordAuthentication yes},
 * vencia — o aluno achava que tinha desligado a senha (auditoria CONT-16).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todo {@code sshd_config.d/NN-...} citado no conteúdo tem NN menor
 * que 50; o conteúdo que grava um drop-in também mostra o {@code sshd -T} (configuração efetiva).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia o arquivo e o drop-in; sem nenhum JSON
 * lido o teste reprova (alvo vazio não é aprovação).</p>
 */
class SshdDropInOrdemGuardTest {

    private static final Pattern DROP_IN = Pattern.compile("sshd_config\\.d/(\\d{2})-[\\w.-]+");

    @Test
    void dropInDeEndurecimentoEntraAntesDoCloudInit() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int arquivos = 0;
        int dropIns = 0;
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json")).toList()) {
                arquivos++;
                String texto = Files.readString(p);
                Matcher m = DROP_IN.matcher(texto);
                while (m.find()) {
                    dropIns++;
                    if (Integer.parseInt(m.group(1)) >= 50) {
                        violacoes.add(p + ": " + m.group());
                    }
                }
                if (texto.contains("sshd_config.d/") && texto.contains("tee /etc/ssh/sshd_config.d")) {
                    assertTrue(texto.contains("sshd -T"), p + " grava drop-in sem mostrar o 'sshd -T'");
                }
            }
        }
        assertTrue(arquivos > 0, "nenhum JSON lido: a guarda não enxergou o conteúdo");
        assertTrue(dropIns > 0, "nenhum drop-in encontrado: o laboratório do SSH sumiu ou a guarda ficou cega");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): "99-" é recusado e "00-"/"10-" passam pela mesma regra. */
    @Test
    void regraDiscriminaAFronteira() {
        assertFalse(numeroOk("sudo tee /etc/ssh/sshd_config.d/99-hardening.conf"));
        assertFalse(numeroOk("/etc/ssh/sshd_config.d/50-hardening.conf"));
        assertTrue(numeroOk("/etc/ssh/sshd_config.d/00-hardening.conf"));
        assertTrue(numeroOk("/etc/ssh/sshd_config.d/49-x.conf"));
    }

    private static boolean numeroOk(String texto) {
        Matcher m = DROP_IN.matcher(texto);
        assertTrue(m.find(), "o padrão não casou: " + texto);
        return Integer.parseInt(m.group(1)) < 50;
    }
}
