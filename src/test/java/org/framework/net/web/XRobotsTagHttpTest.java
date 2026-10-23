package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A área autenticada e as telas de entrada saem do índice pelo cabeçalho {@code X-Robots-Tag}.
 *
 * <p><b>Propósito de negócio:</b> estas rotas não podem ir para o {@code robots.txt}: o arquivo é público
 * e listar caminho privado nele entrega o mapa, e o buscador que obedece ao {@code Disallow} nunca lê o
 * {@code noindex} da página. Sem o cabeçalho, um link externo para {@code /telemetria} faria o buscador
 * registrar a URL (ou a tela de login para onde ela redireciona) no índice.</p>
 *
 * <p><b>Invariantes do domínio:</b> toda resposta sob {@code /admin}, {@code /login} e
 * {@code /telemetria} — inclusive o redirecionamento para o login e o erro — leva
 * {@code X-Robots-Tag: noindex}; página pública didática <b>não</b> leva, nem a que só começa com as
 * mesmas letras ({@code /loginx} não é {@code /login}).</p>
 *
 * <p><b>Comportamento em caso de falha:</b> o teste nomeia a rota e o cabeçalho recebido.</p>
 */
@QuarkusTest
@DisplayName("Área autenticada fora do índice pelo cabeçalho X-Robots-Tag")
class XRobotsTagHttpTest {

    private static final List<String> AREA_PRIVADA = List.of(
            "/telemetria", "/telemetria/api/exportar", "/login", "/admin", "/admin/login");

    @Test
    @DisplayName("toda resposta da área autenticada declara noindex, inclusive redirecionamento e erro")
    void areaPrivadaDeclaraNoindex() {
        List<String> semCabecalho = new ArrayList<>();
        for (String rota : AREA_PRIVADA) {
            Response resposta = given().redirects().follow(false).accept("text/html").when().get(rota);
            String cabecalho = String.valueOf(resposta.getHeader("X-Robots-Tag"));
            if (!cabecalho.contains("noindex")) {
                semCabecalho.add(rota + " (" + resposta.statusCode() + ") X-Robots-Tag=" + cabecalho);
            }
        }
        assertTrue(semCabecalho.isEmpty(), () -> "Rota da área autenticada sem X-Robots-Tag: noindex: " + semCabecalho);
    }

    @Test
    @DisplayName("página pública não recebe noindex, nem a rota que só começa com as mesmas letras")
    void paginaPublicaNaoRecebeNoindex() {
        List<String> indevidas = new ArrayList<>();
        for (String rota : List.of("/", "/portas", "/protocolos/bgp", "/loginx", "/administracao-de-redes")) {
            String cabecalho = String.valueOf(given().redirects().follow(false).when().get(rota).getHeader("X-Robots-Tag"));
            if (cabecalho.contains("noindex")) {
                indevidas.add(rota + " X-Robots-Tag=" + cabecalho);
            }
        }
        assertTrue(indevidas.isEmpty(), () -> "noindex vazou para fora da área autenticada: " + indevidas);
    }
}
