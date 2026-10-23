package org.framework.net.referencias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Guarda dos domínios usados como exemplo no site.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o conteúdo usava {@code exemplo.com} — domínio real (medido em
 * 01/10/2026: ele e qualquer subdomínio resolvem para 104.247.81.99) — em comandos de nmap, testssl, swaks
 * com senha e lftp com senha. Um aluno que copiasse o comando varria ou mandava credencial para um terceiro
 * (auditoria CONT-45). O certo são os nomes reservados para documentação (RFC 2606, RFC 6761) e, para o
 * nmap, o {@code scanme.nmap.org}, que o projeto Nmap autoriza varrer.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) nenhum arquivo do site cita {@code exemplo.com}, {@code .net} ou
 * {@code .org} — o {@code exemplo.com.br} didático do DNS fica de fora, não resolve hoje e não recebe
 * comando de varredura; (2) linha de varredura ou de credencial (nmap, testssl, swaks, lftp, ftp) só aponta
 * para IP, para nome sem ponto (marcador), para {@code scanme.nmap.org} ou para um nome reservado que não
 * resolve (subdomínio de example.com/.net/.org fora o www, ou TLD .example/.test/.invalid/.localhost/.local);
 * e-mail nessas linhas só usa example.com/.net/.org.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista arquivo e trecho; sem nenhum arquivo lido ou
 * sem nenhuma linha de varredura encontrada o teste reprova (alvo vazio não é aprovação).</p>
 */
class DominiosDeExemploGuardTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern DOMINIO_REAL = Pattern.compile(
            "(?:(?<=\\\\n)|(?<![\\w-]))[\\w.-]*(?:exemplo\\.(?:com|net|org)|seu-dominio\\.com)(?!\\.br)(?![\\w-])");
    private static final Pattern FERRAMENTA_ATIVA =
            Pattern.compile("^(?:sudo\\s+)?(?:\\./)?(nmap|testssl\\.sh|swaks|lftp|ftp)\\s");
    private static final Pattern NOME = Pattern.compile("(?<![\\w./-])([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)");
    private static final Pattern IP = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Pattern RESERVADO_SEM_RESOLVER = Pattern.compile(
            "(?:(?!www\\.)[a-z0-9-]+\\.)+example\\.(?:com|net|org)|(?:[a-z0-9-]+\\.)*[a-z0-9-]+\\.(?:example|test|invalid|localhost|local)");
    private static final Pattern EMAIL_RESERVADO = Pattern.compile("example\\.(?:com|net|org)");

    @Test
    void nenhumArquivoDoSiteCitaDominioRealComoExemplo() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int arquivos = 0;
        for (Path p : arquivosDoSite()) {
            arquivos++;
            Matcher m = DOMINIO_REAL.matcher(Files.readString(p));
            while (m.find()) {
                violacoes.add(p + ": " + m.group());
            }
        }
        assertTrue(arquivos > 100, "poucos arquivos lidos (" + arquivos + "): a guarda não enxergou o site");
        assertEquals(List.of(), violacoes);
    }

    @Test
    void varreduraECredencialSoApontamParaAlvoPermitido() throws IOException {
        List<String> violacoes = new ArrayList<>();
        List<String> linhasAtivas = new ArrayList<>();
        for (Path p : arquivosDoSite()) {
            if (!p.toString().endsWith(".json")) {
                continue;
            }
            List<String> textos = new ArrayList<>();
            coletarTextos(JSON.readTree(p.toFile()), textos);
            for (String texto : textos) {
                for (String linha : texto.replace("\\\n", " ").split("\\R")) {
                    String l = linha.strip();
                    if (FERRAMENTA_ATIVA.matcher(l).find()) {
                        linhasAtivas.add(l);
                        String proibido = alvoProibido(l);
                        if (proibido != null) {
                            violacoes.add(p + ": " + proibido + " em: " + l);
                        }
                    }
                }
            }
        }
        assertTrue(linhasAtivas.stream().anyMatch(l -> l.startsWith("nmap")),
                "nenhuma linha de nmap lida: a guarda ficou cega");
        assertTrue(linhasAtivas.stream().anyMatch(l -> l.startsWith("swaks")),
                "nenhuma linha de swaks lida: a guarda ficou cega");
        assertEquals(List.of(), violacoes);
    }

    /** Calibração (A1): o mesmo sinal (um nome com ponto depois da ferramenta) nos dois lados da fronteira. */
    @Test
    void regraDiscriminaAFronteira() {
        assertEquals(null, alvoProibido("nmap -sV scanme.nmap.org"));
        assertEquals(null, alvoProibido("nmap -p 25,110 mail.example.com"));
        assertEquals(null, alvoProibido("nmap -sn 192.168.0.0/24"));
        assertEquals(null, alvoProibido("nmap -p porta host"));
        assertEquals(null, alvoProibido("nmap -p 443 --script ssl-enum-ciphers,http-headers seu-dominio.example"));
        assertEquals(null, alvoProibido("./testssl.sh --protocols --fs servidor.example.com"));
        assertEquals(null, alvoProibido("swaks --server mail.example.com:25 --from teste@example.org --to alvo@example.net"));
        assertEquals(null, alvoProibido("lftp -u paulo,senha ftp.example.com"));
        assertEquals("example.com", alvoProibido("nmap -sS example.com"));
        assertEquals("www.example.com", alvoProibido("nmap -sS www.example.com"));
        assertEquals("exemplo.com.br", alvoProibido("./testssl.sh exemplo.com.br"));
        assertEquals("ftp.exemplo.com", alvoProibido("lftp -u paulo,senha ftp.exemplo.com"));
        assertEquals("@destino.com", alvoProibido("swaks --to beto@destino.com --server smtp.example.com:587"));
        assertTrue(DOMINIO_REAL.matcher("dig exemplo.com A").find());
        assertTrue(DOMINIO_REAL.matcher("\\nexemplo.com.  300 IN A").find());
        assertTrue(DOMINIO_REAL.matcher("curl -IL http://seu-dominio.com").find());
        assertFalse(DOMINIO_REAL.matcher("dig exemplo.com.br A").find());
        assertFalse(DOMINIO_REAL.matcher("dig example.com A").find());
        assertFalse(DOMINIO_REAL.matcher("www.exemplo.com.br").find());
    }

    private static String alvoProibido(String linha) {
        Matcher m = NOME.matcher(linha);
        boolean primeiro = true;
        while (m.find()) {
            String nome = m.group(1).toLowerCase(Locale.ROOT);
            if (primeiro && nome.equals("testssl.sh")) {
                primeiro = false;
                continue;
            }
            primeiro = false;
            boolean email = m.start() > 0 && linha.charAt(m.start() - 1) == '@';
            boolean permitido = email
                    ? EMAIL_RESERVADO.matcher(nome).matches()
                    : IP.matcher(nome).matches()
                    || nome.equals("scanme.nmap.org")
                    || RESERVADO_SEM_RESOLVER.matcher(nome).matches();
            if (!permitido) {
                return (email ? "@" : "") + nome;
            }
        }
        return null;
    }

    private static List<Path> arquivosDoSite() throws IOException {
        try (Stream<Path> s = Stream.concat(Files.walk(Path.of("src/main/resources")), Files.walk(Path.of("src/main/java")))) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.toString();
                        return n.endsWith(".json") || n.endsWith(".html") || n.endsWith(".js") || n.endsWith(".java");
                    })
                    .toList();
        }
    }

    private static void coletarTextos(JsonNode no, List<String> destino) {
        if (no.isTextual()) {
            destino.add(no.asText());
        } else {
            no.forEach(filho -> coletarTextos(filho, destino));
        }
    }
}
