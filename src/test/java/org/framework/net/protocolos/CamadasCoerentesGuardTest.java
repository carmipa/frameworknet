package org.framework.net.protocolos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda da camada de cada protocolo entre o catálogo, o filtro e a navegação.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o TLS aparecia como "Transporte/Sessão" no catálogo, na aba Aplicação da
 * navegação e na camada 6 do OSI; o BGP, como Aplicação no catálogo e na aba Rede (auditoria CONT-25). E o
 * filtro de camada tinha opções que não traziam linha nenhuma — escolhê-las esvaziava a grade.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) toda camada do catálogo tem opção no filtro e toda opção do filtro
 * tem ao menos um protocolo; (2) quando um protocolo aparece numa aba de camada diferente da camada do
 * catálogo, a chamada daquele aprofundamento explica, citando "camada de" + a aba.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista o que diverge; catálogo ou filtro vazios
 * reprovam (alvo vazio não é aprovação).</p>
 */
class CamadasCoerentesGuardTest {

    private static final Path CATALOGO = Path.of("src/main/resources/protocolos/catalogo.json");
    private static final Path PAGINA = Path.of("src/main/resources/templates/protocolos/index.html");
    private static final Pattern FILTRO = Pattern.compile(
            "data-grid-category=\"protocolos\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern OPCAO = Pattern.compile("<option value=\"([^\"]+)\"");

    @Test
    void filtroDeCamadaCobreOCatalogoSemOpcaoMorta() throws IOException {
        Set<String> doCatalogo = new TreeSet<>(camadasDoCatalogo().values().stream()
                .map(c -> c.toLowerCase(Locale.ROOT)).toList());
        Set<String> doFiltro = new TreeSet<>(opcoesDoFiltro());
        assertFalse(doCatalogo.isEmpty(), "catálogo sem camada lida: a guarda ficou cega");
        assertFalse(doFiltro.isEmpty(), "filtro de camada não encontrado na página: a guarda ficou cega");
        assertEquals(doCatalogo, doFiltro, "camadas do catálogo × opções do filtro");
    }

    @Test
    void abaDiferenteDaCamadaDoCatalogoVemExplicada() throws IOException {
        Map<String, String> camadas = camadasDoCatalogo();
        List<String> semExplicacao = new ArrayList<>();
        for (AprofundamentoProtocolo a : AprofundamentoProtocolo.disponiveis()) {
            for (String nome : a.nomesNoCatalogo()) {
                String doCatalogo = camadas.get(nome);
                if (doCatalogo != null && !doCatalogo.equals(a.camada()) && !explica(a)) {
                    semExplicacao.add(a.slug() + ": aba " + a.camada() + " × catálogo " + doCatalogo + " (" + nome + ")");
                }
            }
        }
        assertEquals(List.of(), semExplicacao);
    }

    /** Calibração (A1): a mesma divergência de camada, com e sem a explicação na chamada. */
    @Test
    void regraDiscriminaAFronteira() {
        AprofundamentoProtocolo comExplicacao = new AprofundamentoProtocolo("x", "X", "X", "hub",
                "Roda como aplicação sobre TCP 179, a serviço da camada de Rede.", "t", "c", "Rede", true, List.of());
        AprofundamentoProtocolo semExplicacao = new AprofundamentoProtocolo("x", "X", "X", "hub",
                "O protocolo que mantém a Internet conectada.", "t", "c", "Rede", true, List.of());
        assertTrue(explica(comExplicacao));
        assertFalse(explica(semExplicacao));
    }

    private static boolean explica(AprofundamentoProtocolo a) {
        return a.chamada().toLowerCase(Locale.ROOT).contains("camada de " + a.camada().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> camadasDoCatalogo() throws IOException {
        Map<String, String> mapa = new HashMap<>();
        for (JsonNode item : new ObjectMapper().readTree(CATALOGO.toFile())) {
            mapa.put(item.path("nome").asText(), item.path("camada").asText());
        }
        return mapa;
    }

    private static List<String> opcoesDoFiltro() throws IOException {
        Matcher bloco = FILTRO.matcher(Files.readString(PAGINA));
        List<String> opcoes = new ArrayList<>();
        if (bloco.find()) {
            Matcher m = OPCAO.matcher(bloco.group(1));
            while (m.find()) {
                if (!m.group(1).equals("all")) {
                    opcoes.add(m.group(1));
                }
            }
        }
        return opcoes;
    }
}
