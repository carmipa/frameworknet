package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.VlsmService;
import org.framework.net.resolucaoProblemas.application.export.ExportTxtService;
import org.framework.net.resolucaoProblemas.application.export.ExportZipService;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.application.routing.VlsmRoutingService;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VlsmServiceTest {

    @Inject
    VlsmService vlsmService;

    @Inject
    VlsmRoutingService routingService;

    @Inject
    ExportTxtService exportTxtService;

    @Inject
    ExportZipService exportZipService;

    private NetworkScenarioResult solveDemo(VlsmNormalizationService.DemoScenario demo) {
        return vlsmService.solveNetworkProblem(
                demo.baseNetwork(),
                demo.locations(),
                demo.topologyType(),
                Integer.parseInt(demo.wanPrefix()),
                demo.eigrpAs().isBlank() ? 71 : Integer.parseInt(demo.eigrpAs()),
                demo.remoteAccess(),
                demo.routingMode(),
                demo.ospfProcess().isBlank() ? 1 : Integer.parseInt(demo.ospfProcess()));
    }

    @Test
    void cenarioFiapCheckpoint() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        assertEquals("172.42.0.0/16", s.getBaseNetwork());
        assertEquals(4, s.getTotalLocations());
        assertEquals(4, s.getLanBlocks().size());
        assertTrue(s.getWanLinks().size() >= 3);
        assertNotNull(s.getRouterCommands().get("Matriz"));
        assertTrue(s.getRouterCommands().values().iterator().next().contains("router eigrp"));
    }

    @Test
    void cenarioMazolaGlobalSolution() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO);
        assertEquals("172.63.0.0/16", s.getBaseNetwork());
        assertEquals(3, s.getTotalLocations());
        assertEquals("star", s.getTopologyType());
        assertEquals(2, s.getWanLinks().size());
    }

    @Test
    void oitoRoteadoresSsh() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.EIGHT_ROUTERS_DEMO);
        assertEquals(8, s.getTotalLocations());
        assertEquals("ssh", s.getRemoteAccess());
        String cli = s.getRouterCommands().get("Matriz");
        assertTrue(cli.contains("transport input ssh"));
        assertTrue(cli.contains("ip ssh version 2"));
        // CONT-22: SSH no IOS autentica por usuário; senha de linha não serve, e 1024 bits é pouco.
        assertTrue(cli.contains("username admin secret "), cli);
        assertTrue(cli.contains(" login local"), cli);
        assertTrue(cli.contains("crypto key generate rsa modulus 2048"), cli);
        assertFalse(cli.contains("modulus 1024"), cli);
        assertFalse(cli.contains("\n login\n"), "SSH com senha de linha não autentica:\n" + cli);
    }

    @Inject
    org.framework.net.resolucaoProblemas.application.parsing.EngenhariaReversaService engenhariaReversa;

    @Inject
    org.framework.net.resolucaoProblemas.application.importing.BulkClassImportService bulkClassImport;

    @Inject
    org.framework.net.resolucaoProblemas.application.export.ExportClassZipService exportClassZip;

    /**
     * Auditoria CALC-19: "Filial 1" e "Filial-1" viram o mesmo arquivo, e dois alunos "Lucas Silva" a mesma
     * pasta; o ZIP caía inteiro com "duplicate entry". Agora cada um tem o seu arquivo, relido no ZIP.
     */
    @Test
    void zipNaoCaiComNomesQueColidem() throws Exception {
        NetworkScenarioResult s = vlsmService.solveNetworkProblem("10.0.0.0/16",
                List.of(new LocationInput("Filial 1", "50"), new LocationInput("Filial-1", "50")),
                "star", 30, 71, "telnet", "eigrp_only", 1);
        List<String> nomes = nomesNoZip(exportZipService.generatePacketTracerZipBuffer(s));
        assertEquals(2, nomes.stream().filter(n -> n.startsWith("configs_individuais/")).distinct().count(), nomes.toString());

        var turma = bulkClassImport.parseClassRosterPaste(
                "Lucas Silva\t172.51.0.0/16\t100\t50\nLucas Silva\t172.52.0.0/16\t100\t50");
        assertEquals(2, turma.stream().map(r -> r.getFolderSlug()).distinct().count());
        List<String> daTurma = nomesNoZip(exportClassZip.generateClassRosterZipBuffer(turma,
                new org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData()));
        assertEquals(2, daTurma.stream().filter(n -> n.endsWith("/GUIA_MONTAGEM_PACKET_TRACER.txt")).count(),
                daTurma.toString());
    }

    /**
     * Auditoria CALC-39: o texto citava "router ospf 1" com o processo 7; o mapa de seriais saía com as
     * chaves internas loc_1...; e o erro de um aluno na importação da turma não dizia qual.
     */
    @Test
    void textosDoCenarioUsamOsValoresDoCenario() {
        NetworkScenarioResult s = vlsmService.solveNetworkProblem("10.0.0.0/16",
                List.of(new LocationInput("Matriz", "50"), new LocationInput("Filial", "50")),
                "star", 30, 71, "telnet", "ospf_only", 7);
        String explicacoes = s.getRouterCliExplanations().toString();
        assertTrue(explicacoes.contains("router ospf 7"), explicacoes);
        assertFalse(explicacoes.contains("router ospf 1 "), explicacoes);
        assertEquals(java.util.Set.of("Matriz", "Filial"), s.getTopologyInsights().getSerialWanByLocation().keySet());

        var turma = bulkClassImport.parseClassRosterPaste(
                "Ana Lima\t172.51.0.0/16\t100\t50\nBruno Costa\t172.52.0.0/30\t100\t50");
        EntradaInvalidaException erro = assertThrows(EntradaInvalidaException.class,
                () -> exportClassZip.generateClassRosterZipBuffer(turma,
                        new org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData()));
        assertTrue(erro.getMessage().contains("Bruno Costa"), erro.getMessage());
    }

    private static List<String> nomesNoZip(byte[] zip) throws Exception {
        List<String> nomes = new java.util.ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                nomes.add(e.getName());
            }
        }
        return nomes;
    }

    /**
     * Auditoria CALC-17/CALC-18: o hub de 8 roteadores tem 7 seriais, todas portas reais do 2911 (HWIC-2T
     * tem 0 e 1), e cada enlace leva clock rate em UMA ponta — o script do Projetar, colado na Engenharia
     * reversa do próprio site, não pode ser reprovado pelo relógio.
     */
    @Test
    void seriaisReaisEClockRateUmaVezPorEnlace() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.EIGHT_ROUTERS_DEMO);
        String todos = String.join("\n-------------\n", s.getRouterCommands().values());
        assertFalse(todos.matches("(?s).*Serial0/\\d/[2-9].*"), "porta que o 2911 não tem:\n" + todos);
        // A auditoria do próprio site vem antes da contagem: é ela que precisa enxergar o relógio errado.
        var cenario = engenhariaReversa.interpretar(todos);
        assertTrue(cenario.achados().stream().noneMatch(a -> "Camada física".equals(a.categoria())),
                () -> "o próprio script do Projetar reprovado pelo relógio: " + cenario.achados());
        long relogios = todos.lines().filter(l -> l.strip().startsWith("clock rate ")).count();
        assertEquals(s.getWanLinks().size(), relogios, "um clock rate por enlace");
    }

    /** Fronteira do CONT-22: o modo Telnet continua com senha de linha, sem usuário nem chave RSA. */
    @Test
    void telnetContinuaComSenhaDeLinha() {
        NetworkScenarioResult s = vlsmService.solveNetworkProblem(
                "10.0.0.0/16", List.of(new LocationInput("A", "50"), new LocationInput("B", "50")),
                "star", 30, 71, "telnet", "eigrp_only", 1);
        String cli = s.getRouterCommands().values().iterator().next();
        assertTrue(cli.contains("\n login\n"), cli);
        assertTrue(cli.contains("transport input telnet"), cli);
        assertFalse(cli.contains("crypto key generate"), cli);
        assertFalse(cli.contains("login local"), cli);
    }

    @Test
    void suggestedBasePrefixDentroDoIntervaloValido() {
        List<LocationInput> locs = List.of(
                new LocationInput("A", "50"),
                new LocationInput("B", "50")
        );
        NetworkScenarioResult s = vlsmService.solveNetworkProblem(
                "10.0.0.0/16", locs, "star", 30, 71, "telnet", "eigrp_only", 1);
        int suggested = s.getSuggestedBasePrefix();
        // Prefixo sugerido deve ser um CIDR válido (0..32) e nunca mais amplo que a base.
        assertTrue(suggested >= 0 && suggested <= 32,
                "prefixo sugerido fora de 0..32: " + suggested);
        assertTrue(suggested >= s.getBaseNetworkPrefix(),
                "prefixo sugerido não pode ser mais amplo que a rede base");
    }

    @Test
    void cenarioPequenoEigrpAs100() {
        List<LocationInput> locs = List.of(
                new LocationInput("A", "50"),
                new LocationInput("B", "50")
        );
        NetworkScenarioResult s = vlsmService.solveNetworkProblem(
                "10.0.0.0/24", locs, "star", 30, 100, "telnet", "eigrp_only", 1);
        assertEquals(100, s.getEigrpAs());
        assertTrue(s.getRouterCommands().get("A").contains("router eigrp 100"));
    }

    @Test
    void eigrpAsInvalidoZero() {
        List<LocationInput> locs = List.of(new LocationInput("X", "10"));
        EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class, () ->
                vlsmService.solveNetworkProblem("192.168.0.0/24", locs, "star", 30, 0, "telnet", "auto", 1));
        assertTrue(ex.getMessage().toLowerCase().contains("65535"));
    }

    @Test
    void routingModeAutoQuatroSitesDualSplit() {
        assertEquals("dual_split", routingService.normalizeRoutingMode("auto", 4));
        assertEquals("eigrp_only", routingService.normalizeRoutingMode("auto", 2));
    }

    @Test
    void exportTxtConsolidado() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        String txt = exportTxtService.generatePacketTracerScript(s);
        assertNotNull(txt);
        assertTrue(txt.length() > 200);
        assertTrue(txt.contains("hostname") || txt.contains("configure terminal"));
    }

    @Test
    void exportEntregaRelatorio() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        String relatorio = exportTxtService.generateEntregaRelatorioTxt(s);
        assertTrue(relatorio.length() > 500);
    }

    @Test
    void exportZipLaboratorio() throws Exception {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        byte[] zip = exportZipService.generatePacketTracerZipBuffer(s);
        assertNotNull(zip);
        assertTrue(zip.length > 100);
        int entries = 0;
        boolean algumComScriptCisco = false;
        // A6 — relê o CONTEÚDO de cada entrada, não só conta entradas. Uma entrada vazia ou
        // corrompida passaria no antigo "entries >= 3".
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                byte[] dados = zis.readAllBytes();
                assertTrue(dados.length > 0, "entrada de ZIP vazia: " + entry.getName());
                String texto = new String(dados, java.nio.charset.StandardCharsets.UTF_8);
                if (texto.contains("hostname") || texto.contains("configure terminal")
                        || texto.contains("interface")) {
                    algumComScriptCisco = true;
                }
            }
        }
        assertTrue(entries >= 3);
        assertTrue(algumComScriptCisco, "nenhuma entrada do ZIP continha script Cisco legivel");
    }

    /**
     * Invariante central do VLSM: nenhum bloco alocado (LAN ou WAN) pode sobrepor outro — dois
     * sites recebendo a mesma sub-rede é o pior erro possível de um planejador. Antes desta
     * asserção, os testes só conferiam CONTAGENS; a sobreposição passaria verde.
     *
     * Gabarito independente da implementação (A3): as faixas são recalculadas via inet.ipaddr a
     * partir de network/prefix (low/high do prefix block), não pela lógica do alocador sob teste.
     */
    @Test
    void blocosVlsmNaoSobrepoemEComportamOsHosts() {
        for (VlsmNormalizationService.DemoScenario demo : List.of(
                VlsmNormalizationService.FIAP_CHECKPOINT_DEMO,
                VlsmNormalizationService.EIGHT_ROUTERS_DEMO,
                VlsmNormalizationService.MAZOLAS_GLOBAL_SOLUTION_DEMO)) {
            NetworkScenarioResult s = solveDemo(demo);

            List<String> rotulos = new java.util.ArrayList<>();
            List<long[]> faixas = new java.util.ArrayList<>();

            for (LanBlock lan : s.getLanBlocks()) {
                assertTrue(lan.getHostsSupported() >= lan.getHostsRequired(),
                        "LAN " + lan.getLocationName() + " comporta " + lan.getHostsSupported()
                                + " < requeridos " + lan.getHostsRequired() + " em " + demo.baseNetwork());
                assertNotNull(lan.getNetwork(), "LAN sem network em " + demo.baseNetwork());
                rotulos.add("LAN:" + lan.getLocationName());
                faixas.add(faixaCidr(lan.getNetwork(), lan.getPrefix()));
            }
            for (WanLink wan : s.getWanLinks()) {
                assertNotNull(wan.getNetwork(), "WAN sem network em " + demo.baseNetwork());
                rotulos.add("WAN:" + wan.getName());
                faixas.add(faixaCidr(wan.getNetwork(), wan.getPrefix()));
            }

            for (int i = 0; i < faixas.size(); i++) {
                for (int j = i + 1; j < faixas.size(); j++) {
                    long[] a = faixas.get(i);
                    long[] b = faixas.get(j);
                    boolean disjuntos = a[1] < b[0] || b[1] < a[0];
                    assertTrue(disjuntos, "Sobreposicao entre " + rotulos.get(i) + " e "
                            + rotulos.get(j) + " no cenario " + demo.baseNetwork());
                }
            }
        }
    }

    private static long[] faixaCidr(String network, int prefix) {
        inet.ipaddr.ipv4.IPv4Address a = new inet.ipaddr.IPAddressString(network + "/" + prefix)
                .getAddress().toIPv4().toPrefixBlock();
        return new long[]{a.getLower().longValue(), a.getUpper().longValue()};
    }

    /**
     * CALC-04 + CALC-16: a exclusão do DHCP sai sem "/NN" (o IOS recusava a linha) e cabe na folga da LAN
     * (gateway + 9 fixo deixava um /29 de 5 hosts sem nenhum endereço para entregar); os PCs de teste do
     * diagrama são endereços que o DHCP de fato entrega. Gabarito pela aritmética da LAN, não pelo código:
     * entregáveis = hosts suportados − excluídos, e tem de ser ≥ hosts pedidos.
     */
    @Test
    void dhcpExcluiSemPrefixoECabeNaFolgaDaLan() {
        NetworkScenarioResult s = vlsmService.solveNetworkProblem("192.168.10.0/24",
                List.of(new LocationInput("Loja", "5"), new LocationInput("Sede", "60")),
                "star", 30, 71, "telnet", "eigrp_only", 1);
        String txt = exportTxtService.generatePacketTracerScript(s);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^ip dhcp excluded-address (\\S+)(?: (\\S+))?\\s*$").matcher(txt);
        java.util.Map<String, Long> excluidosPorGateway = new java.util.HashMap<>();
        while (m.find()) {
            assertTrue(m.group(1).matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), "com prefixo: " + m.group());
            assertTrue(m.group(2) == null || m.group(2).matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), "com prefixo: " + m.group());
            long ini = ipLong(m.group(1));
            long fim = m.group(2) == null ? ini : ipLong(m.group(2));
            excluidosPorGateway.put(m.group(1), fim - ini + 1);
        }
        assertEquals(2, excluidosPorGateway.size(), "uma exclusão por LAN: " + excluidosPorGateway);
        for (var lan : s.getLanBlocks()) {
            long excluidos = excluidosPorGateway.get(lan.getGateway());
            assertTrue(lan.getHostsSupported() - excluidos >= lan.getHostsRequired(),
                    lan.getLocationName() + ": " + lan.getHostsSupported() + " hosts, " + excluidos
                            + " excluídos, " + lan.getHostsRequired() + " pedidos");
            // PC de teste do diagrama: o primeiro endereço entregue, logo depois da faixa excluída
            String primeiroEntregue = longIp(ipLong(lan.getGateway()) + excluidos);
            assertTrue(s.getTopologyMermaid().contains(primeiroEntregue), lan.getLocationName()
                    + ": o diagrama não mostra " + primeiroEntregue);
        }
    }

    @Test
    void dhcpNoTopoDoEspacoNaoQuebra() {
        // base no fim do espaço IPv4: gateway + 9 passava de 255.255.255.255 (AddressValueException → 500)
        NetworkScenarioResult s = vlsmService.solveNetworkProblem("255.255.255.248/29",
                List.of(new LocationInput("Loja", "5")), "star", 30, 71, "telnet", "eigrp_only", 1);
        assertTrue(exportTxtService.generatePacketTracerScript(s).contains("ip dhcp excluded-address 255.255.255.249\n")
                || exportTxtService.generatePacketTracerScript(s).contains("ip dhcp excluded-address 255.255.255.249\r\n"));
    }

    private static long ipLong(String ip) {
        long v = 0;
        for (String p : ip.split("\\.")) {
            v = (v << 8) | Long.parseLong(p);
        }
        return v;
    }

    private static String longIp(long v) {
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    /**
     * PROPÓSITO: o aluno cola o script no Packet Tracer; a máscara do IOS é pontuada pura.
     * INVARIANTE: toda linha "ip address A M" e "network A M" traz M como máscara contígua sem "/NN".
     * FALHA: a regressão "255.255.255.128/25" faz o IOS recusar toda interface e pool DHCP.
     */
    @Test
    void cliCiscoUsaMascaraPontuadaSemPrefixo() {
        NetworkScenarioResult s = solveDemo(VlsmNormalizationService.FIAP_CHECKPOINT_DEMO);
        String txt = exportTxtService.generatePacketTracerScript(s) + "\n"
                + String.join("\n", s.getRouterCommands().values());
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*(?:ip address|network) \\d+\\.\\d+\\.\\d+\\.\\d+ (\\S+)\\s*$")
                .matcher(txt);
        int linhas = 0;
        java.util.List<String> invalidas = new java.util.ArrayList<>();
        while (m.find()) {
            linhas++;
            if (!m.group(1).matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                invalidas.add(m.group().strip());
            }
        }
        assertTrue(linhas > 0, "nenhuma linha ip address/network encontrada: instrumento cego");
        assertEquals(java.util.List.of(), invalidas);
        for (var lan : s.getLanBlocks()) {
            assertEquals(lan.getNetmask().replaceAll("/\\d+$", ""), lan.getNetmask(),
                    "netmask da LAN com sufixo");
        }
    }
}
