package org.framework.net.academia.eventos.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.framework.net.academia.eventos.domain.EventoAcademia;
import org.framework.net.academia.eventos.domain.EventoAcademia.ErroJs;
import org.framework.net.academia.eventos.domain.EventoAcademia.FaixaInteracoes;
import org.framework.net.academia.eventos.domain.EventoAcademia.FaixaTempo;
import org.framework.net.academia.eventos.domain.EventoAcademia.TipoErroJs;
import org.framework.net.academia.eventos.domain.EventoAcademia.Visita;
import org.framework.net.academia.eventos.domain.OrcamentoEventos;
import org.framework.net.academia.eventos.domain.ports.TelemetriaAcademiaPort;
import org.framework.net.academia.trilha.application.TrilhaService;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Caso de uso: registrar um evento enviado pela página de uma lição.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> transforma o que o navegador manda em evento da Academia, só se
 * for de uma lição que existe e só se couber no orçamento — e conta o que aceitou e o que recusou,
 * para "ninguém mandou nada" e "eu estava recusando tudo" nunca parecerem a mesma coisa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b></p>
 * <ul>
 *   <li>lição fora do catálogo é recusada e não chega à telemetria;</li>
 *   <li>visita e erro de JavaScript têm orçamentos separados (um não esgota o outro);</li>
 *   <li>números viram faixas aqui — o valor exato não passa adiante;</li>
 *   <li>sem implementação da porta, o evento é contado como não registrado e a lição segue.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nunca lança por causa da telemetria; devolve o
 * {@link Resultado} com o motivo.</p>
 */
@ApplicationScoped
public class RegistrarEventoAcademia {

    private static final Logger LOG = Logger.getLogger(RegistrarEventoAcademia.class);

    /** Visitas aceitas por minuto, somando todos os visitantes. */
    static final int VISITAS_POR_MINUTO = 120;

    /** Erros de JavaScript aceitos por minuto, somando todos os visitantes. */
    static final int ERROS_POR_MINUTO = 30;

    /** O desfecho de um pedido, com o motivo quando não foi aceito. */
    public enum Resultado { ACEITO, LICAO_DESCONHECIDA, DESCARTADO_ORCAMENTO, SEM_TELEMETRIA }

    /** Contadores do que agiu e do que se absteve, desde o arranque. */
    public record Contadores(long aceitos, long licaoDesconhecida, long descartadosVisita,
                             long descartadosErro, long semTelemetria) {
    }

    @Inject
    TrilhaService trilha;

    @Inject
    Instance<TelemetriaAcademiaPort> telemetria;

    private final OrcamentoEventos orcamentoVisitas = new OrcamentoEventos(VISITAS_POR_MINUTO, System::nanoTime);
    private final OrcamentoEventos orcamentoErros = new OrcamentoEventos(ERROS_POR_MINUTO, System::nanoTime);
    private final AtomicLong aceitos = new AtomicLong();
    private final AtomicLong licaoDesconhecida = new AtomicLong();
    private final AtomicLong semTelemetria = new AtomicLong();
    /** Último log de descarte (nanoTime); -1 = nunca. Um log por minuto no máximo. */
    private final AtomicLong ultimoLogDescarte = new AtomicLong(-1);
    static final long INTERVALO_LOG_DESCARTE_NANOS = 60_000_000_000L;

    /** Resumo de visita: tempo e interações viram faixas antes de sair daqui. */
    public Resultado registrarVisita(String licaoId, long segundos, long interacoes, boolean concluiu) {
        if (!trilha.existe(licaoId)) {
            return recusarLicao(licaoId);
        }
        if (!orcamentoVisitas.consumir()) {
            logarDescarte(System.nanoTime());
            return Resultado.DESCARTADO_ORCAMENTO;
        }
        return publicar(new Visita(licaoId, FaixaTempo.de(segundos), FaixaInteracoes.de(interacoes), concluiu));
    }

    /** Erro de JavaScript: a mensagem é saneada na construção do evento. */
    public Resultado registrarErro(String licaoId, TipoErroJs tipo, String mensagem) {
        if (!trilha.existe(licaoId)) {
            return recusarLicao(licaoId);
        }
        if (!orcamentoErros.consumir()) {
            logarDescarte(System.nanoTime());
            return Resultado.DESCARTADO_ORCAMENTO;
        }
        return publicar(new ErroJs(licaoId, tipo, mensagem));
    }

    /**
     * Descarte por orçamento sai no log, no máximo uma vez por minuto, com os contadores desde o
     * arranque (auditoria ACAD-04: os contadores não tinham leitor e o descarte era silencioso).
     *
     * @return {@code true} se este descarte gerou o log
     */
    boolean logarDescarte(long agoraNanos) {
        long anterior = ultimoLogDescarte.get();
        if (anterior >= 0 && agoraNanos - anterior < INTERVALO_LOG_DESCARTE_NANOS) {
            return false;
        }
        if (!ultimoLogDescarte.compareAndSet(anterior, agoraNanos)) {
            return false;
        }
        Contadores c = contadores();
        LOG.warnf("academia descartou eventos por orçamento (desde o arranque): visitas=%d erros=%d aceitos=%d"
                        + " licaoDesconhecida=%d semTelemetria=%d",
                c.descartadosVisita(), c.descartadosErro(), c.aceitos(), c.licaoDesconhecida(), c.semTelemetria());
        return true;
    }

    public Contadores contadores() {
        return new Contadores(aceitos.get(), licaoDesconhecida.get(), orcamentoVisitas.descartados(),
                orcamentoErros.descartados(), semTelemetria.get());
    }

    private Resultado recusarLicao(String licaoId) {
        licaoDesconhecida.incrementAndGet();
        LOG.debugf("evento da Academia recusado motivo=LICAO_DESCONHECIDA tamanho=%d",
                licaoId == null ? -1 : licaoId.length());
        return Resultado.LICAO_DESCONHECIDA;
    }

    private Resultado publicar(EventoAcademia evento) {
        if (telemetria.isUnsatisfied() || telemetria.isAmbiguous()) {
            semTelemetria.incrementAndGet();
            return Resultado.SEM_TELEMETRIA;
        }
        try {
            telemetria.get().registrar(evento);
        } catch (RuntimeException falha) {
            semTelemetria.incrementAndGet();
            LOG.warnf("evento da Academia não registrado motivo=%s", falha.getClass().getSimpleName());
            return Resultado.SEM_TELEMETRIA;
        }
        aceitos.incrementAndGet();
        return Resultado.ACEITO;
    }
}
