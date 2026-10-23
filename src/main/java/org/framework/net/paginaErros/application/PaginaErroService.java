package org.framework.net.paginaErros.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.paginaErros.domain.CatalogoErros;
import org.framework.net.paginaErros.domain.CatalogoErros.ErroApresentado;
import org.framework.net.telemetria.TelemetriaContext;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.telemetria.TelemetriaRequestContext;
import org.framework.net.telemetria.TetoDeEventos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Monta o conteúdo da página de erro e registra o evento.
 *
 * <p><b>Propósito de negócio:</b> transformar um código HTTP seco no material que
 * a tela precisa — texto, ícone, rota tentada e, principalmente, o
 * {@code trace_id} <em>real</em> da requisição. É esse identificador que liga o
 * que o usuário viu ao evento gravado na telemetria; sem ele, "deu erro na tela"
 * é um relato que ninguém consegue investigar.</p>
 *
 * <p><b>Invariantes do domínio:</b> o {@code traceId} exibido é <b>sempre</b> o
 * mesmo que a telemetria registrou para aquele evento. Há dois caminhos: quando
 * existe correlação da requisição no MDC, ela é reaproveitada; quando não existe,
 * um identificador é gerado aqui e vai <b>ao mesmo tempo</b> para a tela e para o
 * evento. O segundo caminho não é enfeite: em rota inexistente o
 * {@code TelemetriaRequestFilter} sequer roda (não é {@code @PreMatching}), e o
 * 404 é justamente o erro mais frequente — deixar o campo como "indisponível" ali
 * esvaziaria a promessa de rastreio no caso que mais importa.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nunca lança. Falha ao ler o contexto
 * degrada para identificador gerado; falha ao registrar o evento é absorvida pelo
 * logger. Uma página de erro que quebra ao ser montada deixaria o usuário com a
 * tela branca que ela existe para evitar.</p>
 */
@ApplicationScoped
public class PaginaErroService {

    private static final String MODULO = "paginaErros";

    @Inject
    TelemetriaContext telemetriaContext;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TetoDeEventos tetoDeEventos;

    /**
     * Dados prontos para o template.
     *
     * @param codigo status HTTP da resposta
     * @param caminho rota que o usuário tentou alcançar
     * @param metodo verbo HTTP da tentativa
     */
    public DadosPaginaErro montar(int codigo, String caminho, String metodo) {
        String rota = caminho == null || caminho.isBlank() ? "/" : caminho;
        ErroApresentado erro = CatalogoErros.porCodigo(codigo, rota);
        String traceId = resolverTraceId();

        registrar(codigo, rota, metodo, traceId);
        return new DadosPaginaErro(erro, codigo, rota, metodo == null ? "GET" : metodo, traceId);
    }

    /**
     * Identificador de rastreio a exibir e a registrar.
     *
     * <p><b>Invariantes do domínio:</b> o valor devolvido aqui é o mesmo que
     * {@link #registrar} grava no evento — é isso que torna o número da tela útil
     * para quem for investigar. Reaproveita a correlação da requisição quando ela
     * existe; caso contrário gera uma, com prefixo {@code err-} para deixar claro
     * na busca que ela nasceu na página de erro e não no filtro de entrada.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> qualquer erro ao ler o MDC cai no
     * identificador gerado — nunca devolve vazio.</p>
     */
    private String resolverTraceId() {
        try {
            TelemetriaRequestContext ctx = telemetriaContext.contextoDoMdc();
            if (ctx != null) {
                if (ctx.traceId() != null && !ctx.traceId().isBlank()) {
                    return ctx.traceId();
                }
                if (ctx.requestId() != null && !ctx.requestId().isBlank()) {
                    return ctx.requestId();
                }
            }
        } catch (RuntimeException ex) {
            // Cai para o identificador gerado logo abaixo.
        }
        return "err-" + UUID.randomUUID().toString().substring(0, 13);
    }

    /**
     * Grava o evento {@code pagina_erro_exibida}.
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> erro de cliente (4xx) passa pelo teto por origem
     * ({@link TetoDeEventos}) — 404 em série gravava um evento por requisição e o caminho escolhido pelo
     * cliente inteiro (auditoria SEC-02); a rota vai saneada e truncada
     * ({@link TelemetriaContext#caminhoParaLog}). Erro do servidor (5xx) grava sempre.</p>
     */
    private void registrar(int codigo, String rota, String metodo, String traceId) {
        int suprimidosAntes = 0;
        if (codigo < 500) {
            TetoDeEventos.Decisao decisao = tetoDeEventos.avaliar("pagina-erro-" + codigo);
            if (!decisao.gravar()) {
                return;
            }
            suprimidosAntes = decisao.suprimidosAntes();
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("httpStatus", codigo);
        campos.put("rota", TelemetriaContext.caminhoParaLog(rota));
        if (suprimidosAntes > 0) {
            campos.put("suprimidosAntes", suprimidosAntes);
        }
        campos.put("metodo", metodo == null ? "GET" : metodo);
        campos.put("traceId", traceId);
        String nivel = codigo >= 500 ? "error" : "warn";
        telemetriaLogger.logEvent(nivel, MODULO, "pagina_erro_exibida",
                codigo >= 500 ? "error" : "warn", campos);
    }

    /**
     * O que o template recebe.
     *
     * <p><b>Invariantes do domínio:</b> nenhum campo é nulo — o template renderiza
     * direto e {@code null} viraria a palavra "null" na tela do usuário.</p>
     */
    public record DadosPaginaErro(
            ErroApresentado erro,
            int codigo,
            String caminho,
            String metodo,
            String traceId) {

        /** Rota exibida no terminal de diagnóstico, com o verbo à frente. */
        public String rotaComVerbo() {
            return metodo + " " + caminho;
        }

        /**
         * Título da aba da página de erro.
         * PROPÓSITO DE NEGÓCIO: quem tem várias abas abertas reconhece o erro e o site pelo título.
         * INVARIANTES: mesmo formato das outras páginas, "&lt;página&gt; | Framework de Redes A&amp;D"
         *   (FRONT-22: o site tinha cinco marcas diferentes); o Qute escapa o "&amp;" ao renderizar.
         * FALHA: não falha; o código vem do próprio erro já classificado.
         */
        public String tituloDaAba() {
            return (semRede() ? "Sem conexão" : String.valueOf(codigo)) + " | Framework de Redes A&D";
        }

        /**
         * Diz se a página representa o aparelho sem rede, e não uma resposta HTTP.
         * PROPÓSITO DE NEGÓCIO: a página offline sai do mesmo template das páginas de erro.
         * INVARIANTES: só o código reservado {@code CatalogoErros.SEM_CONEXAO} responde verdadeiro.
         * FALHA: não falha.
         */
        public boolean semRede() {
            return codigo == org.framework.net.paginaErros.domain.CatalogoErros.SEM_CONEXAO;
        }

        /**
         * O número grande da tela.
         * PROPÓSITO DE NEGÓCIO: sem rede não existe código HTTP; mostrar "0" seria ruído.
         * INVARIANTES: código HTTP real aparece como veio; sem rede aparece "OFF".
         * FALHA: não falha.
         */
        public String codigoExibido() {
            return semRede() ? "OFF" : String.valueOf(codigo);
        }

        /**
         * Texto da pílula de status do topo.
         * PROPÓSITO DE NEGÓCIO: diz em uma palavra de onde veio o erro.
         * INVARIANTES: "HTTP &lt;código&gt;" para resposta real; "SEM REDE" quando nada respondeu.
         * FALHA: não falha.
         */
        public String statusLinha() {
            return semRede() ? "SEM REDE" : "HTTP " + codigo;
        }
    }
}
