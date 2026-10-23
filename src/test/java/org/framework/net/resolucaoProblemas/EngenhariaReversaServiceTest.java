package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.parsing.EngenhariaReversaService;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.EnlaceReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.InterfaceLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engenharia reversa de configuração Cisco: leitura, auditoria e correção.
 *
 * <p><b>Propósito de negócio:</b> o caso de referência é o cenário real que o
 * professor entregou com erro — três roteadores em eBGP, com {@code ip adress}
 * escrito errado, máscara de cinco octetos no anúncio e duas interfaces com o
 * endereço trocado. O valor da aba está em apontar isso e consertar com base em
 * evidência; este teste é o que impede a correção de virar chute.</p>
 *
 * <p><b>Invariantes do domínio:</b> as correções de endereço têm de sair do
 * cruzamento das declarações {@code neighbor} e não da ordem em que os roteadores
 * aparecem no texto — por isso o cenário também é interpretado na ordem invertida,
 * exigindo resultado idêntico.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> a asserção nomeia o roteador, a
 * interface e o valor esperado.</p>
 */
@QuarkusTest
@DisplayName("Resolução: engenharia reversa de configuração Cisco")
class EngenhariaReversaServiceTest {

    /** Cenário do professor, com os erros exatamente como foram entregues. */
    private static final String SCRIPT_SP = """
            ena
            conf t
            hostname SP
            int g0/0
            ip adress 172.19.0.1 255.255.240.0
            no shut
            int s0/3/0
            ip adress 200.200.200.1 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.10 255.255.255.252
            no shut
            router bgp 37510
            neighbor 200.200.200.2 remote-as 48880
            neighbor 200.200.200.9 remote-as 65480
            network 172.19.0.0 mask 255.255.255.240.0
            """;

    private static final String SCRIPT_RJ = """
            ena
            conf t
            hostname RJ
            int g0/0
            ip adress 172.19.16.1 255.255.248.0
            no shut
            int s0/3/0
            ip adress 200.200.200.5 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.6 255.255.255.252
            no shut
            router bgp 48880
            neighbor 200.200.200.6 remote-as 65480
            neighbor 200.200.200.1 remote-as 37510
            network 172.19.16.0 mask 255.255.255.248.0
            """;

    private static final String SCRIPT_MG = """
            ena
            conf t
            hostname MG
            int g0/0
            ip adress 172.19.24.1 255.255.252.0
            no shut
            int s0/3/0
            ip adress 200.200.200.9 255.255.255.252
            clock rate 64000
            no shut
            int s0/3/1
            ip adress 200.200.200.10 255.255.255.252
            no shut
            router bgp 65480
            neighbor 200.200.200.10 remote-as 37510
            neighbor 200.200.200.5 remote-as 48880
            network 172.19.24.0 mask 255.255.255.252.0
            """;

    private static final String CENARIO = SCRIPT_SP + "\n-------------\n" + SCRIPT_RJ
            + "\n-------------\n" + SCRIPT_MG;

    @Inject
    EngenhariaReversaService servico;

    @Test
    @DisplayName("lê os três roteadores, com AS e interfaces")
    void leOsTresRoteadores() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        assertEquals(3, cenario.roteadores().size());
        assertEquals(List.of("SP", "RJ", "MG"),
                cenario.roteadores().stream().map(RoteadorLido::hostname).toList());
        assertEquals(37510, roteador(cenario, "SP").asBgp());
        assertEquals(48880, roteador(cenario, "RJ").asBgp());
        assertEquals(65480, roteador(cenario, "MG").asBgp());
        assertEquals("GigabitEthernet0/0", roteador(cenario, "SP").interfaces().get(0).nome(),
                "O nome abreviado g0/0 precisa sair na forma canônica no script corrigido.");
    }

    @Test
    @DisplayName("corrige \"ip adress\" nas nove ocorrências")
    void corrigeErroDeDigitacaoDoComando() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        long sintaxe = cenario.achados().stream()
                .filter(a -> "Sintaxe".equals(a.categoria()))
                .filter(a -> a.severidade() == AchadoConfiguracao.Severidade.ERRO_CORRIGIDO)
                .count();
        assertEquals(9, sintaxe, "Três interfaces por roteador, nos três scripts.");
        assertTrue(cenario.achados().stream()
                        .anyMatch(a -> a.corrigido().startsWith("ip address 172.19.0.1")),
                "A correção precisa mostrar a linha já corrigida.");
    }

    @Test
    @DisplayName("corrige a máscara de cinco octetos usando a interface conectada como evidência")
    void corrigeMascaraDeCincoOctetos() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        List<AchadoConfiguracao> mascaras = cenario.achados().stream()
                .filter(a -> "Máscara".equals(a.categoria()))
                .toList();
        assertEquals(3, mascaras.size(), "Um anúncio malformado por roteador.");
        assertTrue(mascaras.stream().allMatch(AchadoConfiguracao::temCorrecao));
        assertTrue(mascaras.stream().allMatch(AchadoConfiguracao::temEvidencia),
                "Correção de máscara sem evidência seria palpite.");

        AchadoConfiguracao sp = mascaras.stream()
                .filter(a -> "SP".equals(a.roteador())).findFirst().orElseThrow();
        assertEquals("network 172.19.0.0 mask 255.255.240.0", sp.corrigido());
        assertTrue(sp.evidencia().contains("GigabitEthernet0/0"),
                "A evidência é a interface cuja sub-rede é exatamente a rede anunciada.");
    }

    @Test
    @DisplayName("corrige os endereços trocados de RJ e MG pelo cruzamento das declarações de vizinho")
    void corrigeEnderecosPeloCruzamento() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        assertEquals("200.200.200.2", ip(cenario, "RJ", "Serial0/3/1"),
                "SP declara neighbor .2 remote-as 48880, que é o AS de RJ.");
        assertEquals("200.200.200.6", ip(cenario, "MG", "Serial0/3/1"),
                "RJ declara neighbor .6 remote-as 65480, que é o AS de MG.");
        assertEquals("200.200.200.5", ip(cenario, "RJ", "Serial0/3/0"),
                "Este endereço é corroborado por MG e não pode ser mexido.");
        assertEquals("200.200.200.1", ip(cenario, "SP", "Serial0/3/0"),
                "SP estava correto e não pode sofrer alteração.");

        AchadoConfiguracao correcaoRj = cenario.achados().stream()
                .filter(a -> "Endereçamento".equals(a.categoria()) && "RJ".equals(a.roteador()))
                .findFirst().orElseThrow();
        assertTrue(correcaoRj.evidencia().contains("SP declara"));
        assertTrue(correcaoRj.descricao().contains("mesma sub-rede"),
                "A descrição precisa dizer também o que estava errado no valor antigo.");
    }

    @Test
    @DisplayName("depois das correções o cenário fecha: três enlaces, três LANs, nenhum erro pendente")
    void cenarioFechaConsistente() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        assertEquals(0, cenario.resumo().errosSemCorrecao(),
                () -> "Sobrou erro sem correção: " + cenario
                        .achadosDe(AchadoConfiguracao.Severidade.ERRO_SEM_CORRECAO));
        assertTrue(cenario.pendencias().isEmpty(), "Os três AS foram colados; nada deveria faltar.");
        assertEquals(3, cenario.enlaces().size());
        assertTrue(cenario.enlaces().stream().allMatch(EnlaceReconstruido::completo));
        assertEquals(3, cenario.lans().size());
        assertTrue(cenario.resumo().cenarioConsistente());
    }

    @Test
    @DisplayName("cada enlace serial fica com exatamente um DCE — a confirmação de que a correção é a certa")
    void cadaEnlaceTemUmUnicoDce() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        for (EnlaceReconstruido enlace : cenario.enlaces()) {
            assertFalse("não definido".equals(enlace.ladoDce()),
                    "Enlace " + enlace.cidr() + " ficou sem DCE.");
        }
        assertTrue(cenario.achados().stream()
                        .noneMatch(a -> "Camada física".equals(a.categoria())),
                "Com os endereços corretos, o clock rate cai uma única vez por enlace.");
    }

    @Test
    @DisplayName("regressão: o resultado não pode depender da ordem dos scripts colados")
    void resultadoIndependeDaOrdem() {
        CenarioReconstruido direto = servico.interpretar(CENARIO);
        CenarioReconstruido invertido = servico.interpretar(
                SCRIPT_MG + "\n----\n" + SCRIPT_RJ + "\n----\n" + SCRIPT_SP);

        assertEquals(ip(direto, "RJ", "Serial0/3/1"), ip(invertido, "RJ", "Serial0/3/1"));
        assertEquals(ip(direto, "MG", "Serial0/3/1"), ip(invertido, "MG", "Serial0/3/1"));
        assertEquals(direto.resumo().errosCorrigidos(), invertido.resumo().errosCorrigidos());
        assertTrue(invertido.resumo().cenarioConsistente());
    }

    @Test
    @DisplayName("script corrigido sai pronto para colar, com a marca do que mudou")
    void scriptCorrigidoTrazAMarcaDaCorrecao() {
        CenarioReconstruido cenario = servico.interpretar(CENARIO);

        String rj = cenario.scripts().stream()
                .filter(s -> "RJ".equals(s.roteador())).findFirst().orElseThrow().conteudo();
        assertTrue(rj.contains("ip address 200.200.200.2 255.255.255.252"));
        assertTrue(rj.contains("! CORRIGIDO:"),
                "Script corrigido sem marca vira configuração aplicada às cegas.");
        assertTrue(rj.contains("network 172.19.16.0 mask 255.255.248.0"));

        // O erro de digitação SOBREVIVE dentro do comentário "! CORRIGIDO: era ..." — é
        // ali que ele deve estar. O que não pode é sobreviver numa linha executável.
        List<String> executaveis = rj.lines().map(String::strip)
                .filter(l -> !l.startsWith("!")).toList();
        assertTrue(executaveis.stream().noneMatch(l -> l.contains("adress")),
                "Nenhum comando aplicado no equipamento pode carregar o erro de digitação.");
        assertTrue(rj.lines().anyMatch(l -> l.contains("! CORRIGIDO:") && l.contains("adress")),
                "O comentário precisa preservar o texto original para o usuário conferir.");
    }

    @Test
    @DisplayName("um script só: reconstrói o que dá e declara o que falta, sem inventar")
    void scriptSolitarioGeraPendencia() {
        CenarioReconstruido cenario = servico.interpretar(SCRIPT_SP);

        assertEquals(1, cenario.roteadores().size());
        assertEquals(2, cenario.pendencias().size(),
                "Os dois AS vizinhos não foram colados e precisam aparecer como pendência.");
        assertTrue(cenario.pendencias().stream().anyMatch(p -> p.descricao().contains("48880")));
        assertTrue(cenario.pendencias().stream().anyMatch(p -> p.descricao().contains("65480")));
        assertFalse(cenario.resumo().cenarioConsistente());
        assertTrue(cenario.enlaces().stream().noneMatch(EnlaceReconstruido::completo),
                "Sem o outro script, nenhum enlace pode ser dado como fechado.");
    }

    @Test
    @DisplayName("linha desconhecida aparece na lista, nunca é engolida")
    void linhaDesconhecidaFicaVisivel() {
        CenarioReconstruido cenario = servico.interpretar(
                SCRIPT_SP + "\nfaz a rede funcionar por favor\n");

        assertTrue(cenario.linhasNaoInterpretadas().stream()
                        .anyMatch(l -> l.conteudo().contains("faz a rede funcionar")),
                "Parser que ignora linha em silêncio desenha errado com cara de certo.");
    }

    @Test
    @DisplayName("texto vazio devolve cenário vazio, não erro")
    void textoVazioNaoQuebra() {
        CenarioReconstruido cenario = servico.interpretar("   ");

        assertTrue(cenario.vazio());
        assertFalse(cenario.resumo().cenarioConsistente());
        assertEquals("", cenario.mermaid());
    }

    private RoteadorLido roteador(CenarioReconstruido cenario, String hostname) {
        return cenario.roteadores().stream()
                .filter(r -> hostname.equals(r.hostname())).findFirst().orElseThrow();
    }

    private String ip(CenarioReconstruido cenario, String hostname, String nomeInterface) {
        return roteador(cenario, hostname).interfaces().stream()
                .filter(i -> nomeInterface.equals(i.nome()))
                .map(InterfaceLida::ip)
                .findFirst().orElseThrow();
    }

    // ---- Correções do parecer (C02 e C03) ----

    @Test
    @DisplayName("C02: o diagrama mostra TODAS as LANs do roteador, não só a primeira")
    void diagramaMostraTodasAsLans() {
        String config = """
                hostname R1
                int g0/0
                 ip address 10.1.0.1 255.255.255.0
                 no shutdown
                int g0/1
                 ip address 10.2.0.1 255.255.255.0
                 no shutdown
                """;
        CenarioReconstruido cenario = servico.interpretar(config);
        String mermaid = cenario.mermaid();
        assertTrue(mermaid.contains("LAN 10.1.0.0/24"),
                "A primeira LAN deveria aparecer no diagrama:\n" + mermaid);
        assertTrue(mermaid.contains("LAN 10.2.0.0/24"),
                "A SEGUNDA LAN também deveria aparecer (antes omitida por findFirst):\n" + mermaid);
    }

    @Test
    @DisplayName("C03: reconstrução preserva o clock rate original e não força no shutdown")
    void reconstrucaoPreservaClockRateENaoForcaNoShutdown() {
        String config = """
                hostname R9
                int s0/0/0
                 ip address 192.168.9.1 255.255.255.252
                 clock rate 128000
                """;
        CenarioReconstruido cenario = servico.interpretar(config);
        String script = cenario.scripts().stream()
                .map(CenarioReconstruido.ScriptCorrigido::conteudo)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(script.contains("clock rate 128000"),
                "O clock rate original (128000) deveria ser preservado:\n" + script);
        assertFalse(script.contains("clock rate 64000"),
                "Não pode reinventar 64000 quando o original era 128000:\n" + script);
        assertFalse(script.contains("no shutdown"),
                "A interface não tinha 'no shutdown'; a reconstrução fiel não deve forçá-lo:\n" + script);
    }

    // ---- Fidelidade do estado administrativo: shutdown / no shutdown / ausente ----

    private String reconstruir(String config) {
        return servico.interpretar(config).scripts().stream()
                .map(CenarioReconstruido.ScriptCorrigido::conteudo)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("estado admin: shutdown explícito é preservado na reconstrução")
    void reconstrucaoPreservaShutdownExplicito() {
        String script = reconstruir("""
                hostname R1
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.0
                 shutdown
                """);
        assertTrue(script.lines().anyMatch(l -> l.strip().equals("shutdown")),
                "shutdown explícito deve voltar como 'shutdown':\n" + script);
        assertTrue(script.lines().noneMatch(l -> l.strip().equals("no shutdown")),
                "shutdown explícito não pode virar 'no shutdown':\n" + script);
    }

    @Test
    @DisplayName("estado admin: no shutdown explícito é preservado")
    void reconstrucaoPreservaNoShutdown() {
        String script = reconstruir("""
                hostname R1
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.0
                 no shutdown
                """);
        assertTrue(script.lines().anyMatch(l -> l.strip().equals("no shutdown")),
                "no shutdown explícito deve voltar como 'no shutdown':\n" + script);
        assertTrue(script.lines().noneMatch(l -> l.strip().equals("shutdown")),
                "não pode aparecer um 'shutdown' que não existia:\n" + script);
    }

    /**
     * Auditoria CALC-05: A declara "neighbor 10.0.0.3", que é o BROADCAST do /30 do próprio A. Antes, B
     * (10.0.0.2, certo) era "corrigido" para 10.0.0.3. Agora B não é tocado e o erro é apontado na linha de A.
     * Fronteira (A1): o mesmo cenário com o vizinho certo (.2) não gera achado de rede/broadcast.
     */
    @Test
    @DisplayName("vizinho no broadcast do /30: aponta a linha de quem declarou, não corrige o outro")
    void vizinhoNoBroadcastNaoViraCorrecaoDoOutro() {
        String a = """
                hostname A
                interface Serial0/0/0
                 ip address 10.0.0.1 255.255.255.252
                router bgp 65001
                 neighbor 10.0.0.3 remote-as 65002
                """;
        String b = """
                hostname B
                interface Serial0/0/0
                 ip address 10.0.0.2 255.255.255.252
                router bgp 65002
                 neighbor 10.0.0.1 remote-as 65001
                """;
        CenarioReconstruido cenario = servico.interpretar(a + "\n-------------\n" + b);
        assertEquals("10.0.0.2", ip(cenario, "B", "Serial0/0/0"), "B estava certo e não pode virar o broadcast");
        assertTrue(cenario.achados().stream().anyMatch(x -> "A".equals(x.roteador())
                        && x.descricao().contains("broadcast")),
                () -> "o erro tem de ser apontado em A: " + cenario.achados());
        assertTrue(cenario.achados().stream().noneMatch(x -> "B".equals(x.roteador())
                        && "Endereçamento".equals(x.categoria())),
                () -> "B não tem nada a corrigir: " + cenario.achados());

        CenarioReconstruido certo = servico.interpretar(a.replace("neighbor 10.0.0.3", "neighbor 10.0.0.2")
                + "\n-------------\n" + b);
        assertTrue(certo.achados().stream().noneMatch(x -> x.descricao().contains("broadcast")
                        || x.descricao().contains("endereço de rede")),
                () -> "vizinho certo não pode ser acusado: " + certo.achados());
    }

    /** Auditoria CALC-11: o script reconstruído devolve o que o desenho não usa, em vez de engolir. */
    @Test
    @DisplayName("reconstrução preserva router-id, version, secondary, native e a distância da rota")
    void reconstrucaoNaoEngoleLinhas() {
        String script = reconstruir("""
                hostname R1
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.0
                 ip address 10.0.1.1 255.255.255.0 secondary
                 no shutdown
                interface GigabitEthernet0/1.1
                 encapsulation dot1Q 1 native
                 ip address 10.0.2.1 255.255.255.0
                router ospf 1
                 router-id 1.1.1.1
                 network 10.0.0.0 0.0.255.255 area 0
                router rip
                 version 2
                 network 10.0.0.0
                ip route 0.0.0.0 0.0.0.0 10.0.0.254 200
                """);
        assertTrue(script.contains(" ip address 10.0.0.1 255.255.255.0"), "o primário não pode virar o secundário:\n" + script);
        assertTrue(script.contains(" ip address 10.0.1.1 255.255.255.0 secondary"), script);
        assertTrue(script.contains(" encapsulation dot1Q 1 native"), script);
        assertTrue(script.contains(" router-id 1.1.1.1"), script);
        assertTrue(script.contains(" version 2"), script);
        assertTrue(script.contains("ip route 0.0.0.0 0.0.0.0 10.0.0.254 200"), "rota flutuante virava principal:\n" + script);
    }

    /**
     * Auditoria CALC-12/CALC-14/CONT-33 — gabarito (A3): IOS (network do OSPF/EIGRP casa o IP da interface
     * com rede + curinga; RIP e EIGRP sem curinga usam a classful), Loopback nasce up, "ip add" é abreviação
     * aceita, iBGP é válido e o auto-summary do BGP nasce desligado desde o 12.3. Fronteira (A1): um
     * network de OSPF que não casa com nenhuma interface continua acusado.
     */
    @Test
    @DisplayName("auditoria não acusa o que o IOS aceita, e continua acusando o network inútil")
    void auditoriaSemFalsosPositivos() {
        CenarioReconstruido c = servico.interpretar("""
                hostname R1
                interface GigabitEthernet0/0
                 ip add 10.1.2.1 255.255.255.0
                 no shutdown
                interface Loopback0
                 ip address 1.1.1.1 255.255.255.255
                router ospf 1
                 network 10.0.0.0 0.255.255.255 area 0
                 network 172.16.0.0 0.0.255.255 area 0
                router eigrp 100
                 network 10.0.0.0
                router bgp 65001
                 network 10.1.2.0 mask 255.255.255.0
                """);
        String todos = c.achados().toString();
        assertTrue(c.achados().stream().noneMatch(a -> "Sintaxe".equals(a.categoria())), "\"ip add\" é válido: " + todos);
        assertTrue(c.achados().stream().noneMatch(a -> a.descricao().contains("Loopback0")), todos);
        assertTrue(c.achados().stream().noneMatch(a -> a.descricao().contains("auto-summary")), todos);
        assertTrue(c.achados().stream().noneMatch(a -> "Roteamento".equals(a.categoria())
                        && a.descricao().contains("10.0.0.0")),
                "10.0.0.0 0.255.255.255 (OSPF) e 10.0.0.0 (EIGRP, classful) ligam a Gi0/0 (10.1.2.1): " + todos);
        assertTrue(c.achados().stream().anyMatch(a -> "Roteamento".equals(a.categoria())
                        && a.descricao().contains("172.16.0.0")),
                "network que não casa com nenhuma interface continua acusado: " + todos);

        CenarioReconstruido ibgp = servico.interpretar("""
                hostname A
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.0
                 no shutdown
                router bgp 65001
                 neighbor 10.0.0.2 remote-as 65001
                -------------
                hostname B
                interface GigabitEthernet0/0
                 ip address 10.0.0.2 255.255.255.0
                 no shutdown
                router bgp 65001
                 neighbor 10.0.0.1 remote-as 65001
                """);
        assertTrue(ibgp.achados().stream()
                        .allMatch(a -> a.severidade() == AchadoConfiguracao.Severidade.AVISO),
                () -> "iBGP não é erro, nem gera 'faltam endereços' em quem está certo: " + ibgp.achados());
        assertTrue(ibgp.achados().stream().anyMatch(a -> a.descricao().contains("iBGP")),
                () -> "o AS repetido continua sinalizado como aviso: " + ibgp.achados());
    }

    /**
     * Auditoria CALC-13 — gabarito (A3): o IOS recusa endereço de rede/broadcast na interface ("Bad mask")
     * e sub-redes sobrepostas no mesmo roteador, mesmo com máscaras diferentes; OSPF não forma adjacência
     * com máscara divergente no enlace. Fronteira (A1): /31 usa os dois endereços (RFC 3021) e sub-redes
     * vizinhas que não se sobrepõem passam.
     */
    @Test
    @DisplayName("acusa IP de rede/broadcast, sobreposição com máscaras diferentes e máscara divergente no enlace")
    void enderecamentoQueOIosRecusa() {
        CenarioReconstruido c = servico.interpretar("""
                hostname A
                interface GigabitEthernet0/0
                 ip address 10.0.0.0 255.255.255.0
                 no shutdown
                interface GigabitEthernet0/1
                 ip address 10.0.0.129 255.255.255.128
                 no shutdown
                interface GigabitEthernet0/2
                 ip address 10.9.9.255 255.255.255.0
                 no shutdown
                interface Serial0/0/0
                 ip address 10.1.1.1 255.255.255.252
                 clock rate 64000
                 no shutdown
                -------------
                hostname B
                interface Serial0/0/0
                 ip address 10.1.1.2 255.255.255.248
                 no shutdown
                """);
        String todos = c.achados().toString();
        assertTrue(c.achados().stream().anyMatch(a -> a.descricao().contains("10.0.0.0 é o endereço de rede")), todos);
        assertTrue(c.achados().stream().anyMatch(a -> a.descricao().contains("10.9.9.255 é o broadcast")), todos);
        assertTrue(c.achados().stream().anyMatch(a -> a.descricao().contains("mesma sub-rede")
                && a.descricao().contains("GigabitEthernet0/1")), "10.0.0.0/24 contém 10.0.0.128/25: " + todos);
        assertTrue(c.achados().stream().anyMatch(a -> a.descricao().contains("máscaras diferentes")), todos);

        CenarioReconstruido ok = servico.interpretar("""
                hostname A
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.128
                 no shutdown
                interface GigabitEthernet0/1
                 ip address 10.0.0.129 255.255.255.128
                 no shutdown
                interface Serial0/0/0
                 ip address 10.1.1.0 255.255.255.254
                 clock rate 64000
                 no shutdown
                -------------
                hostname B
                interface Serial0/0/0
                 ip address 10.1.1.1 255.255.255.254
                 no shutdown
                """);
        assertTrue(ok.achados().stream().noneMatch(a -> "Endereçamento".equals(a.categoria())),
                () -> "nada a acusar no endereçamento: " + ok.achados());
    }

    /** Auditoria CALC-39 — RFC 6793/5396: AS de 4 bytes (asplain e asdot) não vira 0. */
    @Test
    @DisplayName("AS de 4 bytes é lido, cruzado e devolvido no script")
    void asDeQuatroBytes() {
        CenarioReconstruido c = servico.interpretar("""
                hostname A
                interface Serial0/0/0
                 ip address 10.0.0.1 255.255.255.252
                 clock rate 64000
                 no shutdown
                router bgp 4200000001
                 neighbor 10.0.0.2 remote-as 64086.59906
                -------------
                hostname B
                interface Serial0/0/0
                 ip address 10.0.0.2 255.255.255.252
                 no shutdown
                router bgp 4200000002
                 neighbor 10.0.0.1 remote-as 4200000001
                """);
        assertEquals(4200000001L, roteador(c, "A").asBgp());
        assertEquals(4200000002L, roteador(c, "B").asBgp());
        // A declara B em asdot (64086.59906 = 64086 × 65536 + 59906 = 4200000002): o cruzamento casa.
        assertTrue(c.achados().stream().noneMatch(x -> "Endereçamento".equals(x.categoria())),
                () -> "o asdot tem de apontar para B: " + c.achados());
        String script = c.scripts().stream().map(CenarioReconstruido.ScriptCorrigido::conteudo)
                .reduce("", (x, y) -> x + "\n" + y);
        assertTrue(script.contains("router bgp 4200000001"), script);
        assertTrue(c.pendencias().isEmpty(), () -> "os dois AS foram colados: " + c.pendencias());
    }

    @Test
    @DisplayName("estado admin: comando ausente não inventa shutdown nem no shutdown")
    void reconstrucaoPreservaAusencia() {
        String script = reconstruir("""
                hostname R1
                interface GigabitEthernet0/0
                 ip address 10.0.0.1 255.255.255.0
                """);
        assertTrue(script.lines().noneMatch(l -> l.strip().equals("shutdown")),
                "ausência de comando não pode virar 'shutdown':\n" + script);
        assertTrue(script.lines().noneMatch(l -> l.strip().equals("no shutdown")),
                "ausência de comando não pode virar 'no shutdown':\n" + script);
    }
}
