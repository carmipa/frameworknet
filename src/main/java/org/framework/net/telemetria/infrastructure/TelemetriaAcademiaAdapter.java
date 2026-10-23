package org.framework.net.telemetria.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.academia.eventos.domain.EventoAcademia;
import org.framework.net.academia.eventos.domain.EventoAcademia.ErroJs;
import org.framework.net.academia.eventos.domain.EventoAcademia.Visita;
import org.framework.net.academia.eventos.domain.ports.TelemetriaAcademiaPort;
import org.framework.net.telemetria.TelemetriaLogger;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado da telemetria da porta de eventos da Academia.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a Academia não conhece a telemetria do site (fronteira
 * congelada); é a telemetria, que é transversal, que implementa a porta declarada pela Academia.
 * Assim a lição é medida sem que ela importe nada de fora.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO (INV-ACAD-005):</b></p>
 * <ul>
 *   <li>visita vai para a telemetria comum com lista fechada de campos (lição, faixa de tempo,
 *       faixa de interações, concluiu) — pode entrar no dataset público;</li>
 *   <li>erro de JavaScript NUNCA vai para a telemetria comum (que alimenta o dataset público):
 *       fica num buffer próprio de no máximo {@link #TETO_ERROS} registros e no log da aplicação;
 *       o mais antigo sai quando o buffer enche, e a saída é contada.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> nunca lança para a Academia; falha da telemetria comum
 * é logada em WARN com a causa.</p>
 */
@ApplicationScoped
public class TelemetriaAcademiaAdapter implements TelemetriaAcademiaPort {

    private static final Logger LOG = Logger.getLogger(TelemetriaAcademiaAdapter.class);
    private static final String MODULO = "academia";

    /** Quantos erros de JavaScript o buffer guarda. */
    static final int TETO_ERROS = 200;

    /** Um erro de JavaScript guardado no buffer próprio. */
    public record ErroGuardado(Instant quando, String licaoId, String tipo, String mensagem) {
    }

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Console do painel da Telemetria (só o dono vê; não é o dataset público). */
    @Inject
    org.framework.net.telemetria.TelemetriaConsoleBuffer console;

    private final Deque<ErroGuardado> erros = new ArrayDeque<>();
    private long errosDescartados;

    @Override
    public void registrar(EventoAcademia evento) {
        try {
            switch (evento) {
                case Visita visita -> registrarVisita(visita);
                case ErroJs erro -> guardarErro(erro);
            }
        } catch (RuntimeException falha) {
            LOG.warnf("telemetria da Academia falhou evento=%s motivo=%s",
                    evento.getClass().getSimpleName(), falha.getClass().getSimpleName());
        }
    }

    private void registrarVisita(Visita visita) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("licao", visita.licaoId());
        campos.put("tempo", visita.tempo().name());
        campos.put("interacoes", visita.interacoes().name());
        campos.put("concluiu", visita.concluiu());
        telemetriaLogger.logEvent("info", MODULO, "academia_visita", campos);
    }

    private synchronized void guardarErro(ErroJs erro) {
        if (erros.size() >= TETO_ERROS) {
            erros.removeFirst();
            errosDescartados++;
        }
        erros.addLast(new ErroGuardado(Instant.now(), erro.licaoId(), erro.tipo().name(), erro.mensagem()));
        LOG.warnf("academia erro_js licao=%s tipo=%s mensagem=%s", erro.licaoId(), erro.tipo(), erro.mensagem());
        // ACAD-04: o buffer não tinha leitor fora dos testes. O console do painel é onde o dono olha.
        if (console != null) {
            console.append("warn", "academia erro_js licao=" + erro.licaoId() + " tipo=" + erro.tipo()
                    + " mensagem=" + erro.mensagem() + " (buffer " + erros.size() + "/" + TETO_ERROS
                    + ", descartados " + errosDescartados + ")");
        }
    }

    /** Os erros guardados, do mais antigo ao mais novo. */
    public synchronized List<ErroGuardado> errosGuardados() {
        return List.copyOf(erros);
    }

    /** Quantos erros saíram do buffer por ele estar cheio. */
    public synchronized long errosDescartados() {
        return errosDescartados;
    }
}
