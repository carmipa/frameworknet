package org.framework.net.calculadora.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.framework.net.calculadora.application.AgregacaoService;
import org.framework.net.calculadora.application.CalculadoraExportService;
import org.framework.net.calculadora.application.DivisaoService;
import org.framework.net.calculadora.application.VlanService;
import org.framework.net.calculadora.domain.PlanoDivisao;
import org.framework.net.calculadora.domain.PlanoVlan;
import org.framework.net.calculadora.exception.CalculadoraException;

import java.util.Locale;

/**
 * Rotas da Calculadora de Sub-redes e VLANs.
 *
 * <p><b>Propósito de negócio:</b> expõe a calculadora em dois pontos de entrada
 * que compartilham a mesma lógica — a página própria em {@code /calculadora},
 * com todas as ferramentas, e os fragmentos consumidos via htmx pela aba
 * "Calculadora" da Análise Didática.</p>
 *
 * <p><b>Invariantes do domínio:</b> todas as rotas do módulo vivem neste único
 * resource. Dividir {@code /calculadora/api/*} entre dois resources faria o
 * JAX-RS resolver apenas um deles e devolver 404 no outro — problema já
 * enfrentado no módulo de Tráfego. Cada POST devolve HTML de fragmento, nunca a
 * página inteira, para que o htmx troque só o painel de resultado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> entradas inválidas sobem como
 * {@link CalculadoraException} e são convertidas pelo
 * {@code CalculadoraExceptionMapper} em 400 com o fragmento de erro (htmx) ou
 * texto puro (demais clientes).</p>
 */
@Path("/calculadora")
public class CalculadoraResource {

    @Inject
    DivisaoService divisaoService;

    @Inject
    VlanService vlanService;

    @Inject
    AgregacaoService agregacaoService;

    @Inject
    CalculadoraExportService exportService;

    @Inject
    @Location("calculadora/index.html")
    Template index;

    @Inject
    @Location("calculadora/partials/resultado_divisao.html")
    Template resultadoDivisao;

    @Inject
    @Location("calculadora/partials/resultado_vlan.html")
    Template resultadoVlan;

    @Inject
    @Location("calculadora/partials/resultado_vlan_id.html")
    Template resultadoVlanId;

    @Inject
    @Location("calculadora/partials/resultado_sumarizacao.html")
    Template resultadoSumarizacao;

    @Inject
    @Location("calculadora/partials/resultado_relacao.html")
    Template resultadoRelacao;

    @Inject
    @Location("calculadora/partials/resultado_faixa.html")
    Template resultadoFaixa;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaInicial(@QueryParam("aba") String aba) {
        return index
                .data("activeMainMenu", "calculadora")
                .data("abaAtiva", normalizarAba(aba));
    }

    /**
     * Divide um bloco em sub-redes de tamanho fixo.
     *
     * <p><b>Propósito de negócio:</b> atende o caso central da tela — "um /21,
     * quantas redes cabem?" — nos três critérios de entrada: por prefixo alvo,
     * por quantidade de sub-redes desejada ou por hosts necessários.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> critério desconhecido ou entrada
     * inválida lançam {@link CalculadoraException} (HTTP 400 + fragmento).</p>
     */
    @POST
    @Path("/api/dividir")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance dividir(
            @FormParam("bloco") String bloco,
            @FormParam("prefixoBase") String prefixoBase,
            @FormParam("criterio") String criterio,
            @FormParam("prefixoAlvo") String prefixoAlvo,
            @FormParam("quantidade") String quantidade,
            @FormParam("hosts") String hosts) {

        PlanoDivisao plano = calcularDivisao(bloco, prefixoBase, criterio, prefixoAlvo, quantidade, hosts);
        return resultadoDivisao
                .data("plano", plano)
                .data("criterio", normalizarCriterio(criterio));
    }

    /** Gera o plano de VLANs com sub-rede, gateway, DHCP e script Cisco. */
    @POST
    @Path("/api/vlan")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance planoVlan(
            @FormParam("bloco") String bloco,
            @FormParam("prefixoBase") String prefixoBase,
            @FormParam("prefixoPorVlan") String prefixoPorVlan,
            @FormParam("vlanInicial") String vlanInicial,
            @FormParam("passoVlan") String passoVlan,
            @FormParam("quantidade") String quantidade,
            @FormParam("nomes") String nomes,
            @FormParam("estrategia") String estrategia) {

        PlanoVlan plano = vlanService.gerarPlano(
                bloco, prefixoBase, prefixoPorVlan, vlanInicial, passoVlan, quantidade, nomes, estrategia);
        return resultadoVlan.data("plano", plano);
    }

    /** Valida um VLAN ID avulso e explica a faixa em que ele cai. */
    @POST
    @Path("/api/vlan-id")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance validarVlanId(@FormParam("vlanId") String vlanId) {
        return resultadoVlanId.data("parecer", vlanService.validarIdIsolado(vlanId));
    }

    /** Sumariza uma lista de redes na menor rota resumo que cobre todas. */
    @POST
    @Path("/api/sumarizar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance sumarizar(@FormParam("redes") String redes) {
        return resultadoSumarizacao.data("resultado", agregacaoService.sumarizar(redes));
    }

    /** Compara dois blocos: iguais, um contém o outro ou disjuntos. */
    @POST
    @Path("/api/comparar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance comparar(
            @FormParam("blocoA") String blocoA,
            @FormParam("blocoB") String blocoB) {
        return resultadoRelacao.data("resultado", agregacaoService.comparar(blocoA, blocoB));
    }

    /** Converte uma faixa de IPs na menor lista de blocos CIDR. */
    @POST
    @Path("/api/faixa")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance faixa(
            @FormParam("inicio") String inicio,
            @FormParam("fim") String fim) {
        return resultadoFaixa.data("resultado", agregacaoService.faixaParaCidr(inicio, fim));
    }

    /**
     * Exporta a divisão em CSV.
     *
     * <p><b>Invariantes do domínio:</b> recalcula o plano a partir dos mesmos
     * parâmetros da tela em vez de guardar estado de sessão, então o arquivo
     * sempre corresponde ao que foi pedido, mesmo em outra aba do navegador.</p>
     */
    @GET
    @Path("/export/divisao.csv")
    @Produces("text/csv; charset=UTF-8")
    public Response exportarDivisao(
            @QueryParam("bloco") String bloco,
            @QueryParam("prefixoBase") String prefixoBase,
            @QueryParam("criterio") String criterio,
            @QueryParam("prefixoAlvo") String prefixoAlvo,
            @QueryParam("quantidade") String quantidade,
            @QueryParam("hosts") String hosts) {

        PlanoDivisao plano = calcularDivisao(bloco, prefixoBase, criterio, prefixoAlvo, quantidade, hosts);
        String nome = "divisao-" + plano.blocoBase().replace('.', '_')
                + "-" + plano.prefixoBase() + "-para-" + plano.prefixoAlvo() + ".csv";
        return respostaCsv(exportService.divisaoCsv(plano), nome);
    }

    /** Exporta o plano de VLANs em CSV. */
    @GET
    @Path("/export/vlan.csv")
    @Produces("text/csv; charset=UTF-8")
    public Response exportarVlan(
            @QueryParam("bloco") String bloco,
            @QueryParam("prefixoBase") String prefixoBase,
            @QueryParam("prefixoPorVlan") String prefixoPorVlan,
            @QueryParam("vlanInicial") String vlanInicial,
            @QueryParam("passoVlan") String passoVlan,
            @QueryParam("quantidade") String quantidade,
            @QueryParam("nomes") String nomes,
            @QueryParam("estrategia") String estrategia) {

        PlanoVlan plano = vlanService.gerarPlano(
                bloco, prefixoBase, prefixoPorVlan, vlanInicial, passoVlan, quantidade, nomes, estrategia);
        String nome = "vlans-" + plano.blocoBase().replace('.', '_') + "-" + plano.prefixoBase() + ".csv";
        return respostaCsv(exportService.vlanCsv(plano), nome);
    }

    private PlanoDivisao calcularDivisao(
            String bloco, String prefixoBase, String criterio,
            String prefixoAlvo, String quantidade, String hosts) {
        return switch (normalizarCriterio(criterio)) {
            case "quantidade" -> divisaoService.dividirPorQuantidade(bloco, prefixoBase, quantidade);
            case "hosts" -> divisaoService.dividirPorHosts(bloco, prefixoBase, hosts);
            default -> divisaoService.dividirPorPrefixo(bloco, prefixoBase, prefixoAlvo);
        };
    }

    private Response respostaCsv(String conteudo, String nomeArquivo) {
        return Response.ok(conteudo)
                .header("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"")
                .build();
    }

    private static String normalizarCriterio(String valor) {
        String v = valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "quantidade", "hosts" -> v;
            default -> "prefixo";
        };
    }

    private static String normalizarAba(String valor) {
        String v = valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "vlan", "agregar", "faixa" -> v;
            default -> "dividir";
        };
    }
}
