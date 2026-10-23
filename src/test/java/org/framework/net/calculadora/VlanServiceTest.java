package org.framework.net.calculadora;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.calculadora.application.VlanService;
import org.framework.net.calculadora.domain.PlanoVlan;
import org.framework.net.calculadora.domain.VlanEntry;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VlanServiceTest {

    @Inject
    VlanService vlanService;

    @Test
    void planoSequencialUsaBlocosConsecutivos() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "4", "ADM,VENDAS,TI,VOZ", "sequencial");

        assertEquals(4, plano.totalVlans());
        assertEquals(256L, plano.capacidadeMaxima());
        assertEquals(254L, plano.hostsPorVlan());
        assertEquals("10,20,30,40", plano.trunkAllowed());

        assertEquals("192.168.0.0/24", plano.vlans().get(0).bloco().cidr());
        assertEquals("192.168.1.0/24", plano.vlans().get(1).bloco().cidr());
        assertEquals("192.168.3.0/24", plano.vlans().get(3).bloco().cidr());
        assertEquals("ADM", plano.vlans().get(0).nome());
        assertEquals("VOZ", plano.vlans().get(3).nome());
    }

    @Test
    void gatewayEhOPrimeiroHostEPoolComecaDepois() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "1", "", "sequencial");
        VlanEntry vlan = plano.vlans().get(0);

        assertEquals("192.168.0.1", vlan.gateway());
        assertEquals("192.168.0.2", vlan.dhcpInicio());
        assertEquals("192.168.0.254", vlan.dhcpFim());
    }

    @Test
    void estrategiaPorOctetoColocaOVlanIdNoTerceiroOcteto() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "3", "", "octeto");

        assertEquals("192.168.10.0/24", plano.vlans().get(0).bloco().cidr());
        assertEquals("192.168.20.0/24", plano.vlans().get(1).bloco().cidr());
        assertEquals("192.168.30.0/24", plano.vlans().get(2).bloco().cidr());
        assertEquals("192.168.10.1", plano.vlans().get(0).gateway());
    }

    @Test
    void estrategiaPorOctetoExigeBlocoBarra16() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("192.168.0.0/24", "", "24", "10", "10", "2", "", "octeto"));
        assertTrue(erro.getMessage().contains("/16 ou maior"));
    }

    @Test
    void estrategiaPorOctetoExigePrefixoBarra24() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("192.168.0.0/16", "", "25", "10", "10", "2", "", "octeto"));
        assertTrue(erro.getMessage().contains("só fecha com /24"));
    }

    @Test
    void sequenciaQueAtingeVlanReservadaCiscoEhRecusada() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("10.0.0.0/8", "", "24", "1000", "1", "5", "", "sequencial"));
        assertTrue(erro.getMessage().contains("1002"));
        assertTrue(erro.getMessage().contains("Cisco"));
    }

    @Test
    void quantidadeAcimaDaCapacidadeDoBlocoEhRecusada() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("192.168.0.0/24", "", "26", "10", "10", "8", "", "sequencial"));
        assertTrue(erro.getMessage().contains("comporta 4 sub-rede"));
    }

    @Test
    void prefixoSemEspacoParaGatewayEhRecusado() {
        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("192.168.0.0/16", "", "31", "10", "10", "2", "", "sequencial"));
        assertTrue(erro.getMessage().contains("gateway"));
    }

    @Test
    void nomeDeVlanEhSanitizadoParaOQueOIosAceita() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "1", "<script>alert(1)</script>", "sequencial");
        String nome = plano.vlans().get(0).nome();

        assertFalse(nome.contains("<"));
        assertFalse(nome.contains(">"));
        assertTrue(nome.length() <= 32);
    }

    @Test
    void comandosCiscoTrazemVlanSviTrunkEDhcp() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "2", "ADM,TI", "sequencial");
        String cli = String.join("\n", plano.comandosCisco());

        assertTrue(cli.contains("vlan 10"));
        assertTrue(cli.contains(" name ADM"));
        assertTrue(cli.contains("switchport trunk allowed vlan 10,20"));
        assertTrue(cli.contains("interface Vlan10"));
        assertTrue(cli.contains("encapsulation dot1Q 20"));
        assertTrue(cli.contains("ip dhcp pool POOL_10"));
        assertTrue(cli.contains("default-router 192.168.0.1"));
    }

    @Test
    void vlan1EntraComAvisoDeDefaultVlan() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "1", "1", "2", "", "sequencial");
        assertTrue(plano.avisos().stream().anyMatch(a -> a.contains("default VLAN")));
    }

    @Test
    void validaFaixasDoVlanId() {
        assertEquals("Normal range", vlanService.validarIdIsolado("10").faixa());
        assertTrue(vlanService.validarIdIsolado("10").utilizavel());

        assertEquals("Default VLAN", vlanService.validarIdIsolado("1").faixa());

        assertEquals("Reservado Cisco", vlanService.validarIdIsolado("1002").faixa());
        assertFalse(vlanService.validarIdIsolado("1002").utilizavel());

        assertEquals("Extended range", vlanService.validarIdIsolado("2000").faixa());
        assertTrue(vlanService.validarIdIsolado("2000").utilizavel());

        assertEquals("Reservado 802.1Q", vlanService.validarIdIsolado("0").faixa());
        assertFalse(vlanService.validarIdIsolado("4095").utilizavel());
    }

    @Test
    @DisplayName("regressão: cada VLAN recebe um intervalo de portas PRÓPRIO")
    void portasDeAcessoNaoSeSobrepoem() {
        // Antes, o script repetia "interface range FastEthernet0/1 - 8" para toda
        // VLAN: colar no switch jogava todas as portas na ÚLTIMA VLAN.
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "3", "ADM,TI,VOZ", "sequencial");

        List<String> ranges = plano.comandosCisco().stream()
                .filter(l -> l.startsWith("interface range FastEthernet") || l.startsWith("interface FastEthernet"))
                .toList();

        assertEquals(3, ranges.size(), "Uma linha de portas por VLAN: " + ranges);
        assertEquals(3, Set.copyOf(ranges).size(), "Intervalos repetidos: " + ranges);
    }

    @Test
    @DisplayName("regressão: nomes de VLAN nunca colidem")
    void nomesDeVlanSaoUnicos() {
        // "VLAN_20" informado para a 1ª VLAN colide com o default da 2ª.
        PlanoVlan colisaoComDefault = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "2", "VLAN_20", "sequencial");
        List<String> nomes = colisaoComDefault.vlans().stream().map(v -> v.nome()).toList();
        assertEquals(2, Set.copyOf(nomes).size(), "Nomes duplicados: " + nomes);

        // Dois nomes de 33 caracteres com prefixo comum colidiam ao truncar em 32.
        PlanoVlan colisaoPorTruncamento = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "2",
                "REDE_ADMINISTRATIVA_DO_PREDIO_001,REDE_ADMINISTRATIVA_DO_PREDIO_002", "sequencial");
        List<String> truncados = colisaoPorTruncamento.vlans().stream().map(v -> v.nome()).toList();
        assertEquals(2, Set.copyOf(truncados).size(), "Nomes duplicados: " + truncados);
        truncados.forEach(n -> assertTrue(n.length() <= 32, "Nome acima de 32: " + n));
    }

    @Test
    @DisplayName("regressão: capacidade da estratégia octeto é 255 (VLAN 0 é reservada), não a do bloco")
    void capacidadeDaEstrategiaOcteto() {
        PlanoVlan plano = vlanService.gerarPlano(
                "10.0.0.0/8", "", "24", "10", "10", "3", "", "octeto");
        // CONT-34: o 3º octeto tem 256 valores, mas o VLAN ID 0 é reservado pelo 802.1Q (1–255).
        assertEquals(255L, plano.capacidadeMaxima(),
                "Com bloco /8 a fórmula sequencial daria 65536, mas o 3º octeto só comporta os IDs 1–255");

        CalculadoraException erro = assertThrows(CalculadoraException.class,
                () -> vlanService.gerarPlano("10.0.0.0/8", "", "24", "250", "10", "3", "", "octeto"));
        assertTrue(erro.getMessage().contains("até 255"), erro.getMessage());
    }

    @Test
    @DisplayName("regressão: VLAN 1 não recebe comando de criação nem de nome")
    void vlan1NaoEhRecriada() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "1", "1", "2", "", "sequencial");
        String cli = String.join("\n", plano.comandosCisco());

        assertFalse(cli.contains("\nvlan 1\n"), "O IOS recusa recriar a VLAN 1:\n" + cli);
        assertTrue(cli.contains("VLAN 1 é a default"), "Deveria explicar por que a VLAN 1 é pulada");
        assertTrue(cli.contains("vlan 2"), "A VLAN 2 deveria continuar sendo criada");
    }

    /** CONT-34: o bloco /8 comporta 65.536 sub-redes /24, mas só existem 4.090 VLAN IDs atribuíveis. */
    @Test
    void capacidadeSequencialNaoPassaDosVlanIdsAtribuiveis() {
        PlanoVlan grande = vlanService.gerarPlano(
                "10.0.0.0/8", "", "24", "10", "10", "3", "", "sequencial");
        assertEquals(4090L, grande.capacidadeMaxima());
        PlanoVlan pequeno = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "10", "10", "3", "", "sequencial");
        assertEquals(256L, pequeno.capacidadeMaxima(), "Abaixo do teto de IDs vale a conta do bloco");
    }

    /** CALC-07/CONT-21: o script tem de rotear colado como está, nas duas opções. */
    @Test
    void scriptRoteiaNasDuasOpcoes() {
        PlanoVlan plano = vlanService.gerarPlano(
                "192.168.0.0/16", "", "24", "1", "9", "2", "", "sequencial");
        List<String> cli = plano.comandosCisco();
        String texto = String.join("\n", cli);

        int ipRouting = cli.indexOf("ip routing");
        assertTrue(ipRouting >= 0 && ipRouting < cli.indexOf("interface Vlan1"),
                "ip routing tem de vir antes das SVIs:\n" + texto);
        int fisica = cli.indexOf("interface GigabitEthernet0/0");
        assertTrue(fisica >= 0 && " no shutdown".equals(cli.get(fisica + 1)),
                "a interface física do router-on-a-stick precisa de no shutdown:\n" + texto);
        assertTrue(cli.contains(" encapsulation dot1Q 1 native"), "VLAN 1 é a native do tronco:\n" + texto);
        assertTrue(cli.contains(" encapsulation dot1Q 10"), "VLAN com tag continua sem native:\n" + texto);
        assertFalse(cli.contains(" encapsulation dot1Q 10 native"));
    }

    @Test
    void vlanIdForaDaFaixaEhRecusado() {
        assertThrows(CalculadoraException.class, () -> vlanService.validarIdIsolado("4096"));
        assertThrows(CalculadoraException.class, () -> vlanService.validarIdIsolado("abc"));
    }
}
