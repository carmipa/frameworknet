package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recusas do CSRF num balde próprio por origem (Lote 1 da auditoria, OPS-05). Antes, pedido com token inválido nunca
 * passava por limite nenhum, e cada um obrigava o filtro a ler um corpo de até 1 MB. O freio NÃO pode morar no limite
 * da rota: uma turma atrás do mesmo NAT, com tokens vencidos depois de uma hora de aula, erra uma vez por aluno e
 * reenvia — e ficaria bloqueada pelo próprio reenvio. Perfil: teto pesado da rota 2, teto de recusas 3.
 */
@QuarkusTest
@TestProfile(RecusaCsrfTetoHttpTest.Perfil.class)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class RecusaCsrfTetoHttpTest {

    static final int TETO_RECUSAS = 3;

    public static class Perfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "framework.security.csrf-enabled", "true",
                    "framework.security.rate-limit-enabled", "true",
                    "framework.security.rate-limit-per-minute", "1000",
                    "framework.security.rate-limit-heavy-per-minute", "2",
                    "framework.security.rate-limit-global-per-minute", "100000",
                    "framework.security.rate-limit-csrf-recusas-per-minute", String.valueOf(TETO_RECUSAS));
        }
    }

    @Inject
    CsrfTokenService tokens;

    private static int postSemToken() {
        return given()
                .header("Accept", "*/*")
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .when().post("/analise")
                .then().extract().statusCode();
    }

    private int postComTokenNoCabecalho() {
        String token = tokens.issueToken();
        return given()
                .header("Accept", "*/*")
                .header("HX-Request", "true")
                .header("X-CSRF-Token", token)
                .cookie(tokens.cookieName(), token)
                .contentType("application/x-www-form-urlencoded")
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.1")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then().extract().statusCode();
    }

    private int postComTokenNoCorpo() {
        String token = tokens.issueToken();
        return given()
                .header("Accept", "*/*")
                .cookie(tokens.cookieName(), token)
                .contentType("application/x-www-form-urlencoded")
                .formParam("csrf_token", token)
                .formParam("endereco", "2001:db8::1")
                .when().post("/ipv6/api/calcular")
                .then().extract().statusCode();
    }

    /**
     * A1: o formulário comum (sem htmx) manda o token no CORPO; ele reserva vaga no balde antes de o corpo ser lido, e
     * a devolve quando o token confere — assim, muito mais envios legítimos que o teto (aqui 3) passam, e as recusas
     * que vêm depois ainda encontram o balde vazio. A2: sem a devolução, o quarto envio legítimo já levaria 429.
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    void envioLegitimoComTokenNoCorpoNaoGastaOTetoDeRecusas() {
        for (int i = 0; i < 3 * TETO_RECUSAS; i++) {
            int status = postComTokenNoCorpo();
            assertNotEquals(429, status, "o envio legítimo " + (i + 1) + " gastou o teto de recusas");
            assertNotEquals(403, status, "o token válido no corpo foi recusado");
        }
        // O recurso relê o corpo que o filtro já leu para achar o token: o cálculo tem de sair inteiro.
        String token = tokens.issueToken();
        String pagina = given()
                .header("Accept", "text/html")
                .cookie(tokens.cookieName(), token)
                .contentType("application/x-www-form-urlencoded")
                .formParam("csrf_token", token)
                .formParam("modo", "cidr")
                .formParam("ip", "10.0.0.1")
                .formParam("cidr", "24")
                .when().post("/analise")
                .then().statusCode(200).extract().asString();
        assertTrue(pagina.contains("10.0.0.255"), "o /analise com token no corpo não calculou");
    }

    /**
     * A2: recusas em série esbarram no balde próprio — só depois de {@code TETO_RECUSAS} recusas vem o 429 (se a
     * recusa gastasse o limite da ROTA, de teto 2, o 429 viria já no terceiro pedido; sem balde nenhum, nunca viria).
     * A1: o aluno honesto da mesma rede, com token válido, continua passando na MESMA rota logo depois — nem o balde
     * de recusas nem o limite da rota o bloqueiam. A janela é por minuto: se ela virar no meio, as recusas recomeçam
     * a contar e o 429 só chega mais tarde, por isso o laço vai até 4 × o teto.
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    void recusasTemBaldeProprioQueNaoBloqueiaQuemTemToken() {
        List<Integer> recusas = new ArrayList<>();
        while (recusas.size() < 4 * TETO_RECUSAS && !recusas.contains(429)) {
            recusas.add(postSemToken());
        }
        assertEquals(429, recusas.get(recusas.size() - 1), "nenhum 429 depois de " + recusas.size() + " recusas: " + recusas);
        assertTrue(recusas.size() >= TETO_RECUSAS + 1,
                "o 429 veio antes do teto de recusas (o limite da rota foi gasto pelas recusas?): " + recusas);
        assertTrue(recusas.subList(0, recusas.size() - 1).stream().allMatch(s -> s == 403),
                "antes do 429, toda recusa é 403: " + recusas);

        int legitimo = postComTokenNoCabecalho();
        assertNotEquals(429, legitimo, "o pedido com token válido foi barrado pelo freio das recusas ou pelo limite da rota");
        assertNotEquals(403, legitimo, "o token válido no cabeçalho foi recusado");

        // Revisão de boa-fé (2ª passada): com o balde da rede CHEIO, o formulário comum com cookie e token válidos no
        // corpo (pequeno) ainda é lido e passa — o colega honesto não paga pelas recusas dos outros.
        int legitimoNoCorpo = postComTokenNoCorpo();
        assertNotEquals(429, legitimoNoCorpo, "o token válido no corpo foi barrado pelo balde cheio da rede");
        assertNotEquals(403, legitimoNoCorpo, "o token válido no corpo foi recusado");
    }
}
