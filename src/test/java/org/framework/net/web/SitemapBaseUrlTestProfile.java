package org.framework.net.web;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Reproduz o host canônico que o perfil {@code prod} configura.
 *
 * <p><b>Propósito de negócio:</b> o sitemap de produção precisa sair em
 * {@code https://} e no domínio público. Este perfil liga a mesma propriedade
 * que o {@code application-prod.properties} liga, para que o comportamento seja
 * medido aqui e não presumido a partir do que o arquivo de produção diz.</p>
 *
 * <p><b>Invariantes do domínio:</b> só a propriedade do host canônico muda; nada
 * de proxy é simulado, porque é justamente do proxy que o sitemap deixou de
 * depender.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> se a propriedade deixar de ser lida,
 * o teste que usa este perfil falha mostrando a URL gerada.</p>
 */
public class SitemapBaseUrlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("framework.site.base-url", "https://frameworknet.carminati.dev.br");
    }
}
