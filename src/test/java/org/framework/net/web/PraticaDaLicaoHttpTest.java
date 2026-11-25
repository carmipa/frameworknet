package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import org.framework.net.academia.trilha.domain.CatalogoTrilha;
import org.framework.net.academia.trilha.domain.Licao;
import org.framework.net.academia.trilha.domain.Nivel;
import org.framework.net.academia.trilha.domain.Pratica;
import org.framework.net.web.domain.PaginasPublicas;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Pratique na ferramenta" no fim de cada lição da Academia (proposta C08, autorizada pela decisão D2).
 *
 * <p>Toda lição do catálogo tem uma prática; a página da lição renderiza o link; o destino é página pública e
 * responde 200 (A6: o link foi seguido, não só conferido no catálogo). A1: rota inexistente com a mesma forma
 * ({@code /analize}) não é página pública, e endereço externo nem chega a virar prática.</p>
 */
@QuarkusTest
class PraticaDaLicaoHttpTest {

    @Test
    void todaLicaoLevaAUmaPaginaQueExiste() {
        int conferidas = 0;
        for (Nivel nivel : CatalogoTrilha.niveis()) {
            for (Licao licao : nivel.licoes()) {
                Pratica pratica = CatalogoTrilha.pratica(licao.id())
                        .orElseThrow(() -> new AssertionError("lição sem prática: " + licao.id()));
                assertTrue(PaginasPublicas.contem(pratica.caminho()),
                        licao.id() + " aponta para página que não é pública: " + pratica.rota());
                String html = given().when().get(licao.rota()).then().statusCode(200).extract().asString();
                assertTrue(html.contains("href=\"" + pratica.rota() + "\""),
                        "a página de " + licao.id() + " não mostra o link da prática");
                given().when().get(pratica.rota()).then().statusCode(200);
                conferidas++;
            }
        }
        assertTrue(conferidas >= 7, "só " + conferidas + " lições conferidas: catálogo vazio?");
    }

    @Test
    void rotaInexistenteEEnderecoExternoSaoPegos() {
        assertFalse(PaginasPublicas.contem("/analize"), "a guarda tem de enxergar rota que não existe");
        assertThrows(IllegalArgumentException.class, () -> new Pratica("https://exemplo.com/x", "externo", "home"));
        assertThrows(IllegalArgumentException.class, () -> new Pratica("/analise?tab=mask&x=1", "duas consultas", "home"));
    }
}
