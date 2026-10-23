package org.framework.net.security;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recusa iniciar em produção sem os segredos obrigatórios.
 *
 * <p><b>Propósito de negócio:</b> a proteção administrativa dependia de
 * {@code isEnforcementActive()}, que só é verdadeira quando a chave está
 * preenchida. Como o perfil de produção resolve a chave para string vazia
 * quando a variável de ambiente falta ({@code ${ADMIN_API_KEY:}}), um deploy
 * sem {@code .env} subia com <b>toda a proteção desligada</b> — sem erro, sem
 * log, servindo {@code /export} e {@code /telemetria} a qualquer um. Esta
 * classe transforma esse silêncio em falha de inicialização.</p>
 *
 * <p><b>Invariantes do domínio:</b> a verificação só vale para o perfil
 * {@code prod} — desenvolvimento e teste continuam subindo com os defaults, que
 * é o que torna o projeto utilizável localmente. Um segredo é considerado
 * ausente quando está em branco <i>ou</i> quando ainda carrega o valor de
 * desenvolvimento versionado no repositório, que é público e portanto vale o
 * mesmo que nenhum.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lança {@link IllegalStateException}
 * no {@code StartupEvent}, o que aborta a inicialização do Quarkus. O container
 * morre no boot e o healthcheck nunca fica saudável — falha ruidosa e visível,
 * em vez de uma aplicação aparentemente sadia e desprotegida.</p>
 */
@ApplicationScoped
public class SegredosObrigatoriosVerificador {

    private static final Logger LOG = Logger.getLogger(SegredosObrigatoriosVerificador.class);

    /**
     * Valores de desenvolvimento versionados no repositório. São públicos, então
     * usá-los em produção equivale a não ter segredo nenhum.
     */
    private static final Set<String> VALORES_DE_DESENVOLVIMENTO = Set.of(
            "dev-admin-key-local",
            "framework-net-dev-csrf-secret",
            "framework-net-dev-csrf-secret-change-in-prod",
            "troque-por-um-valor-forte",
            "changeme");

    private static final int TAMANHO_MINIMO = 16;

    @ConfigProperty(name = "framework.security.admin-api-key", defaultValue = "")
    String adminApiKey;

    @ConfigProperty(name = "framework.security.csrf-secret", defaultValue = "")
    String csrfSecret;

    @ConfigProperty(name = "framework.security.admin-api-key-required", defaultValue = "false")
    boolean chaveAdminObrigatoria;

    /**
     * Roda antes de qualquer outro observador de inicialização do projeto.
     *
     * <p>A prioridade baixa garante que a checagem aconteça antes de a aplicação
     * registrar telemetria de start ou aceitar tráfego.</p>
     */
    void aoIniciar(@Observes @Priority(1) StartupEvent evento) {
        if (!ConfigUtils.getProfiles().contains("prod")) {
            return;
        }

        List<String> problemas = new ArrayList<>();
        if (chaveAdminObrigatoria) {
            verificar("ADMIN_API_KEY", "framework.security.admin-api-key", adminApiKey, problemas);
        }
        // Sempre, e não só com o CSRF ligado: o mesmo segredo assina a sessão da Telemetria (SessaoTelemetriaService).
        // Com o CSRF desligado e sem segredo, qualquer pessoa calcularia um cookie de dono (BACK-15).
        verificar("CSRF_SECRET", "framework.security.csrf-secret", csrfSecret, problemas);

        if (!problemas.isEmpty()) {
            String mensagem = "Recusando iniciar em producao: "
                    + String.join(" | ", problemas)
                    + ". Defina as variaveis no .env da VPS (o scripts/deploy.sh gera valores fortes "
                    + "automaticamente na primeira execucao). Subir sem elas deixaria /export e "
                    + "/telemetria abertos a qualquer um.";
            LOG.fatal(mensagem);
            throw new IllegalStateException(mensagem);
        }
    }

    private void verificar(String variavel, String propriedade, String valor, List<String> problemas) {
        String atual = valor == null ? "" : valor.strip();
        if (atual.isEmpty()) {
            problemas.add(variavel + " ausente (" + propriedade + " vazio)");
            return;
        }
        if (VALORES_DE_DESENVOLVIMENTO.contains(atual)) {
            problemas.add(variavel + " ainda com o valor de desenvolvimento, que e publico no repositorio");
            return;
        }
        if (atual.length() < TAMANHO_MINIMO) {
            problemas.add(variavel + " tem menos de " + TAMANHO_MINIMO + " caracteres");
        }
    }
}
