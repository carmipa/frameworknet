package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.resolucaoProblemas.application.parsing.CenarioExemploReversa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Cobertura HTTP das duas abas da Resolução de Problemas.
 *
 * <p><b>Propósito de negócio:</b> a aba "Projetar" existia sozinha nesta rota e
 * passou a conviver com a aba "Engenharia reversa". Este teste prova que a aba
 * antiga continua íntegra depois da divisão do template — a regressão mais cara
 * possível seria quebrar o que já funcionava para acrescentar o que é novo — e que
 * a aba nova executa, exporta e imprime.</p>
 *
 * <p><b>Invariantes do domínio:</b> a rota sem parâmetro continua abrindo em
 * "Projetar", como sempre abriu; {@code ?aba=reversa} é aditivo. O botão
 * "Executar" é submit de formulário para o servidor, e não JavaScript, porque a
 * interpretação é regra de negócio e precisa ser testável por HTTP.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção aponta a rota e o trecho
 * ausente na resposta.</p>
 */
@QuarkusTest
@DisplayName("Resolução: abas Projetar e Engenharia reversa")
class EngenhariaReversaHttpTest {

    @ParameterizedTest(name = "{0} abre com as duas abas disponíveis")
    @CsvSource({"/resolucao-problemas", "/resolucao-problemas?aba=reversa"})
    void asDuasAbasAparecemNasDuasRotas(String rota) {
        given()
                .when().get(rota)
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("aed-topnav"))
                .body(containsString("Projetar"))
                .body(containsString("Engenharia reversa"))
                .body(not(containsString("Internal Server Error")));
    }

    @Test
    @DisplayName("regressão: a rota sem parâmetro continua sendo a aba Projetar completa")
    void abaProjetarSegueIntegra() {
        given()
                .when().get("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Resolução de Problemas de Redes"))
                .body(containsString("name=\"loc_name\""))
                .body(containsString("?demo=fiap"))
                .body(containsString("Prefixo WAN"))
                .body(containsString("VLSM"))
                .body(not(containsString("Configuração Cisco")));
    }

    @Test
    @DisplayName("a aba de engenharia reversa abre com a área de texto e o botão Executar")
    void abaReversaAbreVazia() {
        given()
                .when().get("/resolucao-problemas?aba=reversa")
                .then()
                .statusCode(200)
                .body(containsString("name=\"config_paste\""))
                .body(containsString("value=\"reverse\""))
                .body(containsString("Executar"))
                .body(not(containsString("Auditoria da configuração")));
    }

    @Test
    @DisplayName("o cenário de exemplo já chega interpretado, com as correções na tela")
    void exemploChegaInterpretado() {
        given()
                .when().get("/resolucao-problemas?aba=reversa&demo=bgp")
                .then()
                .statusCode(200)
                .body(containsString("Auditoria da configuração"))
                .body(containsString("Erro corrigido"))
                .body(containsString("200.200.200.2"))
                .body(containsString("Desenho reconstruído"))
                .body(containsString("Scripts corrigidos"))
                .body(containsString("cenário fecha"));
    }

    @Test
    @DisplayName("Executar interpreta o texto enviado no formulário")
    void executarInterpretaOTextoColado() {
        given()
                .formParam("action_type", "reverse")
                .formParam("aba", "reversa")
                .formParam("config_paste", CenarioExemploReversa.BGP_TRES_AS)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("LANs reconstruídas"))
                .body(containsString("Enlaces ponto a ponto"))
                .body(containsString("Tabelas por roteador"))
                .body(containsString("! CORRIGIDO:"));
    }

    @Test
    @DisplayName("texto sem configuração nenhuma avisa em vez de quebrar")
    void textoIrreconheciveNaoQuebra() {
        given()
                .formParam("action_type", "reverse")
                .formParam("config_paste", "isto aqui nao e configuracao de roteador")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Nada reconhecido no texto colado"))
                .body(not(containsString("Internal Server Error")));
    }

    @Test
    @DisplayName("Limpar devolve a aba vazia, sem perder o menu")
    void limparEsvaziaAArea() {
        given()
                .formParam("action_type", "reverse_limpar")
                .formParam("config_paste", CenarioExemploReversa.BGP_TRES_AS)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("name=\"config_paste\""))
                .body(containsString("aed-topnav"))
                .body(not(containsString("Auditoria da configuração")));
    }

    @Test
    @DisplayName("exporta os scripts corrigidos como arquivo de texto")
    void exportaScriptsCorrigidos() {
        given()
                .formParam("action_type", "reverse_export_scripts")
                .formParam("config_paste", CenarioExemploReversa.BGP_TRES_AS)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .contentType(containsString("text/plain"))
                .header("Content-Disposition", containsString("scripts_corrigidos.txt"))
                .body(containsString("ROTEADOR: SP"))
                .body(containsString("ip address 200.200.200.2 255.255.255.252"))
                .body(containsString("! CORRIGIDO:"));
    }

    @Test
    @DisplayName("exporta o relatório da auditoria com evidência de cada correção")
    void exportaRelatorio() {
        given()
                .formParam("action_type", "reverse_export_relatorio")
                .formParam("config_paste", CenarioExemploReversa.BGP_TRES_AS)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .contentType(containsString("text/plain"))
                .header("Content-Disposition", containsString("relatorio_engenharia_reversa.txt"))
                .body(containsString("RELATORIO DE ENGENHARIA REVERSA"))
                .body(containsString("EVIDENCIA:"))
                .body(containsString("TOPOLOGIA (MERMAID)"));
    }
}
