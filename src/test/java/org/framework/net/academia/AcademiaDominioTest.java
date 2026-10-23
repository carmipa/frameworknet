package org.framework.net.academia;

import org.framework.net.academia.core.application.PortaoAcademia;
import org.framework.net.academia.core.domain.EstadoAcademia;
import org.framework.net.academia.core.domain.ports.VerificacaoArranque;
import org.framework.net.academia.core.infrastructure.LimiteCorpoAcademia;
import org.framework.net.academia.eventos.domain.EventoAcademia.ErroJs;
import org.framework.net.academia.eventos.domain.EventoAcademia.FaixaInteracoes;
import org.framework.net.academia.eventos.domain.EventoAcademia.FaixaTempo;
import org.framework.net.academia.eventos.domain.EventoAcademia.TipoErroJs;
import org.framework.net.academia.eventos.domain.OrcamentoEventos;
import org.framework.net.academia.eventos.domain.SaneadorTexto;
import org.framework.net.academia.trilha.domain.CatalogoTrilha;
import org.framework.net.academia.trilha.domain.Licao;
import org.framework.net.academia.trilha.domain.Nivel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regras da Academia que não precisam do Quarkus de pé.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> provar o portão (a Academia falha sozinha, D12), o catálogo
 * (dono único do que existe), o saneamento (nada pessoal na telemetria, INV-ACAD-005), o orçamento
 * (enxurrada não expulsa evidência) e o teto do corpo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> cada regra tem o caso doente e o legítimo com o mesmo sinal
 * (A1): id de lição bem formado × mal formado; texto com algarismo de dado pessoal × mensagem de
 * erro comum; 8192 × 8193 bytes.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a asserção nomeia a regra e o valor.</p>
 */
@DisplayName("Academia: portão, catálogo, saneamento, orçamento e teto do corpo")
class AcademiaDominioTest {

    // ---------------------------------------------------------------- portão (D12)

    @Test
    @DisplayName("antes do arranque a Academia está fechada, nunca aberta por omissão")
    void fechadaAntesDoArranque() {
        assertEquals(EstadoAcademia.Situacao.DESLIGADA, new PortaoAcademia().estado().situacao());
    }

    @Test
    @DisplayName("chave desligada vence: as verificações nem rodam")
    void chaveDesligadaVence() {
        AtomicLong chamadas = new AtomicLong();
        EstadoAcademia estado = new PortaoAcademia().decidir(false, List.of(verificacao("x", chamadas::incrementAndGet)));
        assertEquals(EstadoAcademia.Situacao.DESLIGADA, estado.situacao());
        assertEquals(0, chamadas.get());
    }

    @Test
    @DisplayName("verificação que falha deixa a Academia degradada com a causa, sem lançar")
    void falhaDegradaComCausa() {
        PortaoAcademia portao = new PortaoAcademia();
        EstadoAcademia estado = portao.decidir(true, List.of(
                verificacao("trilha", () -> { throw new IllegalStateException("lição repetida: x.y"); }),
                verificacao("outra", () -> { throw new LinkageError("sem biblioteca"); })));
        assertEquals(EstadoAcademia.Situacao.DEGRADADA, estado.situacao());
        assertTrue(estado.causa().contains("trilha: IllegalStateException — lição repetida: x.y"), estado.causa());
        assertTrue(estado.causa().contains("outra: LinkageError"), "a segunda falha também aparece");
        assertSame(estado, portao.estado());
    }

    @Test
    @DisplayName("memória esgotada não é engolida pela Academia")
    void erroDaJvmPropaga() {
        assertThrows(OutOfMemoryError.class, () -> new PortaoAcademia().decidir(true,
                List.of(verificacao("x", () -> { throw new OutOfMemoryError("teste"); }))));
    }

    @Test
    @DisplayName("sem falha, pronta; e situação não pronta exige causa")
    void prontaEExigeCausa() {
        assertTrue(new PortaoAcademia().decidir(true, List.of(verificacao("trilha", () -> { }))).pronta());
        assertThrows(IllegalArgumentException.class, () -> EstadoAcademia.degradada(" "));
        assertThrows(IllegalArgumentException.class, () -> EstadoAcademia.desligada(null));
    }

    private static VerificacaoArranque verificacao(String nome, Runnable acao) {
        return new VerificacaoArranque() {
            @Override
            public String nome() {
                return nome;
            }

            @Override
            public void verificar() {
                acao.run();
            }
        };
    }

    // ---------------------------------------------------------------- catálogo

    @Test
    @DisplayName("o catálogo real é válido e as rotas públicas saem dele, na ordem de estudo")
    void catalogoValido() {
        CatalogoTrilha.validar();
        assertEquals(List.of("/academia", "/academia/fundamentos", "/academia/fundamentos/binario",
                "/academia/fundamentos/hexadecimal", "/academia/fundamentos/camadas",
                "/academia/ipv4", "/academia/ipv4/mascara", "/academia/ipv4/subredes",
                "/academia/transporte", "/academia/transporte/aperto", "/academia/transporte/janela"),
                CatalogoTrilha.rotasPublicas());
        assertEquals("ipv4.subredes", CatalogoTrilha.proxima("ipv4.mascara").orElseThrow().id());
        assertEquals("transporte.janela", CatalogoTrilha.proxima("transporte.aperto").orElseThrow().id());
        assertTrue(CatalogoTrilha.proxima("transporte.janela").isEmpty(), "última lição do nível não tem próxima");
        assertTrue(CatalogoTrilha.nivel("enlace").map(n -> !n.aberto()).orElse(false), "enlace segue em breve");
        assertEquals("fundamentos.hexadecimal", CatalogoTrilha.proxima("fundamentos.binario").orElseThrow().id());
        assertTrue(CatalogoTrilha.proxima("fundamentos.camadas").isEmpty(), "última lição do nível não tem próxima");
        assertTrue(CatalogoTrilha.licao("fundamentos.inventada").isEmpty());
        assertTrue(CatalogoTrilha.licao(null).isEmpty());
    }

    @Test
    @DisplayName("A1: id de lição bem formado passa; mal formado, com o mesmo formato aparente, é recusado")
    void idDeLicao() {
        Licao boa = new Licao("fundamentos.binario", "Binário", "resumo", "toggle_on", 10);
        assertEquals("/academia/fundamentos/binario", boa.rota());
        for (String ruim : List.of("Fundamentos.binario", "fundamentos", "fundamentos.", ".binario",
                "fundamentos.binário", "fundamentos.bi/nario", "fundamentos.binario.extra")) {
            assertThrows(IllegalArgumentException.class, () -> new Licao(ruim, "t", "r", "toggle_on", 10), ruim);
        }
        assertThrows(IllegalArgumentException.class, () -> new Licao("a.b", "t", "r", "Toggle On", 10));
    }

    @Test
    @DisplayName("nível aberto sem lição, fechado com lição e lição de outro nível são recusados")
    void nivelCoerente() {
        Licao doOutro = new Licao("ipv4.mascara", "Máscara", "r", "lan", 10);
        assertThrows(IllegalArgumentException.class, () -> new Nivel("fundamentos", "F", "r", "school", true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Nivel("ipv4", "I", "r", "lan", false, List.of(doOutro)));
        assertThrows(IllegalArgumentException.class,
                () -> new Nivel("fundamentos", "F", "r", "school", true, List.of(doOutro)));
    }

    // ---------------------------------------------------------------- saneamento (INV-ACAD-005)

    @Test
    @DisplayName("IP, e-mail e CPF somem do texto; a forma do erro comum continua legível")
    void saneamento() {
        String sujo = SaneadorTexto.sanear("falhou para 8.8.8.8 a@b.com 123.456.789-09 ok");
        assertFalse(sujo.contains("8.8.8.8"), sujo);
        assertFalse(sujo.contains("a@b.com"), sujo);
        assertFalse(sujo.contains("123.456.789-09"), sujo);
        assertFalse(sujo.matches(".*\\d.*"), "nenhum algarismo sobra: " + sujo);
        assertEquals("falhou para #.#.#.# [removido] ###.###.###-## ok", sujo);

        String comum = "Cannot read properties of undefined (reading 'valor')";
        assertEquals(comum, SaneadorTexto.sanear(comum), "A1: mensagem comum passa intacta");
        assertEquals("", SaneadorTexto.sanear(null));
        assertTrue(SaneadorTexto.sanear("x".repeat(5000)).length() <= SaneadorTexto.TETO);
        assertEquals("a b", SaneadorTexto.sanear("a\u0000‮b"));
    }

    @Test
    @DisplayName("o evento de erro sai sempre saneado, mesmo se alguém montá-lo com texto sujo")
    void erroJsNasceSaneado() {
        ErroJs erro = new ErroJs("fundamentos.binario", TipoErroJs.ERRO, "ip 192.168.0.1");
        assertEquals("ip ###.###.#.#", erro.mensagem());
    }

    @Test
    @DisplayName("faixas: fronteiras escritas à mão, valor exato nunca sai")
    void faixas() {
        assertEquals(FaixaTempo.ATE_30S, FaixaTempo.de(-5));
        assertEquals(FaixaTempo.ATE_30S, FaixaTempo.de(30));
        assertEquals(FaixaTempo.ATE_2MIN, FaixaTempo.de(31));
        assertEquals(FaixaTempo.ATE_2MIN, FaixaTempo.de(120));
        assertEquals(FaixaTempo.ATE_10MIN, FaixaTempo.de(121));
        assertEquals(FaixaTempo.ATE_10MIN, FaixaTempo.de(600));
        assertEquals(FaixaTempo.MAIS_10MIN, FaixaTempo.de(601));
        assertEquals(FaixaInteracoes.NENHUMA, FaixaInteracoes.de(0));
        assertEquals(FaixaInteracoes.POUCAS, FaixaInteracoes.de(1));
        assertEquals(FaixaInteracoes.POUCAS, FaixaInteracoes.de(5));
        assertEquals(FaixaInteracoes.VARIAS, FaixaInteracoes.de(6));
        assertEquals(FaixaInteracoes.VARIAS, FaixaInteracoes.de(30));
        assertEquals(FaixaInteracoes.MUITAS, FaixaInteracoes.de(31));
    }

    // ---------------------------------------------------------------- orçamento

    @Test
    @DisplayName("orçamento: a capacidade passa, o excesso é descartado e contado, a janela renova")
    void orcamento() {
        AtomicLong relogio = new AtomicLong(1_000L);
        OrcamentoEventos orcamento = new OrcamentoEventos(3, relogio::get);
        assertTrue(orcamento.consumir());
        assertTrue(orcamento.consumir());
        assertTrue(orcamento.consumir());
        assertFalse(orcamento.consumir());
        assertFalse(orcamento.consumir());
        assertEquals(2, orcamento.descartados());
        relogio.addAndGet(59_999_999_999L);
        assertFalse(orcamento.consumir(), "um nanossegundo antes do minuto ainda é a mesma janela");
        relogio.addAndGet(1L);
        assertTrue(orcamento.consumir(), "um minuto depois, janela nova");
        assertEquals(3, orcamento.descartados());
        assertThrows(IllegalArgumentException.class, () -> new OrcamentoEventos(0, relogio::get));
    }

    // ---------------------------------------------------------------- teto do corpo

    @Test
    @DisplayName("teto do corpo: 8192 bytes passa, 8193 não, cabeçalho ilegível falha fechado")
    void tetoDoCorpo() {
        assertFalse(LimiteCorpoAcademia.excede("8192"));
        assertFalse(LimiteCorpoAcademia.excede(" 0 "));
        assertTrue(LimiteCorpoAcademia.excede("8193"));
        assertTrue(LimiteCorpoAcademia.excede("abc"));
        assertTrue(LimiteCorpoAcademia.excede("99999999999999999999"));
        assertEquals(8192, LimiteCorpoAcademia.TETO_BYTES);
    }
}
