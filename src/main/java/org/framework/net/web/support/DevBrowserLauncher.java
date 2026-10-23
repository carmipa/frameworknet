package org.framework.net.web.support;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

@ApplicationScoped
public class DevBrowserLauncher {

    private static final Logger LOG = Logger.getLogger(DevBrowserLauncher.class);
    private static final long OPEN_DELAY_MS = 1500L;

    /**
     * Marca, em propriedade de sistema, que o navegador já foi aberto nesta JVM.
     *
     * <p><b>Propósito de negócio:</b> em {@code quarkusDev} o Quarkus recria o
     * contexto da aplicação a cada live reload, e com ele RE-DISPARA o
     * {@link StartupEvent}. Sem esta trava, cada arquivo salvo abria uma nova aba
     * do navegador — uma sessão de edição intensa vira "páginas infinitas". A
     * conveniência é abrir uma vez ao subir, não a cada reload.</p>
     *
     * <p><b>Por que propriedade de sistema, e não campo estático:</b> o hot reload
     * troca o classloader da aplicação, zerando campos estáticos das classes de
     * negócio; a propriedade de sistema vive na JVM e sobrevive ao reload, então é
     * o único lugar onde a marca persiste entre recargas.</p>
     */
    private static final String JA_ABRIU = "framework.dev.browser-opened";

    @Inject
    FrameworkDevConfig devConfig;

    private final Vertx vertx;

    public DevBrowserLauncher(Vertx vertx) {
        this.vertx = vertx;
    }

    void onStart(@Observes @Priority(200) StartupEvent event) {
        if (!devConfig.openBrowser() || LaunchMode.current() != LaunchMode.DEVELOPMENT) {
            return;
        }
        // Abre uma única vez por JVM: o StartupEvent re-dispara a cada live reload
        // e, sem esta trava, cada arquivo salvo abriria uma aba nova. A marca é
        // gravada de forma síncrona aqui para que o próximo reload já saia cedo,
        // antes mesmo de agendar o timer.
        if (System.getProperty(JA_ABRIU) != null) {
            return;
        }
        System.setProperty(JA_ABRIU, "true");
        Config config = ConfigProvider.getConfig();
        int port = resolveHttpPort(config);
        String host = resolveHttpHost(config);
        String url = buildBrowserUrl(host, port, devConfig.browserPath());
        vertx.setTimer(OPEN_DELAY_MS, id -> openInBrowser(url));
    }

    static int resolveHttpPort(Config config) {
        String raw = System.getProperty("quarkus.http.port");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("QUARKUS_HTTP_PORT");
        }
        if (raw != null && !raw.isBlank()) {
            try {
                return Integer.parseInt(raw.strip());
            } catch (NumberFormatException ignored) {
                LOG.debugf("Porta HTTP inválida em override: %s", raw);
            }
        }
        return config.getOptionalValue("quarkus.http.port", Integer.class).orElse(8080);
    }

    static String resolveHttpHost(Config config) {
        String raw = System.getProperty("quarkus.http.host");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("QUARKUS_HTTP_HOST");
        }
        if (raw == null || raw.isBlank()) {
            raw = config.getOptionalValue("quarkus.http.host", String.class).orElse("localhost");
        }
        return browserHost(raw);
    }

    static String normalizeBrowserPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    static String buildBrowserUrl(String host, int port, String path) {
        return "http://" + browserHost(host) + ":" + port + normalizeBrowserPath(path);
    }

    static String browserHost(String configuredHost) {
        if (configuredHost == null || configuredHost.isBlank()
                || "0.0.0.0".equals(configuredHost) || "::".equals(configuredHost)) {
            return "127.0.0.1";
        }
        return configuredHost;
    }

    private void openInBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
                LOG.infof("Navegador aberto em %s", url);
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    LOG.infof("Navegador aberto em %s", url);
                    return;
                }
            }
        } catch (Exception e) {
            LOG.debugf(e, "Falha ao abrir navegador (tentativa principal) para %s", url);
        }

        try {
            ProcessBuilder processBuilder = os.contains("mac")
                    ? new ProcessBuilder("open", url)
                    : new ProcessBuilder("xdg-open", url);
            processBuilder.start();
            LOG.infof("Navegador aberto em %s", url);
        } catch (IOException e) {
            LOG.warnf(e, "Não foi possível abrir o navegador automaticamente em %s", url);
        }
    }
}
