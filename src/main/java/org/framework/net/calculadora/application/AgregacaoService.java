package org.framework.net.calculadora.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.calculadora.config.CalculadoraConfig;
import org.framework.net.calculadora.domain.BlocoIpv4;
import org.framework.net.calculadora.domain.ResultadoAgregacao;
import org.framework.net.calculadora.domain.ResultadoFaixa;
import org.framework.net.calculadora.domain.ResultadoRelacao;
import org.framework.net.calculadora.domain.SubnetKernel;
import org.framework.net.calculadora.exception.CalculadoraException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Operações inversas e auxiliares à divisão: sumarizar, comparar e converter faixas.
 *
 * <p><b>Propósito de negócio:</b> cobre o outro lado do plano de endereçamento —
 * juntar várias redes em uma rota resumo (o que se anuncia no OSPF/EIGRP),
 * descobrir se dois blocos colidem antes de o conflito chegar à produção, e
 * traduzir "de tal IP até tal IP" na lista de prefixos que o equipamento aceita.</p>
 *
 * <p><b>Invariantes do domínio:</b> o bloco resumo sempre contém todas as redes
 * informadas; a lista de blocos de uma faixa cobre exatamente a faixa pedida,
 * sem sobra nem falta; todas as contagens usam {@code long}, porque a faixa
 * completa 0.0.0.0–255.255.255.255 tem 4.294.967.296 endereços.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lista vazia, rede malformada, faixa
 * invertida ou quantidade acima do teto configurado lançam
 * {@link CalculadoraException} com mensagem didática. Nenhum método devolve
 * {@code null}.</p>
 */
@ApplicationScoped
public class AgregacaoService {

    @Inject
    SubnetKernel kernel;

    @Inject
    CalculadoraConfig config;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Calcula o menor prefixo único que cobre todas as redes informadas.
     *
     * <p><b>Propósito de negócio:</b> é a rota resumo. Informando
     * 192.168.0.0/24, 192.168.1.0/24, 192.168.2.0/24 e 192.168.3.0/24, devolve
     * 192.168.0.0/22 — um anúncio no lugar de quatro.</p>
     *
     * <p><b>Invariantes do domínio:</b> o resumo nunca deixa rede de fora; o
     * campo {@code enderecosExtras} expõe quanto espaço alheio o resumo arrasta,
     * e vale 0 quando a agregação é exata. Sobreposições entre as entradas são
     * contadas uma única vez, senão o "extra" apareceria negativo.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * nenhuma rede for informada ou se o total passar do teto configurado.</p>
     */
    public ResultadoAgregacao sumarizar(String redesTexto) {
        return telemetriaLogger.medir("calculadora", "sumarizar", () -> {
            List<BlocoIpv4> entradas = lerRedes(redesTexto);

            long menorInicio = Long.MAX_VALUE;
            long maiorFim = Long.MIN_VALUE;
            List<long[]> intervalos = new ArrayList<>(entradas.size());
            for (BlocoIpv4 bloco : entradas) {
                long inicio = kernel.parseIpv4(bloco.rede(), "Rede");
                long fim = kernel.broadcast(inicio, bloco.prefixo());
                menorInicio = Math.min(menorInicio, inicio);
                maiorFim = Math.max(maiorFim, fim);
                intervalos.add(new long[]{inicio, fim});
            }

            int prefixoResumo = prefixoComum(menorInicio, maiorFim);
            long resumo = menorInicio & kernel.mascara(prefixoResumo);
            long totalResumo = kernel.totalIps(prefixoResumo);
            long cobertos = unirIntervalos(intervalos);
            long extras = totalResumo - cobertos;

            telemetriaLogger.logEvent("info", "calculadora", "sumarizacao_calculada", Map.of(
                    "redes", entradas.size(),
                    "prefixoResumo", prefixoResumo,
                    "extras", extras));

            return new ResultadoAgregacao(
                    kernel.formatIpv4(resumo),
                    prefixoResumo,
                    kernel.formatIpv4(kernel.mascara(prefixoResumo)),
                    kernel.wildcard(prefixoResumo),
                    kernel.formatIpv4(resumo),
                    kernel.formatIpv4(kernel.broadcast(resumo, prefixoResumo)),
                    totalResumo,
                    cobertos,
                    extras,
                    extras == 0L,
                    entradas,
                    comandosSumarizacao(kernel.formatIpv4(resumo), prefixoResumo));
        });
    }

    /**
     * Classifica a relação entre dois blocos: iguais, contido ou disjuntos.
     *
     * <p><b>Propósito de negócio:</b> responde "essas duas redes conflitam?" e
     * "essa ACL cobre esse host?" — as duas perguntas que antecedem um plano de
     * endereçamento quebrado.</p>
     *
     * <p><b>Invariantes do domínio:</b> entre dois blocos CIDR válidos não existe
     * sobreposição parcial: ou são disjuntos, ou um contém o outro. O tipo
     * {@code SOBREPOSTOS} existe para completude e não deve ocorrer aqui.</p>
     */
    public ResultadoRelacao comparar(String blocoATexto, String blocoBTexto) {
        return telemetriaLogger.medir("calculadora", "comparar_blocos", () -> {
            SubnetKernel.Rede a = kernel.parseRede(blocoATexto, null, "Bloco A");
            SubnetKernel.Rede b = kernel.parseRede(blocoBTexto, null, "Bloco B");

            long inicioA = a.endereco();
            long fimA = kernel.broadcast(inicioA, a.prefixo());
            long inicioB = b.endereco();
            long fimB = kernel.broadcast(inicioB, b.prefixo());

            BlocoIpv4 blocoA = kernel.bloco(1, inicioA, a.prefixo());
            BlocoIpv4 blocoB = kernel.bloco(2, inicioB, b.prefixo());
            long compartilhados = Math.max(0L, Math.min(fimA, fimB) - Math.max(inicioA, inicioB) + 1L);

            String tipo;
            String rotulo;
            String explicacao;
            boolean conflito = true;

            if (inicioA == inicioB && fimA == fimB) {
                tipo = "IGUAIS";
                rotulo = "Blocos idênticos";
                explicacao = "A e B descrevem exatamente a mesma rede. Em um plano de endereçamento isso é "
                        + "duplicidade: os dois anúncios competem pela mesma faixa.";
            } else if (inicioA <= inicioB && fimA >= fimB) {
                tipo = "A_CONTEM_B";
                rotulo = "A contém B";
                explicacao = blocoA.cidr() + " engloba " + blocoB.cidr() + ". Toda a faixa de B está dentro de A, "
                        + "então uma regra ou rota para A também alcança B — e um endereçamento independente "
                        + "em B colide com A.";
            } else if (inicioB <= inicioA && fimB >= fimA) {
                tipo = "B_CONTEM_A";
                rotulo = "B contém A";
                explicacao = blocoB.cidr() + " engloba " + blocoA.cidr() + ". Toda a faixa de A está dentro de B, "
                        + "então uma regra ou rota para B também alcança A.";
            } else if (inicioA <= fimB && inicioB <= fimA) {
                tipo = "SOBREPOSTOS";
                rotulo = "Sobreposição parcial";
                explicacao = "As faixas se cruzam sem que uma contenha a outra — situação impossível entre dois "
                        + "prefixos CIDR válidos e sinal de entrada inconsistente.";
            } else {
                tipo = "DISJUNTOS";
                rotulo = "Sem conflito";
                explicacao = blocoA.cidr() + " e " + blocoB.cidr() + " não compartilham nenhum endereço. "
                        + "Podem coexistir no mesmo plano de endereçamento.";
                conflito = false;
            }

            return new ResultadoRelacao(tipo, rotulo, explicacao, conflito, blocoA, blocoB, compartilhados);
        });
    }

    /**
     * Converte uma faixa arbitrária de IPs na menor lista de blocos CIDR.
     *
     * <p><b>Propósito de negócio:</b> traduz o pedido do mundo real ("libere de
     * 10.0.0.5 até 10.0.3.200") para os prefixos que entram na ACL.</p>
     *
     * <p><b>Invariantes do domínio:</b> a união dos blocos é exatamente a faixa
     * pedida; a cada passo é escolhido o maior bloco que ao mesmo tempo está
     * alinhado ao endereço atual e não ultrapassa o fim da faixa.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> {@link CalculadoraException} se
     * o início for maior que o fim. Faixas que gerariam mais blocos que o teto
     * configurado são truncadas com {@code truncado = true}.</p>
     */
    public ResultadoFaixa faixaParaCidr(String inicioTexto, String fimTexto) {
        return telemetriaLogger.medir("calculadora", "faixa_para_cidr", () -> {
            long inicio = kernel.parseIpv4(inicioTexto, "IP inicial");
            long fim = kernel.parseIpv4(fimTexto, "IP final");
            if (inicio > fim) {
                throw new CalculadoraException(
                        "O IP inicial (" + kernel.formatIpv4(inicio) + ") é maior que o final ("
                                + kernel.formatIpv4(fim) + "). Inverta os campos.");
            }

            int teto = Math.max(1, config.maxLinhas());
            List<BlocoIpv4> blocos = new ArrayList<>();
            long atual = inicio;
            boolean truncado = false;
            int indice = 0;

            while (atual <= fim) {
                if (blocos.size() >= teto) {
                    truncado = true;
                    break;
                }
                // Maior bloco alinhado em "atual": limitado pelos zeros à direita do endereço.
                int bitsAlinhamento = atual == 0L ? 32 : Math.min(32, Long.numberOfTrailingZeros(atual));
                // Maior bloco que ainda cabe no que resta da faixa.
                long restantes = fim - atual + 1L;
                int bitsRestantes = 63 - Long.numberOfLeadingZeros(restantes);
                int bits = Math.min(bitsAlinhamento, bitsRestantes);

                blocos.add(kernel.bloco(++indice, atual, 32 - bits));
                atual += 1L << bits;
            }

            long total = fim - inicio + 1L;
            telemetriaLogger.logEvent("info", "calculadora", "faixa_convertida", Map.of(
                    "enderecos", total,
                    "blocos", blocos.size(),
                    "truncado", truncado));

            return new ResultadoFaixa(
                    kernel.formatIpv4(inicio),
                    kernel.formatIpv4(fim),
                    total,
                    blocos.size(),
                    truncado,
                    blocos,
                    comandosFaixa(blocos));
        });
    }

    /**
     * Maior prefixo em que os dois extremos ainda coincidem.
     *
     * <p><b>Invariantes do domínio:</b> usa {@code Long.numberOfLeadingZeros}
     * sobre 64 bits e desconta os 32 bits altos vazios; calcular direto em 32
     * bits produziria prefixo errado. XOR zero significa extremos iguais, e o
     * prefixo é /32.</p>
     */
    private int prefixoComum(long inicio, long fim) {
        long diferenca = (inicio ^ fim) & SubnetKernel.MASK32;
        if (diferenca == 0L) {
            return 32;
        }
        return (int) (Long.numberOfLeadingZeros(diferenca) - 32L);
    }

    /**
     * Soma o tamanho da união dos intervalos, contando sobreposição uma vez só.
     */
    private long unirIntervalos(List<long[]> intervalos) {
        List<long[]> ordenados = new ArrayList<>(intervalos);
        ordenados.sort(Comparator.comparingLong(par -> par[0]));
        long total = 0L;
        long inicioAtual = -1L;
        long fimAtual = -1L;
        for (long[] par : ordenados) {
            if (inicioAtual < 0L) {
                inicioAtual = par[0];
                fimAtual = par[1];
            } else if (par[0] <= fimAtual + 1L) {
                fimAtual = Math.max(fimAtual, par[1]);
            } else {
                total += fimAtual - inicioAtual + 1L;
                inicioAtual = par[0];
                fimAtual = par[1];
            }
        }
        if (inicioAtual >= 0L) {
            total += fimAtual - inicioAtual + 1L;
        }
        return total;
    }

    private List<BlocoIpv4> lerRedes(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new CalculadoraException(
                    "Informe pelo menos duas redes para sumarizar, uma por linha (ex.: 192.168.0.0/24).");
        }
        String[] linhas = texto.split("[,;\\n]");
        List<BlocoIpv4> blocos = new ArrayList<>();
        int teto = Math.max(1, config.maxRedesAgregacao());
        for (String linha : linhas) {
            String valor = linha.strip();
            if (valor.isEmpty()) {
                continue;
            }
            if (blocos.size() >= teto) {
                throw new CalculadoraException(
                        "Máximo de " + teto + " redes por sumarização. Divida a lista em lotes menores.");
            }
            SubnetKernel.Rede rede = kernel.parseRede(valor, 32, "Rede \"" + valor + "\"");
            blocos.add(kernel.bloco(blocos.size() + 1, rede.endereco(), rede.prefixo()));
        }
        if (blocos.isEmpty()) {
            throw new CalculadoraException("Nenhuma rede válida foi encontrada na lista.");
        }
        return blocos;
    }

    private List<String> comandosSumarizacao(String resumo, int prefixo) {
        String mascara = kernel.formatIpv4(kernel.mascara(prefixo));
        String wildcard = kernel.wildcard(prefixo);
        return List.of(
                "! OSPF — resumo entre áreas (no ABR)",
                "router ospf 1",
                " area 0 range " + resumo + " " + mascara,
                "!",
                "! EIGRP — resumo na interface de saída",
                "interface GigabitEthernet0/0",
                " ip summary-address eigrp 100 " + resumo + " " + mascara,
                "!",
                "! Rota estática agregada",
                "ip route " + resumo + " " + mascara + " <next-hop>",
                "!",
                "! ACL usando a wildcard do resumo",
                "access-list 10 permit " + resumo + " " + wildcard);
    }

    private List<String> comandosFaixa(List<BlocoIpv4> blocos) {
        List<String> linhas = new ArrayList<>();
        linhas.add("! ACL estendida cobrindo exatamente a faixa pedida");
        for (BlocoIpv4 bloco : blocos) {
            linhas.add("access-list 101 permit ip " + bloco.rede() + " " + bloco.wildcard() + " any");
        }
        return linhas;
    }
}
