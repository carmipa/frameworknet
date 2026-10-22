package org.framework.net.calculadora.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.calculadora.config.CalculadoraConfig;
import org.framework.net.calculadora.domain.BlocoIpv4;
import org.framework.net.calculadora.domain.LinhaCapacidade;
import org.framework.net.calculadora.domain.PlanoDivisao;
import org.framework.net.calculadora.domain.SubnetKernel;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Divisão de um bloco IPv4 em sub-redes de tamanho fixo (FLSM).
 *
 * <p><b>Propósito de negócio:</b> responde as quatro perguntas que um plano de
 * endereçamento sempre faz — "quantas /24 cabem no meu /21?", "quais são elas?",
 * "que prefixo usar para ter 6 sub-redes?" e "que prefixo usar para 500 hosts?".
 * É a ferramenta de bolso; o cenário completo com WAN e roteamento continua
 * sendo do módulo de Resolução de Problemas (VLSM).</p>
 *
 * <p><b>Invariantes do domínio:</b> o prefixo alvo nunca pode ser menor que o
 * do bloco base (não se divide um bloco em pedaços maiores que ele); o total de
 * sub-redes informado é sempre o valor matemático real, mesmo quando a listagem
 * é truncada pelo teto de renderização; toda contagem de endereços usa
 * {@code long}, pois um /0 tem 4.294.967.296 endereços.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> entradas inválidas ou pedidos
 * impossíveis (mais sub-redes ou mais hosts do que o bloco comporta) lançam
 * {@link CalculadoraException} com mensagem didática. Nenhum método devolve
 * {@code null}.</p>
 */
@ApplicationScoped
public class DivisaoService {

    @Inject
    SubnetKernel kernel;

    @Inject
    CalculadoraConfig config;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Divide o bloco base em sub-redes do prefixo alvo.
     *
     * <p><b>Propósito de negócio:</b> é o caso central — {@code 192.168.0.0/21}
     * com alvo {@code /24} devolve 8 sub-redes listadas e a matriz com todas as
     * outras opções de prefixo.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * o alvo for menor que o prefixo base ou estiver fora de 0–32.</p>
     */
    public PlanoDivisao dividirPorPrefixo(String blocoTexto, String prefixoBaseTexto, String prefixoAlvoTexto) {
        return telemetriaLogger.medir("calculadora", "dividir_por_prefixo", () -> {
            SubnetKernel.Rede base = lerBase(blocoTexto, prefixoBaseTexto);
            int alvo = kernel.parsePrefixo(prefixoAlvoTexto, "Prefixo alvo");
            return montarPlano(base, alvo, explicacaoPorPrefixo(base.prefixo(), alvo));
        });
    }

    /**
     * Escolhe o menor prefixo capaz de gerar ao menos {@code quantidade} sub-redes.
     *
     * <p><b>Invariantes do domínio:</b> como só se divide em potências de 2,
     * pedir 6 sub-redes entrega 8 — a sobra é informada na explicação em vez de
     * ser omitida.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * a quantidade não for um inteiro ≥ 1 ou se exigir prefixo maior que /32.</p>
     */
    public PlanoDivisao dividirPorQuantidade(String blocoTexto, String prefixoBaseTexto, String quantidadeTexto) {
        return telemetriaLogger.medir("calculadora", "dividir_por_quantidade", () -> {
            SubnetKernel.Rede base = lerBase(blocoTexto, prefixoBaseTexto);
            long desejadas = lerInteiroPositivo(quantidadeTexto, "Quantidade de sub-redes",
                    "Digite quantas sub-redes o plano precisa ter (ex.: 6), "
                            + "ou volte para o critério \"por prefixo alvo\".");
            int bits = kernel.bitsPara(desejadas);
            int alvo = base.prefixo() + bits;
            if (alvo > 32) {
                throw new CalculadoraException(
                        "Um /" + base.prefixo() + " não comporta " + desejadas + " sub-redes: seriam necessários "
                                + bits + " bits emprestados, o que passaria de /32. "
                                + "O máximo aqui é " + kernel.totalIps(base.prefixo()) + " sub-redes /32.");
            }
            long geradas = 1L << bits;
            long sobra = geradas - desejadas;
            String explicacao = "Pedido: " + desejadas + " sub-redes. Como a divisão só acontece em potências de 2, "
                    + bits + " bit(s) emprestado(s) geram " + geradas + " sub-redes /" + alvo
                    + (sobra > 0 ? " — sobram " + sobra + " sub-redes livres para crescimento." : " — encaixe exato.");
            return montarPlano(base, alvo, explicacao);
        });
    }

    /**
     * Escolhe o menor prefixo capaz de endereçar {@code hosts} hosts por sub-rede.
     *
     * <p><b>Propósito de negócio:</b> responder "que prefixo eu uso para N hosts?" com a mesma conta de
     * hosts úteis que o resto da calculadora mostra.</p>
     *
     * <p><b>Invariantes do domínio:</b> o alvo é o menor bloco cujos hosts úteis cobrem o pedido, pela
     * mesma regra de {@link SubnetKernel#hostsUteis}: até /30 reserva rede e broadcast (por isso 500 hosts
     * exigem /23 e não /24); /31 tem 2 hosts (RFC 3021) e /32 tem 1. Antes a conta fixa {@code hosts + 2}
     * mandava 2 hosts para /30 e recusava a base /31 dizendo, na mesma frase, que ela comportava 2 hosts
     * (auditoria CALC-26).</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * a quantidade de hosts não couber nem no bloco base inteiro.</p>
     */
    public PlanoDivisao dividirPorHosts(String blocoTexto, String prefixoBaseTexto, String hostsTexto) {
        return telemetriaLogger.medir("calculadora", "dividir_por_hosts", () -> {
            SubnetKernel.Rede base = lerBase(blocoTexto, prefixoBaseTexto);
            long hosts = lerInteiroPositivo(hostsTexto, "Hosts por sub-rede",
                    "Digite quantos hosts cada sub-rede precisa comportar (ex.: 500), "
                            + "ou volte para o critério \"por prefixo alvo\".");
            int alvo = 32;
            while (alvo > 0 && kernel.hostsUteis(alvo) < hosts) {
                alvo--;
            }
            if (kernel.hostsUteis(alvo) < hosts) {
                throw new CalculadoraException(
                        "Com rede e broadcast, " + hosts + " hosts exigiriam mais de 32 bits — "
                                + "não cabe em IPv4. O maior bloco possível (/0) tem "
                                + kernel.hostsUteis(0) + " hosts úteis.");
            }
            int hostBits = 32 - alvo;
            if (alvo < base.prefixo()) {
                throw new CalculadoraException(
                        "Cada sub-rede com " + hosts + " hosts exigiria um /" + alvo
                                + ", maior que o bloco base /" + base.prefixo() + ". "
                                + "O bloco base inteiro comporta " + kernel.hostsUteis(base.prefixo()) + " hosts.");
            }
            String explicacao = alvo >= 31
                    ? "Pedido: " + hosts + " host(s) por sub-rede → /" + alvo + (alvo == 31
                            ? ": enlace ponto a ponto, os dois endereços são de host (RFC 3021)."
                            : ": um endereço só (rota de host / loopback).")
                    : "Pedido: " + hosts + " hosts por sub-rede. Reservando rede e broadcast são "
                            + (hosts + 2) + " endereços, que exigem " + hostBits + " bits de host → /" + alvo
                            + ", com " + kernel.hostsUteis(alvo) + " hosts úteis (sobra de "
                            + (kernel.hostsUteis(alvo) - hosts) + ").";
            return montarPlano(base, alvo, explicacao);
        });
    }

    /**
     * Matriz "quantas sub-redes cabem" para todos os prefixos alvo possíveis.
     *
     * <p><b>Propósito de negócio:</b> é a tabela que responde o /21 de uma vez —
     * /22 dá 2, /24 dá 8, /26 dá 32, /30 dá 512 — sem exigir uma consulta por
     * prefixo.</p>
     */
    public List<LinhaCapacidade> matrizCapacidade(int prefixoBase, int prefixoDestaque) {
        List<LinhaCapacidade> linhas = new ArrayList<>();
        for (int alvo = prefixoBase; alvo <= 32; alvo++) {
            linhas.add(new LinhaCapacidade(
                    alvo,
                    kernel.formatIpv4(kernel.mascara(alvo)),
                    1L << (alvo - prefixoBase),
                    kernel.totalIps(alvo),
                    kernel.hostsUteis(alvo),
                    alvo == prefixoDestaque));
        }
        return linhas;
    }

    /**
     * Monta o plano com a listagem truncada no teto configurado.
     *
     * <p><b>Invariantes do domínio:</b> {@code totalSubredes} é sempre o valor
     * real; {@code exibidos} e {@code truncado} descrevem apenas o recorte
     * renderizado, para que a tela nunca sugira que a lista é completa quando
     * não é.</p>
     */
    private PlanoDivisao montarPlano(SubnetKernel.Rede base, int alvo, String explicacao) {
        if (alvo < base.prefixo()) {
            throw new CalculadoraException(
                    "O prefixo alvo /" + alvo + " é maior que o bloco base /" + base.prefixo()
                            + ". Sub-rede é sempre um pedaço menor: use um prefixo de /" + base.prefixo() + " a /32.");
        }

        long total = 1L << (alvo - base.prefixo());
        int teto = Math.max(1, config.maxLinhas());
        int exibidos = (int) Math.min(total, teto);
        long passo = kernel.totalIps(alvo);

        List<BlocoIpv4> blocos = new ArrayList<>(exibidos);
        for (int i = 0; i < exibidos; i++) {
            long rede = (base.endereco() + (long) i * passo) & SubnetKernel.MASK32;
            blocos.add(kernel.bloco(i + 1, rede, alvo));
        }

        String explicacaoFinal = explicacao;
        if (base.ajustado()) {
            explicacaoFinal = "O endereço " + base.entradaOriginal() + " é um host dentro da rede "
                    + kernel.formatIpv4(base.endereco()) + "/" + base.prefixo()
                    + "; o cálculo foi alinhado à rede. " + explicacao;
        }

        telemetriaLogger.logEvent("info", "calculadora", "divisao_calculada", Map.of(
                "prefixoBase", base.prefixo(),
                "prefixoAlvo", alvo,
                "totalSubredes", total,
                "exibidos", exibidos));

        return new PlanoDivisao(
                kernel.formatIpv4(base.endereco()),
                base.prefixo(),
                kernel.formatIpv4(kernel.mascara(base.prefixo())),
                kernel.totalIps(base.prefixo()),
                alvo,
                kernel.formatIpv4(kernel.mascara(alvo)),
                total,
                passo,
                kernel.hostsUteis(alvo),
                (long) (alvo - base.prefixo()),
                exibidos,
                total > exibidos,
                explicacaoFinal,
                blocos,
                matrizCapacidade(base.prefixo(), alvo));
    }

    private String explicacaoPorPrefixo(int prefixoBase, int alvo) {
        int bits = alvo - prefixoBase;
        if (bits < 0) {
            return "";
        }
        long total = 1L << bits;
        return "De /" + prefixoBase + " para /" + alvo + " são " + bits + " bit(s) emprestado(s) do campo de host: "
                + "2^" + bits + " = " + total + " sub-rede(s), cada uma com " + kernel.totalIps(alvo)
                + " endereços e " + kernel.hostsUteis(alvo) + " hosts úteis.";
    }

    private SubnetKernel.Rede lerBase(String blocoTexto, String prefixoBaseTexto) {
        Integer padrao = null;
        if (prefixoBaseTexto != null && !prefixoBaseTexto.isBlank()) {
            padrao = kernel.parsePrefixo(prefixoBaseTexto, "Prefixo do bloco base");
        }
        return kernel.parseRede(blocoTexto, padrao, "Bloco base");
    }

    /**
     * Lê um inteiro positivo de um campo de critério.
     *
     * <p><b>Comportamento em caso de falha:</b> campo em branco devolve uma
     * mensagem que diz o que preencher <i>e</i> qual a alternativa (trocar o
     * critério) — quem chega aqui já trocou o critério na tela e clicou sem
     * digitar, então "não foi informado" sozinho não ajuda a sair do lugar.</p>
     */
    private long lerInteiroPositivo(String texto, String nomeCampo, String dica) {
        String valor = texto == null ? "" : texto.strip().replace(".", "").replace(" ", "");
        if (valor.isEmpty()) {
            throw new CalculadoraException(nomeCampo + " não foi informado. " + dica);
        }
        if (valor.length() > 10 || !org.framework.net.shared.NumeroAscii.digitosAscii(valor)) {
            throw new CalculadoraException(nomeCampo + " deve ser um número inteiro. Recebido: " + texto);
        }
        long numero = org.framework.net.shared.NumeroAscii.parseLongEstrito(valor);
        if (numero < 1L) {
            throw new CalculadoraException(nomeCampo + " deve ser pelo menos 1.");
        }
        if (numero > SubnetKernel.MASK32) {
            throw new CalculadoraException(nomeCampo + " ultrapassa o espaço IPv4 (máximo 4.294.967.295).");
        }
        return numero;
    }
}
