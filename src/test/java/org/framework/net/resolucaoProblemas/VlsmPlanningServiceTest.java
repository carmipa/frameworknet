package org.framework.net.resolucaoProblemas;

import inet.ipaddr.ipv4.IPv4Address;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.planning.VlsmPlanningService;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VlsmPlanningServiceTest {

    @Inject
    VlsmPlanningService planningService;

    @Inject
    VlsmNormalizationService normalizationService;

    @Inject
    Ipv4Kernel ipv4Kernel;

    private static final List<String> KEYS_4 = List.of("loc_1", "loc_2", "loc_3", "loc_4");

    @Test
    void normalizeTopologyPadraoStar() {
        assertEquals("star", planningService.normalizeTopologyType(""));
        assertEquals("star", planningService.normalizeTopologyType("star"));
    }

    @Test
    void normalizeTopologyAliases() {
        assertEquals("star", planningService.normalizeTopologyType("ring"));
        assertEquals("extended_star", planningService.normalizeTopologyType("ring_redundant"));
        assertEquals("extended_star", planningService.normalizeTopologyType("estrela_estendida"));
        assertEquals("mesh", planningService.normalizeTopologyType("mesh"));
    }

    @Test
    void requiredPrefix400Hosts() {
        assertEquals(23, planningService.requiredPrefixForHosts(400));
    }

    @Test
    void requiredPrefix300Hosts() {
        assertEquals(23, planningService.requiredPrefixForHosts(300));
    }

    @Test
    void requiredPrefix50Hosts() {
        assertEquals(26, planningService.requiredPrefixForHosts(50));
    }

    @Test
    void buildWanLinksStarQuatroPontos() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "star", 30);
        assertEquals(3, links.size());
        assertTrue(links.stream().allMatch(l -> l.getEndpoints().get(0).equals("loc_1")));
        Set<String> filiais = links.stream().map(l -> l.getEndpoints().get(1)).collect(Collectors.toSet());
        assertEquals(Set.of("loc_2", "loc_3", "loc_4"), filiais);
    }

    @Test
    void buildWanLinksExtendedStarCincoLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "extended_star", 30);
        assertEquals(5, links.size());
    }

    @Test
    void buildWanLinksMeshSeisLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(base, used, KEYS_4, "mesh", 30);
        assertEquals(6, links.size());
    }

    @Test
    void buildWanLinksDoisPontosUmLink() {
        IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/24", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(
                base, used, List.of("loc_1", "loc_2"), "star", 30);
        assertEquals(1, links.size());
    }

    @Test
    void buildWanLinksUmPontoSemLinks() {
        IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/24", "base");
        List<IPv4Address> used = new ArrayList<>();
        List<WanLink> links = planningService.buildWanLinks(
                base, used, List.of("loc_1"), "star", 30);
        assertEquals(0, links.size());
    }

    /**
     * F02: a alocação precisa continuar sendo EXATAMENTE o first-fit de antes. Oráculo independente
     * (A3): first-fit ingênuo escrito aqui, varrendo todos os blocos alinhados da biblioteca e
     * testando sobreposição contra tudo — o algoritmo que a produção usava, sem otimização.
     * 60 cenários com semente fixa, incluindo "sem espaço" (os dois lados têm de falhar juntos).
     */
    @Test
    void alocacaoIgualAoFirstFitIngenuo() {
        java.util.Random rnd = new java.util.Random(20260924L);
        String[] bases = {"10.0.0.0/16", "172.16.0.0/20", "192.168.0.0/22", "192.168.10.0/24"};
        String[] topos = {"star", "extended_star", "mesh"};
        int comparados = 0;
        int semEspaco = 0;
        for (int cenario = 0; cenario < 60; cenario++) {
            String baseTxt = bases[rnd.nextInt(bases.length)];
            String topo = topos[rnd.nextInt(topos.length)];
            int n = 1 + rnd.nextInt(8);
            int[] hosts = new int[n];
            for (int i = 0; i < n; i++) {
                hosts[i] = 1 + rnd.nextInt(rnd.nextBoolean() ? 30 : 400);
            }
            List<String> keys = new ArrayList<>();
            List<LanBlock> lans = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LanBlock l = new LanBlock();
                l.setLocationKey("loc_" + (i + 1));
                l.setLocationName("L" + (i + 1));
                l.setHostsRequired(hosts[i]);
                lans.add(l);
                keys.add("loc_" + (i + 1));
            }
            IPv4Address base = ipv4Kernel.parseNetwork(baseTxt, "base");

            List<String> esperado;
            try {
                esperado = firstFitIngenuo(baseTxt, hosts, planningService.buildWanLinks(
                        ipv4Kernel.parseNetwork("10.0.0.0/8", "base"), new ArrayList<>(), keys, topo, 30).size());
            } catch (IllegalStateException semLugar) {
                esperado = null;
            }
            List<String> obtido;
            try {
                VlsmPlanningService.PlanningResult pr = planningService.buildLanBlocks(base, lans);
                obtido = new ArrayList<>();
                for (LanBlock l : pr.locations()) {
                    obtido.add(l.getNetwork() + "/" + l.getPrefix());
                }
                for (WanLink w : planningService.buildWanLinks(base, new ArrayList<>(pr.usedSubnets()), keys, topo, 30)) {
                    obtido.add(w.getNetwork() + "/" + w.getPrefix());
                }
            } catch (EntradaInvalidaException semLugar) {
                obtido = null;
            }
            assertEquals(esperado, obtido, "cenário " + cenario + " base " + baseTxt + " " + topo);
            if (esperado == null) {
                semEspaco++;
            } else {
                comparados++;
            }
        }
        assertTrue(comparados > 30, "poucos cenários com solução: instrumento pouco exigente (" + comparados + ")");
        assertTrue(semEspaco > 0, "nenhum cenário sem espaço: o ramo de erro não foi exercitado");
    }

    /** First-fit de referência: LANs por hosts decrescente (ordem estável), depois WANs /30. */
    private static List<String> firstFitIngenuo(String baseTxt, int[] hosts, int wans) {
        inet.ipaddr.ipv4.IPv4Address base = new inet.ipaddr.IPAddressString(baseTxt).getAddress().toIPv4().toPrefixBlock();
        int basePrefix = base.getNetworkPrefixLength();
        List<inet.ipaddr.ipv4.IPv4Address> usados = new ArrayList<>();
        Integer[] ordem = new Integer[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            ordem[i] = i;
        }
        java.util.Arrays.sort(ordem, (a, b) -> Integer.compare(hosts[b], hosts[a]));
        String[] porLocal = new String[hosts.length];
        for (int idx : ordem) {
            int bits = 32 - Integer.numberOfLeadingZeros(hosts[idx] + 2 - 1);
            int prefixo = Math.min(30, 32 - bits);
            if (prefixo < basePrefix) {
                throw new IllegalStateException("não cabe");
            }
            inet.ipaddr.ipv4.IPv4Address bloco = primeiroLivre(base, prefixo, usados);
            usados.add(bloco);
            porLocal[idx] = bloco.getLower().withoutPrefixLength().toCanonicalString() + "/" + prefixo;
        }
        List<String> out = new ArrayList<>(List.of(porLocal));
        for (int k = 0; k < wans; k++) {
            if (30 < basePrefix) {
                throw new IllegalStateException("não cabe");
            }
            inet.ipaddr.ipv4.IPv4Address bloco = primeiroLivre(base, 30, usados);
            usados.add(bloco);
            out.add(bloco.getLower().withoutPrefixLength().toCanonicalString() + "/30");
        }
        return out;
    }

    private static inet.ipaddr.ipv4.IPv4Address primeiroLivre(inet.ipaddr.ipv4.IPv4Address base, int prefixo,
            List<inet.ipaddr.ipv4.IPv4Address> usados) {
        Iterator<inet.ipaddr.ipv4.IPv4Address> it = base.setPrefixLength(prefixo, false).prefixBlockIterator();
        while (it.hasNext()) {
            inet.ipaddr.ipv4.IPv4Address c = it.next();
            if (usados.stream().noneMatch(c::overlaps)) {
                return c;
            }
        }
        throw new IllegalStateException("não cabe");
    }

    /**
     * F02: o custo do first-fit antigo crescia ~n^6 (medido: 60 localidades em malha = 9,6 s). Três
     * planos de 50 localidades em malha (1275 sub-redes cada) têm de caber em poucos segundos.
     */
    @Test
    void malhaDeCinquentaLocalidadesEhRapida() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(4), () -> {
            for (int rep = 0; rep < 3; rep++) {
                List<String> keys = new ArrayList<>();
                List<LanBlock> lans = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    LanBlock l = new LanBlock();
                    l.setLocationKey("loc_" + (i + 1));
                    l.setLocationName("L" + (i + 1));
                    l.setHostsRequired(10 + i);
                    lans.add(l);
                    keys.add("loc_" + (i + 1));
                }
                IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/8", "base");
                VlsmPlanningService.PlanningResult pr = planningService.buildLanBlocks(base, lans);
                assertEquals(1225, planningService.buildWanLinks(base, new ArrayList<>(pr.usedSubnets()),
                        keys, "mesh", 30).size());
            }
        });
    }

    @Test
    void buildWanLinksTopologiaInvalida() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<IPv4Address> used = new ArrayList<>();
        EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class, () ->
                planningService.buildWanLinks(base, used, KEYS_4, "anel", 30));
        assertTrue(ex.getMessage().toLowerCase().contains("inválida")
                || ex.getMessage().toLowerCase().contains("invalida"));
    }

    /**
     * CALC-02: "Prefixo WAN" largo sobre base larga materializava o bloco inteiro e derrubava a JVM.
     * Um /9 tem 8 milhões de endereços; as duas pontas do enlace saem por aritmética, em milissegundos.
     */
    @Test
    void wanLargaNaoPercorreOBloco() {
        IPv4Address base = ipv4Kernel.parseNetwork("10.0.0.0/8", "base");
        List<WanLink> links = assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                planningService.buildWanLinks(base, new ArrayList<>(), List.of("loc_1", "loc_2"), "star", 9));
        assertEquals(1, links.size());
        assertEquals("10.0.0.0", links.get(0).getNetwork());
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), List.copyOf(links.get(0).getIps().values()));
    }

    @Test
    void primeirosHostsUteisRespeitaRfc3021ELimite() {
        // /31 e /32: todos os endereços contam (RFC 3021); /30: rede e broadcast ficam de fora.
        assertEquals(List.of("10.0.0.0", "10.0.0.1"), ipv4Kernel.primeirosHostsUteis(
                ipv4Kernel.parseNetwork("10.0.0.0/31", "b"), 5).stream().map(IPv4Address::toCanonicalString).toList());
        assertEquals(List.of("10.0.0.7"), ipv4Kernel.primeirosHostsUteis(
                ipv4Kernel.parseNetwork("10.0.0.7/32", "b"), 5).stream().map(IPv4Address::toCanonicalString).toList());
        assertEquals(List.of("172.16.0.1"), ipv4Kernel.primeirosHostsUteis(
                ipv4Kernel.parseNetwork("172.16.0.0/12", "b"), 1).stream().map(IPv4Address::toCanonicalString).toList());
        assertThrows(IllegalArgumentException.class, () -> ipv4Kernel.primeirosHostsUteis(
                ipv4Kernel.parseNetwork("10.0.0.0/30", "b"), -1));
    }

    @Test
    void usableHostsExcluiRedeEBroadcast() {
        IPv4Address net30 = ipv4Kernel.parseNetwork("192.168.1.0/30", "base");
        List<IPv4Address> hosts = ipv4Kernel.primeirosHostsUteis(net30, 10);
        List<String> ips = hosts.stream().map(IPv4Address::toCanonicalString).toList();
        // /30 tem 4 endereços: .0 (rede), .1, .2, .3 (broadcast) → apenas .1 e .2 são utilizáveis.
        assertEquals(2, hosts.size());
        assertTrue(ips.contains("192.168.1.1"));
        assertTrue(ips.contains("192.168.1.2"));
        assertTrue(!ips.contains("192.168.1.0"));
        assertTrue(!ips.contains("192.168.1.3"));
    }

    @Test
    void usableHostsLanExcluiBroadcast() {
        IPv4Address net29 = ipv4Kernel.parseNetwork("10.0.0.0/29", "base");
        List<IPv4Address> hosts = ipv4Kernel.primeirosHostsUteis(net29, 10);
        List<String> ips = hosts.stream().map(IPv4Address::toCanonicalString).toList();
        // /29 = 8 endereços; 6 utilizáveis (.1..6), sem rede (.0) nem broadcast (.7).
        assertEquals(6, hosts.size());
        assertTrue(!ips.contains("10.0.0.0"));
        assertTrue(!ips.contains("10.0.0.7"));
    }

    @Test
    void iterateSubnetsGeraMultiplosBlocos() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        Iterator<? extends IPv4Address> it = ipv4Kernel.iterateSubnets(base, 21);
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(32, count);
    }

    @Test
    void buildLanBlocksOrdenaPorHosts() {
        IPv4Address base = ipv4Kernel.parseNetwork("172.19.0.0/16", "base");
        List<LanBlock> locs = normalizationService.normalizeLocationsInput(List.of(
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("Filial", "100"),
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("Matriz", "800"),
                new org.framework.net.resolucaoProblemas.domain.model.LocationInput("CPD", "550")
        ));
        VlsmPlanningService.PlanningResult result = planningService.buildLanBlocks(base, new ArrayList<>(locs));
        assertEquals(3, result.locations().size());

        // O invariante REAL do VLSM é a ALOCAÇÃO maior-primeiro (eficiência: a maior LAN pega o
        // bloco alinhado mais baixo), não a ordem da lista retornada — que preserva a entrada.
        // Entrada embaralhada de propósito (100, 800, 550). Antes, o teste fazia
        // sorted(reverseOrder()) sobre a SAÍDA antes de comparar, anulando a própria verificação:
        // qualquer ordem de alocação passaria verde.
        LanBlock maiorLan = result.locations().stream()
                .max(Comparator.comparingInt(LanBlock::getHostsRequired)).orElseThrow();
        assertEquals(800, maiorLan.getHostsRequired());
        long menorEndereco = result.locations().stream()
                .mapToLong(l -> enderecoComoLong(l.getNetwork())).min().orElseThrow();
        assertEquals(menorEndereco, enderecoComoLong(maiorLan.getNetwork()),
                "a LAN com mais hosts deve ocupar o primeiro bloco (alocação maior-primeiro)");
    }

    private static long enderecoComoLong(String ipv4) {
        return new inet.ipaddr.IPAddressString(ipv4).getAddress().toIPv4().longValue();
    }
}
