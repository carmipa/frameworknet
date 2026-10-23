package org.framework.net.paginaErros.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo dos estados de erro apresentáveis ao usuário.
 *
 * <p><b>Propósito de negócio:</b> quando alguém erra a URL, perde a sessão ou
 * esbarra num limite, a resposta padrão do servidor é uma tela branca com uma
 * frase em inglês — o pior momento possível para o sistema parecer quebrado. Este
 * catálogo dá a cada código HTTP um texto em português que explica <em>o que
 * aconteceu</em> e <em>o que fazer</em>, no vocabulário de redes do próprio
 * framework.</p>
 *
 * <p><b>Invariantes do domínio:</b> o catálogo é a fonte única dos textos — a
 * página não os escreve à mão, e o CSS deriva a cor de acento da classe
 * {@code err-<código>}. Código desconhecido nunca produz página em branco: cai no
 * texto genérico da própria família (com a cor de 400 ou 500), porque erro sem
 * texto é exatamente o problema que esta tela existe para resolver — mas a linha
 * de status mostra o código real. 401 e 403 têm texto próprio fora da
 * administração ({@link AreaDoErro}). Todo código emitido pela aplicação tem
 * entrada aqui, e uma guarda nos testes cruza os dois.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> {@link #porCodigo(int)} sempre devolve
 * um {@link ErroApresentado}; não há caminho que retorne nulo.</p>
 */
public final class CatalogoErros {

    private CatalogoErros() {
    }

    /**
     * Um estado de erro pronto para a tela.
     *
     * <p><b>Invariantes do domínio:</b> {@code hint} descreve a <em>classe</em> do
     * problema, nunca o detalhe interno da exceção. Nome de classe Java, trecho de
     * stack e mensagem de driver não vão para a tela do usuário: quem precisa
     * diagnosticar usa o {@code traceId}, que liga a página ao evento na
     * telemetria.</p>
     */
    public record ErroApresentado(
            int codigo,
            String badge,
            String icone,
            String artTag,
            String titulo,
            String descricao,
            String statusTexto,
            String hint) {

        /** Classe CSS que define a cor de acento, a aura e a cor da chuva Matrix. */
        public String classeCss() {
            return "err-" + codigo;
        }

        public boolean cliente() {
            return codigo >= 400 && codigo < 500;
        }
    }

    private static final Map<Integer, ErroApresentado> CATALOGO = criar();

    private static Map<Integer, ErroApresentado> criar() {
        Map<Integer, ErroApresentado> mapa = new LinkedHashMap<>();

        // ---------- 4xx · cliente ----------
        mapa.put(400, new ErroApresentado(400, "REQUISIÇÃO MALFORMADA", "data_object",
                "PAYLOAD CORROMPIDO",
                "A requisição não pôde ser interpretada.",
                "A sintaxe ou o corpo enviado está inválido. Revise os campos, os parâmetros e os "
                        + "tipos de dados antes de reenviar.",
                "400 Bad Request",
                "Corpo da requisição fora do formato esperado pelo endpoint."));

        mapa.put(401, new ErroApresentado(401, "AUTENTICAÇÃO EXIGIDA", "lock",
                "SESSÃO EXPIRADA",
                "Faça login para continuar.",
                "A Telemetria e as rotas de exportação exigem sessão administrativa ativa. "
                        + "Autentique-se para prosseguir.",
                "401 Unauthorized",
                "Sessão inexistente ou expirada."));

        mapa.put(403, new ErroApresentado(403, "ACESSO BLOQUEADO", "gpp_maybe",
                "FIREWALL ATIVO",
                "Você não tem permissão para este recurso.",
                "Esta rota é protegida por chave administrativa. Autentique-se em Administração "
                        + "para liberar as exportações e a Telemetria.",
                "403 Forbidden",
                "Credencial administrativa ausente ou inválida."));

        mapa.put(404, new ErroApresentado(404, "ROTA NÃO MAPEADA", "travel_explore",
                "PACOTE SEM DESTINO",
                "Esta rota não existe na topologia.",
                "O endereço solicitado não corresponde a nenhum módulo do Framework. Confira a URL "
                        + "ou use um dos atalhos abaixo para retomar a navegação.",
                "404 Not Found",
                "Rota não registrada em nenhum Resource JAX-RS."));

        mapa.put(405, new ErroApresentado(405, "MÉTODO NÃO PERMITIDO", "block",
                "VERBO INCOMPATÍVEL",
                "Este verbo HTTP não é aceito nesta rota.",
                "O endereço existe, mas não responde ao método utilizado. Confira na Documentação "
                        + "qual verbo a rota espera.",
                "405 Method Not Allowed",
                "A rota existe, mas foi registrada para outro método HTTP."));

        mapa.put(409, new ErroApresentado(409, "CONFLITO DE ESTADO", "merge_type",
                "COLISÃO DE ENDEREÇO",
                "Este recurso conflita com um registro existente.",
                "A operação não pôde ser concluída porque geraria duplicidade ou sobreposição. "
                        + "Ajuste os dados e tente novamente.",
                "409 Conflict",
                "O recurso enviado colide com um estado já registrado."));

        mapa.put(422, new ErroApresentado(422, "VALIDAÇÃO FALHOU", "rule",
                "DADOS INCONSISTENTES",
                "Os dados enviados não passaram na validação.",
                "A sintaxe está correta, mas o conteúdo é semanticamente inválido para o cálculo "
                        + "solicitado. Revise os campos destacados.",
                "422 Unprocessable Entity",
                "Valor fora do domínio aceito pela regra de negócio."));

        mapa.put(410, new ErroApresentado(410, "RECURSO REMOVIDO", "link_off",
                "ENDEREÇO DESATIVADO",
                "Este conteúdo foi retirado.",
                "O endereço já existiu, mas o conteúdo foi removido de propósito e não vai voltar. "
                        + "Use o menu para encontrar o que o substituiu.",
                "410 Gone",
                "Recurso removido definitivamente; o endereço não deve ser usado de novo."));

        mapa.put(413, new ErroApresentado(413, "CARGA GRANDE DEMAIS", "cloud_upload",
                "MTU EXCEDIDO",
                "O conteúdo enviado é grande demais.",
                "O servidor recusou o envio pelo tamanho, não pelo formato. Reduza o arquivo ou o "
                        + "texto e envie de novo.",
                "413 Content Too Large",
                "Corpo da requisição acima do limite aceito."));

        mapa.put(414, new ErroApresentado(414, "ENDEREÇO LONGO DEMAIS", "straighten",
                "URL FRAGMENTADA",
                "O endereço da página é longo demais.",
                "A URL passou do tamanho que o servidor aceita — costuma acontecer com link colado "
                        + "com parâmetros repetidos. Volte ao início e navegue pelo menu.",
                "414 URI Too Long",
                "URL acima do limite aceito."));

        mapa.put(415, new ErroApresentado(415, "FORMATO NÃO SUPORTADO", "code_off",
                "CODEC DESCONHECIDO",
                "O formato enviado não é aceito aqui.",
                "O endereço existe, mas não recebe dados neste tipo de conteúdo. Envie pelo "
                        + "formulário da própria página.",
                "415 Unsupported Media Type",
                "Tipo de conteúdo da requisição diferente do esperado pela rota."));

        mapa.put(429, new ErroApresentado(429, "LIMITE EXCEDIDO", "speed",
                "RATE LIMIT ATINGIDO",
                "Muitas requisições em pouco tempo.",
                "O limite de chamadas foi atingido para proteger a plataforma. Aguarde um minuto "
                        + "antes de tentar novamente.",
                "429 Too Many Requests",
                "Limite por minuto excedido para esta origem."));

        mapa.put(431, new ErroApresentado(431, "CABEÇALHOS GRANDES DEMAIS", "view_headline",
                "HEADER OVERFLOW",
                "O navegador enviou cabeçalhos grandes demais.",
                "Normalmente são cookies acumulados. Apague os cookies deste site e recarregue a "
                        + "página.",
                "431 Request Header Fields Too Large",
                "Cabeçalhos ou cookies acima do limite aceito."));

        // ---------- 5xx · servidor ----------
        mapa.put(500, new ErroApresentado(500, "FALHA NO SERVIDOR", "error",
                "PIPELINE INTERROMPIDO",
                "Algo quebrou no processamento.",
                "Um erro inesperado ocorreu ao processar sua requisição. O evento foi registrado "
                        + "na telemetria com o trace_id abaixo, que é o que permite rastreá-lo.",
                "500 Internal Server Error",
                "Exceção não tratada — use o trace_id para localizar o evento na telemetria."));

        mapa.put(502, new ErroApresentado(502, "GATEWAY INVÁLIDO", "router",
                "ROTA SEM PRÓXIMO SALTO",
                "O gateway não conseguiu falar com a aplicação.",
                "A resposta recebida do serviço de origem é inválida ou o backend está "
                        + "inacessível.",
                "502 Bad Gateway",
                "Serviço de origem recusou a conexão."));

        mapa.put(503, new ErroApresentado(503, "SERVIÇO INDISPONÍVEL", "cloud_off",
                "LINK WAN CAÍDO",
                "O serviço está temporariamente fora do ar.",
                "Manutenção ou sobrecarga momentânea. Aguarde alguns instantes e tente novamente.",
                "503 Service Unavailable",
                "Dependência externa sem resposta."));

        mapa.put(504, new ErroApresentado(504, "TEMPO ESGOTADO", "hourglass_disabled",
                "TIMEOUT NA RESPOSTA",
                "A aplicação demorou demais para responder.",
                "O tempo limite foi atingido antes da conclusão do processamento. Tente novamente "
                        + "ou reduza o escopo da operação.",
                "504 Gateway Timeout",
                "Tempo de espera do upstream excedido."));

        mapa.put(507, new ErroApresentado(507, "ARMAZENAMENTO CHEIO", "storage",
                "DISCO SEM ESPAÇO",
                "O servidor ficou sem espaço para guardar isto.",
                "O servidor não conseguiu gravar agora por falta de espaço. As páginas continuam "
                        + "abertas; tente de novo mais tarde.",
                "507 Insufficient Storage",
                "Espaço de armazenamento do servidor esgotado."));

        return Collections.unmodifiableMap(mapa);
    }

    /**
     * Textos de 401 e 403 para quem NÃO está na administração ({@link AreaDoErro#SITE}).
     *
     * <p><b>Invariantes do domínio:</b> nenhum deles cita Telemetria, chave administrativa ou
     * tela de administrador; o 403 lembra a causa honesta mais comum no site, que é a página
     * aberta há tempo demais com o token de segurança do formulário vencido.</p>
     */
    private static final Map<Integer, ErroApresentado> TEXTOS_DO_SITE = Map.of(
            401, new ErroApresentado(401, "IDENTIFICAÇÃO EXIGIDA", "no_accounts",
                    "SEM SESSÃO",
                    "Esta ação pede que você esteja identificado.",
                    "As páginas e as ferramentas continuam abertas. Só esta ação precisa saber quem "
                            + "você é; volte à página anterior e tente de novo por ela.",
                    "401 Unauthorized",
                    "Ação que exige identificação, feita sem sessão."),
            403, new ErroApresentado(403, "AÇÃO NÃO LIBERADA", "lock_person",
                    "PERMISSÃO NEGADA",
                    "Esta ação não está liberada para você.",
                    "Se a página ficou aberta por muito tempo, recarregue-a: o token de segurança "
                            + "do formulário expira. Se persistir, volte ao início.",
                    "403 Forbidden",
                    "Permissão ausente ou token de formulário vencido."));

    /** Código reservado ao estado "sem conexão": não é status HTTP, porque nenhuma resposta chegou. */
    public static final int SEM_CONEXAO = 0;

    /**
     * Estado da página offline: o aparelho do visitante está sem rede.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> a página {@code /offline.html} que o service worker mostra sem rede
     * tinha visual próprio, diferente de todas as outras telas de erro do site. Ela passa a sair do mesmo
     * template e deste catálogo, no mesmo vocabulário de redes ("LINK LOCAL CAÍDO").</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o código é {@link #SEM_CONEXAO}, nunca um status HTTP real — mostrar
     * 503 diria que o servidor respondeu, quando o pedido nem saiu do aparelho; não entra em
     * {@link #codigos()}, porque não é resposta que a aplicação devolva.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não falha; o valor é constante.</p>
     */
    public static ErroApresentado semConexao() {
        return new ErroApresentado(SEM_CONEXAO, "SEM CONEXÃO", "wifi_off",
                "LINK LOCAL CAÍDO",
                "Sem conexão com a internet.",
                "O Framework de Redes precisa de internet para calcular, resolver DNS e consultar "
                        + "geolocalização — nada disso roda no seu aparelho. Assim que a conexão voltar, "
                        + "recarregue a página.",
                "Sem resposta HTTP — rede indisponível",
                "Confira o Wi-Fi ou os dados móveis e toque em Tentar de novo.");
    }

    /** Os códigos cobertos, na ordem do catálogo — usados pela guarda de cobertura nos testes. */
    public static List<Integer> codigos() {
        return List.copyOf(CATALOGO.keySet());
    }

    /**
     * Estado de erro correspondente ao código HTTP.
     *
     * <p><b>Comportamento em caso de falha:</b> código 4xx ou 5xx fora do catálogo
     * recebe o texto genérico da família, com o <b>código real</b> na linha de
     * status (um 418 nunca aparece como "400 Bad Request") e a cor do
     * representante (400 ou 500). Código fora de 400–599 vira o 500 inteiro.
     * Nunca devolve nulo: página de erro em branco por causa de um código não
     * previsto seria o próprio defeito que esta tela combate.</p>
     */
    public static ErroApresentado porCodigo(int codigo) {
        ErroApresentado exato = CATALOGO.get(codigo);
        if (exato != null) {
            return exato;
        }
        if (codigo >= 400 && codigo < 500) {
            return new ErroApresentado(400, "REQUISIÇÃO RECUSADA", "report",
                    "PACOTE DESCARTADO",
                    "O servidor recusou esta requisição.",
                    "O pedido chegou, mas não pôde ser atendido do jeito que foi feito. Confira o "
                            + "endereço e os dados e tente novamente.",
                    codigo + " Client Error",
                    "Código de cliente sem texto próprio no catálogo.");
        }
        if (codigo >= 500 && codigo < 600) {
            ErroApresentado base = CATALOGO.get(500);
            return new ErroApresentado(500, base.badge(), base.icone(), base.artTag(), base.titulo(),
                    base.descricao(), codigo + " Server Error", base.hint());
        }
        return CATALOGO.get(500);
    }

    /**
     * Estado de erro com o texto da área onde aconteceu.
     *
     * <p><b>Comportamento em caso de falha:</b> caminho nulo ou desconhecido é tratado como
     * {@link AreaDoErro#SITE}; código sem variante por área cai em {@link #porCodigo(int)}.</p>
     */
    public static ErroApresentado porCodigo(int codigo, String caminho) {
        if (AreaDoErro.doCaminho(caminho) == AreaDoErro.SITE) {
            ErroApresentado doSite = TEXTOS_DO_SITE.get(codigo);
            if (doSite != null) {
                return doSite;
            }
        }
        return porCodigo(codigo);
    }
}
