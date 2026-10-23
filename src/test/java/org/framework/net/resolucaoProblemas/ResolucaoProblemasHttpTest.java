package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class ResolucaoProblemasHttpTest {

    @Test
    void postCalculateComDemoFiap() {
        given()
                .when().get("/resolucao-problemas?demo=fiap")
                .then()
                .statusCode(200)
                .body(containsString("172.42.0.0"));
    }

    @Test
    void postCalculate() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("eigrp_as", "203")
                .formParam("remote_access", "telnet")
                .formParam("routing_mode", "eigrp_only")
                .formParam("loc_name", "Matriz", "Filial I", "Filial II", "Data Center")
                .formParam("loc_hosts", "400", "390", "350", "300")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Matriz"));
    }

    /** Auditoria CALC-39: o título dos enlaces dizia "/30" com outro prefixo WAN escolhido. */
    @Test
    void tituloDosEnlacesUsaOPrefixoWanEscolhido() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "star")
                .formParam("wan_prefix", "29")
                .formParam("eigrp_as", "203")
                .formParam("remote_access", "telnet")
                .formParam("routing_mode", "eigrp_only")
                .formParam("loc_name", "Matriz", "Filial")
                .formParam("loc_hosts", "100", "50")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Links WAN /29"))
                .body(org.hamcrest.CoreMatchers.not(containsString("Links WAN /30")));
    }

    @Test
    void postExportTxt() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "export")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("eigrp_as", "203")
                .formParam("loc_name", "Matriz", "Filial I")
                .formParam("loc_hosts", "400", "390")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("config_packet_tracer"));
    }

    @Test
    void postExportZip() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "export_zip")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("loc_name", "Matriz", "Filial I")
                .formParam("loc_hosts", "400", "390")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .header("Content-Type", anyOf(containsString("zip"), containsString("octet-stream")));
    }

    @Test
    void postExportEntrega() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "export_entrega")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("loc_name", "Matriz", "Filial I")
                .formParam("loc_hosts", "400", "390")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("documentacao_cenario_rede"));
    }

    @Test
    void postComRedeInvalidaMarcaErro() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network_ip", "999.0.0.0")
                .formParam("base_network_cidr", "16")
                .formParam("topology_type", "star")
                .formParam("loc_name", "Matriz")
                .formParam("loc_hosts", "100")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("is-invalid"))
                .body(anyOf(containsString("Rede base"), containsString("Corrija os campos")));
    }

    @Test
    void postComLocalidadeSemHostsMarcaErro() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "star")
                .formParam("loc_name", "Matriz")
                .formParam("loc_hosts", "")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Hosts da localidade"))
                .body(containsString("is-invalid"));
    }

    @Test
    void demosGsE8() {
        given().when().get("/resolucao-problemas?demo=gs").then().statusCode(200).body(containsString("172.63"));
        given().when().get("/resolucao-problemas?demo=8").then().statusCode(200);
        given().when().get("/resolucao-problemas?demo=1").then().statusCode(200);
    }

    @Test
    void postComIpBarraCampoSeparado() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network_ip", "172.19.0.0/16")
                .formParam("base_network_cidr", "")
                .formParam("topology_type", "star")
                .formParam("loc_name", "Matriz")
                .formParam("loc_hosts", "100")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("Matriz"))
                .body(not(containsString("is-invalid")));
    }

    @Test
    void postComEigrpForaDaFaixaMarcaErro() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "star")
                .formParam("eigrp_as", "70000")
                .formParam("loc_name", "Matriz")
                .formParam("loc_hosts", "100")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(containsString("is-invalid"))
                .body(anyOf(containsString("EIGRP"), containsString("65535")));
    }

    @Test
    void postComNomeMaliciosoNaoInjetaScript() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "calculate")
                .formParam("base_network", "172.42.0.0/16")
                .formParam("topology_type", "star")
                .formParam("loc_name", "</script><script>alert(1)</script>")
                .formParam("loc_hosts", "100")
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .body(not(containsString("<script>alert(1)</script>")));
    }

    @Test
    void postExportClassZip() {
        String roster = "Aluno A\t172.51.0.0\t400\t390\nAluno B\t172.52.0.0\t350\t300";
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("action_type", "export_class_zip")
                .formParam("topology_type", "extended_star")
                .formParam("wan_prefix", "30")
                .formParam("eigrp_as", "71")
                .formParam("routing_mode", "auto")
                .formParam("remote_access", "ssh")
                .formParam("class_roster_paste", roster)
                .when().post("/resolucao-problemas")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("pacote_turma_packet_tracer"))
                .header("Content-Type", anyOf(containsString("zip"), containsString("octet-stream")));
    }
}
