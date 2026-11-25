package org.framework.net.protocolos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todo teste rápido dos aprofundamentos tem uma só alternativa certa, o porquê de cada uma e a fonte (proposta C03).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o teste rápido ensina. Pergunta com duas certas, sem certa, sem o porquê ou sem
 * fonte ensina errado ou sem como conferir; e o servidor conta acerto pela PRIMEIRA certa
 * ({@code ProtocoloAprofundamento.Pergunta#indiceCorreta}), então duas marcadas fariam a contagem discordar de quem
 * escreveu a pergunta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> em todo {@code protocolos/*}{@code /conteudo.json} com {@code autoavaliacao}: lista
 * não vazia; cada pergunta com enunciado, de 2 a 6 alternativas de texto distinto, exatamente uma correta (booleano
 * {@code true}), todas com porquê, e fonte que cita um documento normativo (RFC, IETF, IEEE, ISO, ITU, IANA, W3C ou
 * WHATWG). No piloto,
 * a alternativa certa de cada pergunta é a que as RFCs dão como certa, escrita à mão abaixo (A3) e conferida no texto
 * das RFCs em 25/11/2026. O piloto (tcp, ipv4, http) tem de ser lido: controle positivo contra instrumento cego.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> reprova listando arquivo, pergunta e defeito. A guarda confere a forma da
 * fonte, não se a seção citada sustenta a resposta: isso é o gabarito do piloto, e para pergunta nova é revisão.</p>
 */
@DisplayName("Protocolos: o teste rápido tem uma certa, o porquê de cada alternativa e a fonte")
class AutoavaliacaoGuardTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RAIZ = Path.of("src/main/resources/protocolos");
    private static final Pattern NORMA = Pattern.compile("(?i)\\b(?:RFC|IETF|IEEE|ISO|ITU|IANA|W3C|WHATWG)(?![a-z])");

    /**
     * Trecho do texto da alternativa certa, por protocolo e pergunta, tirado da RFC citada (A3: o esperado não sai do
     * {@code "correta"} do arquivo nem do código).
     */
    private static final Map<String, List<String>> CERTA_PELA_RFC = Map.of(
            "tcp", List.of("SYN-ACK", "controle de fluxo", "encerrada na hora", "até o byte 1000"),
            "ipv4", List.of("descartado", "172.20.5.10", "62", "6 para TCP"),
            "http", List.of("404 Not Found", "GET", "canal TLS", "qual site"));

    /**
     * Defeitos do teste rápido de um conteúdo.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o critério único desta guarda, usado no conteúdo real e na calibração.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> conteúdo sem {@code autoavaliacao} não tem defeito; cada defeito nomeia a
     * origem, a pergunta e, quando cabe, a alternativa.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nó de tipo errado vira defeito.</p>
     */
    static List<String> defeitos(String origem, JsonNode conteudo) {
        JsonNode lista = conteudo.path("autoavaliacao");
        if (lista.isMissingNode() || lista.isNull()) {
            return List.of();
        }
        List<String> defeitos = new ArrayList<>();
        if (!lista.isArray() || lista.isEmpty()) {
            defeitos.add(origem + ": autoavaliacao vazia ou fora de lista");
            return defeitos;
        }
        for (int i = 0; i < lista.size(); i++) {
            JsonNode pergunta = lista.get(i);
            String onde = origem + " pergunta " + i;
            if (pergunta.path("enunciado").asText("").isBlank()) {
                defeitos.add(onde + ": sem enunciado");
            }
            JsonNode alternativas = pergunta.path("alternativas");
            if (!alternativas.isArray() || alternativas.size() < 2 || alternativas.size() > 6) {
                defeitos.add(onde + ": precisa de 2 a 6 alternativas");
            } else {
                defeitos.addAll(defeitosDasAlternativas(onde, alternativas));
            }
            if (!NORMA.matcher(pergunta.path("fonte").asText("")).find()) {
                defeitos.add(onde + ": fonte não cita norma (RFC, IETF, IEEE, ISO, ITU, IANA, W3C ou WHATWG)");
            }
        }
        return defeitos;
    }

    private static List<String> defeitosDasAlternativas(String onde, JsonNode alternativas) {
        List<String> defeitos = new ArrayList<>();
        Set<String> textos = new HashSet<>();
        int certas = 0;
        for (int j = 0; j < alternativas.size(); j++) {
            JsonNode alternativa = alternativas.get(j);
            String texto = alternativa.path("texto").asText("").strip();
            if (texto.isEmpty()) {
                defeitos.add(onde + " alternativa " + j + ": sem texto");
            } else if (!textos.add(texto.toLowerCase(Locale.ROOT))) {
                defeitos.add(onde + " alternativa " + j + ": texto repetido");
            }
            if (alternativa.path("porque").asText("").isBlank()) {
                defeitos.add(onde + " alternativa " + j + ": sem porquê");
            }
            JsonNode correta = alternativa.path("correta");
            if (!correta.isBoolean()) {
                defeitos.add(onde + " alternativa " + j + ": correta não é booleano");
            } else if (correta.booleanValue()) {
                certas++;
            }
        }
        if (certas != 1) {
            defeitos.add(onde + ": " + certas + " alternativas certas (tem de ser exatamente uma)");
        }
        return defeitos;
    }

    private static List<Path> conteudos() throws IOException {
        try (Stream<Path> todos = Files.walk(RAIZ)) {
            return todos.filter(p -> p.getFileName().toString().equals("conteudo.json")).sorted().toList();
        }
    }

    @Test
    @DisplayName("todo teste rápido dos aprofundamentos tem uma certa, o porquê de cada alternativa e a fonte")
    void conteudoReal() throws IOException {
        List<String> comTeste = new ArrayList<>();
        List<String> defeitos = new ArrayList<>();
        for (Path arquivo : conteudos()) {
            JsonNode conteudo = JSON.readTree(arquivo.toFile());
            if (conteudo.has("autoavaliacao")) {
                comTeste.add(arquivo.getParent().getFileName().toString());
            }
            defeitos.addAll(defeitos(arquivo.toString(), conteudo));
        }
        assertTrue(comTeste.containsAll(CERTA_PELA_RFC.keySet()),
                "o piloto não foi lido (achados: " + comTeste + "): instrumento cego?");
        assertEquals(List.of(), defeitos);
    }

    @Test
    @DisplayName("no piloto, a alternativa marcada como certa é a que a RFC citada dá como certa (A3)")
    void gabaritoDoPilotoBateComAsRfcs() throws IOException {
        List<String> divergencias = new ArrayList<>();
        for (Map.Entry<String, List<String>> protocolo : CERTA_PELA_RFC.entrySet()) {
            JsonNode perguntas = JSON.readTree(RAIZ.resolve(protocolo.getKey()).resolve("conteudo.json").toFile())
                    .path("autoavaliacao");
            assertEquals(protocolo.getValue().size(), perguntas.size(),
                    protocolo.getKey() + ": número de perguntas mudou; atualize o gabarito pela RFC");
            for (int i = 0; i < perguntas.size(); i++) {
                String esperado = protocolo.getValue().get(i);
                for (JsonNode alternativa : perguntas.get(i).path("alternativas")) {
                    boolean marcada = alternativa.path("correta").asBoolean(false);
                    boolean pelaRfc = alternativa.path("texto").asText("").contains(esperado);
                    if (marcada != pelaRfc) {
                        divergencias.add(protocolo.getKey() + " pergunta " + i + ": \"" + alternativa.path("texto")
                                .asText() + "\" marcada=" + marcada + ", pela RFC=" + pelaRfc);
                    }
                }
            }
        }
        assertEquals(List.of(), divergencias);
    }

    @Test
    @DisplayName("calibração: cada defeito reprova; o legítimo com o mesmo sinal passa")
    void guardaCalibrada() throws IOException {
        String certaNaUltima = "{\"autoavaliacao\":[{\"enunciado\":\"e\",\"fonte\":\"registro IANA Protocol Numbers\","
                + "\"alternativas\":[" + alt("a", false, "p") + "," + alt("b", false, "p") + "," + alt("c", true, "p")
                + "]}]}";
        String rfcSemEspaco = certaNaUltima.replace("registro IANA Protocol Numbers", "RFC9293, 3.5");
        String whatwg = certaNaUltima.replace("registro IANA Protocol Numbers", "WHATWG Fetch Standard, seção 4");
        assertEquals(List.of(), defeitos("legitimo", JSON.readTree(certaNaUltima)),
                "A1: certa na última posição e fonte da IANA são legítimas");
        assertEquals(List.of(), defeitos("legitimo", JSON.readTree(rfcSemEspaco)), "A1: RFC9293 cita a norma");
        assertEquals(List.of(), defeitos("legitimo", JSON.readTree(whatwg)), "A1: norma do WHATWG é fonte legítima");
        assertEquals(List.of(), defeitos("sem-teste", JSON.readTree("{\"resumo\":\"x\"}")));

        Map<String, String> doentes = Map.of(
                "duas certas", certaNaUltima.replace(alt("a", false, "p"), alt("a", true, "p")),
                "nenhuma certa", certaNaUltima.replace(alt("c", true, "p"), alt("c", false, "p")),
                "sem porquê", certaNaUltima.replace(alt("b", false, "p"), alt("b", false, " ")),
                "fonte sem norma", certaNaUltima.replace("registro IANA Protocol Numbers", "Wikipedia; isolado"),
                "texto repetido", certaNaUltima.replace(alt("b", false, "p"), alt("A", false, "p")),
                "correta em texto", certaNaUltima.replace("\"correta\":true", "\"correta\":\"true\""),
                "lista vazia", "{\"autoavaliacao\":[]}",
                "uma alternativa só", "{\"autoavaliacao\":[{\"enunciado\":\"e\",\"fonte\":\"RFC 791\","
                        + "\"alternativas\":[" + alt("a", true, "p") + "]}]}",
                "sem enunciado", certaNaUltima.replace("\"enunciado\":\"e\"", "\"enunciado\":\"\""));
        List<String> cegos = new ArrayList<>();
        for (Map.Entry<String, String> doente : doentes.entrySet()) {
            if (defeitos(doente.getKey(), JSON.readTree(doente.getValue())).isEmpty()) {
                cegos.add(doente.getKey());
            }
        }
        assertEquals(List.of(), cegos, "A2: a guarda não reprovou estes casos doentes");
    }

    private static String alt(String texto, boolean correta, String porque) {
        return "{\"texto\":\"" + texto + "\",\"correta\":" + correta + ",\"porque\":\"" + porque + "\"}";
    }
}
