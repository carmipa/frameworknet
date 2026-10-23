package org.framework.net.calculadora.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.calculadora.config.CalculadoraConfig;
import org.framework.net.calculadora.domain.BlocoIpv4;
import org.framework.net.calculadora.domain.PlanoVlan;
import org.framework.net.calculadora.domain.SubnetKernel;
import org.framework.net.calculadora.domain.VlanEntry;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plano de VLANs: mapeia VLAN ID ↔ sub-rede e gera a configuração Cisco.
 *
 * <p><b>Propósito de negócio:</b> segmentar um bloco IPv4 em VLANs é o exercício
 * de laboratório mais cobrado depois de sub-redes, e é onde o aluno erra: usa
 * VLAN reservada, esquece o SVI ou monta um trunk que não permite as VLANs
 * criadas. Este serviço entrega o mapa completo e o script pronto.</p>
 *
 * <p><b>Invariantes do domínio:</b> VLAN IDs 0 e 4095 são reservados pelo IEEE
 * 802.1Q e nunca entram no plano; 1002–1005 são reservados pela Cisco (FDDI e
 * Token Ring legados) e também são recusados; o gateway de cada VLAN é sempre o
 * primeiro host utilizável do bloco; o número de VLANs nunca excede a
 * capacidade do bloco base no prefixo escolhido; a lista do
 * {@code switchport trunk allowed vlan} contém exatamente as VLANs geradas; o
 * script roteia de fato nas duas opções ({@code ip routing} no switch L3,
 * {@code no shutdown} na interface física do router-on-a-stick, VLAN 1 como
 * {@code native}); a capacidade exibida nunca passa dos VLAN IDs atribuíveis.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> VLAN ID reservado, prefixo que não
 * comporta gateway mais pool (/31 e /32), quantidade acima da capacidade do
 * bloco ou acima do teto configurado lançam {@link CalculadoraException} com
 * mensagem didática. Nenhum método devolve {@code null}.</p>
 */
@ApplicationScoped
public class VlanService {

    /** Reservados pelo IEEE 802.1Q: 0 é prioridade-only, 4095 é implementation-use. */
    private static final Set<Integer> RESERVADOS_802_1Q = Set.of(0, 4095);

    /** Reservadas pela Cisco para FDDI e Token Ring legados. */
    private static final int LEGADO_CISCO_INICIO = 1002;
    private static final int LEGADO_CISCO_FIM = 1005;

    /** Acima deste ID começa a extended range, que exige VTP transparente em switches antigos. */
    private static final int NORMAL_RANGE_FIM = 1001;

    private static final int VLAN_ID_MAX = 4094;
    private static final int MAX_NOME = 32;

    /** Densidade do switch de acesso assumida ao repartir portas no script gerado. */
    private static final int PORTAS_DO_SWITCH = 24;

    /** Maior VLAN ID que cabe no terceiro octeto — teto da convenção por octeto. */
    private static final int MAX_VLAN_NO_OCTETO = 255;

    /** VLAN IDs atribuíveis: 1–4094 menos as quatro reservadas da Cisco (1002–1005). */
    private static final int VLAN_IDS_ATRIBUIVEIS = VLAN_ID_MAX - (LEGADO_CISCO_FIM - LEGADO_CISCO_INICIO + 1);

    @Inject
    SubnetKernel kernel;

    @Inject
    CalculadoraConfig config;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Gera o plano de VLANs a partir do bloco base.
     *
     * <p><b>Propósito de negócio:</b> dado {@code 192.168.0.0/16}, 4 VLANs a
     * partir da 10 com passo 10 e /24 por VLAN, devolve VLAN 10 → 192.168.0.0/24,
     * VLAN 20 → 192.168.1.0/24 e assim por diante — ou, na estratégia por octeto,
     * VLAN 10 → 192.168.10.0/24, que é a convenção usada em prova.</p>
     *
     * <p><b>Invariantes do domínio:</b> a estratégia {@code octeto} exige bloco
     * base /16 ou menor e prefixo /24 por VLAN, porque só assim o VLAN ID cabe
     * inteiro no terceiro octeto; fora disso o serviço recusa em vez de produzir
     * um mapa que não fecha.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} em
     * qualquer violação de faixa, capacidade ou estratégia.</p>
     */
    public PlanoVlan gerarPlano(
            String blocoTexto,
            String prefixoBaseTexto,
            String prefixoPorVlanTexto,
            String vlanIdInicialTexto,
            String passoVlanIdTexto,
            String quantidadeTexto,
            String nomesTexto,
            String estrategiaTexto) {

        return telemetriaLogger.medir("calculadora", "plano_vlan", () -> gerarPlanoInterno(
                blocoTexto, prefixoBaseTexto, prefixoPorVlanTexto, vlanIdInicialTexto,
                passoVlanIdTexto, quantidadeTexto, nomesTexto, estrategiaTexto));
    }

    private PlanoVlan gerarPlanoInterno(
            String blocoTexto,
            String prefixoBaseTexto,
            String prefixoPorVlanTexto,
            String vlanIdInicialTexto,
            String passoVlanIdTexto,
            String quantidadeTexto,
            String nomesTexto,
            String estrategiaTexto) {

        Integer prefixoPadrao = (prefixoBaseTexto == null || prefixoBaseTexto.isBlank())
                ? null : kernel.parsePrefixo(prefixoBaseTexto, "Prefixo do bloco base");
        SubnetKernel.Rede base = kernel.parseRede(blocoTexto, prefixoPadrao, "Bloco base");

        String estrategia = normalizarEstrategia(estrategiaTexto);
        int prefixoVlan = (prefixoPorVlanTexto == null || prefixoPorVlanTexto.isBlank())
                ? 24 : kernel.parsePrefixo(prefixoPorVlanTexto, "Prefixo por VLAN");

        if (prefixoVlan > 30) {
            throw new CalculadoraException(
                    "Um /" + prefixoVlan + " não comporta gateway mais pool DHCP. "
                            + "Use no máximo /30 por VLAN (2 hosts úteis).");
        }
        if (prefixoVlan < base.prefixo()) {
            throw new CalculadoraException(
                    "O prefixo por VLAN (/" + prefixoVlan + ") não pode ser maior que o bloco base (/"
                            + base.prefixo() + "). Cada VLAN é um pedaço do bloco.");
        }

        // A estratégia é validada antes da capacidade: com bloco e prefixo incompatíveis,
        // o erro útil é "essa convenção não se aplica", não "não cabem N VLANs".
        validarEstrategiaOcteto(base, prefixoVlan, estrategia);

        int vlanInicial = lerVlanId(vlanIdInicialTexto, "VLAN ID inicial");
        int passo = lerPasso(passoVlanIdTexto);
        int quantidade = lerQuantidade(quantidadeTexto);

        // Na convenção por octeto o teto não é o do bloco: a VLAN precisa caber no
        // terceiro octeto, e o 0 é reservado pelo 802.1Q — sobram os IDs 1 a 255
        // (auditoria CONT-34: exibia "de 256 possíveis").
        boolean porOcteto = "octeto".equals(estrategia);
        long capacidade = porOcteto
                ? MAX_VLAN_NO_OCTETO
                : 1L << (prefixoVlan - base.prefixo());
        if (porOcteto) {
            long ultimoId = (long) vlanInicial + (long) (quantidade - 1) * passo;
            if (ultimoId > MAX_VLAN_NO_OCTETO) {
                throw new CalculadoraException(
                        "A convenção \"VLAN ID no 3º octeto\" só alcança VLAN IDs até "
                                + MAX_VLAN_NO_OCTETO + ", e a sequência chegaria à VLAN " + ultimoId
                                + ". Reduza a quantidade, o passo ou o ID inicial, "
                                + "ou use a estratégia sequencial.");
            }
        }
        if (quantidade > capacidade) {
            throw new CalculadoraException(
                    "O bloco " + kernel.formatIpv4(base.endereco()) + "/" + base.prefixo()
                            + " comporta " + capacidade + " sub-rede(s) /" + prefixoVlan
                            + ", mas foram pedidas " + quantidade + " VLANs. "
                            + "Reduza a quantidade ou use um prefixo por VLAN maior.");
        }

        List<String> nomes = lerNomes(nomesTexto);
        List<String> avisos = new ArrayList<>();
        List<VlanEntry> vlans = new ArrayList<>(quantidade);
        Set<Integer> idsUsados = new LinkedHashSet<>();
        Set<String> nomesUsados = new LinkedHashSet<>();

        long passoEnderecos = kernel.totalIps(prefixoVlan);

        for (int i = 0; i < quantidade; i++) {
            long idLongo = (long) vlanInicial + (long) i * passo;
            if (idLongo > VLAN_ID_MAX) {
                throw new CalculadoraException(
                        "A sequência chegaria à VLAN " + idLongo + ", acima do máximo 4094. "
                                + "Reduza a quantidade, o passo ou o VLAN ID inicial.");
            }
            int vlanId = (int) idLongo;
            validarVlanId(vlanId, avisos);
            idsUsados.add(vlanId);

            long rede = "octeto".equals(estrategia)
                    ? redePorOcteto(base, vlanId)
                    : (base.endereco() + (long) i * passoEnderecos) & SubnetKernel.MASK32;

            BlocoIpv4 bloco = kernel.bloco(i + 1, rede, prefixoVlan);
            String nome = nomeUnico(i < nomes.size() ? nomes.get(i) : "VLAN_" + vlanId,
                    vlanId, nomesUsados);
            long total = kernel.totalIps(prefixoVlan);
            // Gateway ocupa o primeiro host; o pool DHCP começa no seguinte e vai até o último host.
            String gateway = kernel.formatIpv4(rede + 1);
            String dhcpInicio = kernel.formatIpv4(rede + 2);
            String dhcpFim = kernel.formatIpv4(rede + total - 2);

            vlans.add(new VlanEntry(
                    vlanId, nome, faixaDoVlanId(vlanId), bloco,
                    gateway, dhcpInicio, dhcpFim, kernel.hostsUteis(prefixoVlan)));
        }

        String trunkAllowed = idsUsados.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<String> comandos = montarComandosCisco(vlans, trunkAllowed);

        telemetriaLogger.logEvent("info", "calculadora", "vlan_plano_gerado", Map.of(
                "vlans", vlans.size(),
                "prefixoVlan", prefixoVlan,
                "estrategia", estrategia));

        return new PlanoVlan(
                kernel.formatIpv4(base.endereco()),
                base.prefixo(),
                prefixoVlan,
                vlans.size(),
                // O "de N possíveis" da tela: o bloco pode comportar 65.536 sub-redes, mas
                // o 802.1Q só tem 4.090 VLAN IDs atribuíveis (CONT-34).
                Math.min(capacidade, VLAN_IDS_ATRIBUIVEIS),
                kernel.hostsUteis(prefixoVlan),
                estrategia,
                trunkAllowed,
                vlanInicial,
                passo,
                String.join(",", nomes),
                vlans,
                avisos,
                comandos);
    }

    /**
     * Valida um VLAN ID isolado e devolve o parecer didático.
     *
     * <p><b>Propósito de negócio:</b> alimenta o validador da tela — o aluno
     * digita 1002 e descobre na hora por que o switch recusa.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * o texto não for inteiro ou estiver fora de 0–4095; IDs reservados não
     * lançam, devolvem o parecer com {@code utilizavel = false}.</p>
     */
    public ParecerVlanId validarIdIsolado(String texto) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.isEmpty()) {
            throw new CalculadoraException("Informe um VLAN ID de 0 a 4095 para validar.");
        }
        int vlanId;
        try {
            vlanId = org.framework.net.shared.NumeroAscii.parseIntEstrito(valor);
        } catch (NumberFormatException ex) {
            throw new CalculadoraException("VLAN ID deve ser um número inteiro. Recebido: " + valor);
        }
        if (vlanId < 0 || vlanId > 4095) {
            throw new CalculadoraException("VLAN ID deve estar entre 0 e 4095. Recebido: " + vlanId);
        }
        if (RESERVADOS_802_1Q.contains(vlanId)) {
            return new ParecerVlanId(vlanId, false, "Reservado 802.1Q", faixaDoVlanId(vlanId),
                    vlanId == 0
                            ? "VLAN 0 não identifica VLAN alguma: o quadro 802.1Q usa esse valor apenas para "
                            + "transportar prioridade (priority tagged). O switch recusa."
                            : "VLAN 4095 é reservada para uso interno da implementação. O switch recusa.");
        }
        if (vlanId >= LEGADO_CISCO_INICIO && vlanId <= LEGADO_CISCO_FIM) {
            return new ParecerVlanId(vlanId, false, "Reservado Cisco", faixaDoVlanId(vlanId),
                    "1002–1005 são pré-criadas pela Cisco para FDDI e Token Ring. Existem por padrão, "
                            + "não podem ser removidas nem reaproveitadas para dados.");
        }
        if (vlanId == 1) {
            return new ParecerVlanId(vlanId, true, "Default VLAN", faixaDoVlanId(vlanId),
                    "VLAN 1 é a default: existe sempre, não pode ser apagada e é a native VLAN padrão do trunk. "
                            + "Boa prática é não usá-la para dados nem deixá-la como native.");
        }
        if (vlanId > NORMAL_RANGE_FIM) {
            return new ParecerVlanId(vlanId, true, "Extended range", faixaDoVlanId(vlanId),
                    "1006–4094 é a extended range: válida, mas em switches com VTP nas versões 1 e 2 exige "
                            + "modo transparente e não é propagada pelo VTP.");
        }
        return new ParecerVlanId(vlanId, true, "Normal range", faixaDoVlanId(vlanId),
                "2–1001 é a faixa normal para VLANs de dados: propagada por VTP e aceita em qualquer switch.");
    }

    /**
     * Rejeita IDs reservados dentro de uma sequência e acumula avisos para os
     * que são válidos mas exigem cuidado.
     */
    private void validarVlanId(int vlanId, List<String> avisos) {
        if (RESERVADOS_802_1Q.contains(vlanId)) {
            throw new CalculadoraException(
                    "A sequência atingiu a VLAN " + vlanId + ", reservada pelo padrão 802.1Q. "
                            + "Ajuste o VLAN ID inicial ou o passo.");
        }
        if (vlanId >= LEGADO_CISCO_INICIO && vlanId <= LEGADO_CISCO_FIM) {
            throw new CalculadoraException(
                    "A sequência atingiu a VLAN " + vlanId + ", reservada pela Cisco (FDDI/Token Ring, 1002–1005). "
                            + "Ajuste o VLAN ID inicial ou o passo para pular essa faixa.");
        }
        if (vlanId == 1) {
            avisos.add("VLAN 1 entrou no plano: é a default VLAN do switch e a native VLAN padrão do trunk. "
                    + "Boa prática é reservá-la e usar outra faixa para dados.");
        }
        if (vlanId > NORMAL_RANGE_FIM) {
            avisos.add("VLAN " + vlanId + " está na extended range (1006–4094): exige VTP transparente em "
                    + "switches com VTP v1/v2 e não é propagada pelo VTP.");
        }
    }

    /**
     * Confere as pré-condições estruturais da convenção "VLAN ID no 3º octeto".
     *
     * <p><b>Propósito de negócio:</b> roda antes de qualquer conta de capacidade
     * para que o usuário receba o motivo real da recusa — a convenção não se
     * aplica ao bloco escolhido — em vez de um "não cabem N VLANs" que o levaria
     * a mexer no campo errado.</p>
     *
     * <p><b>Invariantes do domínio:</b> a convenção exige prefixo /24 por VLAN e
     * bloco base /16 ou mais curto, porque é a única combinação em que o VLAN ID
     * ocupa o terceiro octeto inteiro.</p>
     */
    private void validarEstrategiaOcteto(SubnetKernel.Rede base, int prefixoVlan, String estrategia) {
        if (!"octeto".equals(estrategia)) {
            return;
        }
        if (prefixoVlan != 24) {
            throw new CalculadoraException(
                    "A convenção \"VLAN ID no 3º octeto\" só fecha com /24 por VLAN. "
                            + "Escolha /24 ou troque a estratégia para sequencial.");
        }
        if (base.prefixo() > 16) {
            throw new CalculadoraException(
                    "A convenção \"VLAN ID no 3º octeto\" exige um bloco base /16 ou maior (ex.: 192.168.0.0/16), "
                            + "porque o terceiro octeto precisa estar livre. Bloco informado: /" + base.prefixo() + ".");
        }
    }

    /**
     * Deriva a rede pela convenção "VLAN ID no terceiro octeto".
     *
     * <p><b>Invariantes do domínio:</b> as pré-condições de bloco e prefixo já
     * foram verificadas por {@link #validarEstrategiaOcteto}; aqui resta o que
     * depende de cada VLAN — um ID acima de 255 não tem terceiro octeto
     * correspondente e é recusado.</p>
     */
    private long redePorOcteto(SubnetKernel.Rede base, int vlanId) {
        if (vlanId > 255) {
            throw new CalculadoraException(
                    "VLAN " + vlanId + " não cabe no terceiro octeto (máximo 255). "
                            + "Use a estratégia sequencial para VLAN IDs acima de 255.");
        }
        long rede = (base.endereco() & kernel.mascara(16)) | ((long) vlanId << 8);
        return rede & SubnetKernel.MASK32;
    }

    private List<String> montarComandosCisco(List<VlanEntry> vlans, String trunkAllowed) {
        List<String> linhas = new ArrayList<>();
        linhas.add("! ===== Switch: criação das VLANs =====");
        for (VlanEntry vlan : vlans) {
            if (vlan.vlanId() == 1) {
                // A VLAN 1 já existe de fábrica: "vlan 1" + "name" é recusado pelo IOS.
                linhas.add("! VLAN 1 é a default e já existe — não se cria nem se renomeia.");
                continue;
            }
            linhas.add("vlan " + vlan.vlanId());
            linhas.add(" name " + vlan.nome());
        }
        linhas.add("!");
        linhas.add("! ===== Switch: portas de acesso =====");
        linhas.add("! Cada VLAN recebe um intervalo PRÓPRIO de portas. Repetir o mesmo");
        linhas.add("! intervalo para todas jogaria o switch inteiro na última VLAN.");
        int portasPorVlan = Math.max(1, PORTAS_DO_SWITCH / Math.max(1, vlans.size()));
        int proximaPorta = 1;
        for (VlanEntry vlan : vlans) {
            int inicio = proximaPorta;
            int fim = inicio + portasPorVlan - 1;
            if (fim > PORTAS_DO_SWITCH) {
                linhas.add("! " + vlan.nome() + " (VLAN " + vlan.vlanId() + "): sem portas livres "
                        + "num switch de " + PORTAS_DO_SWITCH + " portas — defina o intervalo à mão.");
                continue;
            }
            proximaPorta = fim + 1;
            linhas.add(inicio == fim
                    ? "interface FastEthernet0/" + inicio
                    : "interface range FastEthernet0/" + inicio + " - " + fim);
            linhas.add(" description " + vlan.nome());
            linhas.add(" switchport mode access");
            linhas.add(" switchport access vlan " + vlan.vlanId());
        }
        linhas.add("!");
        linhas.add("! ===== Switch: tronco 802.1Q =====");
        linhas.add("interface GigabitEthernet0/1");
        linhas.add(" switchport trunk encapsulation dot1q");
        linhas.add(" switchport mode trunk");
        linhas.add(" switchport trunk allowed vlan " + trunkAllowed);
        linhas.add("!");
        linhas.add("! ===== Roteamento entre VLANs — opção A: SVI em switch L3 =====");
        // Sem "ip routing" o switch L3 responde em cada SVI mas não encaminha entre elas
        // (auditoria CALC-07/CONT-21).
        linhas.add("ip routing");
        for (VlanEntry vlan : vlans) {
            linhas.add("interface Vlan" + vlan.vlanId());
            linhas.add(" ip address " + vlan.gateway() + " " + vlan.bloco().mascara());
            linhas.add(" no shutdown");
        }
        linhas.add("!");
        linhas.add("! ===== Roteamento entre VLANs — opção B: router-on-a-stick =====");
        // A interface física do roteador nasce em shutdown: sem o "no shutdown" nela, nenhuma
        // subinterface sobe. A VLAN 1 é a native do tronco e chega sem tag.
        linhas.add("interface GigabitEthernet0/0");
        linhas.add(" no shutdown");
        for (VlanEntry vlan : vlans) {
            linhas.add("interface GigabitEthernet0/0." + vlan.vlanId());
            linhas.add(" encapsulation dot1Q " + vlan.vlanId() + (vlan.vlanId() == 1 ? " native" : ""));
            linhas.add(" ip address " + vlan.gateway() + " " + vlan.bloco().mascara());
        }
        linhas.add("!");
        linhas.add("! ===== DHCP por VLAN =====");
        for (VlanEntry vlan : vlans) {
            linhas.add("ip dhcp excluded-address " + vlan.bloco().primeiroHost() + " " + vlan.gateway());
            linhas.add("ip dhcp pool POOL_" + vlan.vlanId());
            linhas.add(" network " + vlan.bloco().rede() + " " + vlan.bloco().mascara());
            linhas.add(" default-router " + vlan.gateway());
        }
        linhas.add("!");
        linhas.add("! ===== Wildcard de cada VLAN (ACL / OSPF / EIGRP) =====");
        for (VlanEntry vlan : vlans) {
            linhas.add("! VLAN " + vlan.vlanId() + " -> " + vlan.bloco().rede() + " " + vlan.bloco().wildcard());
        }
        return linhas;
    }

    private String faixaDoVlanId(int vlanId) {
        if (RESERVADOS_802_1Q.contains(vlanId)) {
            return "Reservado 802.1Q";
        }
        if (vlanId == 1) {
            return "Default (1)";
        }
        if (vlanId >= LEGADO_CISCO_INICIO && vlanId <= LEGADO_CISCO_FIM) {
            return "Reservado Cisco (1002–1005)";
        }
        if (vlanId <= NORMAL_RANGE_FIM) {
            return "Normal (2–1001)";
        }
        return "Extended (1006–4094)";
    }

    private String normalizarEstrategia(String texto) {
        String valor = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        return "octeto".equals(valor) ? "octeto" : "sequencial";
    }

    private int lerVlanId(String texto, String nomeCampo) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.isEmpty()) {
            return 10;
        }
        int vlanId;
        try {
            vlanId = org.framework.net.shared.NumeroAscii.parseIntEstrito(valor);
        } catch (NumberFormatException ex) {
            throw new CalculadoraException(nomeCampo + " deve ser um número inteiro de 1 a 4094. Recebido: " + valor);
        }
        if (vlanId < 1 || vlanId > VLAN_ID_MAX) {
            throw new CalculadoraException(
                    nomeCampo + " deve estar entre 1 e 4094 (0 e 4095 são reservados pelo 802.1Q). Recebido: " + vlanId);
        }
        return vlanId;
    }

    private int lerPasso(String texto) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.isEmpty()) {
            return 10;
        }
        int passo;
        try {
            passo = org.framework.net.shared.NumeroAscii.parseIntEstrito(valor);
        } catch (NumberFormatException ex) {
            throw new CalculadoraException("Passo entre VLAN IDs deve ser um número inteiro. Recebido: " + valor);
        }
        if (passo < 1 || passo > 1000) {
            throw new CalculadoraException("Passo entre VLAN IDs deve estar entre 1 e 1000. Recebido: " + passo);
        }
        return passo;
    }

    private int lerQuantidade(String texto) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.isEmpty()) {
            throw new CalculadoraException("Informe quantas VLANs o plano deve gerar.");
        }
        int quantidade;
        try {
            quantidade = org.framework.net.shared.NumeroAscii.parseIntEstrito(valor);
        } catch (NumberFormatException ex) {
            throw new CalculadoraException("Quantidade de VLANs deve ser um número inteiro. Recebido: " + valor);
        }
        int teto = Math.max(1, config.maxVlans());
        if (quantidade < 1) {
            throw new CalculadoraException("Quantidade de VLANs deve ser pelo menos 1.");
        }
        if (quantidade > teto) {
            throw new CalculadoraException(
                    "Quantidade de VLANs acima do limite de exibição (" + teto + "). "
                            + "Gere o plano em lotes menores.");
        }
        return quantidade;
    }

    private List<String> lerNomes(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split("[,;\\n]"))
                .map(bruto -> bruto.strip())
                .filter(nome -> !nome.isEmpty())
                .map(nome -> sanitizarNome(nome))
                .limit(config.maxVlans())
                .toList();
    }

    /**
     * Normaliza o nome da VLAN para o que o IOS aceita.
     *
     * <p><b>Invariantes do domínio:</b> o comando {@code name} não aceita espaço
     * nem caractere de marcação; o nome é limitado a 32 caracteres, que é o
     * máximo do IOS, e caracteres fora de letras, dígitos, {@code _} e {@code -}
     * viram {@code _}. Isso também fecha a porta para injeção de HTML no
     * fragmento renderizado.</p>
     */
    /**
     * Garante que cada VLAN receba um nome distinto.
     *
     * <p><b>Propósito de negócio:</b> o IOS recusa {@code name} repetido com
     * "VLAN name already in use", e o script pararia no meio. A colisão acontece
     * sem má-fé: informar só "VLAN_20" para duas VLANs faz a primeira usar esse
     * nome e a segunda cair no default {@code "VLAN_" + 20} — o mesmo texto. O
     * truncamento em 32 caracteres cria a mesma colisão com nomes longos de
     * prefixo comum.</p>
     *
     * <p><b>Invariantes do domínio:</b> o nome devolvido é único no plano e
     * nunca passa de {@link #MAX_NOME} caracteres. O sufixo desambiguador
     * <b>substitui</b> a cauda do nome em vez de ser acrescentado e truncado
     * depois — anexar e truncar devolveria o mesmo texto para um nome que já
     * ocupa os 32 caracteres, e a busca por um nome livre nunca terminaria.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> se nem as 999 variações forem
     * suficientes (impossível com o teto de VLANs), devolve o nome com o VLAN ID,
     * que é único por construção porque cada VLAN ID aparece uma única vez.</p>
     */
    private String nomeUnico(String desejado, int vlanId, Set<String> usados) {
        if (usados.add(desejado)) {
            return desejado;
        }
        for (int sequencia = 1; sequencia <= 999; sequencia++) {
            String sufixo = "_" + vlanId + (sequencia == 1 ? "" : "_" + sequencia);
            int corte = Math.min(desejado.length(), MAX_NOME - sufixo.length());
            String base = corte > 0 ? desejado.substring(0, corte) : "VLAN";
            String candidato = base + sufixo;
            if (usados.add(candidato)) {
                return candidato;
            }
        }
        return "VLAN_" + vlanId;
    }

    private String sanitizarNome(String bruto) {
        String limpo = bruto.replaceAll("[^A-Za-z0-9_-]", "_");
        if (limpo.length() > MAX_NOME) {
            limpo = limpo.substring(0, MAX_NOME);
        }
        return limpo.isEmpty() ? "VLAN" : limpo;
    }

    /**
     * Parecer sobre um VLAN ID avulso.
     *
     * @param vlanId     o ID consultado
     * @param utilizavel se pode ser criado para dados em um switch Cisco
     * @param faixa      rótulo curto da faixa (normal, extended, reservado…)
     * @param faixaDetalhe rótulo com os limites numéricos da faixa
     * @param explicacao motivo, em linguagem didática
     */
    public record ParecerVlanId(
            int vlanId, boolean utilizavel, String faixa, String faixaDetalhe, String explicacao) {
    }
}
