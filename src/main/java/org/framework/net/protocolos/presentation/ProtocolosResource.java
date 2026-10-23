package org.framework.net.protocolos.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.protocolos.application.BgpAprofundamentoService;
import org.framework.net.protocolos.application.DnsAprofundamentoService;
import org.framework.net.protocolos.application.ProtocolosAprofundamentoService;
import org.framework.net.protocolos.application.ProtocolosService;
import org.framework.net.protocolos.application.SshAprofundamentoService;
import org.framework.net.protocolos.application.TlsAprofundamentoService;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.framework.net.protocolos.exception.ProtocolosException;

/**
 * Entrada HTTP do módulo de Protocolos: catálogo e aprofundamentos.
 *
 * <p><b>Propósito de negócio:</b> {@code /protocolos} é a aba Geral — o
 * DataGrid comparativo com os 47 protocolos do catálogo. Abaixo dela ficam as
 * páginas de aprofundamento, uma por protocolo ({@code /protocolos/bgp},
 * {@code /protocolos/ssh}), que respondem à pergunta que a tabela não responde:
 * "me explica este protocolo".</p>
 *
 * <p><b>Invariantes do domínio:</b> todas as rotas do módulo vivem NESTA classe,
 * de propósito. O projeto já pagou o preço de dois {@code @Path} concorrendo sob
 * o mesmo prefixo ({@code /trafego/api/*} devolvendo 404 quando decodificador e
 * painel ao vivo eram resources separados); um único resource por módulo elimina
 * a ambiguidade de casamento de rota. O sub-menu de toda página do módulo vem de
 * {@link AprofundamentoProtocolo#disponiveis()} — nunca escrito à mão no
 * template — para que o registro continue sendo a fonte única.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> conteúdo ausente ou inválido já
 * derrubou a aplicação no boot (os catálogos falham fechados). Slug registrado
 * sem correspondência lança {@link ProtocolosException}, traduzida em resposta
 * de erro por {@code ProtocolosExceptionMapper}; slug inexistente na URL não
 * chega aqui — não há rota curinga, então o JAX-RS responde 404.</p>
 */
@Path("/protocolos")
public class ProtocolosResource {

    private static final String MENU_ATIVO = "protocolos";

    @Inject
    ProtocolosService protocolosService;

    @Inject
    BgpAprofundamentoService bgpAprofundamentoService;

    @Inject
    SshAprofundamentoService sshAprofundamentoService;

    @Inject
    DnsAprofundamentoService dnsAprofundamentoService;

    @Inject
    TlsAprofundamentoService tlsAprofundamentoService;

    @Inject
    ProtocolosAprofundamentoService protocolosAprofundamentoService;

    @Inject
    @Location("protocolos/index.html")
    Template index;

    @Inject
    @Location("protocolos/aprofundamento.html")
    Template aprofundamento;

    @Inject
    @Location("protocolos/bgp/index.html")
    Template bgp;

    @Inject
    @Location("protocolos/ssh/index.html")
    Template ssh;

    @Inject
    @Location("protocolos/dns/index.html")
    Template dns;

    @Inject
    @Location("protocolos/tls/index.html")
    Template tls;

    /** Aba Geral: o catálogo comparativo completo. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listar() {
        return comSubMenu(index, "geral")
                .data("protocolosCatalogo", protocolosService.montarProtocolosCatalogoExibicao())
                .data("protocolosTroubleshooting", protocolosService.montarTroubleshootingRoteamento());
    }

    /** Aprofundamento do BGP-4. */
    @GET
    @Path("/bgp")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundamentoBgp() {
        return comSubMenu(bgp, "bgp")
                .data("conteudo", bgpAprofundamentoService.carregarParaExibicao());
    }

    /** Aprofundamento do SSH. */
    @GET
    @Path("/ssh")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundamentoSsh() {
        return comSubMenu(ssh, "ssh")
                .data("conteudo", sshAprofundamentoService.carregarParaExibicao());
    }

    /** Aprofundamento do DNS. */
    @GET
    @Path("/dns")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundamentoDns() {
        return comSubMenu(dns, "dns")
                .data("conteudo", dnsAprofundamentoService.carregarParaExibicao());
    }

    /** Aprofundamento do TLS. */
    @GET
    @Path("/tls")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundamentoTls() {
        return comSubMenu(tls, "tls")
                .data("conteudo", tlsAprofundamentoService.carregarParaExibicao());
    }

    /**
     * Aprofundamento GENÉRICO (HTTP, FTP, SMTP, Telnet, Handshake, e adiante
     * TCP/UDP/ICMP/IPv4/IPv6/ARP): um único template alimentado por
     * {@code conteudo.json}, resolvido pelo slug da URL.
     *
     * <p><b>Invariantes do domínio:</b> esta rota curinga só atende slugs
     * registrados como genéricos; os protocolos com página dedicada (BGP, SSH,
     * DNS, TLS) têm {@code @Path} literal, e o JAX-RS prefere o literal ao
     * template, então nunca caem aqui. O filtro {@code generico} impede que um
     * slug dedicado seja atendido pelo template errado caso a ordem de casamento
     * mude.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> slug não registrado, em branco ou
     * dedicado sem ser genérico resulta em {@link NotFoundException} (404 pela
     * página de erro), nunca conteúdo de outro protocolo.</p>
     */
    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance aprofundamentoGenerico(@PathParam("slug") String slug) {
        AprofundamentoProtocolo item = AprofundamentoProtocolo.porSlug(slug)
                .filter(AprofundamentoProtocolo::generico)
                .orElseThrow(NotFoundException::new);
        return comSubMenu(aprofundamento, item.slug())
                .data("item", item)
                .data("conteudo", protocolosAprofundamentoService.carregarParaExibicao(item.slug()));
    }

    /**
     * Prepara a instância do template com o que toda página do módulo precisa.
     *
     * <p><b>Invariantes do domínio:</b> o slug ativo precisa existir no registro
     * (ou ser {@code geral}); assim o sub-menu nunca renderiza sem nenhum item
     * marcado, que é o estado em que o usuário perde a noção de onde está. O
     * sub-menu tem dois níveis (camada → protocolo): {@code camadas} vem de
     * {@link AprofundamentoProtocolo#agrupadoPorCamada()} e {@code camadaAtiva} é
     * a camada do slug ativo (ou {@code geral} na aba comparativa).</p>
     *
     * <p><b>Comportamento em caso de falha:</b> slug desconhecido lança
     * {@link ProtocolosException} em vez de renderizar uma página sem destaque —
     * é erro de programação, e falhar alto o revela no primeiro acesso.</p>
     */
    private TemplateInstance comSubMenu(Template template, String slugAtivo) {
        String camadaAtiva = "geral";
        if (!"geral".equals(slugAtivo)) {
            camadaAtiva = AprofundamentoProtocolo.porSlug(slugAtivo)
                    .map(AprofundamentoProtocolo::camada)
                    .orElseThrow(() -> new ProtocolosException(
                            "Aprofundamento \"" + slugAtivo + "\" não está registrado em AprofundamentoProtocolo"));
        }
        return template
                .data("activeMainMenu", MENU_ATIVO)
                .data("aprofundamentos", AprofundamentoProtocolo.disponiveis())
                .data("camadas", AprofundamentoProtocolo.agrupadoPorCamada())
                .data("camadaAtiva", camadaAtiva)
                .data("aprofundamentoAtivo", slugAtivo);
    }
}
