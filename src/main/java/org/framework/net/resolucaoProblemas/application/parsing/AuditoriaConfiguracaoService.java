package org.framework.net.resolucaoProblemas.application.parsing;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.domain.kernel.MascaraIpv4;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.Pendencia;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.BlocoRoteamento;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.InterfaceLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RedeAnunciada;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.VizinhoBgp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auditoria de coerência entre os scripts colados.
 *
 * <p><b>Propósito de negócio:</b> é aqui que a ferramenta ganha utilidade real
 * para quem estuda. O aluno cola a configuração que o professor passou — muitas
 * vezes com erro — e precisa saber <em>o que</em> está errado, <em>onde</em>, e
 * <em>por que</em> a correção é aquela. A chave do algoritmo é que as declarações
 * {@code neighbor <ip> remote-as <as>} formam um sistema <b>sobredeterminado</b>:
 * cada roteador declara onde o OUTRO está. Se A diz que B mora em {@code .2},
 * então B tem de possuir {@code .2}; quando não possui, o erro não é apenas
 * detectado — fica localizado, e a correção sai do próprio texto.</p>
 *
 * <p><b>Invariantes do domínio:</b> correção só é aplicada quando é
 * <b>derivável</b> — existe evidência textual e a solução é única. Havendo mais de
 * uma atribuição possível, o achado nasce como
 * {@link AchadoConfiguracao.Severidade#ERRO_SEM_CORRECAO} e a ferramenta para,
 * porque escolher a mais bonita seria adivinhar. Nada é alterado em silêncio:
 * toda correção vira achado com o antes, o depois e a evidência.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> configuração vazia devolve resultado
 * vazio. AS declarado como vizinho e ausente do texto colado não é erro — é
 * {@link Pendencia}, com a instrução de colar aquele script.</p>
 */
@ApplicationScoped
public class AuditoriaConfiguracaoService {

    /** Teto de permutações testadas ao resolver endereços faltantes de um roteador. */
    private static final int MAX_ENDERECOS_AMBIGUOS = 4;

    /**
     * Confere a configuração lida e devolve o modelo já corrigido.
     *
     * <p><b>Comportamento em caso de falha:</b> nunca lança; problemas viram
     * achados ou pendências.</p>
     */
    public ResultadoAuditoria auditar(ConfiguracaoLida lida) {
        List<AchadoConfiguracao> achados = new ArrayList<>(lida.achadosSintaxe());
        List<Pendencia> pendencias = new ArrayList<>();

        if (lida.isVazia()) {
            return new ResultadoAuditoria(List.of(), achados, pendencias);
        }

        List<RoteadorLido> roteadores = new ArrayList<>(lida.roteadores());
        conferirIdentificacao(roteadores, achados);

        roteadores = corrigirMascarasAnunciadas(roteadores, achados);
        roteadores = corrigirEnderecosPorCruzamento(roteadores, achados, pendencias);

        conferirVizinhos(roteadores, achados);
        conferirEnderecamento(roteadores, achados);
        conferirEnlacesSeriais(roteadores, achados);
        conferirAnuncios(roteadores, achados);
        conferirBoasPraticas(roteadores, achados);

        achados.sort((a, b) -> {
            int porSeveridade = a.severidade().compareTo(b.severidade());
            return porSeveridade != 0 ? porSeveridade : Integer.compare(a.linha(), b.linha());
        });
        return new ResultadoAuditoria(roteadores, achados, pendencias);
    }

    // ------------------------------------------------------------ identificação

    private void conferirIdentificacao(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        Map<Long, String> porAs = new LinkedHashMap<>();
        Set<String> nomes = new LinkedHashSet<>();
        for (RoteadorLido r : roteadores) {
            if (!nomes.add(r.hostname())) {
                achados.add(AchadoConfiguracao.semCorrecao("Identificação", r.hostname(),
                        r.linhaInicial(), "hostname " + r.hostname(),
                        "Dois blocos usam o mesmo hostname \"" + r.hostname() + "\".",
                        "Nomes repetidos impedem dizer a qual roteador cada interface pertence."));
            }
            long as = r.asBgp();
            if (as > 0) {
                String anterior = porAs.put(as, r.hostname());
                if (anterior != null) {
                    // AS repetido é iBGP, que é legítimo (CONT-33: saía como erro). O que se perde é só a
                    // dedução de endereço por AS, que precisa de um roteador por AS.
                    achados.add(AchadoConfiguracao.aviso("BGP", r.hostname(), r.linhaInicial(),
                            "AS " + as + " declarado em \"" + anterior + "\" e em \"" + r.hostname()
                                    + "\" — sessão iBGP entre eles?",
                            "iBGP é válido; só a correção de endereço pelo cruzamento de \"neighbor ... remote-as\" "
                                    + "deixa de valer para esse AS, que aponta para mais de um roteador."));
                }
            }
        }
    }

    // ------------------------------------------------------------------ máscara

    /**
     * Conserta {@code network <rede> mask <máscara>} inválido usando a interface conectada.
     *
     * <p><b>Invariantes do domínio:</b> a correção só acontece quando existe UMA
     * interface do mesmo roteador cuja sub-rede é exatamente a rede anunciada — é
     * essa coincidência que prova qual máscara o autor quis escrever. Sem ela, o
     * achado fica sem correção.</p>
     */
    private List<RoteadorLido> corrigirMascarasAnunciadas(
            List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {

        List<RoteadorLido> saida = new ArrayList<>();
        for (RoteadorLido r : roteadores) {
            List<BlocoRoteamento> blocos = new ArrayList<>();
            boolean mudou = false;

            for (BlocoRoteamento bloco : r.blocos()) {
                List<RedeAnunciada> redes = new ArrayList<>();
                for (RedeAnunciada rede : bloco.redes()) {
                    RedeAnunciada corrigida = corrigirMascara(r, rede, achados);
                    mudou |= corrigida != rede;
                    redes.add(corrigida);
                }
                blocos.add(new BlocoRoteamento(bloco.protocolo(), bloco.identificador(),
                        bloco.vizinhos(), redes, bloco.autoSummaryDesligado(), bloco.linha(),
                        bloco.outrasLinhas()));
            }
            saida.add(mudou
                    ? new RoteadorLido(r.hostname(), r.linhaInicial(), r.interfaces(), blocos, r.rotasEstaticas())
                    : r);
        }
        return saida;
    }

    private RedeAnunciada corrigirMascara(
            RoteadorLido r, RedeAnunciada rede, List<AchadoConfiguracao> achados) {

        if (rede.mascara() == null || rede.mascara().isBlank()
                || MascaraIpv4.prefixoDe(rede.mascara()) >= 0) {
            return rede;
        }

        InterfaceLida origem = null;
        for (InterfaceLida i : r.interfacesComIp()) {
            if (MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo()).equals(rede.rede())) {
                origem = i;
                break;
            }
        }

        int octetos = MascaraIpv4.contarOctetos(rede.mascara());
        String defeito = octetos != 4
                ? "a máscara tem " + octetos + " octetos e IPv4 usa quatro"
                : "a máscara não é contígua";

        if (origem == null) {
            achados.add(AchadoConfiguracao.semCorrecao("Máscara", r.hostname(), rede.linha(),
                    rede.textoOriginal(),
                    "Máscara inválida no anúncio de " + rede.rede() + " — " + defeito + ".",
                    "Nenhuma interface deste roteador tem sub-rede igual a " + rede.rede()
                            + ", então não há no texto de onde derivar a máscara correta."));
            return rede;
        }

        String correta = origem.mascara();
        String textoCorrigido = rede.textoOriginal().replace(rede.mascara(), correta);
        achados.add(AchadoConfiguracao.corrigido("Máscara", r.hostname(), rede.linha(),
                rede.textoOriginal(), textoCorrigido,
                "Máscara inválida no anúncio de " + rede.rede() + " — " + defeito + ".",
                "A interface " + origem.nome() + " tem " + origem.ip() + " " + correta
                        + ", cuja sub-rede é exatamente " + rede.rede() + "."));
        return new RedeAnunciada(rede.rede(), correta, rede.wildcard(), rede.area(),
                textoCorrigido, rede.linha());
    }

    // -------------------------------------------------------------- cruzamento

    /**
     * Corrige endereços de interface usando as declarações de vizinho dos outros scripts.
     *
     * <p><b>Invariantes do domínio:</b> um endereço só é atribuído a uma interface
     * quando a atribuição é a <b>única</b> que deixa o roteador coerente. A busca
     * testa as permutações possíveis e exige solução única; duas soluções válidas
     * significam informação insuficiente, e aí a ferramenta declara em vez de
     * escolher.</p>
     */
    private List<RoteadorLido> corrigirEnderecosPorCruzamento(
            List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados,
            List<Pendencia> pendencias) {

        Map<Long, RoteadorLido> porAs = indicePorAs(roteadores);
        Map<String, List<Exigencia>> exigidos = mapearExigencias(roteadores, porAs, pendencias);
        Set<String> todosEnderecos = enderecosDeclarados(roteadores);

        List<RoteadorLido> saida = new ArrayList<>();
        for (RoteadorLido alvo : roteadores) {
            List<Exigencia> exigencias = exigidos.getOrDefault(alvo.hostname(), List.of());
            if (exigencias.isEmpty()) {
                saida.add(alvo);
                continue;
            }

            Set<String> possui = new LinkedHashSet<>();
            for (InterfaceLida i : alvo.interfacesComIp()) {
                possui.add(i.ip());
            }

            List<Exigencia> faltantes = exigencias.stream()
                    .filter(e -> !possui.contains(e.endereco()))
                    .toList();
            if (faltantes.isEmpty()) {
                saida.add(alvo);
                continue;
            }

            Set<String> corroborados = exigencias.stream().map(Exigencia::endereco)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<InterfaceLida> candidatas = alvo.interfacesComIp().stream()
                    .filter(i -> i.serial() || i.prefixo() >= 30)
                    .filter(i -> !corroborados.contains(i.ip()))
                    .toList();

            List<InterfaceLida> corrigidas = resolver(alvo, faltantes, candidatas, roteadores, exigidos);
            if (corrigidas == null) {
                registrarSemCorrecao(alvo, faltantes, candidatas, achados);
                saida.add(alvo);
                continue;
            }

            saida.add(aplicar(alvo, candidatas, corrigidas, faltantes, roteadores,
                    todosEnderecos, achados));
        }
        return saida;
    }

    /**
     * Encontra a atribuição única de endereços faltantes às interfaces candidatas.
     *
     * @return a lista de interfaces já com os novos endereços, na ordem de
     *         {@code candidatas}, ou {@code null} quando não há solução única.
     */
    private List<InterfaceLida> resolver(
            RoteadorLido alvo, List<Exigencia> faltantes, List<InterfaceLida> candidatas,
            List<RoteadorLido> roteadores, Map<String, List<Exigencia>> exigidos) {

        if (faltantes.isEmpty() || faltantes.size() != candidatas.size()
                || faltantes.size() > MAX_ENDERECOS_AMBIGUOS) {
            return null;
        }

        List<List<Exigencia>> permutacoes = new ArrayList<>();
        permutar(new ArrayList<>(faltantes), 0, permutacoes);

        List<InterfaceLida> unica = null;
        for (List<Exigencia> tentativa : permutacoes) {
            List<InterfaceLida> hipotese = new ArrayList<>();
            for (int i = 0; i < candidatas.size(); i++) {
                hipotese.add(candidatas.get(i).comIp(tentativa.get(i).endereco()));
            }
            if (!coerente(alvo, candidatas, hipotese, tentativa, roteadores, exigidos)) {
                continue;
            }
            if (unica != null) {
                return null; // mais de uma solução válida: informação insuficiente
            }
            unica = hipotese;
        }
        return unica;
    }

    /**
     * A hipótese deixa o roteador coerente?
     *
     * <p>Três exigências: nenhuma interface do roteador compartilha sub-rede com
     * outra; o endereço não pertence comprovadamente a outro roteador; e o vizinho
     * que exigiu aquele endereço realmente alcança a interface — ou seja, quem
     * declarou tem uma ponta na mesma sub-rede.</p>
     *
     * <p><b>Invariantes do domínio:</b> a colisão é medida contra o endereço que
     * OUTRO roteador comprovadamente possui (isto é, que algum script corrobora),
     * nunca contra o que ele meramente declarou. Endereço declarado e não
     * corroborado é justamente o que está sob suspeita de estar errado; tratá-lo
     * como ocupado faria o resultado depender da ordem em que os roteadores são
     * processados — dois erros espelhados nunca se resolveriam.</p>
     */
    private boolean coerente(
            RoteadorLido alvo, List<InterfaceLida> candidatas, List<InterfaceLida> hipotese,
            List<Exigencia> tentativa, List<RoteadorLido> roteadores,
            Map<String, List<Exigencia>> exigidos) {

        List<InterfaceLida> finais = new ArrayList<>();
        for (InterfaceLida i : alvo.interfacesComIp()) {
            int idx = candidatas.indexOf(i);
            finais.add(idx >= 0 ? hipotese.get(idx) : i);
        }

        for (int a = 0; a < finais.size(); a++) {
            for (int b = a + 1; b < finais.size(); b++) {
                InterfaceLida ia = finais.get(a);
                InterfaceLida ib = finais.get(b);
                if (ia.prefixo() == ib.prefixo()
                        && MascaraIpv4.mesmaSubRede(ia.ip(), ib.ip(), ia.prefixo())) {
                    return false;
                }
            }
        }

        for (int i = 0; i < hipotese.size(); i++) {
            InterfaceLida nova = hipotese.get(i);
            for (Map.Entry<String, List<Exigencia>> entrada : exigidos.entrySet()) {
                if (entrada.getKey().equals(alvo.hostname())) {
                    continue;
                }
                boolean pertenceAoOutro = entrada.getValue().stream()
                        .anyMatch(e -> e.endereco().equals(nova.ip()));
                if (pertenceAoOutro) {
                    return false;
                }
            }
            if (!alcancaQuemExigiu(nova, tentativa.get(i), roteadores)) {
                return false;
            }
        }
        return true;
    }

    private boolean alcancaQuemExigiu(
            InterfaceLida nova, Exigencia exigencia, List<RoteadorLido> roteadores) {
        for (RoteadorLido r : roteadores) {
            if (!r.hostname().equals(exigencia.declaradoPor())) {
                continue;
            }
            for (InterfaceLida i : r.interfacesComIp()) {
                if (MascaraIpv4.mesmaSubRede(i.ip(), nova.ip(), nova.prefixo())) {
                    return true;
                }
            }
        }
        return false;
    }

    private RoteadorLido aplicar(
            RoteadorLido alvo, List<InterfaceLida> candidatas, List<InterfaceLida> corrigidas,
            List<Exigencia> faltantes, List<RoteadorLido> roteadores,
            Set<String> todosEnderecos, List<AchadoConfiguracao> achados) {

        List<InterfaceLida> novas = new ArrayList<>();
        for (InterfaceLida original : alvo.interfaces()) {
            int idx = candidatas.indexOf(original);
            if (idx < 0) {
                novas.add(original);
                continue;
            }
            InterfaceLida nova = corrigidas.get(idx);
            achados.add(AchadoConfiguracao.corrigido("Endereçamento", alvo.hostname(), original.linha(),
                    "ip address " + original.ip() + " " + original.mascara(),
                    "ip address " + nova.ip() + " " + nova.mascara(),
                    "A interface " + original.nome() + " tinha " + original.ip() + ", "
                            + defeitosDe(alvo, original, roteadores, todosEnderecos) + ".",
                    evidenciaDe(nova.ip(), faltantes)));
            novas.add(nova);
        }
        return new RoteadorLido(alvo.hostname(), alvo.linhaInicial(), novas, alvo.blocos(),
                alvo.rotasEstaticas());
    }

    /** Enumera, em texto, tudo o que estava errado com o endereço antigo. */
    private String defeitosDe(RoteadorLido alvo, InterfaceLida original,
                              List<RoteadorLido> roteadores, Set<String> todosEnderecos) {
        List<String> motivos = new ArrayList<>();

        for (RoteadorLido outro : roteadores) {
            if (outro.hostname().equals(alvo.hostname())) {
                continue;
            }
            for (InterfaceLida io : outro.interfacesComIp()) {
                if (io.ip().equals(original.ip())) {
                    motivos.add("endereço duplicado com " + outro.hostname() + " " + io.nome());
                }
            }
        }
        for (InterfaceLida irma : alvo.interfacesComIp()) {
            if (!irma.equals(original) && irma.prefixo() == original.prefixo()
                    && MascaraIpv4.mesmaSubRede(irma.ip(), original.ip(), original.prefixo())) {
                motivos.add("mesma sub-rede da interface " + irma.nome());
            }
        }
        for (RoteadorLido r : roteadores) {
            BlocoRoteamento bgp = r.bgp();
            if (bgp == null) {
                continue;
            }
            for (VizinhoBgp v : bgp.vizinhos()) {
                if (v.ip().equals(original.ip()) && v.remoteAs() != alvo.asBgp()) {
                    motivos.add(r.hostname() + " declara esse endereço como sendo do AS " + v.remoteAs());
                }
            }
        }
        if (motivos.isEmpty()) {
            motivos.add("endereço não confirmado por nenhum outro script");
        }
        return String.join("; ", motivos);
    }

    private String evidenciaDe(String endereco, List<Exigencia> faltantes) {
        for (Exigencia e : faltantes) {
            if (e.endereco().equals(endereco)) {
                return e.declaradoPor() + " declara \"neighbor " + e.endereco()
                        + " remote-as " + e.asAlvo() + "\" (linha " + e.linha()
                        + "), logo esse endereço tem de existir neste roteador.";
            }
        }
        return "";
    }

    private void registrarSemCorrecao(
            RoteadorLido alvo, List<Exigencia> faltantes, List<InterfaceLida> candidatas,
            List<AchadoConfiguracao> achados) {

        String enderecos = faltantes.stream().map(Exigencia::endereco).toList().toString();
        String motivo = candidatas.isEmpty()
                ? "o roteador não tem interface ponto a ponto livre para receber esse endereço"
                : "há " + candidatas.size() + " interface(s) candidata(s) para "
                        + faltantes.size() + " endereço(s), sem atribuição única";
        achados.add(AchadoConfiguracao.semCorrecao("Endereçamento", alvo.hostname(),
                alvo.linhaInicial(), "",
                "Faltam neste roteador os endereços " + enderecos
                        + ", exigidos pelas declarações de vizinho dos outros scripts.",
                "Não foi possível derivar a correção: " + motivo + "."));
    }

    // ----------------------------------------------------------- conferências

    private void conferirVizinhos(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        for (RoteadorLido r : roteadores) {
            BlocoRoteamento bgp = r.bgp();
            if (bgp == null) {
                continue;
            }
            Set<String> proprios = new LinkedHashSet<>();
            for (InterfaceLida i : r.interfacesComIp()) {
                proprios.add(i.ip());
            }
            for (VizinhoBgp v : bgp.vizinhos()) {
                if (proprios.contains(v.ip())) {
                    achados.add(AchadoConfiguracao.semCorrecao("BGP", r.hostname(), v.linha(),
                            "neighbor " + v.ip() + " remote-as " + v.remoteAs(),
                            "O vizinho " + v.ip() + " é um endereço do próprio roteador.",
                            "Uma sessão BGP precisa de dois roteadores; apontar para si mesmo nunca sobe."));
                    continue;
                }
                InterfaceLida reservado = interfaceOndeEReservado(r, v.ip());
                if (reservado != null) {
                    achados.add(AchadoConfiguracao.semCorrecao("BGP", r.hostname(), v.linha(),
                            "neighbor " + v.ip() + " remote-as " + v.remoteAs(),
                            "O vizinho " + v.ip() + " é o endereço de "
                                    + (v.ip().equals(MascaraIpv4.enderecoDeRede(reservado.ip(), reservado.prefixo()))
                                        ? "rede" : "broadcast")
                                    + " da sub-rede " + MascaraIpv4.cidrDe(reservado.ip(), reservado.prefixo())
                                    + " (" + reservado.nome() + ").",
                            "Nenhum roteador pode ter esse endereço; o erro está nesta linha. Aponte para o "
                                    + "endereço real do vizinho no enlace."));
                    continue;
                }
                boolean conectado = r.interfacesComIp().stream()
                        .anyMatch(i -> MascaraIpv4.mesmaSubRede(i.ip(), v.ip(), i.prefixo()));
                if (!conectado) {
                    achados.add(AchadoConfiguracao.semCorrecao("BGP", r.hostname(), v.linha(),
                            "neighbor " + v.ip() + " remote-as " + v.remoteAs(),
                            "O vizinho " + v.ip() + " não pertence a nenhuma sub-rede conectada a "
                                    + r.hostname() + ".",
                            "Em eBGP direto o vizinho precisa estar numa sub-rede de uma interface "
                                    + "local, salvo uso explícito de ebgp-multihop."));
                }
            }
        }
    }

    /**
     * Confere o endereçamento de cada interface e entre roteadores.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> apontar o endereço que o IOS recusa ou que derruba a comunicação antes
     * de o aluno colar o script.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> um IP por equipamento; interface não recebe o endereço de rede nem o
     * de broadcast da própria sub-rede (até /30); no mesmo roteador nenhuma sub-rede pode conter outra,
     * mesmo com máscaras diferentes (10.0.0.0/24 contém 10.0.0.128/25); as duas pontas de um enlace usam a
     * mesma máscara (auditoria CALC-13).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; cada problema vira um achado na linha da
     * interface.</p>
     */
    private void conferirEnderecamento(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        Map<String, String> donos = new LinkedHashMap<>();
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                if (i.prefixo() <= 30 && (i.ip().equals(MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo()))
                        || i.ip().equals(MascaraIpv4.broadcastDe(i.ip(), i.prefixo())))) {
                    boolean rede = i.ip().equals(MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo()));
                    achados.add(AchadoConfiguracao.semCorrecao("Endereçamento", r.hostname(), i.linha(),
                            "ip address " + i.ip() + " " + i.mascara(),
                            "O endereço " + i.ip() + " é o " + (rede ? "endereço de rede" : "broadcast")
                                    + " de " + MascaraIpv4.cidrDe(i.ip(), i.prefixo()) + " (" + i.nome() + ").",
                            "O IOS recusa esse endereço na interface (\"Bad mask\"); use um host do bloco."));
                }
                String anterior = donos.put(i.ip(), r.hostname() + " " + i.nome());
                if (anterior != null) {
                    achados.add(AchadoConfiguracao.semCorrecao("Endereçamento", r.hostname(), i.linha(),
                            "ip address " + i.ip() + " " + i.mascara(),
                            "Endereço " + i.ip() + " usado em dois lugares: " + anterior
                                    + " e " + r.hostname() + " " + i.nome() + ".",
                            "Dois equipamentos com o mesmo IP na mesma rede derrubam a comunicação."));
                }
            }
            List<InterfaceLida> comIp = r.interfacesComIp();
            for (int a = 0; a < comIp.size(); a++) {
                for (int b = a + 1; b < comIp.size(); b++) {
                    InterfaceLida ia = comIp.get(a);
                    InterfaceLida ib = comIp.get(b);
                    // Sobreposição vale com máscaras diferentes também: a menor contém a maior.
                    int menor = Math.min(ia.prefixo(), ib.prefixo());
                    if (MascaraIpv4.mesmaSubRede(ia.ip(), ib.ip(), menor)) {
                        achados.add(AchadoConfiguracao.semCorrecao("Endereçamento", r.hostname(),
                                ib.linha(), "ip address " + ib.ip() + " " + ib.mascara(),
                                "As interfaces " + ia.nome() + " e " + ib.nome()
                                        + " estão na mesma sub-rede "
                                        + MascaraIpv4.cidrDe(ia.ip(), ia.prefixo()) + ".",
                                "O IOS recusa a segunda interface: sub-redes sobrepostas no mesmo roteador."));
                    }
                }
            }
        }
        conferirMascarasDoEnlace(roteadores, achados);
    }

    /** As duas pontas de um enlace com máscaras diferentes: cada lado calcula outra sub-rede. */
    private void conferirMascarasDoEnlace(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        for (int x = 0; x < roteadores.size(); x++) {
            for (int y = x + 1; y < roteadores.size(); y++) {
                RoteadorLido ra = roteadores.get(x);
                RoteadorLido rb = roteadores.get(y);
                for (InterfaceLida ia : ra.interfacesComIp()) {
                    for (InterfaceLida ib : rb.interfacesComIp()) {
                        int menor = Math.min(ia.prefixo(), ib.prefixo());
                        if (ia.prefixo() != ib.prefixo() && !ia.ip().equals(ib.ip())
                                && MascaraIpv4.mesmaSubRede(ia.ip(), ib.ip(), menor)) {
                            achados.add(AchadoConfiguracao.aviso("Endereçamento", rb.hostname(), ib.linha(),
                                    "O enlace entre " + ra.hostname() + " " + ia.nome() + " (/" + ia.prefixo()
                                            + ") e " + rb.hostname() + " " + ib.nome() + " (/" + ib.prefixo()
                                            + ") usa máscaras diferentes.",
                                    "Cada ponta calcula outra sub-rede e outro broadcast; os roteadores podem até "
                                            + "se falar, mas o OSPF não forma adjacência com máscara divergente."));
                        }
                    }
                }
            }
        }
    }

    /**
     * Confere o relógio dos enlaces seriais.
     *
     * <p><b>Invariantes do domínio:</b> enlace serial sem {@code clock rate} em
     * nenhuma ponta nunca sobe — e a correção NÃO é derivável do texto, porque o
     * lado DCE é definido pelo cabo, não pela configuração. Por isso este achado
     * nasce sem correção, mesmo sendo óbvio o que falta.</p>
     */
    private void conferirEnlacesSeriais(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        Map<String, List<Ponta>> porSubRede = new LinkedHashMap<>();
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                if (!i.serial() || i.prefixo() < 30) {
                    continue;
                }
                porSubRede.computeIfAbsent(MascaraIpv4.cidrDe(i.ip(), i.prefixo()),
                        k -> new ArrayList<>()).add(new Ponta(r.hostname(), i));
            }
        }

        for (Map.Entry<String, List<Ponta>> entrada : porSubRede.entrySet()) {
            List<Ponta> pontas = entrada.getValue();
            if (pontas.size() != 2) {
                continue;
            }
            long comRelogio = pontas.stream().filter(p -> p.itf().temClockRate()).count();
            if (comRelogio == 0) {
                Ponta p = pontas.get(0);
                achados.add(AchadoConfiguracao.semCorrecao("Camada física", p.roteador(),
                        p.itf().linha(), "",
                        "O enlace " + entrada.getKey() + " (" + pontas.get(0).roteador() + " ↔ "
                                + pontas.get(1).roteador() + ") não tem clock rate em nenhuma ponta.",
                        "Enlace serial exige DCE em exatamente uma ponta; sem relógio a linha "
                                + "não sobe. Qual ponta é DCE depende do cabo, e isso o texto não diz."));
            } else if (comRelogio == 2) {
                Ponta p = pontas.get(1);
                achados.add(AchadoConfiguracao.aviso("Camada física", p.roteador(), p.itf().linha(),
                        "O enlace " + entrada.getKey() + " tem clock rate nas duas pontas.",
                        "A ponta ligada como DTE ignora o comando; não quebra, mas indica que o "
                                + "papel de cada lado não foi definido."));
            }
        }
    }

    /**
     * Confere se cada {@code network} de fato faz alguma coisa.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> apontar o anúncio que não sai (BGP) ou o {@code network} que não liga
     * o protocolo em nenhuma interface (OSPF, EIGRP, RIP).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> a gramática é a do bloco (auditoria CALC-12: a regra do BGP era
     * aplicada aos quatro). BGP: o prefixo tem de existir exatamente na tabela — aqui, ser sub-rede de uma
     * interface. OSPF e EIGRP com wildcard: basta UMA interface cujo IP case com rede + wildcard
     * ({@code network 10.0.0.0 0.255.255.255} liga todas as interfaces 10.x). EIGRP sem wildcard e RIP: a
     * rede é classful e casa qualquer interface dentro dela.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; endereço ou wildcard ilegível não gera aviso
     * (a leitura do texto já acusa o que está malformado).</p>
     */
    private void conferirAnuncios(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        for (RoteadorLido r : roteadores) {
            for (BlocoRoteamento bloco : r.blocos()) {
                for (RedeAnunciada rede : bloco.redes()) {
                    if ("BGP".equals(bloco.protocolo())) {
                        boolean conectada = r.interfacesComIp().stream()
                                .anyMatch(i -> MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo())
                                        .equals(rede.rede()));
                        if (!conectada) {
                            achados.add(AchadoConfiguracao.aviso("Roteamento", r.hostname(), rede.linha(),
                                    "O bloco BGP anuncia " + rede.rede()
                                            + ", que não é sub-rede de nenhuma interface deste roteador.",
                                    "No BGP, \"network\" só anuncia prefixo que já esteja na tabela de "
                                            + "roteamento; sem rota correspondente o anúncio não sai."));
                        }
                        continue;
                    }
                    long base = MascaraIpv4.ipParaLong(rede.rede());
                    long curinga = rede.wildcard() == null || rede.wildcard().isBlank()
                            ? curingaClassful(base) : MascaraIpv4.ipParaLong(rede.wildcard());
                    if (base < 0 || curinga < 0) {
                        continue;
                    }
                    long fixos = ~curinga & 0xFFFFFFFFL;
                    boolean ligaAlguma = r.interfacesComIp().stream()
                            .map(i -> MascaraIpv4.ipParaLong(i.ip()))
                            .anyMatch(ip -> ip >= 0 && (ip & fixos) == (base & fixos));
                    if (!ligaAlguma) {
                        achados.add(AchadoConfiguracao.aviso("Roteamento", r.hostname(), rede.linha(),
                                "O \"network " + rede.rede() + "\" do " + bloco.protocolo()
                                        + " não casa com o IP de nenhuma interface deste roteador.",
                                "Em " + bloco.protocolo() + " o \"network\" escolhe as INTERFACES onde o "
                                        + "protocolo roda (IP da interface dentro de rede + curinga); sem "
                                        + "nenhuma, a linha não faz nada."));
                    }
                }
            }
        }
    }

    /** Curinga da rede classful do endereço (A /8, B /16, C /24); D/E e inválidos devolvem -1. */
    private static long curingaClassful(long endereco) {
        if (endereco < 0) {
            return -1;
        }
        int o1 = (int) (endereco >>> 24);
        if (o1 < 128) {
            return 0x00FFFFFFL;
        }
        if (o1 < 192) {
            return 0x0000FFFFL;
        }
        if (o1 < 224) {
            return 0x000000FFL;
        }
        return -1;
    }

    private void conferirBoasPraticas(List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                // Loopback nasce ligada, e subinterface segue a física: "no shutdown" ausente nelas não é
                // problema (auditoria CALC-14, CONT-33).
                boolean nasceLigada = i.loopback() || i.nome().contains(".");
                if (i.shutdownExplicito()) {
                    achados.add(AchadoConfiguracao.aviso("Interface", r.hostname(), i.linha(),
                            "A interface " + i.nome() + " tem endereço, mas está em \"shutdown\" explícito.",
                            "É intencional? Um endereço numa interface em shutdown não entra em operação até o \"no shutdown\"."));
                } else if (!i.noShutdown() && !nasceLigada) {
                    achados.add(AchadoConfiguracao.aviso("Interface", r.hostname(), i.linha(),
                            "A interface " + i.nome() + " tem endereço, mas não recebeu \"no shutdown\".",
                            "Interface de roteador nasce administrativamente desligada no IOS."));
                }
            }
            // Sem aviso de "no auto-summary" no BGP: desde o IOS 12.3 o auto-summary do BGP já nasce
            // desligado (CONT-33: o aviso mandava corrigir o que já é o padrão).
        }
    }

    // ----------------------------------------------------------------- apoio

    private Map<Long, RoteadorLido> indicePorAs(List<RoteadorLido> roteadores) {
        Map<Long, RoteadorLido> indice = new LinkedHashMap<>();
        for (RoteadorLido r : roteadores) {
            if (r.asBgp() > 0) {
                indice.putIfAbsent(r.asBgp(), r);
            }
        }
        return indice;
    }

    /**
     * Interface do roteador cuja sub-rede tem {@code ip} como endereço de rede ou de broadcast.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> separar o vizinho que aponta para um endereço impossível do vizinho
     * que aponta para um endereço que falta no outro roteador.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só vale para prefixos até /30 — em /31 os dois endereços são de host
     * (RFC 3021) e /32 é um endereço só.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; sem interface nessa condição devolve {@code null}.</p>
     */
    private static InterfaceLida interfaceOndeEReservado(RoteadorLido r, String ip) {
        for (InterfaceLida i : r.interfacesComIp()) {
            if (i.prefixo() <= 30 && MascaraIpv4.mesmaSubRede(i.ip(), ip, i.prefixo())
                    && (ip.equals(MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo()))
                        || ip.equals(MascaraIpv4.broadcastDe(i.ip(), i.prefixo())))) {
                return i;
            }
        }
        return null;
    }

    /** Para cada roteador, os endereços que os OUTROS scripts afirmam que ele possui. */
    private Map<String, List<Exigencia>> mapearExigencias(
            List<RoteadorLido> roteadores, Map<Long, RoteadorLido> porAs,
            List<Pendencia> pendencias) {

        Map<String, List<Exigencia>> exigencias = new LinkedHashMap<>();
        Set<Long> ausentesRegistrados = new LinkedHashSet<>();
        // AS com mais de um roteador (iBGP) não diz QUAL deles tem o endereço: deduzir pelo primeiro da lista
        // acusava "faltam endereços" num roteador certo (CONT-33).
        Map<Long, Long> roteadoresPorAs = roteadores.stream().filter(x -> x.asBgp() > 0)
                .collect(java.util.stream.Collectors.groupingBy(RoteadorLido::asBgp,
                        java.util.stream.Collectors.counting()));

        for (RoteadorLido r : roteadores) {
            BlocoRoteamento bgp = r.bgp();
            if (bgp == null) {
                continue;
            }
            for (VizinhoBgp v : bgp.vizinhos()) {
                if (roteadoresPorAs.getOrDefault(v.remoteAs(), 0L) > 1) {
                    continue;
                }
                RoteadorLido dono = porAs.get(v.remoteAs());
                if (dono == null) {
                    if (ausentesRegistrados.add(v.remoteAs())) {
                        pendencias.add(new Pendencia(
                                "O AS " + v.remoteAs() + " é vizinho de " + r.hostname()
                                        + " (via " + v.ip() + "), mas o script dele não foi colado.",
                                "Cole a configuração do roteador com \"router bgp " + v.remoteAs()
                                        + "\" para fechar esse lado da topologia."));
                    }
                    continue;
                }
                // Vizinho que é rede/broadcast da sub-rede do próprio declarante não é endereço possível:
                // o erro é da linha do declarante (conferirVizinhos acusa), e "corrigir" o outro roteador
                // para esse endereço o deixaria com o broadcast do /30 (auditoria CALC-05).
                if (interfaceOndeEReservado(r, v.ip()) != null) {
                    continue;
                }
                exigencias.computeIfAbsent(dono.hostname(), k -> new ArrayList<>())
                        .add(new Exigencia(v.ip(), r.hostname(), v.remoteAs(), v.linha()));
            }
        }
        return exigencias;
    }

    private Set<String> enderecosDeclarados(List<RoteadorLido> roteadores) {
        Set<String> enderecos = new LinkedHashSet<>();
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                enderecos.add(i.ip());
            }
        }
        return enderecos;
    }

    private void permutar(List<Exigencia> atual, int inicio, List<List<Exigencia>> destino) {
        if (inicio == atual.size() - 1 || atual.isEmpty()) {
            destino.add(new ArrayList<>(atual));
            return;
        }
        for (int i = inicio; i < atual.size(); i++) {
            java.util.Collections.swap(atual, inicio, i);
            permutar(atual, inicio + 1, destino);
            java.util.Collections.swap(atual, inicio, i);
        }
    }

    /** Endereço que um script afirma pertencer a outro roteador. */
    private record Exigencia(String endereco, String declaradoPor, long asAlvo, int linha) {
    }

    private record Ponta(String roteador, InterfaceLida itf) {
    }

    /** Saída da auditoria: modelo já corrigido, achados e pendências. */
    public record ResultadoAuditoria(
            List<RoteadorLido> roteadores,
            List<AchadoConfiguracao> achados,
            List<Pendencia> pendencias) {
    }
}
