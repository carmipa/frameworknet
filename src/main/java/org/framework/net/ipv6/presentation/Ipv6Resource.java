package org.framework.net.ipv6.presentation;

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
import org.framework.net.analiseDidatica.support.PdfSimplesService;
import org.framework.net.ipv6.application.Ipv6CidrService;
import org.framework.net.ipv6.domain.Ipv6AnaliseRica;
import org.framework.net.ipv6.exception.Ipv6Exception;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rotas da Calculadora IPv6.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> expõe a calculadora IPv6 em {@code /ipv6} — análise de
 * endereço/prefixo e divisão de prefixo em sub-redes — preenchendo a lacuna de "não temos
 * calculadora IPv6". Espelha a estrutura da Calculadora IPv4 (HTMX, fragmentos).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todas as rotas do módulo vivem neste único resource (dois
 * {@code @Path} sob {@code /ipv6/api/*} em resources diferentes fariam o JAX-RS devolver 404 —
 * armadilha já enfrentada no Tráfego). Cada POST devolve fragmento HTML para o htmx trocar só o
 * painel de resultado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada inválida sobe como {@link Ipv6Exception},
 * convertida pelo {@code Ipv6ExceptionMapper} em 400 (fragmento htmx ou texto puro).</p>
 */
@Path("/ipv6")
public class Ipv6Resource {

    @Inject
    Ipv6CidrService service;

    @Inject
    @Location("ipv6/index.html")
    Template index;

    @Inject
    @Location("ipv6/analise.html")
    Template analisePagina;

    @Inject
    @Location("ipv6/resolucao.html")
    Template resolucaoPagina;

    @Inject
    @Location("ipv6/partials/resultado_resolucao.html")
    Template resultadoResolucao;

    @Inject
    @Location("ipv6/partials/resultado_projeto.html")
    Template resultadoProjeto;

    @Inject
    @Location("ipv6/partials/resultado_engenharia.html")
    Template resultadoEngenharia;

    @Inject
    @Location("ipv6/partials/resultado_analise.html")
    Template resultadoAnalise;

    @Inject
    @Location("ipv6/partials/resultado_divisao.html")
    Template resultadoDivisao;

    @Inject
    @Location("ipv6/partials/resultado_eui64.html")
    Template resultadoEui64;

    @Inject
    @Location("ipv6/partials/resultado_ula.html")
    Template resultadoUla;

    @Inject
    @Location("ipv6/partials/resultado_comparacao.html")
    Template resultadoComparacao;

    @Inject
    @Location("ipv6/partials/resultado_nibbles.html")
    Template resultadoNibbles;

    @Inject
    @Location("ipv6/partials/resultado_dominio.html")
    Template resultadoDominio;

    @Inject
    @Location("ipv6/partials/resultado_sumarizacao.html")
    Template resultadoSumarizacao;

    @Inject
    @Location("ipv6/partials/resultado_faixa.html")
    Template resultadoFaixa;

    @Inject
    @Location("ipv6/partials/resultado_vlan.html")
    Template resultadoVlan;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "ipv6");
    }

    /** Página "Análise Didática IPv6" (análise rica + comparador + domínio). */
    @GET
    @Path("/analise")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaAnalise() {
        return analisePagina.data("activeMainMenu", "ipv6-analise");
    }

    /** Página "Resolução IPv6" (planejador de delegação de prefixo). */
    @GET
    @Path("/resolucao")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance paginaResolucao() {
        return resolucaoPagina.data("activeMainMenu", "ipv6-resolucao");
    }

    /** Planeja a delegação de prefixo: aloca um /alvo por nome de sub-rede. */
    @POST
    @Path("/api/resolver")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance resolver(
            @FormParam("base") String base,
            @FormParam("prefixoAlvo") String prefixoAlvo,
            @FormParam("nomes") String nomes) {
        java.util.List<String> lista = nomes == null ? java.util.List.of()
                : java.util.Arrays.asList(nomes.split("\\r?\\n"));
        return resultadoResolucao.data("p", service.planejarDelegacao(base, parsePrefixo(prefixoAlvo), lista));
    }

    /** Projeta uma rede IPv6 completa (aba Projetar do Laboratório de Resolução). */
    @POST
    @Path("/api/projetar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance projetar(
            @FormParam("base") String base,
            @FormParam("prefixoLan") String prefixoLan,
            @FormParam("prefixoWan") String prefixoWan,
            @FormParam("topologia") String topologia,
            @FormParam("local") java.util.List<String> locais,
            @FormParam("locais") String locaisTexto,
            @FormParam("eigrpAs") String eigrpAs,
            @FormParam("ospfProc") String ospfProc) {
        // Localidades dinâmicas chegam como vários campos 'local'; 'locais' (textarea) é reserva.
        java.util.List<String> lista;
        if (locais != null && !locais.isEmpty()) {
            lista = locais;
        } else if (locaisTexto != null && !locaisTexto.isBlank()) {
            lista = java.util.Arrays.asList(locaisTexto.split("\\r?\\n"));
        } else {
            lista = java.util.List.of();
        }
        return resultadoProjeto.data("p", service.projetarRede(base, parsePrefixo(prefixoLan),
                parsePrefixo(prefixoWan), topologia, lista, parseInteiro(eigrpAs, 100), parseInteiro(ospfProc, 1)));
    }

    /** Engenharia reversa de configuração Cisco IPv6 (aba do Laboratório de Resolução). */
    @POST
    @Path("/api/engenharia")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance engenharia(@FormParam("config") String config) {
        return resultadoEngenharia.data("e", service.engenhariaReversa(config));
    }

    private int parseInteiro(String bruto, int padrao) {
        String t = bruto == null ? "" : bruto.strip();
        if (t.isEmpty()) {
            return padrao;
        }
        try {
            return org.framework.net.shared.NumeroAscii.parseIntEstrito(t);
        } catch (NumberFormatException ex) {
            return padrao;
        }
    }

    /** Analisa um endereço ou prefixo IPv6 (normalização, tipo, rede, faixa, IID, binário). */
    @POST
    @Path("/api/calcular")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance calcular(@FormParam("endereco") String endereco) {
        return resultadoAnalise.data("r", service.analisarRica(endereco));
    }

    /** Decomposição por nibble + expansão/compressão (aba Nibbles da Análise). */
    @POST
    @Path("/api/nibbles")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance nibbles(@FormParam("endereco") String endereco) {
        return resultadoNibbles.data("n", service.nibbles(endereco));
    }

    /** Divide um prefixo base em sub-redes do prefixo alvo. */
    @POST
    @Path("/api/dividir")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance dividir(
            @FormParam("bloco") String bloco,
            @FormParam("prefixoAlvo") String prefixoAlvo) {
        return resultadoDivisao.data("d", service.dividir(bloco, parsePrefixo(prefixoAlvo)));
    }

    /** Deriva o EUI-64 / endereço SLAAC de um prefixo /64 + MAC. */
    @POST
    @Path("/api/eui64")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance eui64(@FormParam("prefixo") String prefixo, @FormParam("mac") String mac) {
        return resultadoEui64.data("e", service.eui64(prefixo, mac));
    }

    /** Gera um prefixo ULA (RFC 4193). */
    @POST
    @Path("/api/ula")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance ula(@FormParam("subnetId") String subnetId) {
        return resultadoUla.data("u", service.gerarUla(subnetId));
    }

    /** Sumariza N prefixos IPv6 (um por linha) em um supernet + blocos mesclados mínimos. */
    @POST
    @Path("/api/sumarizar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance sumarizar(@FormParam("prefixos") String prefixos) {
        java.util.List<String> lista = prefixos == null ? java.util.List.of()
                : java.util.Arrays.asList(prefixos.split("\\r?\\n"));
        return resultadoSumarizacao.data("s", service.sumarizar(lista));
    }

    /** Plano de VLANs IPv6 (um /64 por VLAN + gateway + trunk + Cisco SVI). */
    @POST
    @Path("/api/vlan")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance vlan(
            @FormParam("base") String base,
            @FormParam("prefixoLan") String prefixoLan,
            @FormParam("vlanId") java.util.List<String> vlanIds,
            @FormParam("vlanNome") java.util.List<String> vlanNomes,
            @FormParam("dhcpv6") String dhcpv6) {
        boolean dhcp = "on".equalsIgnoreCase(dhcpv6) || "true".equalsIgnoreCase(dhcpv6) || "1".equals(dhcpv6);
        return resultadoVlan.data("v", service.planejarVlans(base, parsePrefixo(prefixoLan), vlanIds, vlanNomes, dhcp));
    }

    /** Converte uma faixa [início, fim] IPv6 na lista mínima de blocos CIDR. */
    @POST
    @Path("/api/faixa")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance faixa(@FormParam("inicio") String inicio, @FormParam("fim") String fim) {
        return resultadoFaixa.data("f", service.faixaParaCidr(inicio, fim));
    }

    /** Compara dois endereços/prefixos IPv6 (mesma /64, contenção, distância). */
    @POST
    @Path("/api/comparar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance comparar(@FormParam("a") String a, @FormParam("b") String b) {
        return resultadoComparacao.data("c", service.comparar(a, b));
    }

    /** Resolve o registro AAAA (IPv6) de um domínio e analisa o endereço. */
    @POST
    @Path("/api/dominio")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance dominio(@FormParam("dominio") String dominio) {
        return resultadoDominio.data("dom", service.resolverDominio(dominio));
    }

    /**
     * Exporta a análise IPv6 do {@code endereco} informado em JSON (protegido por chave admin, como
     * o {@code /export/json} do IPv4). Vive neste mesmo resource pelo invariante do módulo.
     */
    @GET
    @Path("/export/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> exportarJson(@QueryParam("endereco") String endereco) {
        Ipv6AnaliseRica.Resultado r = service.analisarRica(endereco);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generated_at", Instant.now().toString());
        payload.put("entrada", r.base().entrada());
        payload.put("comprimido", r.base().comprimido());
        payload.put("expandido", r.base().expandido());
        payload.put("prefixo", r.base().prefixo());
        payload.put("tipo", r.base().tipo());
        payload.put("descricao_tipo", r.base().descricaoTipo());
        payload.put("rede", r.base().rede());
        payload.put("primeiro", r.base().primeiro());
        payload.put("ultimo", r.base().ultimo());
        payload.put("total_enderecos", r.totalEnderecos());
        payload.put("total_potencia", r.totalPotencia());
        payload.put("interface_id", r.base().interfaceId());
        payload.put("solicited_node", r.base().solicitedNode());
        payload.put("reverso_ip6_arpa", r.base().reversePtr());
        payload.put("gateway", r.gateway());
        payload.put("bits_rede", r.bitsRede());
        payload.put("bits_interface", r.bitsInterface());
        payload.put("delegacao", r.delegacao());
        payload.put("referencia", r.referencia());
        return payload;
    }

    /**
     * Exporta a análise IPv6 do {@code endereco} em PDF (protegido por chave admin), reusando o
     * {@link PdfSimplesService} do IPv4.
     */
    @GET
    @Path("/export/pdf")
    public Response exportarPdf(@QueryParam("endereco") String endereco) throws IOException {
        Ipv6AnaliseRica.Resultado r = service.analisarRica(endereco);
        byte[] pdf = PdfSimplesService.gerarPdfSimples(r.textoCopia());
        return Response.ok(pdf)
                .type("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"analise_ipv6.pdf\"")
                .build();
    }

    private int parsePrefixo(String bruto) {
        String txt = bruto == null ? "" : bruto.strip();
        if (txt.startsWith("/")) {
            txt = txt.substring(1).strip();
        }
        if (txt.isEmpty()) {
            throw new Ipv6Exception("Informe o prefixo alvo (ex.: 64).");
        }
        try {
            return org.framework.net.shared.NumeroAscii.parseIntEstrito(txt);
        } catch (NumberFormatException ex) {
            throw new Ipv6Exception("Prefixo alvo inválido: use um número de 0 a 128.");
        }
    }
}
