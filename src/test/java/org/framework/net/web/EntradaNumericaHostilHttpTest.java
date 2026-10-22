package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda: número hostil em qualquer campo de formulário ou de query não derruba a página com 500.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o padrão {@code Character::isDigit} + {@code Integer.parseInt} se repetia
 * pelo site; "99999999999" virava {@code NumberFormatException} e página de erro, e dígitos de outros
 * alfabetos passavam pela checagem (auditoria CALC-34). Corrigir dois lugares não impede o terceiro; esta
 * guarda varre TODO recurso JAX-RS do projeto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> cada parâmetro {@code @FormParam}/{@code @RestForm}/{@code @QueryParam}/
 * {@code @RestQuery} recebe cada valor hostil, um parâmetro por vez, e a resposta é menor que 500. Ficam de
 * fora, de propósito, a telemetria (o dataset publica em repositório público) e o administrativo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção lista método, caminho, parâmetro e valor de cada 500;
 * se nenhum recurso for lido, a guarda reprova (alvo vazio não é aprovação).</p>
 */
@QuarkusTest
class EntradaNumericaHostilHttpTest {

    private static final List<String> HOSTIS = List.of("99999999999", "١٢", "-1", "1e9");
    private static final List<String> FORA = List.of("/telemetria", "/admin", "/q/", "/login", "/logout");

    @Test
    void nenhumCampoDevolve500ComNumeroHostil() throws Exception {
        List<String> falhas = new ArrayList<>();
        int alvos = 0;
        for (Class<?> c : recursos()) {
            String base = caminho(c.getAnnotation(Path.class));
            for (Method m : c.getDeclaredMethods()) {
                boolean post = m.isAnnotationPresent(POST.class);
                boolean get = m.isAnnotationPresent(GET.class);
                if (!post && !get) {
                    continue;
                }
                String rota = (base + caminho(m.getAnnotation(Path.class))).replaceAll("\\{[^}]+}", "x");
                if (rota.isEmpty()) {
                    rota = "/";
                }
                String rotaFinal = rota;
                if (FORA.stream().anyMatch(rotaFinal::startsWith)) {
                    continue;
                }
                Map<String, Boolean> campos = campos(m);
                for (Map.Entry<String, Boolean> campo : campos.entrySet()) {
                    for (String valor : HOSTIS) {
                        alvos++;
                        RequestSpecification req = given().redirects().follow(false);
                        req = campo.getValue() ? req.contentType("application/x-www-form-urlencoded")
                                .formParam(campo.getKey(), valor) : req.queryParam(campo.getKey(), valor);
                        Response r = post ? req.post(rota) : req.get(rota);
                        if (r.statusCode() >= 500) {
                            falhas.add((post ? "POST " : "GET ") + rota + " " + campo.getKey() + "=" + valor
                                    + " -> " + r.statusCode());
                        }
                    }
                }
            }
        }
        // A Análise lê um MultivaluedMap inteiro; os campos numéricos dela entram à mão.
        for (String campo : List.of("cidr", "regua_count", "comparador_cidr_a", "comparador_cidr_b",
                "history_limit", "history_page")) {
            for (String modo : List.of("cidr", "comparador", "mask")) {
                for (String valor : HOSTIS) {
                    alvos++;
                    int st = given().contentType("application/x-www-form-urlencoded")
                            .formParam("modo", modo).formParam("ip", "10.0.0.1").formParam(campo, valor)
                            .post("/analise").statusCode();
                    if (st >= 500) {
                        falhas.add("POST /analise modo=" + modo + " " + campo + "=" + valor + " -> " + st);
                    }
                }
            }
        }
        // A Resolução só lê os hosts quando a localidade tem nome e a rede base é válida: sem isso a
        // validação para antes e o parse nunca é alcançado (a mutação N2 mostrou a guarda cega aqui).
        for (String campo : List.of("loc_hosts", "wan_prefix", "eigrp_as", "ospf_process", "base_network_cidr")) {
            for (String valor : HOSTIS) {
                alvos++;
                Map<String, String> form = new LinkedHashMap<>(Map.of(
                        "action_type", "calculate", "base_network", "10.0.0.0/16", "topology_type", "star",
                        "loc_name", "A", "loc_hosts", "10", "wan_prefix", "30", "eigrp_as", "100",
                        "ospf_process", "1", "remote_access", "telnet"));
                form.put(campo, valor);
                int st = given().redirects().follow(false).contentType("application/x-www-form-urlencoded")
                        .formParams(form).post("/resolucao-problemas").statusCode();
                if (st >= 500) {
                    falhas.add("POST /resolucao-problemas " + campo + "=" + valor + " -> " + st);
                }
            }
        }
        assertTrue(alvos > 100, "a guarda enxergou poucos alvos (" + alvos + "): a varredura ficou cega");
        assertEquals(List.of(), falhas);
    }

    /**
     * BACK-10 (auditoria de 22/10/2026): "+24" e "٢٤" (dígitos árabe-índicos) passavam pelo parseInt do Java e a
     * Análise exibia /24. Agora o CIDR com sinal ou de outro alfabeto é recusado como não número, e "24" passa.
     */
    @Test
    void cidrComSinalOuOutroAlfabetoERecusado() {
        for (String ruim : List.of("+24", "٢٤")) {
            given().contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .formParam("modo", "cidr").formParam("ip", "10.0.0.1").formParam("cidr", ruim)
                    .post("/analise").then().statusCode(org.hamcrest.Matchers.lessThan(500))
                    .body(org.hamcrest.Matchers.containsString("O CIDR deve ser um número inteiro entre 0 e 32."));
        }
        given().contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("modo", "cidr").formParam("ip", "10.0.0.1").formParam("cidr", "24")
                .post("/analise").then().statusCode(200)
                .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("O CIDR deve ser um número inteiro")));
    }

    private static Map<String, Boolean> campos(Method m) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Parameter p : m.getParameters()) {
            for (Annotation a : p.getAnnotations()) {
                String nome = switch (a) {
                    case FormParam f -> f.value();
                    case RestForm f -> f.value().isEmpty() ? p.getName() : f.value();
                    case QueryParam q -> q.value();
                    case RestQuery q -> q.value().isEmpty() ? p.getName() : q.value();
                    default -> null;
                };
                if (nome != null) {
                    out.put(nome, a instanceof FormParam || a instanceof RestForm);
                }
            }
        }
        return out;
    }

    private static String caminho(Path p) {
        if (p == null) {
            return "";
        }
        String v = p.value();
        return v.isEmpty() || v.equals("/") ? "" : (v.startsWith("/") ? v : "/" + v);
    }

    private static List<Class<?>> recursos() throws IOException, ClassNotFoundException {
        java.nio.file.Path raiz = java.nio.file.Path.of("build/classes/java/main");
        List<Class<?>> out = new ArrayList<>();
        try (Stream<java.nio.file.Path> s = Files.walk(raiz)) {
            for (java.nio.file.Path f : s.filter(x -> x.toString().endsWith(".class")
                    && !x.getFileName().toString().contains("$")).toList()) {
                String nome = raiz.relativize(f).toString().replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                Class<?> c = Class.forName(nome, false, Thread.currentThread().getContextClassLoader());
                if (c.isAnnotationPresent(Path.class)) {
                    out.add(c);
                }
            }
        }
        assertTrue(out.size() > 20, "poucos recursos JAX-RS encontrados: " + out.size());
        return out;
    }
}
