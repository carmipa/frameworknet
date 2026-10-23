package org.framework.net.analiseTrafego.presentation;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo;
import org.framework.net.analiseTrafego.aovivo.TrafegoAoVivoService;
import org.framework.net.analiseTrafego.application.ConstrutorPacoteService;
import org.framework.net.analiseTrafego.application.LabDnsIcmpService;
import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.PacoteConstruido;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.List;
import java.util.Map;

/**
 * Módulo Tráfego: dashboard de tráfego ao vivo (simulação didática) +
 * decodificador didático de pacotes (offline).
 */
@Path("/trafego")
public class TrafegoResource {

    @Inject
    TrafegoDecoderService decoderService;

    @Inject
    ConstrutorPacoteService construtorService;

    @Inject
    LabDnsIcmpService labDnsIcmpService;

    @Inject
    TrafegoAoVivoService aoVivoService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @io.quarkus.qute.Location("trafego/index.html")
    Template index;

    @Inject
    @io.quarkus.qute.Location("trafego/partials/resultado_decodificacao.html")
    Template decodificacaoFragmento;

    @Inject
    @io.quarkus.qute.Location("trafego/partials/resultado_construcao.html")
    Template construcaoFragmento;

    @Inject
    @io.quarkus.qute.Location("trafego/partials/resultado_lab.html")
    Template labFragmento;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "trafego")
                .data("cenariosLab", labDnsIcmpService.cenariosDisponiveis());
    }

    /**
     * Decodifica o hex dump e devolve as camadas já renderizadas como fragmento,
     * que o htmx troca no painel de resultado. Hex inválido também volta como
     * fragmento (com o aviso), e não como erro HTTP.
     */
    @POST
    @Path("/api/decodificar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance decodificar(
            @FormParam("hex") String hex,
            @FormParam("camada") @DefaultValue("auto") String camada) {
        ResultadoDecodificacao resultado = decoderService.decodificar(hex, camada);
        telemetriaLogger.logEvent(resultado.ok() ? "info" : "warn", "analiseTrafego", "decode_packet",
                resultado.ok() ? "ok" : "error",
                Map.of("bytes", resultado.totalBytes(), "camadas", resultado.camadas().size()));
        return decodificacaoFragmento.data("resultado", resultado);
    }

    /**
     * Monta um pacote sintético a partir dos campos do formulário e devolve o
     * fragmento com os bytes em hex + o botão que os decodifica de volta. As
     * flags TCP chegam como múltiplos valores do mesmo campo e viram um CSV.
     */
    @POST
    @Path("/api/construir")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance construir(
            @FormParam("protocolo") @DefaultValue("tcp") String protocolo,
            @FormParam("ipOrigem") String ipOrigem,
            @FormParam("ipDestino") String ipDestino,
            @FormParam("portaOrigem") @DefaultValue("0") String portaOrigem,
            @FormParam("portaDestino") @DefaultValue("0") String portaDestino,
            @FormParam("flags") List<String> flags,
            @FormParam("ttl") @DefaultValue("64") String ttl,
            @FormParam("seq") @DefaultValue("0") String seq,
            @FormParam("window") @DefaultValue("0") String window,
            @FormParam("mensagem") @DefaultValue("") String mensagem,
            @FormParam("checksum") @DefaultValue("valido") String checksum) {
        String flagsCsv = flags == null ? "" : String.join(",", flags);
        boolean checksumValido = !"invalido".equals(checksum);
        PacoteConstruido pacote = construtorService.montar(protocolo, ipOrigem, ipDestino,
                portaOrigem, portaDestino, flagsCsv, ttl, seq, window, mensagem, checksumValido);
        return construcaoFragmento.data("sim", pacote);
    }

    /**
     * Laboratório DNS/ICMP: devolve o fragmento com o dataset fictício de um
     * cenário, classificado com evidência. GET porque só lê o catálogo; cenário
     * desconhecido vira HTTP 400.
     */
    @GET
    @Path("/api/lab")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance laboratorio(@QueryParam("cenario") String cenario) {
        try {
            return labFragmento.data("lab", labDnsIcmpService.analisar(cenario));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Snapshot do tráfego ao vivo (simulação didática, VPS-safe). */
    @GET
    @Path("/api/aovivo")
    @Produces(MediaType.APPLICATION_JSON)
    public SnapshotAoVivo aovivo() {
        return aoVivoService.snapshotDemo();
    }
}
