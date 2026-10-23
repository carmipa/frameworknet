package org.framework.net.ipv6.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.exception.DnsResolucaoException;
import org.framework.net.analiseDidatica.infrastructure.dns.DnsResolver;
import org.framework.net.ipv6.config.Ipv6Config;
import org.framework.net.ipv6.domain.Ipv6AnaliseRica;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.AnaliseIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.ComparacaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DecomposicaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.DivisaoIpv6;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.Eui64Result;
import org.framework.net.ipv6.domain.Ipv6SubnetKernel.UlaResult;
import org.framework.net.ipv6.exception.Ipv6Exception;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.Map;

/**
 * Orquestra a Calculadora IPv6: valida a intenção, chama o kernel e registra telemetria.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> ponto de aplicação entre o resource HTTP e o {@link
 * Ipv6SubnetKernel}, aplicando o teto de renderização da configuração.</p>
 * <p><b>INVARIANTES DO DOMÍNIO:</b> não reimplementa cálculo — delega ao kernel; o teto de linhas
 * vem sempre da config, nunca do cliente.</p>
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> deixa a {@code Ipv6Exception} do kernel subir para o
 * mapper (400); registra o evento de erro na telemetria via mapper.</p>
 */
@ApplicationScoped
public class Ipv6CidrService {

    @Inject
    Ipv6SubnetKernel kernel;

    @Inject
    Ipv6Config config;

    @Inject
    DnsResolver dnsResolver;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public AnaliseIpv6 analisar(String entrada) {
        AnaliseIpv6 r = kernel.analisar(entrada);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_analise",
                Map.of("status", "ok", "prefixo", r.prefixo(), "tipo", r.tipo()));
        return r;
    }

    /** Decomposição por nibble + expansão/compressão (RFC 4291/5952) para a aba Nibbles. */
    public Ipv6SubnetKernel.NibblesIpv6 nibbles(String entrada) {
        Ipv6SubnetKernel.NibblesIpv6 r = kernel.nibbles(entrada);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_nibbles", Map.of("status", "ok"));
        return r;
    }

    /** Decomposição didática profunda (128 bits, corte rede/interface, delegação) para a Análise. */
    public DecomposicaoIpv6 decompor(String entrada) {
        DecomposicaoIpv6 d = kernel.decompor(entrada);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_analise",
                Map.of("status", "ok", "prefixo", d.base().prefixo(), "tipo", d.base().tipo()));
        return d;
    }

    /**
     * Análise rica (gêmeo IPv6 da Análise Didática IPv4): grade de 128 bits, aplicação do prefixo,
     * capacidade, passo-a-passo, linha do tempo, delegação, régua, referência, conversão, GRC, CLI,
     * termos, banner e resumo tipo prova. O teto da régua vem da config, nunca do cliente.
     */
    public Ipv6AnaliseRica.Resultado analisarRica(String entrada) {
        Ipv6AnaliseRica.Resultado r = kernel.analisarRica(entrada, config.reguaCount());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_analise",
                Map.of("status", "ok", "prefixo", r.base().prefixo(), "tipo", r.base().tipo(),
                        "modo", "rica"));
        return r;
    }

    public DivisaoIpv6 dividir(String cidr, int prefixoAlvo) {
        DivisaoIpv6 r = kernel.dividir(cidr, prefixoAlvo, config.maxLinhas());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_divisao",
                Map.of("status", "ok", "prefixoBase", r.prefixoBase(),
                        "prefixoAlvo", r.prefixoAlvo(), "exibidas", r.exibidas()));
        return r;
    }

    /** Deriva o Interface ID EUI-64 e o endereço SLAAC de um prefixo /64 + MAC. */
    public Eui64Result eui64(String prefixo, String mac) {
        Eui64Result r = kernel.eui64(prefixo, mac);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_eui64", Map.of("status", "ok"));
        return r;
    }

    /** Gera um prefixo ULA (fd00::/8) pseudo-aleatório (RFC 4193). */
    public UlaResult gerarUla(String subnetId) {
        UlaResult r = kernel.gerarUla(subnetId);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_ula", Map.of("status", "ok"));
        return r;
    }

    /**
     * Planeja a delegação de prefixo (Resolução IPv6): aloca um /prefixoAlvo para cada nome de
     * sub-rede a partir do bloco base. O teto de linhas vem da config, nunca do cliente.
     */
    public Ipv6SubnetKernel.DelegacaoPlano planejarDelegacao(String baseCidr, int prefixoAlvo, java.util.List<String> nomes) {
        Ipv6SubnetKernel.DelegacaoPlano p = kernel.planejarDelegacao(baseCidr, prefixoAlvo, nomes, config.maxLinhas());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_resolucao",
                Map.of("status", "ok", "prefixoBase", p.prefixoBase(), "prefixoAlvo", p.prefixoAlvo(),
                        "usados", p.usados()));
        return p;
    }

    /** Sumariza N prefixos/endereços IPv6 (supernet, blocos mesclados, contenção). */
    public Ipv6SubnetKernel.SumarizacaoIpv6 sumarizar(java.util.List<String> entradas) {
        Ipv6SubnetKernel.SumarizacaoIpv6 r = kernel.sumarizar(entradas);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_sumarizacao",
                Map.of("status", "ok", "entradas", r.entradas(), "blocos", r.blocosMesclados().size()));
        return r;
    }

    /** Plano de VLANs IPv6: um /prefixoLan por VLAN + gateway + trunk + Cisco SVI. */
    public Ipv6SubnetKernel.VlanPlano planejarVlans(String baseCidr, int prefixoLan,
            java.util.List<String> idsTexto, java.util.List<String> nomes, boolean dhcpv6) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (idsTexto != null) {
            for (String s : idsTexto) {
                Integer v = null;
                if (s != null && !s.strip().isEmpty()) {
                    try {
                        v = Integer.valueOf(s.strip());
                    } catch (NumberFormatException ex) {
                        v = -1; // marca inválido; o kernel rejeita com mensagem didática
                    }
                }
                ids.add(v);
            }
        }
        Ipv6SubnetKernel.VlanPlano r = kernel.planejarVlans(baseCidr, prefixoLan, ids, nomes, dhcpv6, config.maxLinhas());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_vlan",
                Map.of("status", "ok", "total", r.total(), "dhcpv6", dhcpv6));
        return r;
    }

    /** Converte uma faixa [início, fim] IPv6 na lista mínima de blocos CIDR. */
    public Ipv6SubnetKernel.FaixaCidrIpv6 faixaParaCidr(String inicio, String fim) {
        Ipv6SubnetKernel.FaixaCidrIpv6 r = kernel.faixaParaCidr(inicio, fim, config.maxLinhas());
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_faixa_cidr",
                Map.of("status", "ok", "blocos", r.quantidadeBlocos()));
        return r;
    }

    /** Projeta uma rede IPv6 (Laboratório de Resolução, aba Projetar). */
    public Ipv6SubnetKernel.ProjetoRede projetarRede(String base, int prefixoLan, int prefixoWan,
            String topologia, java.util.List<String> locais, int eigrpAs, int ospfProc) {
        Ipv6SubnetKernel.ProjetoRede r = kernel.projetarRede(base, prefixoLan, prefixoWan, topologia,
                locais, eigrpAs, ospfProc);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_projetar",
                Map.of("status", "ok", "locais", r.totalLocais(), "links", r.totalLinks(),
                        "topologia", r.topologia()));
        return r;
    }

    /** Engenharia reversa de configuração Cisco IPv6 (Laboratório de Resolução). */
    public Ipv6SubnetKernel.EngenhariaReversaIpv6 engenhariaReversa(String config) {
        Ipv6SubnetKernel.EngenhariaReversaIpv6 r = kernel.engenhariaReversa(config);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_engenharia_reversa",
                Map.of("status", "ok", "interfaces", r.interfaces().size(), "achados", r.achados().size()));
        return r;
    }

    /** Compara dois endereços/prefixos IPv6 (mesma /64, contenção, distância, bits comuns). */
    public ComparacaoIpv6 comparar(String a, String b) {
        ComparacaoIpv6 r = kernel.comparar(a, b);
        telemetriaLogger.logEvent("info", "ipv6", "ipv6_comparar",
                Map.of("status", "ok", "mesmaLan", r.mesmaLan(), "bitsComuns", r.bitsComuns()));
        return r;
    }

    /**
     * Resolve o registro AAAA (IPv6) de um domínio e analisa o endereço resultante.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> aba "Domínio → AAAA" — mostra o IPv6 que um nome publica e o
     * decompõe (tipo, forma canônica, Interface ID, solicited-node, reverso ip6.arpa), reusando o
     * mesmo motor de análise e a mesma egress DNS endurecida (guardas SSRF) da Análise IPv4.</p>
     * <p><b>INVARIANTES DO DOMÍNIO:</b> não reimplementa resolução DNS — delega ao {@link DnsResolver}
     * (único ponto de saída com bloqueio de hostname interno e recusa de endereço não-público).</p>
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> domínio vazio/sem ponto lança {@link Ipv6Exception}
     * antes de qualquer rede; falha de resolução ({@link DnsResolucaoException}) é reembalada como
     * {@link Ipv6Exception} para virar 400 didático no {@code Ipv6ExceptionMapper}.</p>
     */
    public DominioAaaaResult resolverDominio(String dominio) {
        String d = dominio == null ? "" : dominio.strip();
        if (d.isEmpty()) {
            throw new Ipv6Exception("Informe um domínio (ex.: google.com).");
        }
        if (!d.contains(".")) {
            throw new Ipv6Exception("Domínio inválido. Use algo como google.com ou www.example.org.");
        }
        try {
            String aaaa = dnsResolver.resolverAaaaComCache(d);
            Ipv6AnaliseRica.Resultado analise = kernel.analisarRica(aaaa, config.reguaCount());
            telemetriaLogger.logEvent("info", "ipv6", "ipv6_dominio_aaaa",
                    Map.of("status", "ok", "tipo", analise.base().tipo()));
            return new DominioAaaaResult(d, aaaa, analise);
        } catch (DnsResolucaoException ex) {
            telemetriaLogger.logEvent("warn", "ipv6", "ipv6_dominio_aaaa",
                    Map.of("status", "erro"));
            throw new Ipv6Exception(ex.getMessage(), ex);
        }
    }

    /** Domínio resolvido para IPv6 (AAAA) com a análise rica completa do endereço. */
    public record DominioAaaaResult(String dominio, String enderecoAaaa, Ipv6AnaliseRica.Resultado analise) { }
}
