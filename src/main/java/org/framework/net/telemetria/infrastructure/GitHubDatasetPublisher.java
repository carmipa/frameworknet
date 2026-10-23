package org.framework.net.telemetria.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Envia o snapshot do dataset direto para o repositório público no GitHub.
 *
 * <p><b>Propósito de negócio:</b> o botão "Sincronizar dataset" publica sem que
 * nada fique guardado no servidor — gera em memória, envia, descarta. Menos um
 * lugar onde dado de visitante pode ficar esquecido em disco.</p>
 *
 * <p><b>Invariantes do domínio:</b> a regra <i>append-only</i> do repositório é
 * garantida pelo <b>protocolo</b>, não pela nossa disciplina: a API de conteúdo do
 * GitHub só sobrescreve um arquivo quando recebe o {@code sha} da versão atual, e
 * este publicador <b>nunca</b> envia {@code sha}. Arquivo que já existe faz o
 * GitHub responder 422 e o envio para ali. Não há caminho de código, nem por
 * engano, que reescreva um snapshot publicado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nada lança para a apresentação. Sem
 * token configurado, sem rede ou com recusa do GitHub, devolve
 * {@link Resultado} negativo com motivo curto. O token nunca aparece em log,
 * mensagem de erro ou telemetria — só o status HTTP e o caminho do arquivo.</p>
 */
@ApplicationScoped
public class GitHubDatasetPublisher {

    private static final String API = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @ConfigProperty(name = "framework.dataset.github.token")
    Optional<String> tokenConfig;

    @ConfigProperty(name = "framework.dataset.github.repositorio")
    Optional<String> repositorioConfig;

    @ConfigProperty(name = "framework.dataset.github.branch")
    Optional<String> branchConfig;

    @Inject
    ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    private String token() {
        return tokenConfig.orElse("").strip();
    }

    /** No formato {@code dono/repositorio}. */
    private String repositorio() {
        return repositorioConfig.orElse("").strip();
    }

    private String branch() {
        String b = branchConfig.orElse("").strip();
        return b.isEmpty() ? "main" : b;
    }

    /** Há credencial e destino para sincronizar? */
    public boolean configurado() {
        return !token().isBlank() && repositorio().contains("/");
    }

    /** Destino, para exibir na tela. Nunca inclui credencial. */
    public String destinoParaExibicao() {
        return configurado() ? repositorio() + " (" + branch() + ")" : "não configurado";
    }

    /**
     * Cria os arquivos do snapshot no repositório.
     *
     * <p><b>Invariantes do domínio:</b> um arquivo por requisição, sem {@code sha}
     * — criar, nunca sobrescrever. Se o primeiro arquivo já existir, o snapshot
     * daquela data já foi publicado e a operação inteira para: publicar metade de
     * um snapshot seria pior que não publicar.</p>
     */
    public Resultado publicar(String data, Map<String, String> arquivos) {
        if (!configurado()) {
            return Resultado.falha("Sincronização não configurada: falta o token ou o repositório.");
        }

        List<String> criados = new ArrayList<>();
        for (Map.Entry<String, String> arquivo : arquivos.entrySet()) {
            Envio envio = enviar(arquivo.getKey(), arquivo.getValue(), data);
            if (!envio.ok()) {
                if (envio.jaExiste()) {
                    return Resultado.jaPublicado(data, criados);
                }
                return Resultado.falha("Falha ao enviar " + arquivo.getKey()
                        + " (HTTP " + envio.status() + "). " + criados.size()
                        + " arquivo(s) já haviam sido criados.");
            }
            criados.add(arquivo.getKey());
        }
        return Resultado.sucesso(data, criados,
                "https://github.com/" + repositorio() + "/tree/" + branch() + "/dataset/" + data);
    }

    private Envio enviar(String caminho, String conteudo, String data) {
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("message", "Snapshot " + data + " — sincronizado pelo painel de telemetria");
            corpo.put("content", Base64.getEncoder()
                    .encodeToString(conteudo.getBytes(StandardCharsets.UTF_8)));
            corpo.put("branch", branch());
            // Sem "sha" de proposito: a API so sobrescreve quando ele e enviado.
            // A ausencia dele E a garantia de append-only.

            HttpRequest requisicao = HttpRequest.newBuilder(
                            URI.create(API + "/repos/" + repositorio() + "/contents/" + caminho))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token())
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "framework-net-java-quarkus")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(corpo), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = cliente().send(requisicao, HttpResponse.BodyHandlers.ofString());
            int status = resposta.statusCode();
            boolean jaExiste = status == 422
                    && resposta.body() != null && resposta.body().contains("sha");
            return new Envio(status == 201 || status == 200, jaExiste, status);
        } catch (Exception ex) {
            return new Envio(false, false, 0);
        }
    }

    private HttpClient cliente() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    /**
     * Snapshots que EXISTEM hoje no repositorio publico.
     *
     * <p><b>Proposito de negocio:</b> responder "foi publicado ou nao?" lendo a
     * fonte de verdade, e nao o nosso proprio log. Log diz o que tentamos fazer;
     * o repositorio diz o que de fato esta la. Quando os dois divergem, quem
     * manda e o repositorio.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> lista vazia e o motivo. Nao
     * distingue "nao ha snapshot" de "nao consegui perguntar" no valor de
     * retorno: quem chama recebe o campo {@code consultado} para nao confundir
     * ausencia de dado com ausencia de resposta.</p>
     */
    public Estado listarSnapshots() {
        if (repositorio().isBlank() || !repositorio().contains("/")) {
            return new Estado(false, List.of(), "Repositorio nao configurado.");
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(
                            URI.create(API + "/repos/" + repositorio() + "/contents/dataset?ref=" + branch()))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "framework-net-java-quarkus")
                    .GET();
            // O repositorio e publico: da para consultar sem token. Se houver, usa.
            if (!token().isBlank()) {
                b.header("Authorization", "Bearer " + token());
            }
            HttpResponse<String> r = cliente().send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) {
                return new Estado(true, List.of(), "Nenhum snapshot publicado ainda.");
            }
            if (r.statusCode() != 200) {
                return new Estado(false, List.of(), "GitHub respondeu HTTP " + r.statusCode() + ".");
            }
            List<String> datas = new ArrayList<>();
            for (var no : objectMapper.readTree(r.body())) {
                if ("dir".equals(no.path("type").asText())) {
                    datas.add(no.path("name").asText());
                }
            }
            datas.sort(java.util.Comparator.reverseOrder());
            return new Estado(true, datas, "");
        } catch (Exception ex) {
            return new Estado(false, List.of(), "Nao foi possivel consultar o repositorio.");
        }
    }

    /** O que existe publicado, e se conseguimos mesmo perguntar. */
    public record Estado(boolean consultado, List<String> snapshots, String observacao) {
    }

    private record Envio(boolean ok, boolean jaExiste, int status) {
    }

    /** Desfecho da sincronização, pronto para a tela. */
    public record Resultado(boolean ok, String data, List<String> arquivos, String url, String mensagem) {

        static Resultado sucesso(String data, List<String> arquivos, String url) {
            return new Resultado(true, data, arquivos, url,
                    arquivos.size() + " arquivo(s) publicado(s) no snapshot " + data + ".");
        }

        static Resultado jaPublicado(String data, List<String> arquivos) {
            return new Resultado(false, data, arquivos, "",
                    "O snapshot " + data + " já existe no repositório e não pode ser sobrescrito — "
                            + "a regra é acrescentar, nunca reescrever. Publique amanhã ou remova a "
                            + "pasta manualmente se ela estiver incompleta.");
        }

        static Resultado falha(String motivo) {
            return new Resultado(false, "", List.of(), "", motivo);
        }
    }
}
