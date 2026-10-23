package org.framework.net.analiseDidatica.support;

import org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * FRONT-06: a "Causa" abaixo do erro da Análise Didática contradizia a mensagem — "use a aba CIDR" virava
 * "o prefixo precisa ser de 0 a 32", o domínio vazio virava "não pôde ser resolvido no DNS", e o
 * "inválido: octeto 2 fora de 0-255" da máscara nunca achava regra.
 *
 * <p>O observado vem dos produtores (o parser real do IPv4, e as frases fixas conferidas na fonte); o
 * esperado é o que a mensagem diz, escrito à mão (A3). Os pares de fronteira carregam o MESMO sinal que
 * enganava a regra antiga (a palavra CIDR, o "No modo", o "domínio") e têm causas diferentes (A1).</p>
 */
class ErroDidaticoServiceTest {

    private final ErroDidaticoService servico = new ErroDidaticoService();

    static Stream<Arguments> frasesDosProdutores() {
        return Stream.of(
                arguments("No modo Máscara Decimal, informe a máscara contígua (ex.: 255.255.255.240). "
                                + "Esta aba é só para análise da máscara/prefixo; com IP + máscara use a aba CIDR.",
                        "No modo Máscara Decimal, informe a máscara contígua", "Faltou preencher"),
                arguments("No modo Comparador CIDR, informe um endereço IP.",
                        "No modo Comparador CIDR, informe um endereço IP.", "Faltou preencher"),
                arguments("No modo Descobrir CIDR do IP, informe um endereço IP.",
                        "No modo Descobrir CIDR do IP, informe um endereço IP.", "Faltou preencher"),
                arguments("No modo CIDR, informe o endereço IPv4 e o CIDR (0–32), "
                                + "ou apenas o IPv4 para descobrir o / automaticamente pelo 1º octeto.",
                        "No modo CIDR, informe o endereço IPv4 e o CIDR (0–32), ", "Faltou preencher"),
                arguments("Selecione um modo e preencha o campo correspondente.",
                        "Selecione um modo e preencha o campo correspondente.", "Faltou preencher"),
                arguments("O CIDR deve ser um número inteiro entre 0 e 32.",
                        "O CIDR deve ser um número inteiro entre 0 e 32.", "de 0 a 32"),
                arguments("No modo Domínio, o CIDR (se informado) deve ser um número inteiro entre 0 e 32.",
                        "No modo Domínio, o CIDR (se informado) deve ser um número inteiro entre 0 e 32.", "de 0 a 32"),
                arguments("CIDR inválido para cálculo de rede.", "CIDR inválido para cálculo de rede.", "de 0 a 32"),
                arguments("Máscara decimal inválida. Use máscara contígua (ex.: 255.255.255.0), não valores como 255.0.255.0.",
                        "Máscara decimal inválida. Use máscara contígua ", "contíguos"),
                arguments("Wildcard inválida. Use formato x.x.x.x com inverso de máscara contígua (ex.: 0.0.15.255).",
                        "Wildcard inválida. Use formato x.x.x.x com inverso de máscara contígua ", "inverso de uma máscara"),
                arguments("Domínio/hostname inválido. Use algo como google.com ou servidor.local.",
                        "Domínio/hostname inválido. Use algo como google.com ou servidor.local.", "forma de um hostname"),
                arguments("Domínio/hostname vazio.", "Domínio/hostname vazio.", "Faltou preencher"),
                arguments("Não foi possível resolver o domínio informado: nao-existe.example",
                        "Não foi possível resolver o domínio informado: ", "não devolveu endereço"),
                arguments("Erro interno ao resolver DNS. Tente novamente.",
                        "Erro interno ao resolver DNS. Tente novamente.", "lado do servidor"),
                arguments("IPv6 inválido: formato não reconhecido.", "IPv6 inválido: formato não reconhecido.", "formato IPv6"),
                arguments("Esta consulta do histórico não pertence a este navegador ou já expirou "
                                + "(o histórico fica só no navegador em que foi feito e some ao fechá-lo).",
                        "Esta consulta do histórico não pertence a este navegador ou já expirou ", "só no navegador"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("frasesDosProdutores")
    void causaNaoContradizAMensagem(String mensagem, String trechoNaFonte, String esperado) {
        String causa = servico.explicar(mensagem).get("causa");
        assertTrue(causa.contains(esperado), "para \"" + mensagem + "\" veio: " + causa);
    }

    /** A tabela acima não pode envelhecer em silêncio: cada frase fixa ainda tem de existir na fonte. */
    @ParameterizedTest(name = "[{index}] a fonte ainda emite")
    @MethodSource("frasesDosProdutores")
    void fraseAindaEEmitidaPelaFonte(String mensagem, String trechoNaFonte, String esperado) throws IOException {
        Path raiz = Path.of("src/main/java/org/framework/net/analiseDidatica");
        assertTrue(Files.isDirectory(raiz), "fonte não encontrada: " + raiz.toAbsolutePath());
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            boolean achou = arquivos.filter(p -> p.toString().endsWith(".java")).anyMatch(p -> {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8).contains(trechoNaFonte);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertTrue(achou, "nenhum produtor emite mais: " + trechoNaFonte);
        }
    }

    /** O parser real do IPv4 é o produtor: as mensagens compostas ("Máscara decimal inválido: octeto 4...") vêm dele. */
    @Test
    void mensagensDoParserIpv4TemCausaCerta() {
        Ipv4Kernel kernel = new Ipv4Kernel();
        String[][] casos = {
                {"1.2.3", "IP", "forma de um IPv4"},
                {"1..2.3", "Máscara decimal", "vazio ou tem algo"},
                {"1.a.2.3", "Wildcard mask", "vazio ou tem algo"},
                {"255.255.255.300", "Máscara decimal", "de 0 a 255"},
                {"0.0.0.999", "Wildcard mask", "de 0 a 255"},
                {"010.1.1.1", "IP", "zero à esquerda"},
                {"", "IP", "Faltou preencher"},
        };
        for (String[] caso : casos) {
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> kernel.parseIpv4Parts(caso[0], caso[1]), "o parser aceitou " + caso[0]);
            String causa = servico.explicar(ex.getMessage()).get("causa");
            assertTrue(causa.contains(caso[2]), "para \"" + ex.getMessage() + "\" veio: " + causa);
        }
    }

    @Test
    void erroVazioNaoTemExplicacao() {
        assertNull(servico.explicar(null));
        assertNull(servico.explicar("   "));
    }

    /** Mensagem sem regra recebe o texto genérico, que não afirma causa nenhuma — nunca a de outro erro. */
    @Test
    void mensagemDesconhecidaRecebeOTextoGenerico() {
        String causa = servico.explicar("Alguma recusa nova que ninguém mapeou.").get("causa");
        assertTrue(causa.contains("não passou nas validações"), causa);
        assertFalse(causa.contains("0 a 32") || causa.contains("DNS"), causa);
    }
}
