package org.framework.net.web.domain;

import org.framework.net.academia.trilha.domain.CatalogoTrilha;
import org.framework.net.camadas.domain.CamadaAprofundamento;
import org.framework.net.certificados.domain.CertificadoAprofundamento;
import org.framework.net.criptografia.domain.CriptografiaAprofundamento;
import org.framework.net.ferramentas.domain.FerramentasAprofundamento;
import org.framework.net.portas.domain.PortaAprofundamento;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.framework.net.wifi.domain.WifiAprofundamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registro único das páginas HTML públicas e indexáveis do site.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> três mecanismos precisam responder à mesma
 * pergunta — "esta rota é uma página pública?". O {@code /sitemap.xml} responde
 * para oferecê-la ao buscador; o {@code <link rel="canonical">} responde para
 * declarar o endereço oficial dela; as guardas de teste respondem para cruzar
 * menu × robots × sitemap. Antes desta classe a lista morava dentro do
 * {@code SitemapResource}, e a segunda cópia teria divergido em silêncio — que é
 * exatamente o modo de falha que a invariante de fonte única existe para impedir:
 * duas listas discordando aparecem como os dois lados "corretos".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> (1) só entram páginas HTML públicas — nunca
 * rota autenticada, de API, de exportação ou fechada no {@code robots.txt};
 * sitemap e robots dizendo coisas opostas é contradição que o buscador resolve
 * contra o site; (2) os aprofundamentos NÃO são escritos à mão aqui: vêm dos
 * registros de cada módulo, a mesma fonte que alimenta os sub-menus, então página
 * nova entra sozinha; (3) as rotas são caminhos absolutos começando por
 * {@code /}, sem host, sem query e sem barra final — a raiz é o único
 * {@code "/"}; a normalização de quem consulta tem de casar com esta forma.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não há entrada externa nem I/O — a
 * lista é estática, montada uma vez na carga da classe, e nenhum método lança.
 * {@link #contem(String)} devolve {@code false} para entrada nula, em branco ou
 * desconhecida, nunca uma correspondência aproximada: quem pergunta por rota que
 * não existe recebe "não é pública", que é a resposta segura tanto para o
 * canonical (não emite a tag) quanto para as guardas. Página que deixar de
 * existir vira entrada morta — é o {@code SitemapHttpTest} que impede, exigindo
 * 200 de cada rota listada.</p>
 */
public final class PaginasPublicas {

    /** Páginas públicas, na ordem em que aparecem no menu. */
    private static final List<String> ROTAS = montar();

    /** Mesma lista, para consulta por rota em tempo constante. */
    private static final Set<String> INDICE = Set.copyOf(ROTAS);

    private PaginasPublicas() {
    }

    /**
     * As rotas públicas, na ordem do menu.
     *
     * <p>Não estão aqui, de propósito: {@code /telemetria} (autenticada),
     * {@code /informacoes} (dispara consulta geográfica externa a cada acesso),
     * {@code /admin}, {@code /login}, {@code /history}, {@code /export} e as rotas
     * de API — todas fechadas no {@code robots.txt}.</p>
     */
    public static List<String> rotas() {
        return ROTAS;
    }

    /**
     * Diz se a rota é uma página pública indexável.
     *
     * @param rota caminho absoluto já normalizado (sem host, sem query, sem barra
     *             final); {@code null} e branco respondem {@code false}
     */
    public static boolean contem(String rota) {
        return rota != null && !rota.isBlank() && INDICE.contains(rota);
    }

    /**
     * Rotas públicas da Academia, sem nunca derrubar a lista do site.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> esta lista é lida na carga da classe e alimenta o canonical de
     * TODAS as páginas. Um defeito no catálogo da trilha (lição com valor inválido) lançava
     * {@code ExceptionInInitializerError} aqui, e a classe inteira ficava inutilizável: o site todo
     * respondia 500 por causa da Academia (auditoria ACAD-02, D12).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> falha da Academia tira só as rotas dela; as demais páginas
     * continuam na lista.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> qualquer {@link RuntimeException} ou
     * {@link LinkageError} da fonte vira lista vazia e log em ERROR com a causa.</p>
     */
    static List<String> rotasDaAcademia(java.util.function.Supplier<List<String>> fonte) {
        try {
            return fonte.get();
        } catch (RuntimeException | LinkageError falha) {
            org.jboss.logging.Logger.getLogger(PaginasPublicas.class).errorf(falha,
                    "rotas da Academia fora da lista pública (catálogo com defeito): %s", falha.getClass().getSimpleName());
            return List.of();
        }
    }

    private static List<String> montar() {
        List<String> paginas = new ArrayList<>();
        paginas.add("/");
        paginas.add("/analise");
        paginas.add("/calculadora");
        paginas.add("/ipv6/analise");
        paginas.add("/ipv6");
        paginas.add("/ipv6/resolucao");
        paginas.add("/laboratorios");
        paginas.add("/laboratorios/camadas");
        paginas.add("/laboratorios/aneis-e-rede");
        // Academia: landing, níveis abertos e lições — fonte única é a trilha.
        paginas.addAll(rotasDaAcademia(CatalogoTrilha::rotasPublicas));
        paginas.add("/portas");
        // Aprofundamentos de portas (Anatomia + famílias) — fonte única.
        PortaAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/protocolos");
        // Aprofundamentos por protocolo — fonte única, logo após a aba Geral.
        AprofundamentoProtocolo.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/certificados");
        // Aprofundamentos de certificados (X.509/PKI) — fonte única.
        CertificadoAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/camadas");
        CamadaAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/criptografia");
        CriptografiaAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/wifi");
        WifiAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/ferramentas");
        paginas.add("/ferramentas/rede");
        FerramentasAprofundamento.disponiveis().forEach(item -> paginas.add(item.rota()));
        paginas.add("/resolucao-problemas");
        paginas.add("/localizacao");
        paginas.add("/trafego");
        paginas.add("/seguranca");
        paginas.add("/diagnostico");
        paginas.add("/documentacao");
        paginas.add("/sobre");
        return List.copyOf(paginas);
    }
}
