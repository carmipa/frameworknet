package org.framework.net.resolucaoProblemas.application.parsing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.EnlaceReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.LanReconstruida;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.Resumo;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.ScriptCorrigido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.TabelaRoteador;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caso de uso da aba "Engenharia reversa" do módulo de Resolução de Problemas.
 *
 * <p><b>Propósito de negócio:</b> orquestra as três etapas — ler o texto colado,
 * auditar a coerência entre os scripts e reconstruir o projeto — devolvendo em uma
 * única chamada o que a tela precisa mostrar. É a inversão da aba "Projetar": lá
 * se parte de requisitos para produzir configuração; aqui se parte da configuração
 * para recuperar o projeto e apontar o que está errado nele.</p>
 *
 * <p><b>Invariantes do domínio:</b> o texto original do usuário nunca é alterado —
 * o script corrigido é material novo, ao lado do original. O evento de telemetria
 * é sempre atribuído ao módulo {@code resolucaoProblemas}, coerente com a
 * atribuição de rota do dashboard, e carrega as contagens que permitem saber se a
 * ferramenta está de fato encontrando erro ou só desenhando.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> texto vazio devolve um cenário vazio
 * com {@code cenarioConsistente = false}, sem exceção — cabe à tela dizer que não
 * havia o que interpretar. Nenhuma etapa lança por conteúdo malformado: defeito de
 * configuração vira achado, não erro de servidor.</p>
 */
@ApplicationScoped
public class EngenhariaReversaService {

    private static final String MODULO = "resolucaoProblemas";
    private static final String EVENTO = "engenharia_reversa_parse";

    /**
     * Teto de roteadores por execução. Laboratório real tem poucas dezenas; sem teto, 256 KB colados
     * (o limite do campo) viravam ~1200 roteadores e uma resposta de 6,9 MB — 30 por minuto por IP.
     */
    public static final int MAX_ROTEADORES = 50;

    /**
     * Teto de interfaces COM endereço IP somadas em todos os roteadores — o mesmo do IPv6 ({@code Ipv6SubnetKernel}). O teto de
     * roteadores sozinho não bastava: 50 roteadores com ~66 interfaces cada (3300 no total, cabe no campo de 256 KB)
     * levavam 4,2 s de CPU e 9,2 MB de resposta por requisição, com o tempo crescendo mais que linearmente
     * (auditoria de 22/10/2026, BACK-01). Laboratório didático real tem poucas dezenas de interfaces.
     */
    public static final int MAX_INTERFACES = 200;

    @Inject
    CiscoConfigParser parser;

    @Inject
    AuditoriaConfiguracaoService auditoriaService;

    @Inject
    ReconstrucaoTopologiaService reconstrucaoService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Interpreta a configuração colada e devolve o projeto reconstruído.
     *
     * <p><b>Comportamento em caso de falha:</b> entrada nula ou em branco produz
     * cenário vazio; mais de {@link #MAX_ROTEADORES} roteadores ou de {@link #MAX_INTERFACES} interfaces somadas lança
     * {@link EntradaInvalidaException} com a contagem, antes de montar tabela ou desenho.</p>
     */
    public CenarioReconstruido interpretar(String texto) {
        ConfiguracaoLida lida = parser.analisar(texto);
        if (lida.roteadores().size() > MAX_ROTEADORES) {
            throw new EntradaInvalidaException("A engenharia reversa lê até " + MAX_ROTEADORES
                    + " roteadores por vez; o texto colado tem " + lida.roteadores().size()
                    + ". Divida em partes e cole uma de cada vez.");
        }
        // Conta só interface COM endereço IP — é ela que entra no cruzamento de LANs e enlaces (o custo medido). Um
        // switch empilhado com 200 portas sem IP é um equipamento só e não pode ser "dividido em partes"
        // (revisão de boa-fé, 22/10/2026).
        long interfaces = lida.roteadores().stream()
                .flatMap(r -> r.interfaces().stream())
                .filter(i -> i.ip() != null && !i.ip().isBlank())
                .count();
        if (interfaces > MAX_INTERFACES) {
            throw new EntradaInvalidaException("A engenharia reversa lê até " + MAX_INTERFACES
                    + " interfaces com endereço IP por vez, somando todos os roteadores; o texto colado tem " + interfaces
                    + ". Cole os roteadores em partes, ou apague os blocos de interface que não interessam ao exercício.");
        }
        AuditoriaConfiguracaoService.ResultadoAuditoria auditoria = auditoriaService.auditar(lida);

        List<RoteadorLido> roteadores = auditoria.roteadores();
        List<AchadoConfiguracao> achados = auditoria.achados();

        List<LanReconstruida> lans = reconstrucaoService.montarLans(roteadores);
        List<EnlaceReconstruido> enlaces = reconstrucaoService.montarEnlaces(roteadores);
        List<TabelaRoteador> tabelas = reconstrucaoService.montarTabelas(roteadores);
        List<ScriptCorrigido> scripts = reconstrucaoService.montarScripts(roteadores, achados);
        String mermaid = reconstrucaoService.montarMermaid(roteadores, enlaces, lans);

        int corrigidos = contar(achados, AchadoConfiguracao.Severidade.ERRO_CORRIGIDO);
        int semCorrecao = contar(achados, AchadoConfiguracao.Severidade.ERRO_SEM_CORRECAO);
        int avisos = contar(achados, AchadoConfiguracao.Severidade.AVISO);
        boolean consistente = !roteadores.isEmpty() && semCorrecao == 0
                && auditoria.pendencias().isEmpty()
                && enlaces.stream().allMatch(EnlaceReconstruido::completo);

        Resumo resumo = new Resumo(roteadores.size(), enlaces.size(), lans.size(),
                corrigidos, semCorrecao, avisos, lida.linhasNaoInterpretadas().size(), consistente);

        registrarTelemetria(lida, resumo, auditoria.pendencias().size());

        return new CenarioReconstruido(roteadores, achados, lida.linhasNaoInterpretadas(),
                lans, enlaces, tabelas, auditoria.pendencias(), scripts, mermaid, resumo);
    }

    private int contar(List<AchadoConfiguracao> achados, AchadoConfiguracao.Severidade severidade) {
        return (int) achados.stream().filter(a -> a.severidade() == severidade).count();
    }

    /**
     * Registra a interpretação na telemetria.
     *
     * <p><b>Propósito de negócio:</b> as contagens respondem se a aba está sendo
     * usada para corrigir configuração de verdade ou apenas para desenhar — é o que
     * decide se vale ampliar o interpretador para mais protocolos.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> falha de telemetria é absorvida
     * pelo logger e nunca derruba a interpretação.</p>
     */
    private void registrarTelemetria(ConfiguracaoLida lida, Resumo resumo, int pendencias) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("linhas", lida.totalLinhas());
        campos.put("roteadores", resumo.roteadores());
        campos.put("enlaces", resumo.enlaces());
        campos.put("errosCorrigidos", resumo.errosCorrigidos());
        campos.put("errosSemCorrecao", resumo.errosSemCorrecao());
        campos.put("avisos", resumo.avisos());
        campos.put("naoInterpretadas", resumo.linhasNaoInterpretadas());
        campos.put("pendencias", pendencias);
        campos.put("consistente", resumo.cenarioConsistente());
        String status = resumo.roteadores() == 0 ? "vazio"
                : resumo.cenarioConsistente() ? "ok" : "com_pendencias";
        telemetriaLogger.logEvent("info", MODULO, EVENTO, status, campos);
    }
}
