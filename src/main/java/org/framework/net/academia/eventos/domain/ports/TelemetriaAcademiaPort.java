package org.framework.net.academia.eventos.domain.ports;

import org.framework.net.academia.eventos.domain.EventoAcademia;

/**
 * Por onde os eventos da Academia saem para a telemetria do site.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a Academia mede o que acontece nas lições sem importar a
 * telemetria do site (fatia não conhece módulo de fora — INV-ACAD-001). Quem implementa é um
 * adaptador do lado da telemetria.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> {@code Visita} pode entrar no dataset público (só faixas e id
 * de lição); {@code ErroJs} fica FORA do dataset público, num buffer próprio com teto
 * (INV-ACAD-005).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> a implementação nunca lança para o chamador: falha de
 * registro é logada e engolida, porque telemetria não pode derrubar a lição.</p>
 */
public interface TelemetriaAcademiaPort {

    /** Registra um evento já validado e saneado. */
    void registrar(EventoAcademia evento);
}
