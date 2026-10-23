package org.framework.net.resolucaoProblemas.application.export;

import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.telemetria.TelemetriaLogger;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.NetworkScenarioResult;
import org.framework.net.resolucaoProblemas.domain.model.PtRouterTable;
import org.framework.net.resolucaoProblemas.domain.model.RoutingPlan;
import org.framework.net.resolucaoProblemas.domain.model.WanLink;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ExportTxtService {

    private static final String PACKET_TRACER_ROUTER_MODEL = "2911";
    private static final String PACKET_TRACER_SWITCH_MODEL = "2960";
    private static final String LAB_ENABLE_PASSWORD = "cisco";
    public static final String LAB_VTY_PASSWORD = "fiap";
    private static final String LAB_SSH_DOMAIN = "lab.fiap.local";
    public static final String LAB_SSH_USER = "admin";
    private static final int LAB_SERIAL_CLOCK_BPS = 64000;

    @Inject
    VlsmNormalizationService normalizationService;

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public String normalizeRemoteAccessExport(String value) {
        String mode = value == null ? "telnet" : value.strip().toLowerCase(Locale.ROOT);
        if (Set.of("ssh", "telnet", "both").contains(mode)) {
            return mode;
        }
        return "telnet";
    }

    /**
     * Linhas de IOS que liberam o acesso remoto pelas VTY do roteador do laboratório.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno cola o bloco no Packet Tracer e entra no roteador por
     * Telnet ou SSH sem montar a configuração de acesso à mão.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> com SSH (ou SSH + Telnet) as VTY usam {@code login local} com um
     * usuário definido, porque o SSH do IOS não autentica pela senha de linha; a chave RSA tem 2048 bits;
     * só Telnet mantém a senha de linha.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; modo desconhecido ou nulo vira "telnet"
     * (ver {@link #normalizeRemoteAccessExport(String)}).</p>
     */
    public List<String> remoteAccessCliLines(String mode) {
        mode = normalizeRemoteAccessExport(mode);
        if ("telnet".equals(mode)) {
            return List.of(
                    "! Acesso remoto Telnet (VTY) — laboratorio / Global Solution FIAP",
                    "line vty 0 4",
                    " password " + LAB_VTY_PASSWORD,
                    " login",
                    " transport input telnet",
                    "exit"
            );
        }
        // Auditoria CONT-22: o SSH do IOS só autentica por usuário (local ou AAA) — com a senha de linha
        // ("password" + "login") a sessão SSH é recusada. Chave de 1024 bits está abaixo do mínimo atual;
        // 2048 é o recomendado (Cisco, "Configuring Secure Shell"; NIST SP 800-131A).
        List<String> lines = new ArrayList<>();
        lines.add("! Acesso remoto SSH (VTY) — laboratorio (usuario " + LAB_SSH_USER + ", senha "
                + LAB_VTY_PASSWORD + ")");
        lines.add("ip domain-name " + LAB_SSH_DOMAIN);
        lines.add("username " + LAB_SSH_USER + " secret " + LAB_VTY_PASSWORD);
        lines.add("crypto key generate rsa modulus 2048");
        lines.add("ip ssh version 2");
        lines.add("!");
        lines.add("line vty 0 4");
        lines.add(" login local");
        lines.add("both".equals(mode) ? " transport input ssh telnet" : " transport input ssh");
        lines.add("exit");
        return lines;
    }

    public List<String> packetTracerHardwareNoteCliLines() {
        return List.of(
                "! OBSERVACAO — Equipamento no Cisco Packet Tracer (padrao deste laboratorio):",
                "! Roteadores: Cisco " + PACKET_TRACER_ROUTER_MODEL,
                "! Switches: Cisco " + PACKET_TRACER_SWITCH_MODEL,
                "! Ajuste apenas se o enunciado da disciplina indicar outro modelo."
        );
    }

    public String packetTracerHardwareNotePlainBlock() {
        return "OBSERVACAO — Equipamento no Cisco Packet Tracer\n"
                + "  Padrao deste laboratorio: roteadores Cisco " + PACKET_TRACER_ROUTER_MODEL
                + " e switches Cisco " + PACKET_TRACER_SWITCH_MODEL + ".\n"
                + "  Os comandos CLI assumem interfaces típicas desses modelos "
                + "(ex.: GigabitEthernet0/0; seriais Serial0/3/0 e 0/3/1, depois 0/2, 0/1 e 0/0).\n"
                + "\n";
    }

    public List<String> scenarioRoutingExportLines(NetworkScenarioResult scenario) {
        RoutingPlan plan = scenario.getRoutingPlan();
        String mode = plan != null && plan.getRoutingMode() != null
                ? plan.getRoutingMode() : scenario.getRoutingMode();
        List<String> lines = new ArrayList<>();
        lines.add("Modo de roteamento:  " + (plan != null ? plan.getRoutingLabel() : mode));
        lines.add("AS EIGRP:             " + scenario.getEigrpAs());
        lines.add("Processo OSPF:        " + scenario.getOspfProcess());
        lines.add("Acesso remoto (VTY):  " + scenario.getRemoteAccess().toUpperCase(Locale.ROOT));
        if (plan != null && plan.getEigrpLocationNames() != null && !plan.getEigrpLocationNames().isEmpty()) {
            lines.add("Sites EIGRP:          " + String.join(", ", plan.getEigrpLocationNames()));
        }
        if (plan != null && plan.getOspfLocationNames() != null && !plan.getOspfLocationNames().isEmpty()) {
            lines.add("Sites OSPF:           " + String.join(", ", plan.getOspfLocationNames()));
        }
        if (plan != null && plan.getBoundaryLocationNames() != null && !plan.getBoundaryLocationNames().isEmpty()) {
            lines.add("Fronteira (redistr.): " + String.join(", ", plan.getBoundaryLocationNames()));
        }
        return lines;
    }

    public List<String> scenarioValidationHintLines(NetworkScenarioResult scenario) {
        RoutingPlan plan = scenario.getRoutingPlan();
        String mode = plan != null ? plan.getRoutingMode() : "eigrp_only";
        List<String> hints = new ArrayList<>();
        hints.add("show ip interface brief");
        if ("eigrp_only".equals(mode) || "dual_split".equals(mode)) {
            hints.add("show ip eigrp neighbors | show ip route eigrp " + scenario.getEigrpAs());
        }
        if ("ospf_only".equals(mode) || "dual_split".equals(mode)) {
            hints.add("show ip ospf neighbor | show ip route ospf");
        }
        if ("dual_split".equals(mode)) {
            hints.add("show ip route (rotas redistribuidas entre EIGRP e OSPF)");
        }
        hints.add("ping entre PCs de filiais em dominios distintos (se houver split)");
        return hints;
    }

    /**
     * Portas seriais de um Cisco 2911 com quatro HWIC-2T, na ordem em que o laboratorio as usa.
     *
     * <p>A HWIC-2T tem as portas 0 e 1; "Serial0/3/2" em diante nao existe (auditoria CALC-18). A ordem
     * comeca no slot 3, que e o que o Packet Tracer do laboratorio traz montado.</p>
     */
    private static final List<String> SERIAIS_DO_2911 = List.of(
            "Serial0/3/0", "Serial0/3/1", "Serial0/2/0", "Serial0/2/1",
            "Serial0/1/0", "Serial0/1/1", "Serial0/0/0", "Serial0/0/1");

    /**
     * Nome da n-esima serial do roteador do laboratorio.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o script e a tabela do Packet Tracer citam a mesma porta, e uma porta
     * que existe no 2911.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> ate oito seriais, todas reais (quatro HWIC-2T); da nona em diante o
     * nome segue o slot 3 so para nao sumir, e o script avisa que faltam modulos.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nao lanca; indice negativo cai na primeira porta.</p>
     */
    public static String serialDoLaboratorio(int indice) {
        if (indice < 0) {
            return SERIAIS_DO_2911.get(0);
        }
        return indice < SERIAIS_DO_2911.size() ? SERIAIS_DO_2911.get(indice) : "Serial0/3/" + indice;
    }

    /**
     * Nome de arquivo que ainda não foi usado no mesmo ZIP.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> "Filial 1" e "Filial-1" (ou dois alunos "Lucas Silva") viram o mesmo
     * identificador; o segundo arquivo derrubava o ZIP inteiro com "duplicate entry" (auditoria CALC-19).</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> o primeiro fica com o nome original; os seguintes ganham "_2", "_3"...
     * antes da extensão; o nome devolvido entra em {@code usados}.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; nome sem extensão recebe o sufixo no fim.</p>
     */
    public static String nomeUnico(String nome, java.util.Set<String> usados) {
        if (usados.add(nome)) {
            return nome;
        }
        int ponto = nome.lastIndexOf('.');
        String raiz = ponto > 0 ? nome.substring(0, ponto) : nome;
        String extensao = ponto > 0 ? nome.substring(ponto) : "";
        for (int n = 2; ; n++) {
            String candidato = raiz + "_" + n + extensao;
            if (usados.add(candidato)) {
                return candidato;
            }
        }
    }

    public String routerExportFilename(String locationName) {
        return "R-" + normalizationService.normalizeCliIdentifier(locationName, "ROTEADOR") + ".txt";
    }

    public List<PtRouterTable> buildPtRouterTables(NetworkScenarioResult scenario) {
        List<LanBlock> lanBlocks = scenario.getLanBlocks();
        List<WanLink> wanLinks = scenario.getWanLinks();
        Map<String, LanBlock> locationsByKey = new LinkedHashMap<>();
        for (LanBlock item : lanBlocks) {
            locationsByKey.put(item.getLocationKey(), item);
        }
        List<PtRouterTable> tables = new ArrayList<>();
        for (LanBlock location : lanBlocks) {
            PtRouterTable table = new PtRouterTable();
            table.setRouterName(location.getRouterName());
            table.setLocationName(location.getLocationName());
            List<PtRouterTable.PtRouterRow> rows = new ArrayList<>();
            rows.add(row("GigabitEthernet0/0", location.getGateway(), location.getNetmask(),
                    "/" + location.getPrefix(), "LAN", "Gateway da LAN " + location.getLocationName()));
            int serialIdx = 0;
            for (WanLink link : wanLinks) {
                if (!link.getIps().containsKey(location.getLocationKey())) {
                    continue;
                }
                String endpointA = link.getEndpoints().get(0);
                String endpointB = link.getEndpoints().get(1);
                String neighborKey = endpointA.equals(location.getLocationKey()) ? endpointB : endpointA;
                LanBlock neighbor = locationsByKey.get(neighborKey);
                String neighborName = neighbor != null ? neighbor.getLocationName() : neighborKey;
                rows.add(row(serialDoLaboratorio(serialIdx), link.getIps().get(location.getLocationKey()),
                        link.getNetmask(), "/" + link.getPrefix(), "WAN",
                        link.getName() + " → vizinho " + neighborName));
                serialIdx++;
            }
            rows.add(row("pool LAN_" + location.getCliId(), location.getGateway(), location.getNetmask(),
                    "/" + location.getPrefix(), "DHCP",
                    "Serviço DHCP neste roteador: rede do pool " + location.getNetwork() + " "
                            + location.getNetmask() + "; default-router = IP de Gi0/0 (linha acima). "
                            + "Packet Tracer: Gi0/0 tem de estar com esse IP e up/up antes dos PCs "
                            + "obterem endereço por DHCP."));
            table.setRows(rows);
            tables.add(table);
        }
        return tables;
    }

    public Map<String, String> generateRouterLabBlocks(NetworkScenarioResult scenario) {
        List<LanBlock> lanBlocks = scenario.getLanBlocks();
        List<WanLink> wanLinks = scenario.getWanLinks();
        int eigrpAsI = clampAs(scenario.getEigrpAs());
        int ospfProcessI = clampAs(scenario.getOspfProcess());
        String remoteAccess = normalizeRemoteAccessExport(scenario.getRemoteAccess());
        RoutingPlan routingPlan = scenario.getRoutingPlan();
        Map<String, String> routerRoles = routingPlan != null ? routingPlan.getRouterRoles() : Map.of();
        Map<String, LanBlock> locationsByKey = new LinkedHashMap<>();
        for (LanBlock item : lanBlocks) {
            locationsByKey.put(item.getLocationKey(), item);
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        for (LanBlock location : lanBlocks) {
            // Sem prefixo: o IOS recusa "ip dhcp excluded-address 192.168.10.17/32 ..." (CALC-04), e a
            // reserva vem da folga real da LAN (CALC-16) — com o gateway + 9 fixo, um /29 de 5 hosts
            // ficava sem nenhum endereço para entregar. Dentro da LAN não há transbordo de endereço.
            IPv4Address gatewayIp = ipv4Kernel.parseNetwork(location.getGateway() + "/32", "Gateway")
                    .withoutPrefixLength();
            int reservados = ipv4Kernel.reservaDhcpAposGateway(location.getHostsSupported(), location.getHostsRequired());
            String faixaExcluida = reservados == 0
                    ? gatewayIp.toCanonicalString()
                    : gatewayIp.toCanonicalString() + " " + gatewayIp.increment(reservados).toIPv4().toCanonicalString();
            List<String> blockLines = new ArrayList<>();
            blockLines.addAll(packetTracerHardwareNoteCliLines());
            blockLines.add("!");
            blockLines.add("enable");
            blockLines.add("configure terminal");
            blockLines.add("hostname " + location.getRouterName());
            blockLines.add("no ip domain-lookup");
            blockLines.add("!");
            blockLines.add("! Senhas de laboratorio (ex.: quadro de aula); "
                    + "ajuste se o enunciado pedir outras — nunca em producao");
            blockLines.add("enable password " + LAB_ENABLE_PASSWORD);
            blockLines.add("!");
            blockLines.add("! Ajuste de console para evitar interrupções de log durante colagem");
            blockLines.add("line con 0");
            blockLines.add(" logging synchronous");
            blockLines.add("exit");
            blockLines.add("!");
            blockLines.addAll(remoteAccessCliLines(remoteAccess));
            blockLines.add("!");
            blockLines.add("interface GigabitEthernet0/0");
            blockLines.add(" description LAN_" + location.getCliId());
            blockLines.add(" ip address " + location.getGateway() + " " + location.getNetmask());
            blockLines.add(" no shutdown");
            blockLines.add("!");

            int serialIdx = 0;
            for (WanLink link : wanLinks) {
                if (!link.getIps().containsKey(location.getLocationKey())) {
                    continue;
                }
                String endpointA = link.getEndpoints().get(0);
                String endpointB = link.getEndpoints().get(1);
                String neighborKey = endpointA.equals(location.getLocationKey()) ? endpointB : endpointA;
                LanBlock neighbor = locationsByKey.get(neighborKey);
                String neighborCli = normalizationService.normalizeCliIdentifier(
                        neighbor != null ? neighbor.getLocationName() : neighborKey, "DESTINO");
                List<String> serialLines = new ArrayList<>();
                if (serialIdx >= SERIAIS_DO_2911.size()) {
                    serialLines.add("! este roteador precisa de mais de " + SERIAIS_DO_2911.size()
                            + " seriais: o 2911 com quatro HWIC-2T tem só essas; acrescente modulos");
                }
                serialLines.add("interface " + serialDoLaboratorio(serialIdx));
                serialLines.add(" description LINK_PARA_" + neighborCli);
                serialLines.add(" ip address " + link.getIps().get(location.getLocationKey()) + " " + link.getNetmask());
                serialLines.add(" no shutdown");
                // Uma ponta por ENLACE (a primeira do par) leva o clock rate. Pela posicao da serial no
                // roteador, enlace que nao era o primeiro de nenhum lado ficava sem relogio e enlace que era
                // o primeiro dos dois ganhava dois (auditoria CALC-17).
                if (endpointA.equals(location.getLocationKey())) {
                    serialLines.add("! clock rate nesta ponta do enlace (lado DCE por convencao de aula); "
                            + "no PT use a porta marcada como DCE (senao mova ao vizinho)");
                    serialLines.add(" clock rate " + LAB_SERIAL_CLOCK_BPS);
                }
                serialLines.add("!");
                blockLines.addAll(serialLines);
                serialIdx++;
            }

            List<String[]> networkPairs = eigrpNetworkPairsForRouter(
                    location.getLocationKey(), location, wanLinks);
            String routingRole = routerRoles.getOrDefault(location.getLocationKey(), "eigrp");
            String roleNote = switch (routingRole) {
                case "ospf" -> "dominio OSPF";
                case "boundary" -> "fronteira EIGRP+OSPF (redistribuicao)";
                default -> "dominio EIGRP";
            };

            blockLines.add("! DHCP no roteador (Packet Tracer): Gi0/0 com "
                    + location.getGateway() + " " + location.getNetmask() + " + no shutdown "
                    + "antes dos PCs obterem lease (rede LAN " + location.getNetwork() + "/"
                    + location.getPrefix() + ").");
            blockLines.add("! Configuração de serviço DHCP");
            blockLines.add("ip dhcp excluded-address " + faixaExcluida);
            blockLines.add("ip dhcp pool LAN_" + location.getCliId());
            blockLines.add(" network " + location.getNetwork() + " " + location.getNetmask());
            blockLines.add(" default-router " + location.getGateway());
            blockLines.add(" dns-server 8.8.8.8");
            blockLines.add("!");
            blockLines.add("! Papel de roteamento (automatico): " + roleNote);
            blockLines.add("!");
            blockLines.addAll(routingProtocolCliLines(
                    routingRole, networkPairs, eigrpAsI, ospfProcessI, location.getGateway()));
            blockLines.add("end");
            blockLines.add("write memory");
            blockLines.add("!");
            blockLines.addAll(routingVerifyCliLines(routingRole, eigrpAsI, ospfProcessI));
            blocks.put(location.getLocationName(), String.join("\n", blockLines).strip());
        }
        return blocks;
    }

    public String generatePacketTracerScript(NetworkScenarioResult scenario) {
        requireExportScenario(scenario);
        List<LanBlock> lanBlocks = scenario.getLanBlocks();
        List<String> lines = new ArrayList<>();
        lines.add("!");
        lines.add("! SCRIPT DE PROVISIONAMENTO - FRAMEWORK DE REDES ANALISE DIDATICA AVANCADA");
        lines.add("!");
        lines.addAll(packetTracerHardwareNoteCliLines());
        lines.add("!");
        lines.add("! Rede base: " + scenario.getBaseNetwork());
        lines.add("! Topologia WAN: " + scenario.getTopologyType().toUpperCase(Locale.ROOT));
        lines.add("! Prefixo WAN: /" + scenario.getWanPrefix());
        lines.add("! AS EIGRP: " + scenario.getEigrpAs());
        lines.add("! OSPF process: " + scenario.getOspfProcess());
        lines.add("! Roteamento: " + (scenario.getRoutingPlan() != null
                ? scenario.getRoutingPlan().getRoutingLabel() : "EIGRP"));
        lines.add("! Acesso remoto VTY: " + scenario.getRemoteAccess().toUpperCase(Locale.ROOT));
        lines.add("! Total de localidades: " + scenario.getTotalLocations());
        lines.add("!");
        lines.add("! MODO DE USO:");
        lines.add("! 1) Abra o CLI do roteador alvo no Packet Tracer.");
        lines.add("! 2) Cole somente o bloco correspondente a esse roteador.");
        lines.add("! 3) Repita para todos os roteadores do cenário.");
        lines.add("!");

        Map<String, String> routerBlocks = generateRouterLabBlocks(scenario);
        for (LanBlock location : lanBlocks) {
            lines.add("!" + "=".repeat(78));
            lines.add("! ROTEADOR: " + location.getLocationName().toUpperCase(Locale.ROOT));
            lines.add("!" + "=".repeat(78));
            lines.add(routerBlocks.get(location.getLocationName()));
            lines.add("!");
        }
        telemetriaLogger.logEvent("info", "resolucaoProblemas", "problem_export_txt",
                Map.of("status", "ok", "locations", scenario.getTotalLocations()));
        return String.join("\n", lines).strip() + "\n";
    }

    public String generatePacketTracerMontagemGuide(NetworkScenarioResult scenario) {
        List<LanBlock> lanBlocks = requireExportScenario(scenario);
        Map<String, LanBlock> locationsByKey = new LinkedHashMap<>();
        for (LanBlock item : lanBlocks) {
            locationsByKey.put(item.getLocationKey(), item);
        }
        List<String> lines = new ArrayList<>();
        lines.add("=".repeat(78));
        lines.add("GUIA DE MONTAGEM NO CISCO PACKET TRACER");
        lines.add("=".repeat(78));
        lines.add("");
        lines.add(packetTracerHardwareNotePlainBlock());
        lines.add("IMPORTANTE — Como este pacote configura a rede:");
        lines.add("  O Cisco Packet Tracer NAO le um unico arquivo que aplique tudo sozinho. "
                + "Os arquivos .txt deste pacote sao comandos IOS para colar no CLI de cada roteador (bloco a bloco). "
                + "Depois disso, roteamento (EIGRP e/ou OSPF), redistribuicao na fronteira e DHCP "
                + "passam a funcionar conforme o cenario calculado.");
        lines.add("");
        lines.add("O QUE HA NESTE PACOTE (arquivos)");
        lines.add("-".repeat(78));
        lines.add("  1) GUIA_MONTAGEM_PACKET_TRACER.txt (este guia) — leia primeiro.");
        lines.add("  2) README_LAB.txt — instrucoes curtas.");
        lines.add("  3) LAB_TOPOLOGY.mermaid — diagrama logico (topologia WAN + LAN).");
        lines.add("  4) config_packet_tracer_consolidado.txt — todos os scripts CLI num unico arquivo.");
        lines.add("  5) configs_individuais/R-*.txt — um arquivo por roteador.");
        lines.add("");
        lines.add("RESUMO DO CENARIO CALCULADO");
        lines.add("-".repeat(78));
        lines.add("  Rede base:       " + scenario.getBaseNetwork());
        lines.add("  Topologia WAN:   " + scenario.getTopologyType().toUpperCase(Locale.ROOT));
        lines.add("  Prefixo WAN:     /" + scenario.getWanPrefix());
        lines.add("  Localidades:     " + scenario.getTotalLocations());
        scenarioRoutingExportLines(scenario).forEach(l -> lines.add("  " + l));
        lines.add("");
        lines.add("GATEWAYS LAN (Gi0/0) — Packet Tracer / DHCP");
        lines.add("-".repeat(78));
        for (LanBlock loc : lanBlocks) {
            lines.add("  " + loc.getLocationName() + ": " + loc.getGateway() + " " + loc.getNetmask()
                    + "  (rede " + loc.getNetwork() + "/" + loc.getPrefix() + ")");
        }
        lines.add("");
        lines.add("PASSOS DA SEQUENCIA (idem pagina web)");
        lines.add("-".repeat(78));
        int stepIdx = 1;
        for (String step : scenario.getPacketTracerSteps()) {
            lines.add("  " + stepIdx++ + ". " + step);
        }
        lines.add("");
        lines.add("LINKS WAN (referencia)");
        lines.add("-".repeat(78));
        if (scenario.getWanLinks().isEmpty()) {
            lines.add("  (nenhum link WAN neste cenario)");
        } else {
            for (WanLink link : scenario.getWanLinks()) {
                String ep0 = link.getEndpoints().get(0);
                String ep1 = link.getEndpoints().get(1);
                LanBlock n0 = locationsByKey.get(ep0);
                LanBlock n1 = locationsByKey.get(ep1);
                lines.add("  " + link.getName() + ": " + link.getNetwork() + "/" + link.getPrefix() + " | "
                        + link.getIps().get(ep0) + " <-> " + link.getIps().get(ep1)
                        + " (" + (n0 != null ? n0.getLocationName() : ep0) + " <-> "
                        + (n1 != null ? n1.getLocationName() : ep1) + ")");
            }
        }
        lines.add("");
        lines.add("INTERFACES E IPs POR ROTEADOR (para configurar no PT)");
        lines.add("-".repeat(78));
        for (PtRouterTable block : buildPtRouterTables(scenario)) {
            lines.add(block.getRouterName() + " — " + block.getLocationName());
            for (PtRouterTable.PtRouterRow row : block.getRows()) {
                lines.add(String.format("  %-22s %-16s %-16s %-8s %-6s %s",
                        row.getInterface(), row.getIp(), row.getMask(), row.getCidr(), row.getRole(), row.getDescription()));
            }
            lines.add("");
        }
        lines.add("=".repeat(78));
        lines.add("FIM DO GUIA");
        lines.add("=".repeat(78));
        lines.add("");
        return String.join("\n", lines).strip() + "\n";
    }

    public String generateEntregaRelatorioTxt(NetworkScenarioResult scenario) {
        List<LanBlock> lanBlocks = requireExportScenario(scenario);
        List<String> lines = new ArrayList<>();
        lines.add("=".repeat(78));
        lines.add("DOCUMENTACAO DO CENARIO DE REDE — EXPORTACAO AUTOMATICA");
        lines.add("=".repeat(78));
        lines.add("Gerado em: " + org.framework.net.shared.FusoDoSite.formato("yyyy-MM-dd HH:mm:ss").format(java.time.Instant.now())
                + " (" + org.framework.net.shared.FusoDoSite.ROTULO + ")");
        lines.add("");
        lines.add(packetTracerHardwareNotePlainBlock());
        lines.add("1) RESUMO DO PLANEJAMENTO");
        lines.add("-".repeat(78));
        lines.add("Rede base:           " + scenario.getBaseNetwork());
        lines.add("Hosts solicitados:   " + scenario.getTotalHostsRequested());
        lines.add("Hosts suportados:    " + scenario.getTotalHostsSupported());
        lines.add("Eficiencia geral:    " + scenario.getOverallEfficiencyPct() + "%");
        lines.add("Uso da rede base:    " + scenario.getUsedAddressPct() + "% (livre: "
                + scenario.getFreeAddressPct() + "%)");
        lines.add("Endereco livre:      " + scenario.getFreeAddresses());
        lines.add("Prefixo base atual:  /" + scenario.getBaseNetworkPrefix());
        lines.add("Prefixo sugerido:    /" + scenario.getSuggestedBasePrefix());
        lines.add("Localidades:         " + scenario.getTotalLocations());
        lines.add("Topologia WAN:       " + scenario.getTopologyType().toUpperCase(Locale.ROOT));
        lines.add("Prefixo WAN:         /" + scenario.getWanPrefix());
        lines.add("AS EIGRP:            " + scenario.getEigrpAs());
        lines.add("Processo OSPF:       " + scenario.getOspfProcess());
        lines.add("Acesso remoto:       " + scenario.getRemoteAccess().toUpperCase(Locale.ROOT));
        lines.add("");
        lines.add("1.1) Roteamento (EIGRP / OSPF / fronteira)");
        lines.add("-".repeat(78));
        scenarioRoutingExportLines(scenario).forEach(lines::add);
        lines.add("");
        lines.add("2) LANs (VLSM)");
        lines.add("-".repeat(78));
        for (LanBlock lan : lanBlocks) {
            lines.add(String.format("%-18s %-22s %-18s %-14s %-14s %-8s",
                    lan.getLocationName(), lan.getNetwork() + "/" + lan.getPrefix(),
                    lan.getNetmask(), lan.getWildcard(), lan.getGateway(), lan.getEfficiencyPct()));
            lines.add("    Faixa de hosts: " + lan.getHostRangeStart() + " - " + lan.getHostRangeEnd());
            lines.add("    Necessario / Suportado: " + lan.getHostsRequired() + " / " + lan.getHostsSupported());
            lines.add("");
        }
        lines.add("2.1) EXPLICACAO DIDATICA DO CALCULO VLSM");
        lines.add("-".repeat(78));
        for (LanBlock lan : lanBlocks) {
            lines.add(lan.getLocationName() + ": " + lan.getHostsRequired() + " hosts -> /"
                    + lan.getCalculatedPrefix());
            lines.add("    Necessarios c/ rede+broadcast: " + lan.getHostsNeededTotal());
            lines.add("    Bits de host: " + lan.getHostBits());
            lines.add("    Alocacao: " + lan.getNetwork() + "/" + lan.getPrefix() + " ("
                    + lan.getHostsSupported() + " hosts suportados)");
            lines.add("    Eficiencia: " + lan.getEfficiencyPct() + "%");
            lines.add("");
        }
        var top = scenario.getTopologyInsights();
        lines.add("2.2) COMPARACAO DE TOPOLOGIA WAN");
        lines.add("-".repeat(78));
        if (top != null) {
            lines.add("Recomendacao: " + top.getRecommended().toUpperCase(Locale.ROOT));
            lines.add("Justificativa: " + top.getRecommendedReason());
            lines.add("Selecionada pelo usuario: " + scenario.getTopologyType().toUpperCase(Locale.ROOT));
            lines.add("Nota: " + top.getSelectedNote());
            lines.add("Checkpoint FIAP (2 roteadores com >=3 seriais): "
                    + (top.isFiapCheckpointSerialOk() ? "SIM" : "NAO") + " — " + top.getFiapCheckpointNote());
            lines.add("");
            lines.add("Star -> links: " + top.getStarLinks() + " | custo: " + top.getStarCost());
            lines.add("Estrela estendida -> links: " + top.getExtendedStarLinks()
                    + " | custo: " + top.getExtendedStarCost());
            lines.add("Mesh -> links: " + top.getMeshLinks() + " | custo: " + top.getMeshCost());
        }
        lines.add("");
        lines.add("2.3) PROJECAO DE CRESCIMENTO");
        lines.add("-".repeat(78));
        for (var item : scenario.getGrowthForecast()) {
            lines.add(item.getLocationName() + ": atual /" + item.getCurrentPrefix());
            for (var g : item.getScenarios()) {
                lines.add("    +" + g.getFactorLabel() + " -> " + g.getFutureHosts() + " hosts, /"
                        + g.getRequiredPrefix() + " (" + (g.isFitsCurrent() ? "OK" : "AJUSTAR") + ")");
            }
            lines.add("");
        }
        lines.add("3) Links WAN");
        lines.add("-".repeat(78));
        if (scenario.getWanLinks().isEmpty()) {
            lines.add("(nenhum link WAN neste cenario)");
        } else {
            for (WanLink link : scenario.getWanLinks()) {
                String a = link.getEndpoints().get(0);
                String b = link.getEndpoints().get(1);
                lines.add(link.getName() + ": " + link.getNetwork() + "/" + link.getPrefix()
                        + " (mascara " + link.getNetmask() + ")");
                lines.add("    " + a + " -> " + link.getIps().get(a));
                lines.add("    " + b + " -> " + link.getIps().get(b));
                lines.add("");
            }
        }
        lines.add("3.1) Interfaces por roteador (Packet Tracer)");
        lines.add("-".repeat(78));
        for (PtRouterTable block : buildPtRouterTables(scenario)) {
            lines.add(block.getRouterName() + " — " + block.getLocationName());
            for (PtRouterTable.PtRouterRow row : block.getRows()) {
                lines.add(String.format("%-22s %-16s %-16s %-8s %-6s %s",
                        row.getInterface(), row.getIp(), row.getMask(), row.getCidr(), row.getRole(), row.getDescription()));
            }
            lines.add("");
        }
        lines.add("4) Topologia Mermaid");
        lines.add("-".repeat(78));
        lines.add(scenario.getTopologyMermaid() == null ? "" : scenario.getTopologyMermaid().strip());
        lines.add("");
        lines.add("5) Sequencia sugerida no aplicação no Packet Tracer");
        lines.add("-".repeat(78));
        int idx = 1;
        for (String step : scenario.getPacketTracerSteps()) {
            lines.add(idx++ + ". " + step);
        }
        lines.add("");
        lines.add("5.1) Checklist final de validacao");
        lines.add("-".repeat(78));
        for (String item : scenario.getPacketTracerChecklist()) {
            lines.add("[ ] " + item);
        }
        lines.add("");
        lines.add("6) Comandos Cisco CLI por roteador");
        lines.add("-".repeat(78));
        Map<String, String> routerCommands = scenario.getRouterCommands();
        List<String> sortedNames = new ArrayList<>(routerCommands.keySet());
        sortedNames.sort(String::compareToIgnoreCase);
        for (String routerName : sortedNames) {
            lines.add("");
            lines.add("!" + "=".repeat(77));
            lines.add("! ROTEADOR: " + routerName.toUpperCase(Locale.ROOT));
            lines.add("!" + "=".repeat(77));
            lines.add(routerCommands.get(routerName).strip());
            lines.add("");
        }
        lines.add("=".repeat(78));
        lines.add("FIM DO RELATORIO");
        lines.add("=".repeat(78));
        lines.add("");
        return String.join("\n", lines).strip() + "\n";
    }

    private List<String[]> eigrpNetworkPairsForRouter(String locationKey, LanBlock location, List<WanLink> wanLinks) {
        List<String[]> raw = new ArrayList<>();
        raw.add(new String[]{location.getNetwork(), location.getWildcard()});
        for (WanLink link : wanLinks) {
            if (link.getIps().containsKey(locationKey)) {
                raw.add(new String[]{link.getNetwork(), link.getWildcard()});
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> out = new ArrayList<>();
        raw.stream()
                .sorted(Comparator.comparing(a -> a[0]))
                .forEach(pair -> {
                    String key = pair[0] + "|" + pair[1];
                    if (seen.add(key)) {
                        out.add(pair);
                    }
                });
        return out;
    }

    private List<String> routingProtocolCliLines(
            String role, List<String[]> networkPairs, int eigrpAsI, int ospfProcessI, String gatewayIp) {
        boolean useEigrp = "eigrp".equals(role) || "boundary".equals(role);
        boolean useOspf = "ospf".equals(role) || "boundary".equals(role);
        List<String> lines = new ArrayList<>();
        if (useEigrp) {
            lines.add("! Roteamento EIGRP");
            lines.add("router eigrp " + eigrpAsI);
            lines.add(" no auto-summary");
            for (String[] pair : networkPairs) {
                lines.add(" network " + pair[0] + " " + pair[1]);
            }
            lines.add("!");
        }
        if (useOspf) {
            lines.add("! Roteamento OSPF (area 0 — laboratorio)");
            lines.add("router ospf " + ospfProcessI);
            lines.add(" router-id " + gatewayIp);
            for (String[] pair : networkPairs) {
                lines.add(" network " + pair[0] + " " + pair[1] + " area 0");
            }
            lines.add("!");
        }
        if ("boundary".equals(role)) {
            lines.add("! Redistribuicao automatica na fronteira EIGRP/OSPF");
            lines.add("router eigrp " + eigrpAsI);
            lines.add(" redistribute ospf " + ospfProcessI + " metric 10000 100 255 1 1500");
            lines.add("router ospf " + ospfProcessI);
            lines.add(" redistribute eigrp " + eigrpAsI + " subnets");
            lines.add("!");
        }
        return lines;
    }

    private List<String> routingVerifyCliLines(String role, int eigrpAsI, int ospfProcessI) {
        List<String> lines = new ArrayList<>();
        lines.add("! Comandos de verificação (executar após aplicar o bloco):");
        lines.add("! show ip interface brief");
        if ("eigrp".equals(role) || "boundary".equals(role)) {
            lines.add("! show ip eigrp neighbors");
            lines.add("! show ip route eigrp " + eigrpAsI);
        }
        if ("ospf".equals(role) || "boundary".equals(role)) {
            lines.add("! show ip ospf neighbor");
            lines.add("! show ip route ospf");
        }
        lines.add("! show ip route");
        lines.add("! show running-config | section router");
        return lines;
    }

    private PtRouterTable.PtRouterRow row(String iface, String ip, String mask, String cidr, String role, String desc) {
        PtRouterTable.PtRouterRow row = new PtRouterTable.PtRouterRow();
        row.setInterface(iface);
        row.setIp(ip);
        row.setMask(mask);
        row.setCidr(cidr);
        row.setRole(role);
        row.setDescription(desc);
        return row;
    }

    private List<LanBlock> requireExportScenario(NetworkScenarioResult scenario) {
        if (scenario == null) {
            throw new EntradaInvalidaException("Cenário vazio para exportação.");
        }
        List<LanBlock> lanBlocks = scenario.getLanBlocks();
        if (lanBlocks == null || lanBlocks.isEmpty()) {
            throw new EntradaInvalidaException("Não há localidades no cenário para exportação.");
        }
        return lanBlocks;
    }

    private int clampAs(int value) {
        if (value >= 1 && value <= 65535) {
            return value;
        }
        return 71;
    }
}
