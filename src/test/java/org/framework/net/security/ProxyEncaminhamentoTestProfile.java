package org.framework.net.security;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Reproduz a configuração de proxy do perfil {@code prod}.
 *
 * <p><b>Propósito de negócio:</b> tudo o que depende de "quem é o visitante" e de
 * "a origem era HTTPS" nasce dessa configuração. Ela é a única coisa entre o
 * cabeçalho que o Nginx manda e o que a aplicação acredita — e errar nela não
 * gera erro nenhum, só respostas silenciosamente erradas.</p>
 *
 * <p><b>Invariantes do domínio:</b> os valores espelham
 * {@code application-prod.properties}, mudando só a faixa de proxy confiável para
 * o laço local (é de onde o teste chama) e o limite de taxa, para caber em três
 * requisições. {@code allow-forwarded} fica <b>false</b>: com ele ligado, o
 * Vert.x lê só o cabeçalho {@code Forwarded}, que o Nginx do NPM não envia.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> se a configuração deixar de valer, o
 * teste que usa este perfil falha mostrando o que a aplicação enxergou.</p>
 */
public class ProxyEncaminhamentoTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.http.proxy.trusted-proxies", "127.0.0.1/32,::1/128",
                "quarkus.http.proxy.proxy-address-forwarding", "true",
                "quarkus.http.proxy.allow-forwarded", "false",
                "quarkus.http.proxy.enable-forwarded-host", "false",
                "quarkus.http.proxy.enable-forwarded-prefix", "false",
                "framework.security.rate-limit-enabled", "true",
                "framework.security.rate-limit-per-minute", "2");
    }
}
